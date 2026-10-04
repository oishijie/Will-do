# 闪记（Flash Note）P0 实现计划

> 分支：`feature/flash-note`
> 基线：`main` @ `8845248`

## 一、需求与范围

对标 realme「小布记忆」的轻量收藏：**一键收藏网页/公众号文章 → 后台 AI 生成摘要 → 保留原文链接可回跳**。

以「闪记」形态并入现有**随手记**（`feature/note`），不新建模块。

**P0 范围**：分享链接 → 抽取正文 → 存为闪记条目 → AI 摘要 → 列表展示 + 点击跳转
**P0 不含**：云同步扩展、批量管理、摘要历史版本、体表可读性分级

## 二、关键设计决策（已核实）

### 2.1 数据层：不新建表，扩展段落类型

`NoteEntity.documentJson` 存的是序列化 `NoteDocument`，**新增段落类型不需要 Room migration**（DB 仍 22 版）。

```
NoteParagraphType 新增：
  SUMMARY      AI 摘要（内容为 "__PENDING__" / "__FAILED__" 或真实摘要）
  SOURCE_LINK  原文链接 + 来源站点名
```

复用现成能力：搜索（`noteSearchQuery` 已支持）、`NoteCard` 列表卡片、`NoteEditorScreen` 详情页、备份导出。

### 2.2 收藏入口：复用现成分享中转模式

照 `ImagePinHandleActivity` + `activity-alias` 的模式新建：

```
platform/entry/flash/FlashNoteHandleActivity   ← 中转，Theme.ShareRelay
  + activity-alias「闪记」                      ← SEND + text/* + EXTRA_TEXT
```

项目已有 `ACTION_SEND` 处理先例（`ImagePinHandleActivity:61`），**无需新做悬浮按钮**。

### 2.3 正文抽取：无障碍读节点树

`TextAccessibilityService` 已声明且已使用 `rootInActiveWindow`，**读节点树抽正文可行，无需新权限**。

降级策略（必须做）：
| 情况 | 处理 |
|---|---|
| 正文 ≥ 200 字 | 正常抽取 |
| 正文 < 200 字（微信未展开全文） | 提示「点击展开全文后重新收藏」 |
| 完全读不到节点 | 仅存标题 + 链接，摘要标记 `__FAILED__` |

### 2.4 AI 摘要：WorkManager 异步

项目已有 WorkManager（4 处在用），**必须走 Worker 而非自起协程**——否则 App 被杀后摘要丢失。

参照 `WeatherSyncWorker` / `ImageShareRecognitionWorker` 的写法，登记到 `WorkerCatalog`。

防抖（防止重复烧 AI）：
1. 同 URL 去重（已有 `NoteTransferManager` 可参考去重思路）
2. 已生成摘要的条目不再请求
3. 失败重试上限 2 次，之后标记 `__FAILED__` 不再重试

### 2.5 微信跳转：复制链接 + 提示

微信无公开 API 可从外部跳转到指定文章，这是唯一现实可行方案。

```
收藏时：静默复制原文链接到剪贴板
点击条目：
  普通网页 → ACTION_VIEW 唤起浏览器
  微信文章 → Toast「链接已复制，请在微信中粘贴打开」
条目上用小图标区分两类（地球 / 微信气泡），否则用户会困惑
```

## 三、实施步骤（每步可编译验证）

### Step 1 · 数据层（约 200 行，无 UI）

1. `NoteParagraphType` 加 `SUMMARY`、`SOURCE_LINK`
2. `NoteDocument` 扩展：`sourceUrl`、`sourceSiteName`、`isFlashNote` 便捷属性
3. `FlashNoteDraft` 数据类 + `NoteRepository.createFlashNote(...)`
4. 状态常量：`SUMMARY_PENDING = "__PENDING__"`、`SUMMARY_FAILED = "__FAILED__"`
5. 去重：`NoteRepository.findBySourceUrl(url)`

**验证**：`compileNativeDebugKotlin`

### Step 2 · 分享入口（约 150 行）

1. `FlashNoteHandleActivity`（照 `ImagePinHandleActivity` 结构）
2. `AndroidManifest.xml` 注册 activity + activity-alias
3. `FlashNoteRecognizer`：解析 `Intent.EXTRA_TEXT` / `EXTRA_SUBJECT` → 提取 URL + 标题
4. 识别来源站点（微信 `mp.weixin.qq.com` / 其他按 host）

**验证**：编译 + 手动从浏览器分享一次，确认条目落库

### Step 3 · 正文抽取（约 250 行）

1. `TextAccessibilityService` 增加「当前页面文本提取」能力（`AccessibilityNodeInfo` 树遍历 + 过滤）
2. 过滤规则：剔除导航栏/广告/评论区特征节点（按 `viewIdResourceName`、`contentDescription`、文本长度阈值）
3. 降级策略按 §2.3

**验证**：编译 + 微信/浏览器实测能抽到正文

### Step 4 · AI 摘要 Worker（约 250 行）

1. `AiPrompts.addFlashNoteSummaryPrompt()` —— 输入正文，要求 50~100 字、客观不加解读、**只输出摘要本身**
2. `FlashNoteSummaryWorker : CoroutineWorker`
3. `WorkerCatalog` 登记
4. 状态回写：清空段落库（清 `document_json` 重存，**不用 SQL**）
5. 防抖三道按 §2.4

**验证**：编译 + 实测摘要质量与耗时

### Step 5 · 列表与跳转（约 300 行）

1. `NoteCard` 增加闪记样式：摘要（粗）→ 标题 → 来源 · 时间
2. `SUMMARY_PENDING` 显示「摘要生成中…」，`SUMMARY_FAILED` 显示「摘要生成失败」
3. 点击条目 → 按 §2.5 分流跳转
4. 微信条目自动复制链接

**验证**：编译 + 完整跑通分享 → 收藏 → 摘要 → 跳转

### Step 6 · 收尾

1. 「随手记」文案是否改名为「闪记」——**需你确认**，涉及底栏、设置页、多处文案
2. `README` / `UPSTREAM.md` 更新
3. `FeatureCatalog` / `PageCatalog` 台账登记（架构守卫测试会校验）

## 四、风险清单

| 风险 | 等级 | 应对 |
|---|---|---|
| 微信内置浏览器节点结构随版本变化 | **高** | 降级到「仅标题+链接」，不阻塞主流程 |
| 无障碍服务用户未开启 | 中 | 入口处检测并提示授权 |
| AI 摘要质量不稳定 | 中 | 提示词约束 + 失败重试上限 |
| `document_json` 频繁重写 | 低 | 摘要完成才写一次，非高频 |
| 底栏/设置页改名涉及多处 | 低 | 单独提交，可独立回退 |

## 五、待你确认的点

1. **是否改名「随口记」→「闪记」**？（涉及底栏 + 设置页 + `HomeEntryConfig` + 引导页，约 8~10 处文案）
2. **无障碍授权的引导方式**：入口处弹窗引导，还是在通用偏好里加开关说明？
3. **Step 6 前是否先跑一轮真机验证**，避免一次性做完再发现问题？

---

## 需求扩展（第二版，2026-10-04）

从「仅网页/公众号」扩展到四类内容。**架构无需调整** —— 四类共用同一套段落模型：

| 类型 | 落地方式 | 新增能力 |
|---|---|---|
| 网页 / 公众号 | `SOURCE_LINK` + 正文段落 | 无（P0 已完成） |
| 纯文本 | 仅正文段落，无链接段 | `ACTION_PROCESS_TEXT` 入口（约 50 行） |
| 图片 | `IMAGE` 段落存原图 | 存储层现成（`NoteAttachmentStore`） |

### 已确认的产品决策

1. **图片不做 OCR，只存原图**。项目无本地 OCR 依赖，走多模态模型识别会消耗双倍额度
   且延迟更长（超出需求文档「30 秒内」）。图片闪记摘要留空。
2. **纯文本走选中文字菜单**（`ACTION_PROCESS_TEXT`），不依赖分享菜单的手动两步。
3. **先收尾网页版**再扩展纯文本与图片。

### 需求文档的一处约束说明

需求表格要求「点击条目唤起微信跳转至对应文章页」，但**微信未开放该 API**，
外部 App 无法直接跳转到指定文章。当前实现为：收藏时静默复制链接，
点击条目时复制并 Toast 提示「链接已复制，请在微信中粘贴打开」。

# 上游同步说明（UPSTREAM）

本文档记录本项目与上游仓库的差异基线，供日后移植上游新功能时快速判断改动成本。

> 最后核对：2026-10-04

## 一、上游基线

| 项 | 值 |
| --- | --- |
| 上游副本路径 | `D:\VibeCoding\will-dododo\Will-do-main`（GitHub zip 下载，**无 `.git`**） |
| 副本日期 | 2026-10-01 23:19 |
| 本项目仓库 | `github.com/AIXINJUELUOAI/Will-do.git`（283 commits，最早 2026-01-27） |

**注意**：上游副本是 zip，无提交历史，拿不到增量。因此每次同步都要**整目录重新下载**，再用文件对比确定差异。

## 二、规模对比

| 项 | 上游 | 本项目 | 差异 |
| --- | --- | --- | --- |
| `app/src/main` kt 文件数 | 634 | 544 | −90 |
| `app/src/main` 代码行数 | 121,097 | 105,186 | −15,911 |
| `versionName` / `versionCode` | 2.3.0 / 175 | 2.3.0 / 175 | 一致 |
| **Room `version`** | **20** | **22** | **本地更高（+2）** |

**差异统计（`app/src/main`）：内容不同135 个文件、仅上游有 36 项、仅本地有 6 项。**

## 三、本项目主动删除的功能（上游仍保留，勿盲目移植）

这些是本项目**有意下线**的功能，上游若不重新调整，恢复它们等于回退本地决策。

| 功能 | 删除时间 | 涉及内容 |
| --- | --- | --- |
| 课表 / 课程表 | 2026-10-03 | `feature/schedule/domain/course`、`feature/backup/courseimport`、`SchedulePage.kt`、`TimeTableEditorScreen.kt`、`CourseManagerScreen.kt`、`CourseManagementDialog.kt` 及其 UiContract |
| 自动记账 | 2026-10-03 | `feature/accounting/`（application/data/domain/ui 四层）、`AccountingRecognitionNotificationPolicy.kt`、`AccountingRulesEditor.kt`、`AgentAccountingService`、`AgentBillModels` |
| 小米 HyperOS 灵动岛（Xposed） | 2026-10-03 | `platform/xposed/`（5 个文件）、`platform/capsule/miui/MiuiIslandManager.kt`、`assets/xposed_init`、Manifest 中 4 条 xposed meta-data |
| 设置页侧栏与左滑抽屉 | 2026-10-03 | `SettingsSidebar.kt`、`SettingsNavRow.kt`、`shared/ui/layout/PushSlideLayout.kt` |
| Markwon Markdown 方案（从未接入） | 2026-10-04 | `component/MarkdownText.kt`、`feature/note/.../editor/markdown/`（11 个文件） |

### 连带摘除的依赖

| 依赖 | 原因 |
| --- | --- |
| `org.smali:dexlib2:2.5.2` | 记账的微信 DEX hook 引擎 |
| `io.github.d4viddf:hyperisland_kit:0.4.3` | 灵动岛 UI 库 |
| `de.robv.android.xposed:api:82` | Xposed 桥接接口 |
| `io.noties.markwon:*`（6 个 artifact） | Markdown 方案未接入 |

**移植上游若重新引入这些功能，需同步恢复对应依赖。**

## 四、本项目独有新增（移植时原样保留）

这 6 处是本地新增目录/文件，**与上游无重叠，不会产生冲突**：

```
feature/cloudsync/ui/                              WebDAV 同步区块（从 AI 页抽出）
feature/settings/floating/                         悬浮与侧边栏设置页
feature/settings/quickmemo/                        随口记设置页
feature/settings/schedule/                         日程与通知设置页
feature/settings/shell/.../FeatureHubSettingsPage.kt    功能设置聚合页
feature/settings/shell/.../component/SettingsTabPage.kt  设置主页（唯一入口 UI）
```

## 五、差异分布（135 个文件，按目录）

| 目录 | 差异文件数 | 冲突风险 |
| --- | --- | --- |
| `shared/management/catalog` | 7 | **高** —— 页面/功能台账，改动会连带路由与守卫测试 |
| `platform/widget` | 6 | 中 |
| `feature/recognition/application/ai` | 5 | **高** —— 提示词与模型调用协议 |
| `shared/operation`、`shared/query`、`shared/api` | 10 | 中 —— 对外接口契约 |
| `platform/receiver` | 4 | 中 |
| `feature/settings/**` | ~15 | **高** —— 设置页已按功能域重排 |
| `feature/home/ui/render/material` | 3 | 中 —— 首页已含日历三视图 |
| `feature/schedule/**` | ~10 | **高** —— 课表下线 + 数据库 22 版 |
| 其余 | ~75 | 低 |

## 六、移植成本速查

| 上游改动类型 | 难度 | 说明 |
| --- | --- | --- |
| 新增独立功能目录 | **低** | 复制目录 + 接 4 处注册（枚举 → PageCatalog → Connector → SettingsTabPage） |
| 修UI 细节（配色/间距/文案） | 低 | 逐文件对照，无结构性冲突 |
| 新增数据表 / 字段 | **中** | 本地 DB 已到 **version 22**，上游是 20。移植时 migration 编号必须接在 22 之后，不能照抄上游的 |
| 改动识别协议（`AiPrompts` / `ApiModelProvider`） | **高** | 本地已改：文字识别走专用提示词 + 固定关闭思考 |
| 改动设置页结构 | **高** | 本地已重排为「通用/功能」两组，删侧栏、单栏化 |
| 恢复课表 / 记账 / 灵动岛 | **需评估** | 等于回退本项目的下线决策 |

## 七、同步操作流程

1. 重新下载上游 zip，解压到 `Will-do-main`（覆盖旧副本）
2. 更新本文档第一节的「副本日期」，并重跑规模对比
3. 用文件对比确认差异数：
   ```bash
   diff -rq <上游>/app/src/main <本地>/app/src/main
   ```
4. 按第六节判断改动成本，再决定是否移植
5. 移植后必跑：`./gradlew assembleNativeDebug testNativeDebugUnitTest`
   （当前基线：135 个单测全过）

## 八、必须保留的本地约定

移植时容易误删、但**不能删**的东西：

- **`MIGRATION_21_22`**（`EventsDatabase.kt`）：记账表 DROP 语句。删了老用户数据库升级直接崩。
- **`MIGRATION_20_21`**：课表历史数据清理。
- 迁移测试 `Database17CompatibilityTest`：断言已对齐 22 版 schema。
- `PageCatalogTest`：校验 `SettingsDestination` 枚举与台账一一对应，新增页面必须登记。

package com.antgskds.calendarassistant.feature.note.flash

import android.view.accessibility.AccessibilityNodeInfo
import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteExtraction
import com.antgskds.calendarassistant.platform.accessibility.TextAccessibilityService

/**
 * 闪记正文抽取。
 *
 * 读取当前前台窗口的无障碍节点树，筛出正文段落。分享动作发生时前台窗口仍是原页面，
 * 因此可直接取当前节点树，无需跳转或二次加载。
 *
 * 局限：微信内置浏览器与部分浏览器会随版本调整节点结构，抽取失败时按降级处理，
 * 不阻塞收藏主流程。
 */
object FlashNoteContentExtractor {

    /** 单个文本节点的最小长度，过滤图标标签与按钮文字。 */
    private const val MIN_NODE_TEXT_LENGTH = 8

    /** 整页正文的下限，低于此值视为未展开全文。 */
    private const val MIN_TOTAL_LENGTH = FlashNoteExtraction.MIN_CONTENT_LENGTH

    /** 单段落最大长度，超长多半是导航栏拼接或重复内容。 */
    private const val MAX_PARAGRAPH_LENGTH = 2000

    /** 遍历节点树的最大深度，防止异常页面导致栈溢出。 */
    private const val MAX_DEPTH = 60

    /** 最多收集的段落数，防止超长页面拖慢分享流程。 */
    private const val MAX_PARAGRAPHS = 400

    /** 噪声特征：命中即丢弃该节点文本。 */
    private val NOISE_PATTERNS = listOf(
        "广告", "推广", "点击查看", "扫码关注", "相关推荐", "热门推荐",
        "评论", "点赞", "收藏本文", "分享给", "举报", "版权所有",
        "免责声明", "_cookie", "登录/注册", "首页 产品 下载"
    )

    /** 浏览器/WebView 的常见根节点 id，用作正文起点候选。 */
    private val CONTENT_HINTS = listOf(
        "android:id/content",
        "android:id/root",
        "main",
        "article",
        "post-content",
        "js_content"
    )

    /**
     * 抽取当前页面的正文。
     *
     * 无障碍服务未开启或取不到节点时返回空正文（[FlashNoteExtraction.degraded] 为 true）。
     */
    fun extractCurrentPage(): FlashNoteExtraction {
        val service = TextAccessibilityService.instance
            ?: return FlashNoteExtraction(content = "", degraded = true)
        val root = runCatching { service.rootInActiveWindow }
            .getOrNull()
            ?: return FlashNoteExtraction(content = "", degraded = true)

        val paragraphs = try {
            collectParagraphs(root)
        } catch (e: Exception) {
            emptyList()
        } finally {
            runCatching { root.recycle() }
        }

        val content = paragraphs
            .joinToString("\n\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        return FlashNoteExtraction(
            content = content,
            degraded = content.length < MIN_TOTAL_LENGTH
        )
    }

    /** 深度优先遍历，按块级节点边界切分段落。 */
    private fun collectParagraphs(root: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        // AccessibilityNodeInfo 未公开稳定的节点 id，这里用 hashCode 作为去重键，
        // 避免同一节点被多个父节点引用时重复收集。
        val visited = HashSet<Int>()

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_DEPTH || result.size >= MAX_PARAGRAPHS) return
            if (!visited.add(node.hashCode())) return

            val className = node.className?.toString().orEmpty()
            if (className.endsWith("TextView") && !node.isEditable) {
                val text = node.text?.toString().orEmpty().trim()
                if (isUsableParagraph(text)) {
                    result += text
                    return // 叶子文本不再往下拆
                }
            }

            for (i in 0 until node.childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                walk(child, depth + 1)
            }

            // 内容容器节点本身可能带长文本（部分 WebView 整段渲染）
            if (result.isEmpty()) {
                val id = node.viewIdResourceName.orEmpty()
                if (CONTENT_HINTS.any { id.contains(it) }) {
                    node.text?.toString()?.trim()
                        ?.takeIf { isUsableParagraph(it) }
                        ?.let { result += it }
                }
            }
        }

        walk(root, 0)
        return result
    }

    private fun isUsableParagraph(text: String): Boolean {
        if (text.length < MIN_NODE_TEXT_LENGTH) return false
        if (text.length > MAX_PARAGRAPH_LENGTH) return false
        // 纯数字、纯符号、单字母行多为序号或装饰
        if (text.none { it.isLetter() }) return false
        return NOISE_PATTERNS.none { text.contains(it) }
    }
}

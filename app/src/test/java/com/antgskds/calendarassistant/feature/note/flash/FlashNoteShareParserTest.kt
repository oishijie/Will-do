package com.antgskds.calendarassistant.feature.note.flash

import com.antgskds.calendarassistant.feature.note.domain.flash.FlashNoteSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashNoteShareParserTest {

    // ---------- parse：能提取到链接的走网页分支 ----------

    @Test
    fun `extracts plain http url`() {
        val source = FlashNoteShareParser.parse("https://example.com/article", null)
        assertNotNull(source)
        assertEquals("https://example.com/article", source!!.url)
        assertEquals(FlashNoteSourceKind.WEB, source.kind)
        assertEquals("example.com", source.siteName)
    }

    @Test
    fun `adds scheme to schemeless url`() {
        val source = FlashNoteShareParser.parse("www.example.com/post", null)
        assertNotNull(source)
        assertTrue(source!!.url.startsWith("https://"))
    }

    @Test
    fun `strips trailing chinese punctuation from url`() {
        val source = FlashNoteShareParser.parse("看看这个 https://example.com/a，很有意思", null)
        assertNotNull(source)
        assertEquals("https://example.com/a", source!!.url)
    }

    @Test
    fun `recognizes wechat article and requires clipboard relay`() {
        val source = FlashNoteShareParser.parse("https://mp.weixin.qq.com/s/abc123", null)
        assertNotNull(source)
        assertEquals(FlashNoteSourceKind.WECHAT, source!!.kind)
        assertTrue(source.requiresClipboardRelay)
        assertEquals("微信公众号", source.siteName)
    }

    @Test
    fun `prefers subject as title and strips noise prefix`() {
        val source = FlashNoteShareParser.parse(
            sharedText = "https://example.com/x",
            sharedSubject = "分享：深度好文"
        )
        assertNotNull(source)
        assertEquals("深度好文", source!!.articleTitle)
    }

    @Test
    fun `keeps query string intact`() {
        val source = FlashNoteShareParser.parse(
            "https://example.com/search?q=android&page=2",
            null
        )
        assertNotNull(source)
        assertTrue(source!!.url.contains("q=android"))
        assertTrue(source.url.contains("page=2"))
    }

    // ---------- parse：无链接时返回 null（由调用方降级） ----------

    @Test
    fun `returns null when text has no url`() {
        assertNull(FlashNoteShareParser.parse("这是一段没有链接的文字", null))
    }

    @Test
    fun `returns null when both extras are blank`() {
        assertNull(FlashNoteShareParser.parse("  ", "  "))
        assertNull(FlashNoteShareParser.parse(null, null))
    }

    /**
     * 回归测试：系统文字选择菜单（ColorOS 等）会把选中文字包成 ACTION_SEND，
     * 此时解析必然拿不到 URL，调用方必须走纯文本降级而不是报错丢弃。
     * 详见 FlashNoteHandleActivity 里 source == null 的分支。
     */
    @Test
    fun `plain text share falls back to text flash note`() {
        val shared = "这是一段选中的文字，没有链接"
        assertNull(FlashNoteShareParser.parse(shared, null))

        val plain = FlashNoteShareParser.extractPlainText(shared, null)
        assertNotNull(plain)
        assertEquals(shared, plain)
    }

    // ---------- extractPlainText：降级路径的正文提取 ----------

    @Test
    fun `extractPlainText keeps content when subject is separate title`() {
        val plain = FlashNoteShareParser.extractPlainText(
            sharedText = "正文第一段\n正文第二段",
            sharedSubject = "标题"
        )
        assertEquals("正文第一段\n正文第二段", plain)
    }

    @Test
    fun `extractPlainText drops duplicated title prefix`() {
        val plain = FlashNoteShareParser.extractPlainText(
            sharedText = "标题\n正文内容",
            sharedSubject = "标题"
        )
        assertNotNull(plain)
        assertEquals("正文内容", plain)
    }

    @Test
    fun `extractPlainText returns null for empty input`() {
        assertNull(FlashNoteShareParser.extractPlainText(null, null))
        assertNull(FlashNoteShareParser.extractPlainText("   ", null))
    }

    @Test
    fun `extractPlainText truncates very long content`() {
        val huge = "字".repeat(30000)
        val plain = FlashNoteShareParser.extractPlainText(huge, null)
        assertNotNull(plain)
        assertEquals(20000, plain!!.length)
    }
}

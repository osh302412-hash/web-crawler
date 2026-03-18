package com.crawler.parser

import com.crawler.config.CrawlConfig
import com.crawler.url.UrlNormalizer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested

class HtmlParserTest {

    private val parser = HtmlParser(UrlNormalizer(CrawlConfig()))

    @Nested
    inner class TitleExtraction {
        @Test
        fun `extracts title from HTML`() {
            val html = "<html><head><title>Test Page</title></head><body></body></html>"
            val result = parser.parse(html, "http://example.com")
            assertThat(result.title).isEqualTo("Test Page")
        }

        @Test
        fun `returns null for missing title`() {
            val html = "<html><head></head><body></body></html>"
            val result = parser.parse(html, "http://example.com")
            assertThat(result.title).isNull()
        }

        @Test
        fun `returns null for blank title`() {
            val html = "<html><head><title>  </title></head><body></body></html>"
            val result = parser.parse(html, "http://example.com")
            assertThat(result.title).isNull()
        }
    }

    @Nested
    inner class LinkExtraction {
        @Test
        fun `extracts absolute links`() {
            val html = """
                <html><body>
                <a href="http://example.com/page1">Page 1</a>
                <a href="http://example.com/page2">Page 2</a>
                </body></html>
            """.trimIndent()

            val result = parser.parse(html, "http://example.com")
            assertThat(result.links).containsExactlyInAnyOrder(
                "http://example.com/page1",
                "http://example.com/page2"
            )
        }

        @Test
        fun `resolves relative links`() {
            val html = """
                <html><body>
                <a href="/about">About</a>
                <a href="contact">Contact</a>
                </body></html>
            """.trimIndent()

            val result = parser.parse(html, "http://example.com/pages/index.html")
            assertThat(result.links).contains("http://example.com/about")
        }

        @Test
        fun `filters mailto links`() {
            val html = """
                <html><body>
                <a href="mailto:test@example.com">Email</a>
                <a href="http://example.com/page">Page</a>
                </body></html>
            """.trimIndent()

            val result = parser.parse(html, "http://example.com")
            assertThat(result.links).containsExactly("http://example.com/page")
        }

        @Test
        fun `filters javascript links`() {
            val html = """
                <html><body>
                <a href="javascript:void(0)">Click</a>
                <a href="http://example.com/page">Page</a>
                </body></html>
            """.trimIndent()

            val result = parser.parse(html, "http://example.com")
            assertThat(result.links).containsExactly("http://example.com/page")
        }

        @Test
        fun `filters tel links`() {
            val html = """
                <html><body>
                <a href="tel:+1234567890">Call</a>
                </body></html>
            """.trimIndent()

            val result = parser.parse(html, "http://example.com")
            assertThat(result.links).isEmpty()
        }

        @Test
        fun `deduplicates links within page`() {
            val html = """
                <html><body>
                <a href="http://example.com/page">Link 1</a>
                <a href="http://example.com/page">Link 2</a>
                </body></html>
            """.trimIndent()

            val result = parser.parse(html, "http://example.com")
            assertThat(result.links).hasSize(1)
        }

        @Test
        fun `respects base tag`() {
            val html = """
                <html>
                <head><base href="http://cdn.example.com/"></head>
                <body><a href="page">Link</a></body>
                </html>
            """.trimIndent()

            val result = parser.parse(html, "http://example.com")
            assertThat(result.links).contains("http://cdn.example.com/page")
        }

        @Test
        fun `skips empty href attributes`() {
            val html = """
                <html><body>
                <a href="">Empty</a>
                <a href="  ">Blank</a>
                </body></html>
            """.trimIndent()

            val result = parser.parse(html, "http://example.com")
            assertThat(result.links).isEmpty()
        }
    }

    @Nested
    inner class CanonicalUrl {
        @Test
        fun `extracts canonical URL`() {
            val html = """
                <html>
                <head><link rel="canonical" href="http://example.com/canonical-page"></head>
                <body></body>
                </html>
            """.trimIndent()

            val result = parser.parse(html, "http://example.com/page?ref=123")
            assertThat(result.canonicalUrl).isEqualTo("http://example.com/canonical-page")
        }

        @Test
        fun `returns null when no canonical link`() {
            val html = "<html><head></head><body></body></html>"
            val result = parser.parse(html, "http://example.com")
            assertThat(result.canonicalUrl).isNull()
        }
    }

    @Nested
    inner class MalformedHtml {
        @Test
        fun `handles malformed HTML gracefully`() {
            val html = "<html><body><a href='/page'>unclosed"
            val result = parser.parse(html, "http://example.com")
            assertThat(result.links).contains("http://example.com/page")
        }

        @Test
        fun `handles empty HTML`() {
            val result = parser.parse("", "http://example.com")
            assertThat(result.links).isEmpty()
            assertThat(result.title).isNull()
        }
    }
}

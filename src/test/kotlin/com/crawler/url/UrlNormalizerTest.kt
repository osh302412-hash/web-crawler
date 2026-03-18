package com.crawler.url

import com.crawler.config.CrawlConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested

class UrlNormalizerTest {

    private val normalizer = UrlNormalizer(CrawlConfig())

    @Nested
    inner class BasicNormalization {
        @Test
        fun `normalizes scheme and host to lowercase`() {
            assertThat(normalizer.normalize("HTTP://EXAMPLE.COM/Path"))
                .isEqualTo("http://example.com/Path")
        }

        @Test
        fun `removes default port 80 for http`() {
            assertThat(normalizer.normalize("http://example.com:80/page"))
                .isEqualTo("http://example.com/page")
        }

        @Test
        fun `removes default port 443 for https`() {
            assertThat(normalizer.normalize("https://example.com:443/page"))
                .isEqualTo("https://example.com/page")
        }

        @Test
        fun `preserves non-default port`() {
            assertThat(normalizer.normalize("http://example.com:8080/page"))
                .isEqualTo("http://example.com:8080/page")
        }

        @Test
        fun `adds root path if missing`() {
            assertThat(normalizer.normalize("http://example.com"))
                .isEqualTo("http://example.com/")
        }
    }

    @Nested
    inner class FragmentHandling {
        @Test
        fun `strips fragment by default`() {
            assertThat(normalizer.normalize("http://example.com/page#section"))
                .isEqualTo("http://example.com/page")
        }

        @Test
        fun `preserves fragment when configured`() {
            val n = UrlNormalizer(CrawlConfig(stripFragment = false))
            assertThat(n.normalize("http://example.com/page#section"))
                .isEqualTo("http://example.com/page#section")
        }
    }

    @Nested
    inner class TrailingSlash {
        @Test
        fun `strips trailing slash by default`() {
            assertThat(normalizer.normalize("http://example.com/page/"))
                .isEqualTo("http://example.com/page")
        }

        @Test
        fun `preserves root path slash`() {
            assertThat(normalizer.normalize("http://example.com/"))
                .isEqualTo("http://example.com/")
        }

        @Test
        fun `preserves trailing slash when configured`() {
            val n = UrlNormalizer(CrawlConfig(stripTrailingSlash = false))
            assertThat(n.normalize("http://example.com/page/"))
                .isEqualTo("http://example.com/page/")
        }
    }

    @Nested
    inner class QueryString {
        @Test
        fun `preserves query string`() {
            assertThat(normalizer.normalize("http://example.com/search?q=test&page=1"))
                .isEqualTo("http://example.com/search?q=test&page=1")
        }
    }

    @Nested
    inner class RelativeUrls {
        @Test
        fun `resolves relative path against base URL`() {
            assertThat(normalizer.normalize("/about", "http://example.com/page"))
                .isEqualTo("http://example.com/about")
        }

        @Test
        fun `resolves relative path with dot segments`() {
            assertThat(normalizer.normalize("../other", "http://example.com/dir/page"))
                .isEqualTo("http://example.com/other")
        }

        @Test
        fun `resolves protocol-relative URL`() {
            assertThat(normalizer.normalize("//cdn.example.com/script.js", "http://example.com"))
                .isEqualTo("http://cdn.example.com/script.js")
        }
    }

    @Nested
    inner class InvalidUrls {
        @Test
        fun `returns null for non-HTTP scheme`() {
            assertThat(normalizer.normalize("ftp://example.com")).isNull()
        }

        @Test
        fun `returns null for mailto`() {
            assertThat(normalizer.normalize("mailto:user@example.com")).isNull()
        }

        @Test
        fun `returns null for javascript`() {
            assertThat(normalizer.normalize("javascript:void(0)")).isNull()
        }

        @Test
        fun `returns null for empty string`() {
            assertThat(normalizer.normalize("")).isNull()
        }
    }

    @Nested
    inner class CrawlableLinkFilter {
        @Test
        fun `filters mailto links`() {
            assertThat(UrlNormalizer.isCrawlableLink("mailto:test@example.com")).isFalse()
        }

        @Test
        fun `filters javascript links`() {
            assertThat(UrlNormalizer.isCrawlableLink("javascript:void(0)")).isFalse()
        }

        @Test
        fun `filters tel links`() {
            assertThat(UrlNormalizer.isCrawlableLink("tel:+1234567890")).isFalse()
        }

        @Test
        fun `filters fragment-only links`() {
            assertThat(UrlNormalizer.isCrawlableLink("#section")).isFalse()
        }

        @Test
        fun `allows http links`() {
            assertThat(UrlNormalizer.isCrawlableLink("http://example.com")).isTrue()
        }

        @Test
        fun `allows relative links`() {
            assertThat(UrlNormalizer.isCrawlableLink("/about")).isTrue()
        }

        @Test
        fun `filters empty links`() {
            assertThat(UrlNormalizer.isCrawlableLink("")).isFalse()
        }
    }
}

package com.crawler.robots

import com.crawler.config.CrawlConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested

class RobotsPolicyTest {

    private val config = CrawlConfig(userAgent = "LearningCrawler/1.0")
    private val policy = RobotsPolicy(config)

    @Nested
    inner class Parsing {
        @Test
        fun `parses disallow rules for wildcard agent`() {
            val robots = policy.parseRobotsTxt("""
                User-agent: *
                Disallow: /private/
                Disallow: /admin/
            """.trimIndent())

            assertThat(robots.rules).hasSize(2)
            assertThat(robots.rules[0].path).isEqualTo("/private/")
            assertThat(robots.rules[0].allowed).isFalse()
        }

        @Test
        fun `parses allow rules`() {
            val robots = policy.parseRobotsTxt("""
                User-agent: *
                Allow: /public/
                Disallow: /
            """.trimIndent())

            assertThat(robots.rules).hasSize(2)
            assertThat(robots.rules[0].path).isEqualTo("/public/")
            assertThat(robots.rules[0].allowed).isTrue()
        }

        @Test
        fun `parses crawl-delay`() {
            val robots = policy.parseRobotsTxt("""
                User-agent: *
                Crawl-delay: 2.5
                Disallow: /private/
            """.trimIndent())

            assertThat(robots.crawlDelay).isEqualTo(2.5)
        }

        @Test
        fun `parses sitemap directives`() {
            val robots = policy.parseRobotsTxt("""
                User-agent: *
                Disallow: /private/
                Sitemap: https://example.com/sitemap.xml
            """.trimIndent())

            assertThat(robots.sitemaps).containsExactly("https://example.com/sitemap.xml")
        }

        @Test
        fun `ignores comments`() {
            val robots = policy.parseRobotsTxt("""
                # This is a comment
                User-agent: *
                Disallow: /private/ # inline comment
            """.trimIndent())

            assertThat(robots.rules).hasSize(1)
            assertThat(robots.rules[0].path).isEqualTo("/private/")
        }

        @Test
        fun `handles empty disallow (allow all)`() {
            val robots = policy.parseRobotsTxt("""
                User-agent: *
                Disallow:
            """.trimIndent())

            assertThat(robots.rules).isEmpty()
        }

        @Test
        fun `handles empty content`() {
            val robots = policy.parseRobotsTxt("")
            assertThat(robots.rules).isEmpty()
        }
    }

    @Nested
    inner class PathMatching {
        @Test
        fun `blocks disallowed path`() {
            val robots = policy.parseRobotsTxt("""
                User-agent: *
                Disallow: /private/
            """.trimIndent())

            assertThat(policy.isPathAllowed(robots, "/private/secret.html")).isFalse()
        }

        @Test
        fun `allows non-disallowed path`() {
            val robots = policy.parseRobotsTxt("""
                User-agent: *
                Disallow: /private/
            """.trimIndent())

            assertThat(policy.isPathAllowed(robots, "/public/page.html")).isTrue()
        }

        @Test
        fun `more specific allow overrides disallow`() {
            val robots = policy.parseRobotsTxt("""
                User-agent: *
                Allow: /private/public-page
                Disallow: /private/
            """.trimIndent())

            assertThat(policy.isPathAllowed(robots, "/private/public-page")).isTrue()
            assertThat(policy.isPathAllowed(robots, "/private/secret")).isFalse()
        }

        @Test
        fun `disallow root blocks everything`() {
            val robots = policy.parseRobotsTxt("""
                User-agent: *
                Disallow: /
            """.trimIndent())

            assertThat(policy.isPathAllowed(robots, "/anything")).isFalse()
        }

        @Test
        fun `empty rules allow everything`() {
            val robots = RobotsPolicy.RobotsData(emptyList())
            assertThat(policy.isPathAllowed(robots, "/anything")).isTrue()
        }
    }

    @Nested
    inner class FallbackBehavior {
        @Test
        fun `allows access when robots checking is disabled`() {
            val disabledPolicy = RobotsPolicy(CrawlConfig(respectRobotsTxt = false))
            assertThat(disabledPolicy.isAllowed("http://example.com/private/")).isTrue()
        }
    }
}

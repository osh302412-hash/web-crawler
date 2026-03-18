package com.crawler.fetcher

import com.crawler.config.CrawlConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.Duration

class HttpFetcherTest {

    private lateinit var server: MockWebServer
    private lateinit var fetcher: HttpFetcher

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        fetcher = HttpFetcher(CrawlConfig(
            requestTimeout = Duration.ofSeconds(5),
            connectTimeout = Duration.ofSeconds(2),
            maxRedirects = 3,
            maxRetries = 2,
            retryBackoffBase = Duration.ofMillis(50),
        ))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Nested
    inner class BasicFetch {
        @Test
        fun `fetches HTML page successfully`() {
            server.enqueue(MockResponse()
                .setBody("<html><body>Hello</body></html>")
                .setHeader("Content-Type", "text/html"))

            val result = fetcher.fetch(server.url("/page").toString())

            assertThat(result.statusCode).isEqualTo(200)
            assertThat(result.body).contains("Hello")
            assertThat(result.contentType).contains("text/html")
            assertThat(result.error).isNull()
            assertThat(result.responseTimeMs).isGreaterThanOrEqualTo(0)
        }

        @Test
        fun `records 404 status code`() {
            server.enqueue(MockResponse().setResponseCode(404))

            val result = fetcher.fetch(server.url("/missing").toString())
            assertThat(result.statusCode).isEqualTo(404)
        }

        @Test
        fun `records 500 status code`() {
            server.enqueue(MockResponse().setResponseCode(500))

            val result = fetcher.fetch(server.url("/error").toString())
            assertThat(result.statusCode).isEqualTo(500)
        }
    }

    @Nested
    inner class RedirectHandling {
        @Test
        fun `follows redirect and records chain`() {
            server.enqueue(MockResponse()
                .setResponseCode(301)
                .setHeader("Location", "/final"))
            server.enqueue(MockResponse()
                .setBody("Final page")
                .setHeader("Content-Type", "text/html"))

            val result = fetcher.fetch(server.url("/start").toString())

            assertThat(result.statusCode).isEqualTo(200)
            assertThat(result.redirectChain).hasSize(1)
            assertThat(result.body).contains("Final page")
        }

        @Test
        fun `handles too many redirects`() {
            // Enqueue more redirects than max
            for (i in 0..4) {
                server.enqueue(MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", "/redirect$i"))
            }

            val result = fetcher.fetch(server.url("/start").toString())
            assertThat(result.error).contains("Too many redirects")
        }
    }

    @Nested
    inner class RetryLogic {
        @Test
        fun `identifies 5xx as retryable`() {
            val result = com.crawler.model.FetchResult(
                requestUrl = "http://example.com",
                finalUrl = "http://example.com",
                statusCode = 503,
                contentType = null,
                body = null,
            )
            assertThat(fetcher.isRetryable(result)).isTrue()
        }

        @Test
        fun `identifies network error as retryable`() {
            val result = com.crawler.model.FetchResult(
                requestUrl = "http://example.com",
                finalUrl = "http://example.com",
                statusCode = -1,
                contentType = null,
                body = null,
                error = "Connection refused",
            )
            assertThat(fetcher.isRetryable(result)).isTrue()
        }

        @Test
        fun `identifies 404 as non-retryable`() {
            val result = com.crawler.model.FetchResult(
                requestUrl = "http://example.com",
                finalUrl = "http://example.com",
                statusCode = 404,
                contentType = null,
                body = null,
            )
            assertThat(fetcher.isRetryable(result)).isFalse()
        }

        @Test
        fun `identifies 200 as non-retryable`() {
            val result = com.crawler.model.FetchResult(
                requestUrl = "http://example.com",
                finalUrl = "http://example.com",
                statusCode = 200,
                contentType = "text/html",
                body = "<html></html>",
            )
            assertThat(fetcher.isRetryable(result)).isFalse()
        }

        @Test
        fun `calculates exponential backoff`() {
            assertThat(fetcher.calculateBackoff(1)).isEqualTo(50)   // base
            assertThat(fetcher.calculateBackoff(2)).isEqualTo(100)  // base * 2
            assertThat(fetcher.calculateBackoff(3)).isEqualTo(200)  // base * 4
        }

        @Test
        fun `retries on 5xx then succeeds`() {
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse()
                .setBody("OK")
                .setHeader("Content-Type", "text/html"))

            val result = fetcher.fetchWithRetry(server.url("/flaky").toString())
            assertThat(result.statusCode).isEqualTo(200)
            assertThat(server.requestCount).isEqualTo(2)
        }
    }

    @Nested
    inner class ContentType {
        @Test
        fun `detects HTML content type`() {
            val result = com.crawler.model.FetchResult(
                requestUrl = "http://example.com",
                finalUrl = "http://example.com",
                statusCode = 200,
                contentType = "text/html; charset=utf-8",
                body = "<html></html>",
            )
            assertThat(fetcher.isHtmlContent(result)).isTrue()
        }

        @Test
        fun `detects XHTML content type`() {
            val result = com.crawler.model.FetchResult(
                requestUrl = "http://example.com",
                finalUrl = "http://example.com",
                statusCode = 200,
                contentType = "application/xhtml+xml",
                body = "<html></html>",
            )
            assertThat(fetcher.isHtmlContent(result)).isTrue()
        }

        @Test
        fun `rejects non-HTML content type`() {
            val result = com.crawler.model.FetchResult(
                requestUrl = "http://example.com",
                finalUrl = "http://example.com",
                statusCode = 200,
                contentType = "application/pdf",
                body = null,
            )
            assertThat(fetcher.isHtmlContent(result)).isFalse()
        }

        @Test
        fun `rejects null content type`() {
            val result = com.crawler.model.FetchResult(
                requestUrl = "http://example.com",
                finalUrl = "http://example.com",
                statusCode = 200,
                contentType = null,
                body = null,
            )
            assertThat(fetcher.isHtmlContent(result)).isFalse()
        }
    }

    @Nested
    inner class Timeout {
        @Test
        fun `handles connection timeout`() {
            // Use unreachable address to trigger timeout
            val timeoutFetcher = HttpFetcher(CrawlConfig(
                connectTimeout = Duration.ofMillis(100),
                requestTimeout = Duration.ofMillis(200),
                maxRetries = 0,
            ))

            val result = timeoutFetcher.fetch("http://192.0.2.1/timeout")
            assertThat(result.statusCode).isEqualTo(-1)
            assertThat(result.error).isNotNull()
        }
    }
}

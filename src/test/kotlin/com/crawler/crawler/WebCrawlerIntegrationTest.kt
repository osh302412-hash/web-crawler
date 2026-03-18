package com.crawler.crawler

import com.crawler.config.CrawlConfig
import com.google.gson.Gson
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Duration

class WebCrawlerIntegrationTest {

    private lateinit var server: MockWebServer

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `crawls a simple site end-to-end`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/" -> MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("""
                            <html>
                            <head><title>Home</title></head>
                            <body>
                                <a href="/about">About</a>
                                <a href="/contact">Contact</a>
                            </body>
                            </html>
                        """.trimIndent())
                    "/about" -> MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("""
                            <html>
                            <head><title>About</title></head>
                            <body><a href="/">Home</a></body>
                            </html>
                        """.trimIndent())
                    "/contact" -> MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("""
                            <html>
                            <head><title>Contact</title></head>
                            <body><a href="/">Home</a></body>
                            </html>
                        """.trimIndent())
                    "/robots.txt" -> MockResponse()
                        .setResponseCode(404)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()

        val config = CrawlConfig(
            seedUrls = listOf(server.url("/").toString()),
            maxPages = 10,
            maxDepth = 2,
            perHostDelay = Duration.ofMillis(10),
            outputDir = tempDir.absolutePath,
            respectRobotsTxt = true,
        )

        val crawler = WebCrawler(config)
        val stats = crawler.crawl()

        assertThat(stats.pagesSuccess).isEqualTo(3)
        assertThat(stats.duplicatesSkipped).isGreaterThanOrEqualTo(0)

        // Verify JSONL output
        val jsonlFiles = tempDir.listFiles { f -> f.name.endsWith(".jsonl") }!!
        assertThat(jsonlFiles).hasSize(1)
        val lines = jsonlFiles[0].readLines()
        assertThat(lines.size).isEqualTo(3)

        // Verify structure of saved results
        val gson = Gson()
        @Suppress("UNCHECKED_CAST")
        val first = gson.fromJson(lines[0], Map::class.java) as Map<String, Any>
        assertThat(first.keys).contains("requestUrl", "finalUrl", "statusCode",
            "title", "linkCount", "depth", "crawledAt")
    }

    @Test
    fun `respects robots txt blocking`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/robots.txt" -> MockResponse()
                        .setHeader("Content-Type", "text/plain")
                        .setBody("""
                            User-agent: *
                            Disallow: /private/
                        """.trimIndent())
                    "/" -> MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("""
                            <html><head><title>Home</title></head><body>
                            <a href="/public">Public</a>
                            <a href="/private/secret">Secret</a>
                            </body></html>
                        """.trimIndent())
                    "/public" -> MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("<html><head><title>Public</title></head><body>OK</body></html>")
                    "/private/secret" -> MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("<html><head><title>Secret</title></head><body>Hidden</body></html>")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()

        val config = CrawlConfig(
            seedUrls = listOf(server.url("/").toString()),
            maxPages = 10,
            maxDepth = 2,
            perHostDelay = Duration.ofMillis(10),
            outputDir = tempDir.absolutePath,
            respectRobotsTxt = true,
        )

        val crawler = WebCrawler(config)
        val stats = crawler.crawl()

        assertThat(stats.robotsBlocked).isGreaterThanOrEqualTo(1)
        // /private/secret should not appear in successful results
        val jsonlFiles = tempDir.listFiles { f -> f.name.endsWith(".jsonl") }!!
        val lines = jsonlFiles[0].readLines()
        val urls = lines.map { Gson().fromJson(it, Map::class.java)["requestUrl"] as String }
        assertThat(urls).noneMatch { it.contains("/private/") }
    }

    @Test
    fun `follows redirects correctly`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/robots.txt" -> MockResponse().setResponseCode(404)
                    "/" -> MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("""
                            <html><head><title>Home</title></head><body>
                            <a href="/old-page">Old Page</a>
                            </body></html>
                        """.trimIndent())
                    "/old-page" -> MockResponse()
                        .setResponseCode(301)
                        .setHeader("Location", "/new-page")
                    "/new-page" -> MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("<html><head><title>New Page</title></head><body>Redirected</body></html>")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()

        val config = CrawlConfig(
            seedUrls = listOf(server.url("/").toString()),
            maxPages = 10,
            maxDepth = 2,
            perHostDelay = Duration.ofMillis(10),
            outputDir = tempDir.absolutePath,
        )

        val crawler = WebCrawler(config)
        val stats = crawler.crawl()

        assertThat(stats.pagesSuccess).isGreaterThanOrEqualTo(2)
    }

    @Test
    fun `skips non-HTML content`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/robots.txt" -> MockResponse().setResponseCode(404)
                    "/" -> MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("""
                            <html><head><title>Home</title></head><body>
                            <a href="/image.png">Image</a>
                            <a href="/page">Page</a>
                            </body></html>
                        """.trimIndent())
                    "/image.png" -> MockResponse()
                        .setHeader("Content-Type", "image/png")
                        .setBody("fake-png-data")
                    "/page" -> MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("<html><head><title>Page</title></head><body>OK</body></html>")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()

        val config = CrawlConfig(
            seedUrls = listOf(server.url("/").toString()),
            maxPages = 10,
            maxDepth = 2,
            perHostDelay = Duration.ofMillis(10),
            outputDir = tempDir.absolutePath,
        )

        val crawler = WebCrawler(config)
        val stats = crawler.crawl()

        // image.png should have been fetched but not parsed for links
        assertThat(stats.pagesSuccess).isEqualTo(2) // / and /page only
    }

    @Test
    fun `respects max pages limit`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/robots.txt") return MockResponse().setResponseCode(404)
                val pageNum = request.path?.removePrefix("/page")?.toIntOrNull() ?: 0
                val nextPage = pageNum + 1
                return MockResponse()
                    .setHeader("Content-Type", "text/html")
                    .setBody("""
                        <html><head><title>Page $pageNum</title></head><body>
                        <a href="/page$nextPage">Next</a>
                        </body></html>
                    """.trimIndent())
            }
        }
        server.start()

        val config = CrawlConfig(
            seedUrls = listOf(server.url("/page0").toString()),
            maxPages = 3,
            maxDepth = 10,
            perHostDelay = Duration.ofMillis(10),
            outputDir = tempDir.absolutePath,
        )

        val crawler = WebCrawler(config)
        val stats = crawler.crawl()

        assertThat(stats.pagesProcessed).isLessThanOrEqualTo(3)
    }

    @Test
    fun `respects max depth limit`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/robots.txt") return MockResponse().setResponseCode(404)
                return MockResponse()
                    .setHeader("Content-Type", "text/html")
                    .setBody("""
                        <html><head><title>${request.path}</title></head><body>
                        <a href="${request.path}/deeper">Go deeper</a>
                        </body></html>
                    """.trimIndent())
            }
        }
        server.start()

        val config = CrawlConfig(
            seedUrls = listOf(server.url("/start").toString()),
            maxPages = 100,
            maxDepth = 2,
            perHostDelay = Duration.ofMillis(10),
            outputDir = tempDir.absolutePath,
        )

        val crawler = WebCrawler(config)
        val stats = crawler.crawl()

        // depth 0: /start, depth 1: /start/deeper, depth 2: /start/deeper/deeper
        assertThat(stats.pagesProcessed).isLessThanOrEqualTo(3)
    }

    @Test
    fun `handles server errors with retry`() {
        var requestCount = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/robots.txt") return MockResponse().setResponseCode(404)
                requestCount++
                return if (requestCount <= 2) {
                    MockResponse().setResponseCode(503)
                } else {
                    MockResponse()
                        .setHeader("Content-Type", "text/html")
                        .setBody("<html><head><title>OK</title></head><body>Recovered</body></html>")
                }
            }
        }
        server.start()

        val config = CrawlConfig(
            seedUrls = listOf(server.url("/flaky").toString()),
            maxPages = 5,
            maxDepth = 1,
            perHostDelay = Duration.ofMillis(10),
            outputDir = tempDir.absolutePath,
            maxRetries = 3,
            retryBackoffBase = Duration.ofMillis(10),
        )

        val crawler = WebCrawler(config)
        val stats = crawler.crawl()

        assertThat(stats.pagesSuccess).isGreaterThanOrEqualTo(1)
    }
}

package com.crawler.config

import java.time.Duration

/**
 * Central configuration for the web crawler.
 * All safety limits have conservative defaults to avoid overwhelming target sites.
 */
data class CrawlConfig(
    val seedUrls: List<String> = emptyList(),
    val maxPages: Int = 50,
    val maxDepth: Int = 3,
    val userAgent: String = "LearningCrawler/1.0 (+https://github.com/example/web-crawler)",
    val requestTimeout: Duration = Duration.ofSeconds(10),
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val perHostDelay: Duration = Duration.ofMillis(1000),
    val maxRetries: Int = 3,
    val retryBackoffBase: Duration = Duration.ofMillis(500),
    val maxRedirects: Int = 5,
    val respectRobotsTxt: Boolean = true,
    val robotsTxtCacheDuration: Duration = Duration.ofMinutes(30),
    val allowedContentTypes: Set<String> = setOf("text/html", "application/xhtml+xml"),
    val outputDir: String = "crawl-output",
    val outputFormat: OutputFormat = OutputFormat.JSONL,
    val crawlStrategy: CrawlStrategy = CrawlStrategy.BFS,
    val sameDomainOnly: Boolean = true,
    val stripFragment: Boolean = true,
    val stripTrailingSlash: Boolean = true,
    val normalizeHttps: Boolean = false,
    val robotsFallbackAllow: Boolean = true,
)

enum class OutputFormat {
    JSONL
}

enum class CrawlStrategy {
    BFS, DFS
}

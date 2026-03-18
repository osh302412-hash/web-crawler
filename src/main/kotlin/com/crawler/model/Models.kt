package com.crawler.model

import java.time.Instant

/** A URL queued for crawling with its metadata. */
data class CrawlRequest(
    val url: String,
    val depth: Int,
    val parentUrl: String? = null,
)

/** The result of fetching a single URL. */
data class FetchResult(
    val requestUrl: String,
    val finalUrl: String,
    val statusCode: Int,
    val contentType: String?,
    val body: String?,
    val headers: Map<String, List<String>> = emptyMap(),
    val error: String? = null,
    val redirectChain: List<String> = emptyList(),
    val responseTimeMs: Long = 0,
)

/** Parsed content from an HTML page. */
data class ParsedPage(
    val url: String,
    val title: String?,
    val links: List<String>,
    val canonicalUrl: String? = null,
)

/** Final crawl result persisted to storage. */
data class CrawlResult(
    val requestUrl: String,
    val finalUrl: String,
    val statusCode: Int,
    val title: String?,
    val linkCount: Int,
    val extractedLinks: List<String>,
    val depth: Int,
    val crawledAt: Instant,
    val error: String? = null,
    val responseTimeMs: Long = 0,
)

/** Statistics collected during a crawl session. */
data class CrawlStats(
    var pagesProcessed: Int = 0,
    var pagesSuccess: Int = 0,
    var pagesFailed: Int = 0,
    var duplicatesSkipped: Int = 0,
    var robotsBlocked: Int = 0,
    var retryCount: Int = 0,
    var totalResponseTimeMs: Long = 0,
) {
    val averageResponseTimeMs: Long
        get() = if (pagesSuccess > 0) totalResponseTimeMs / pagesSuccess else 0

    override fun toString(): String = buildString {
        appendLine("=== Crawl Statistics ===")
        appendLine("Pages processed:    $pagesProcessed")
        appendLine("Pages success:      $pagesSuccess")
        appendLine("Pages failed:       $pagesFailed")
        appendLine("Duplicates skipped: $duplicatesSkipped")
        appendLine("Robots blocked:     $robotsBlocked")
        appendLine("Retries:            $retryCount")
        appendLine("Avg response time:  ${averageResponseTimeMs}ms")
    }
}

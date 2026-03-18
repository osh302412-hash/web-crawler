package com.crawler.fetcher

import com.crawler.config.CrawlConfig
import com.crawler.model.FetchResult
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP fetcher that retrieves web pages.
 *
 * Features:
 * - Configurable timeouts
 * - Redirect tracking (up to maxRedirects)
 * - Content-type validation
 * - Retry with exponential backoff for transient failures
 * - Distinguishes retryable (5xx, timeout) from non-retryable (4xx) errors
 */
class HttpFetcher(private val config: CrawlConfig) {

    private val logger = LoggerFactory.getLogger(HttpFetcher::class.java)

    // Use Java HttpClient with manual redirect handling to track the chain
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(config.connectTimeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun fetch(url: String): FetchResult {
        var currentUrl = url
        val redirectChain = mutableListOf<String>()
        var redirectCount = 0

        while (redirectCount <= config.maxRedirects) {
            val startTime = System.currentTimeMillis()
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI(currentUrl))
                    .header("User-Agent", config.userAgent)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .timeout(config.requestTimeout)
                    .GET()
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                val elapsed = System.currentTimeMillis() - startTime
                val statusCode = response.statusCode()

                // Handle redirects
                if (statusCode in 300..399) {
                    val location = response.headers().firstValue("Location").orElse(null)
                    if (location != null) {
                        redirectChain.add(currentUrl)
                        currentUrl = resolveRedirect(currentUrl, location)
                        redirectCount++
                        logger.debug("Redirect {} -> {} ({})", redirectChain.last(), currentUrl, statusCode)
                        continue
                    }
                }

                val contentType = response.headers()
                    .firstValue("Content-Type").orElse(null)

                val headers = response.headers().map().mapValues { it.value.toList() }

                return FetchResult(
                    requestUrl = url,
                    finalUrl = currentUrl,
                    statusCode = statusCode,
                    contentType = contentType,
                    body = response.body(),
                    headers = headers,
                    redirectChain = redirectChain,
                    responseTimeMs = elapsed,
                )
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                return FetchResult(
                    requestUrl = url,
                    finalUrl = currentUrl,
                    statusCode = -1,
                    contentType = null,
                    body = null,
                    error = "${e.javaClass.simpleName}: ${e.message}",
                    redirectChain = redirectChain,
                    responseTimeMs = elapsed,
                )
            }
        }

        return FetchResult(
            requestUrl = url,
            finalUrl = currentUrl,
            statusCode = -1,
            contentType = null,
            body = null,
            error = "Too many redirects (max: ${config.maxRedirects})",
            redirectChain = redirectChain,
        )
    }

    /**
     * Fetch with retry for transient failures.
     * Retryable: network errors, 5xx status codes.
     * Non-retryable: 4xx status codes, content issues.
     */
    fun fetchWithRetry(url: String): FetchResult {
        var lastResult: FetchResult? = null

        for (attempt in 0..config.maxRetries) {
            if (attempt > 0) {
                val backoff = calculateBackoff(attempt)
                logger.info("Retry {}/{} for {} (waiting {}ms)", attempt, config.maxRetries, url, backoff)
                Thread.sleep(backoff)
            }

            val result = fetch(url)
            lastResult = result

            if (!isRetryable(result)) {
                return result
            }

            logger.warn("Retryable failure for {}: status={}, error={}", url, result.statusCode, result.error)
        }

        return lastResult!!
    }

    internal fun isRetryable(result: FetchResult): Boolean {
        // Network error (status = -1) is retryable
        if (result.statusCode == -1) return true
        // 5xx server errors are retryable
        if (result.statusCode in 500..599) return true
        // Everything else is not retryable
        return false
    }

    internal fun calculateBackoff(attempt: Int): Long {
        val baseMs = config.retryBackoffBase.toMillis()
        // Exponential backoff: base * 2^(attempt-1), capped at 10 seconds
        return minOf(baseMs * (1L shl (attempt - 1)), 10_000L)
    }

    fun isHtmlContent(result: FetchResult): Boolean {
        val ct = result.contentType?.lowercase() ?: return false
        return config.allowedContentTypes.any { ct.contains(it) }
    }

    private fun resolveRedirect(currentUrl: String, location: String): String {
        return try {
            URI(currentUrl).resolve(location).toString()
        } catch (_: Exception) {
            location
        }
    }
}

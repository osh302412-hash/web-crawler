package com.crawler.crawler

import com.crawler.config.CrawlConfig
import com.crawler.fetcher.HttpFetcher
import com.crawler.frontier.CrawlFrontier
import com.crawler.model.*
import com.crawler.parser.HtmlParser
import com.crawler.ratelimit.RateLimiter
import com.crawler.robots.RobotsPolicy
import com.crawler.storage.CrawlStorage
import com.crawler.storage.JsonlStorage
import com.crawler.url.UrlNormalizer
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Instant

/**
 * Main crawler orchestrator.
 *
 * Flow: seed URLs → frontier → dequeue → robots check → rate limit →
 *       fetch (with retry) → content-type check → parse → extract links →
 *       normalize → enqueue new URLs → persist result → repeat
 */
class WebCrawler(private val config: CrawlConfig) {

    private val logger = LoggerFactory.getLogger(WebCrawler::class.java)

    private val normalizer = UrlNormalizer(config)
    private val frontier = CrawlFrontier(config)
    private val robotsPolicy = RobotsPolicy(config)
    private val rateLimiter = RateLimiter(config.perHostDelay)
    private val fetcher = HttpFetcher(config)
    private val parser = HtmlParser(normalizer)
    private val stats = CrawlStats()

    fun crawl(): CrawlStats {
        val storage: CrawlStorage = JsonlStorage(config.outputDir)

        try {
            logger.info("=== Crawl started ===")
            logger.info("Seed URLs: {}", config.seedUrls)
            logger.info("Max pages: {}, Max depth: {}", config.maxPages, config.maxDepth)

            // Enqueue seed URLs
            for (seedUrl in config.seedUrls) {
                val normalized = normalizer.normalize(seedUrl)
                if (normalized != null) {
                    frontier.enqueue(CrawlRequest(url = normalized, depth = 0))
                } else {
                    logger.warn("Invalid seed URL: {}", seedUrl)
                }
            }

            // Main crawl loop
            while (!frontier.isEmpty() && stats.pagesProcessed < config.maxPages) {
                val request = frontier.dequeue() ?: break
                processUrl(request, storage)
            }

            logger.info("=== Crawl finished ===")
            logger.info(stats.toString())
            logger.info("Frontier enqueued: {}, dequeued: {}, remaining: {}",
                frontier.enqueueCount, frontier.dequeueCount, frontier.size)
            logger.info("Results saved: {}", storage.count())

        } finally {
            storage.close()
        }

        return stats
    }

    internal fun processUrl(request: CrawlRequest, storage: CrawlStorage) {
        val url = request.url
        logger.info("Processing [depth={}, page={}/{}]: {}",
            request.depth, stats.pagesProcessed + 1, config.maxPages, url)

        stats.pagesProcessed++

        // Check robots.txt
        if (!robotsPolicy.isAllowed(url)) {
            logger.info("Blocked by robots.txt: {}", url)
            stats.robotsBlocked++
            return
        }

        // Apply rate limiting
        rateLimiter.waitIfNeeded(url)

        // Fetch with retry
        val fetchResult = fetcher.fetchWithRetry(url)

        // Track retries
        if (fetchResult.error != null && fetchResult.statusCode == -1) {
            stats.pagesFailed++
            saveFetchError(request, fetchResult, storage)
            return
        }

        if (fetchResult.statusCode !in 200..299) {
            stats.pagesFailed++
            saveFetchError(request, fetchResult, storage)
            return
        }

        stats.totalResponseTimeMs += fetchResult.responseTimeMs

        // Check content type
        if (!fetcher.isHtmlContent(fetchResult)) {
            logger.debug("Skipping non-HTML content: {} ({})", url, fetchResult.contentType)
            return
        }

        // Parse HTML
        val body = fetchResult.body ?: return
        val parsed = parser.parse(body, fetchResult.finalUrl)

        // Handle canonical URL
        val effectiveUrl = parsed.canonicalUrl ?: fetchResult.finalUrl
        if (parsed.canonicalUrl != null && parsed.canonicalUrl != fetchResult.finalUrl) {
            logger.debug("Using canonical URL: {} -> {}", fetchResult.finalUrl, parsed.canonicalUrl)
            if (frontier.hasVisited(parsed.canonicalUrl)) {
                stats.duplicatesSkipped++
                return
            }
            frontier.markVisited(parsed.canonicalUrl)
        }

        // Save result
        val result = CrawlResult(
            requestUrl = request.url,
            finalUrl = effectiveUrl,
            statusCode = fetchResult.statusCode,
            title = parsed.title,
            linkCount = parsed.links.size,
            extractedLinks = parsed.links,
            depth = request.depth,
            crawledAt = Instant.now(),
            responseTimeMs = fetchResult.responseTimeMs,
        )
        storage.save(result)
        stats.pagesSuccess++

        // Enqueue discovered links
        enqueueLinks(parsed.links, request, effectiveUrl)
    }

    private fun enqueueLinks(links: List<String>, parentRequest: CrawlRequest, parentUrl: String) {
        val nextDepth = parentRequest.depth + 1
        if (nextDepth > config.maxDepth) return

        for (link in links) {
            if (config.sameDomainOnly && !isSameDomain(link, parentUrl)) {
                continue
            }
            val added = frontier.enqueue(CrawlRequest(
                url = link,
                depth = nextDepth,
                parentUrl = parentUrl,
            ))
            if (!added && frontier.hasVisited(link)) {
                stats.duplicatesSkipped++
            }
        }
    }

    private fun isSameDomain(url1: String, url2: String): Boolean {
        return try {
            URI(url1).host?.lowercase() == URI(url2).host?.lowercase()
        } catch (_: Exception) {
            false
        }
    }

    private fun saveFetchError(request: CrawlRequest, fetchResult: FetchResult, storage: CrawlStorage) {
        val result = CrawlResult(
            requestUrl = request.url,
            finalUrl = fetchResult.finalUrl,
            statusCode = fetchResult.statusCode,
            title = null,
            linkCount = 0,
            extractedLinks = emptyList(),
            depth = request.depth,
            crawledAt = Instant.now(),
            error = fetchResult.error ?: "HTTP ${fetchResult.statusCode}",
            responseTimeMs = fetchResult.responseTimeMs,
        )
        storage.save(result)
    }
}

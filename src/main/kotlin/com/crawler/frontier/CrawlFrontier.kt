package com.crawler.frontier

import com.crawler.config.CrawlConfig
import com.crawler.config.CrawlStrategy
import com.crawler.model.CrawlRequest
import org.slf4j.LoggerFactory
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap

/**
 * URL Frontier: manages the queue of URLs to visit.
 *
 * Uses BFS (queue) by default because it explores pages level-by-level,
 * giving broader coverage of a site before going deep. This is the standard
 * approach for web crawlers as it avoids getting trapped in deep link chains.
 *
 * In-memory implementation. Extension point: replace with Redis-backed queue
 * for distributed crawling.
 */
class CrawlFrontier(private val config: CrawlConfig) {

    private val logger = LoggerFactory.getLogger(CrawlFrontier::class.java)
    private val queue = LinkedList<CrawlRequest>()
    private val visitedUrls = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var enqueueCount: Int = 0
        private set

    @Volatile
    var dequeueCount: Int = 0
        private set

    @Volatile
    var duplicatesSkipped: Int = 0
        private set

    val size: Int get() = queue.size
    val visitedCount: Int get() = visitedUrls.size

    /**
     * Enqueue a URL if it hasn't been visited and respects depth limits.
     * Returns true if the URL was added.
     */
    @Synchronized
    fun enqueue(request: CrawlRequest): Boolean {
        if (request.depth > config.maxDepth) {
            logger.debug("Depth limit reached for: {}", request.url)
            return false
        }
        if (!visitedUrls.add(request.url)) {
            duplicatesSkipped++
            logger.debug("Duplicate skipped: {}", request.url)
            return false
        }
        queue.add(request)
        enqueueCount++
        logger.debug("Enqueued [depth={}]: {}", request.depth, request.url)
        return true
    }

    /**
     * Dequeue the next URL to crawl.
     * BFS: poll from head. DFS: poll from tail.
     */
    @Synchronized
    fun dequeue(): CrawlRequest? {
        val request = when (config.crawlStrategy) {
            CrawlStrategy.BFS -> queue.pollFirst()
            CrawlStrategy.DFS -> queue.pollLast()
        }
        if (request != null) {
            dequeueCount++
        }
        return request
    }

    @Synchronized
    fun isEmpty(): Boolean = queue.isEmpty()

    fun hasVisited(url: String): Boolean = visitedUrls.contains(url)

    fun markVisited(url: String) {
        visitedUrls.add(url)
    }
}

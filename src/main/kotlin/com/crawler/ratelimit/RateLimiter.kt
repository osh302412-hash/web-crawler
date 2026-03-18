package com.crawler.ratelimit

import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-host rate limiter implementing politeness delays.
 *
 * Ensures that consecutive requests to the same host are separated
 * by at least the configured delay. This prevents overwhelming any
 * single server with rapid requests.
 *
 * Extension point: could be backed by Redis for distributed rate limiting.
 */
class RateLimiter(private val defaultDelay: Duration) {

    private val logger = LoggerFactory.getLogger(RateLimiter::class.java)
    private val lastAccessTimes = ConcurrentHashMap<String, Instant>()

    /**
     * Waits if necessary to respect the per-host delay.
     * Returns the actual wait time in milliseconds.
     */
    fun waitIfNeeded(url: String): Long {
        val host = extractHost(url) ?: return 0
        val now = Instant.now()
        val lastAccess = lastAccessTimes[host]

        if (lastAccess != null) {
            val elapsed = Duration.between(lastAccess, now)
            if (elapsed < defaultDelay) {
                val waitTime = defaultDelay.minus(elapsed).toMillis()
                if (waitTime > 0) {
                    logger.debug("Rate limiting: waiting {}ms for host {}", waitTime, host)
                    Thread.sleep(waitTime)
                }
                lastAccessTimes[host] = Instant.now()
                return waitTime
            }
        }

        lastAccessTimes[host] = now
        return 0
    }

    fun recordAccess(url: String) {
        val host = extractHost(url) ?: return
        lastAccessTimes[host] = Instant.now()
    }

    private fun extractHost(url: String): String? {
        return try {
            URI(url).host?.lowercase()
        } catch (_: Exception) {
            null
        }
    }
}

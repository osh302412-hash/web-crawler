package com.crawler.robots

import com.crawler.config.CrawlConfig
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches and parses robots.txt, then checks if a given path is allowed.
 *
 * Supports:
 * - User-agent matching (exact match + wildcard '*')
 * - Allow / Disallow directives
 * - Crawl-delay directive (informational)
 * - Caching with configurable TTL
 *
 * Fallback policy: if robots.txt cannot be fetched (network error, 5xx),
 * the configurable robotsFallbackAllow determines behavior (default: allow).
 * A 404 means no restrictions.
 */
class RobotsPolicy(private val config: CrawlConfig) {

    private val logger = LoggerFactory.getLogger(RobotsPolicy::class.java)
    private val cache = ConcurrentHashMap<String, CachedRobots>()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(config.connectTimeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    data class RobotsRule(
        val path: String,
        val allowed: Boolean,
    )

    data class RobotsData(
        val rules: List<RobotsRule>,
        val crawlDelay: Double? = null,
        val sitemaps: List<String> = emptyList(),
    )

    private data class CachedRobots(
        val data: RobotsData?,
        val fetchedAt: Instant,
        val isError: Boolean = false,
    )

    fun isAllowed(url: String): Boolean {
        if (!config.respectRobotsTxt) return true

        return try {
            val uri = URI(url)
            val host = "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
            val robots = getOrFetchRobots(host)
            if (robots == null) return config.robotsFallbackAllow

            val path = uri.rawPath ?: "/"
            isPathAllowed(robots, path)
        } catch (e: Exception) {
            logger.warn("Error checking robots.txt for {}: {}", url, e.message)
            config.robotsFallbackAllow
        }
    }

    internal fun isPathAllowed(robots: RobotsData, path: String): Boolean {
        // Find the most specific matching rule
        var bestMatch: RobotsRule? = null
        var bestLength = -1

        for (rule in robots.rules) {
            if (path.startsWith(rule.path) && rule.path.length > bestLength) {
                bestMatch = rule
                bestLength = rule.path.length
            }
        }

        return bestMatch?.allowed ?: true
    }

    private fun getOrFetchRobots(host: String): RobotsData? {
        val cached = cache[host]
        if (cached != null) {
            val age = java.time.Duration.between(cached.fetchedAt, Instant.now())
            if (age < config.robotsTxtCacheDuration) {
                return if (cached.isError) null else cached.data
            }
        }
        return fetchAndCacheRobots(host)
    }

    private fun fetchAndCacheRobots(host: String): RobotsData? {
        val robotsUrl = "$host/robots.txt"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI(robotsUrl))
                .header("User-Agent", config.userAgent)
                .timeout(config.requestTimeout)
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            when {
                response.statusCode() == 200 -> {
                    val data = parseRobotsTxt(response.body())
                    cache[host] = CachedRobots(data, Instant.now())
                    logger.info("Fetched robots.txt from {} ({} rules)", host, data.rules.size)
                    data
                }
                response.statusCode() == 404 -> {
                    // No robots.txt means everything is allowed
                    val data = RobotsData(emptyList())
                    cache[host] = CachedRobots(data, Instant.now())
                    logger.info("No robots.txt at {} (404) - all paths allowed", host)
                    data
                }
                else -> {
                    logger.warn("robots.txt at {} returned status {}", host, response.statusCode())
                    cache[host] = CachedRobots(null, Instant.now(), isError = true)
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to fetch robots.txt from {}: {}", host, e.message)
            cache[host] = CachedRobots(null, Instant.now(), isError = true)
            null
        }
    }

    internal fun parseRobotsTxt(content: String): RobotsData {
        val rules = mutableListOf<RobotsRule>()
        val sitemaps = mutableListOf<String>()
        var crawlDelay: Double? = null
        var isRelevantAgent = false

        val lines = content.lines()

        for (line in lines) {
            val trimmed = line.split("#").first().trim()
            if (trimmed.isEmpty()) continue

            val colonIndex = trimmed.indexOf(':')
            if (colonIndex == -1) continue

            val directive = trimmed.substring(0, colonIndex).trim().lowercase()
            val value = trimmed.substring(colonIndex + 1).trim()

            when (directive) {
                "user-agent" -> {
                    val agent = value.lowercase()
                    isRelevantAgent = agent == "*" ||
                        config.userAgent.lowercase().contains(agent)
                    // Specific agent match takes precedence over wildcard
                }
                "disallow" -> {
                    if (isRelevantAgent && value.isNotEmpty()) {
                        rules.add(RobotsRule(value, allowed = false))
                    }
                }
                "allow" -> {
                    if (isRelevantAgent && value.isNotEmpty()) {
                        rules.add(RobotsRule(value, allowed = true))
                    }
                }
                "crawl-delay" -> {
                    if (isRelevantAgent) {
                        crawlDelay = value.toDoubleOrNull()
                    }
                }
                "sitemap" -> {
                    sitemaps.add(value)
                }
            }
        }

        return RobotsData(rules, crawlDelay, sitemaps)
    }

    fun clearCache() {
        cache.clear()
    }
}

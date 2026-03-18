package com.crawler.url

import com.crawler.config.CrawlConfig
import java.net.URI

/**
 * Normalizes URLs to a canonical form for consistent deduplication.
 *
 * Policies applied:
 * - Resolves relative URLs against a base URL
 * - Removes fragment (#section) by default
 * - Removes trailing slash by default
 * - Lowercases scheme and host
 * - Removes default ports (80 for http, 443 for https)
 * - Does NOT normalize http↔https by default (configurable)
 * - Preserves query strings as-is
 */
class UrlNormalizer(private val config: CrawlConfig = CrawlConfig()) {

    fun normalize(url: String, baseUrl: String? = null): String? {
        return try {
            val resolved = if (baseUrl != null) {
                URI(baseUrl).resolve(url)
            } else {
                URI(url)
            }

            val scheme = resolved.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null

            val host = resolved.host?.lowercase() ?: return null
            val port = normalizePort(scheme, resolved.port)
            val path = normalizePath(resolved.rawPath ?: "/")
            val query = resolved.rawQuery
            val fragment = if (config.stripFragment) null else resolved.rawFragment

            buildString {
                append(if (config.normalizeHttps) "https" else scheme)
                append("://")
                append(host)
                if (port != -1) append(":$port")
                append(path)
                if (!query.isNullOrEmpty()) append("?$query")
                if (!fragment.isNullOrEmpty()) append("#$fragment")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizePort(scheme: String, port: Int): Int {
        if (port == -1) return -1
        if (scheme == "http" && port == 80) return -1
        if (scheme == "https" && port == 443) return -1
        return port
    }

    private fun normalizePath(path: String): String {
        val p = if (path.isEmpty()) "/" else path
        return if (config.stripTrailingSlash && p.length > 1 && p.endsWith("/")) {
            p.dropLast(1)
        } else {
            p
        }
    }

    companion object {
        /** Quick check: is this a URL scheme we should crawl? */
        fun isHttpUrl(url: String): Boolean {
            val trimmed = url.trim()
            return trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
        }

        /** Filter out non-crawlable link schemes. */
        fun isCrawlableLink(href: String): Boolean {
            val trimmed = href.trim().lowercase()
            if (trimmed.isEmpty()) return false
            val nonCrawlable = listOf("mailto:", "javascript:", "tel:", "ftp:", "data:", "#")
            return nonCrawlable.none { trimmed.startsWith(it) }
        }
    }
}

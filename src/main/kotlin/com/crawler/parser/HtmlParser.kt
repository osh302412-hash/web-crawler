package com.crawler.parser

import com.crawler.model.ParsedPage
import com.crawler.url.UrlNormalizer
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory

/**
 * Parses HTML content and extracts links, title, and canonical URL.
 *
 * Link extraction:
 * - Extracts href from <a> tags
 * - Resolves relative URLs against the page URL (respects <base> tag)
 * - Filters out non-HTTP schemes (mailto:, javascript:, tel:, etc.)
 * - Normalizes extracted URLs
 *
 * Canonical URL:
 * - Checks for <link rel="canonical"> in <head>
 * - If present, it can be used as the authoritative URL for deduplication
 */
class HtmlParser(private val normalizer: UrlNormalizer) {

    private val logger = LoggerFactory.getLogger(HtmlParser::class.java)

    fun parse(html: String, pageUrl: String): ParsedPage {
        return try {
            val doc = Jsoup.parse(html, pageUrl)

            // Extract title
            val title = doc.title().takeIf { it.isNotBlank() }

            // Check for <base> tag
            val baseHref = doc.selectFirst("base[href]")?.absUrl("href")
            val effectiveBase = baseHref ?: pageUrl

            // Extract canonical URL
            val canonicalUrl = doc.selectFirst("link[rel=canonical]")
                ?.attr("abs:href")
                ?.takeIf { it.isNotBlank() }
                ?.let { normalizer.normalize(it) }

            // Extract links from <a> tags
            val links = doc.select("a[href]")
                .asSequence()
                .map { it.attr("href").trim() }
                .filter { it.isNotBlank() }
                .filter { UrlNormalizer.isCrawlableLink(it) }
                .mapNotNull { normalizer.normalize(it, effectiveBase) }
                .distinct()
                .toList()

            logger.debug("Parsed {}: title='{}', links={}, canonical={}",
                pageUrl, title, links.size, canonicalUrl)

            ParsedPage(
                url = pageUrl,
                title = title,
                links = links,
                canonicalUrl = canonicalUrl,
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse HTML from {}: {}", pageUrl, e.message)
            ParsedPage(
                url = pageUrl,
                title = null,
                links = emptyList(),
                canonicalUrl = null,
            )
        }
    }
}

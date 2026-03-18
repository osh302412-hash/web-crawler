package com.crawler.storage

import com.crawler.model.CrawlResult

/**
 * Abstraction for crawl result storage.
 * Extension point: implement PostgreSQL or SQLite-backed storage.
 */
interface CrawlStorage {
    fun save(result: CrawlResult)
    fun close()
    fun count(): Int
}

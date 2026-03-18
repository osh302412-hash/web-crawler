package com.crawler.frontier

import com.crawler.config.CrawlConfig
import com.crawler.config.CrawlStrategy
import com.crawler.model.CrawlRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested

class CrawlFrontierTest {

    @Nested
    inner class Enqueue {
        @Test
        fun `enqueues URL and increments counter`() {
            val frontier = CrawlFrontier(CrawlConfig(maxDepth = 5))
            val added = frontier.enqueue(CrawlRequest("http://example.com", depth = 0))

            assertThat(added).isTrue()
            assertThat(frontier.size).isEqualTo(1)
            assertThat(frontier.enqueueCount).isEqualTo(1)
        }

        @Test
        fun `rejects duplicate URL`() {
            val frontier = CrawlFrontier(CrawlConfig(maxDepth = 5))
            frontier.enqueue(CrawlRequest("http://example.com", depth = 0))
            val added = frontier.enqueue(CrawlRequest("http://example.com", depth = 1))

            assertThat(added).isFalse()
            assertThat(frontier.size).isEqualTo(1)
            assertThat(frontier.duplicatesSkipped).isEqualTo(1)
        }

        @Test
        fun `rejects URL exceeding max depth`() {
            val frontier = CrawlFrontier(CrawlConfig(maxDepth = 2))
            val added = frontier.enqueue(CrawlRequest("http://example.com/deep", depth = 3))

            assertThat(added).isFalse()
            assertThat(frontier.size).isEqualTo(0)
        }

        @Test
        fun `allows URL at exact max depth`() {
            val frontier = CrawlFrontier(CrawlConfig(maxDepth = 2))
            val added = frontier.enqueue(CrawlRequest("http://example.com", depth = 2))

            assertThat(added).isTrue()
        }
    }

    @Nested
    inner class Dequeue {
        @Test
        fun `BFS dequeues in FIFO order`() {
            val frontier = CrawlFrontier(CrawlConfig(maxDepth = 5, crawlStrategy = CrawlStrategy.BFS))
            frontier.enqueue(CrawlRequest("http://example.com/first", depth = 0))
            frontier.enqueue(CrawlRequest("http://example.com/second", depth = 0))

            assertThat(frontier.dequeue()?.url).isEqualTo("http://example.com/first")
            assertThat(frontier.dequeue()?.url).isEqualTo("http://example.com/second")
        }

        @Test
        fun `DFS dequeues in LIFO order`() {
            val frontier = CrawlFrontier(CrawlConfig(maxDepth = 5, crawlStrategy = CrawlStrategy.DFS))
            frontier.enqueue(CrawlRequest("http://example.com/first", depth = 0))
            frontier.enqueue(CrawlRequest("http://example.com/second", depth = 0))

            assertThat(frontier.dequeue()?.url).isEqualTo("http://example.com/second")
            assertThat(frontier.dequeue()?.url).isEqualTo("http://example.com/first")
        }

        @Test
        fun `returns null when empty`() {
            val frontier = CrawlFrontier(CrawlConfig())
            assertThat(frontier.dequeue()).isNull()
        }

        @Test
        fun `increments dequeue counter`() {
            val frontier = CrawlFrontier(CrawlConfig(maxDepth = 5))
            frontier.enqueue(CrawlRequest("http://example.com", depth = 0))
            frontier.dequeue()

            assertThat(frontier.dequeueCount).isEqualTo(1)
        }
    }

    @Nested
    inner class VisitTracking {
        @Test
        fun `tracks visited URLs`() {
            val frontier = CrawlFrontier(CrawlConfig(maxDepth = 5))
            frontier.enqueue(CrawlRequest("http://example.com", depth = 0))

            assertThat(frontier.hasVisited("http://example.com")).isTrue()
            assertThat(frontier.hasVisited("http://other.com")).isFalse()
        }

        @Test
        fun `markVisited adds to visited set without enqueuing`() {
            val frontier = CrawlFrontier(CrawlConfig(maxDepth = 5))
            frontier.markVisited("http://example.com")

            assertThat(frontier.hasVisited("http://example.com")).isTrue()
            assertThat(frontier.size).isEqualTo(0)
        }
    }
}

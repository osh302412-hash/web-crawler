package com.crawler.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class RateLimiterTest {

    @Test
    fun `first request to host is not delayed`() {
        val limiter = RateLimiter(Duration.ofMillis(500))
        val waited = limiter.waitIfNeeded("http://example.com/page1")
        assertThat(waited).isEqualTo(0)
    }

    @Test
    fun `second request to same host is delayed`() {
        val limiter = RateLimiter(Duration.ofMillis(200))
        limiter.waitIfNeeded("http://example.com/page1")

        val start = System.currentTimeMillis()
        limiter.waitIfNeeded("http://example.com/page2")
        val elapsed = System.currentTimeMillis() - start

        assertThat(elapsed).isGreaterThanOrEqualTo(150) // allow some tolerance
    }

    @Test
    fun `different hosts are not delayed relative to each other`() {
        val limiter = RateLimiter(Duration.ofMillis(500))
        limiter.waitIfNeeded("http://example.com/page1")

        val start = System.currentTimeMillis()
        limiter.waitIfNeeded("http://other.com/page1")
        val elapsed = System.currentTimeMillis() - start

        assertThat(elapsed).isLessThan(100)
    }

    @Test
    fun `handles invalid URL gracefully`() {
        val limiter = RateLimiter(Duration.ofMillis(200))
        val waited = limiter.waitIfNeeded("not-a-valid-url")
        assertThat(waited).isEqualTo(0)
    }
}

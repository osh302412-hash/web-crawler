package com.crawler.storage

import com.crawler.model.CrawlResult
import com.google.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

class JsonlStorageTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `saves result as JSONL line`() {
        val storage = JsonlStorage(tempDir.absolutePath)
        val result = createResult("http://example.com")

        storage.save(result)
        storage.close()

        val files = tempDir.listFiles { f -> f.name.endsWith(".jsonl") }!!
        assertThat(files).hasSize(1)

        val lines = files[0].readLines()
        assertThat(lines).hasSize(1)

        val parsed = Gson().fromJson(lines[0], Map::class.java)
        assertThat(parsed["requestUrl"]).isEqualTo("http://example.com")
        assertThat(parsed["statusCode"]).isEqualTo(200.0) // Gson parses as double
    }

    @Test
    fun `saves multiple results as separate lines`() {
        val storage = JsonlStorage(tempDir.absolutePath)

        storage.save(createResult("http://example.com/page1"))
        storage.save(createResult("http://example.com/page2"))
        storage.save(createResult("http://example.com/page3"))
        storage.close()

        val files = tempDir.listFiles { f -> f.name.endsWith(".jsonl") }!!
        val lines = files[0].readLines()
        assertThat(lines).hasSize(3)
    }

    @Test
    fun `tracks record count`() {
        val storage = JsonlStorage(tempDir.absolutePath)

        storage.save(createResult("http://example.com/1"))
        storage.save(createResult("http://example.com/2"))

        assertThat(storage.count()).isEqualTo(2)
        storage.close()
    }

    @Test
    fun `creates output directory if not exists`() {
        val subDir = File(tempDir, "sub/dir")
        val storage = JsonlStorage(subDir.absolutePath)
        storage.save(createResult("http://example.com"))
        storage.close()

        assertThat(subDir.exists()).isTrue()
    }

    private fun createResult(url: String) = CrawlResult(
        requestUrl = url,
        finalUrl = url,
        statusCode = 200,
        title = "Test Page",
        linkCount = 5,
        extractedLinks = listOf("http://example.com/link1"),
        depth = 0,
        crawledAt = Instant.parse("2024-01-01T00:00:00Z"),
    )
}

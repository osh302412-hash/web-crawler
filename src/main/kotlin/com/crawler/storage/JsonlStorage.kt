package com.crawler.storage

import com.crawler.model.CrawlResult
import com.google.gson.*
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.lang.reflect.Type
import java.time.Instant

/**
 * Stores crawl results as JSON Lines (one JSON object per line).
 *
 * JSONL format is ideal for streaming writes and easy post-processing
 * with tools like jq, Python, or any line-oriented tool.
 *
 * Output file: {outputDir}/crawl-{timestamp}.jsonl
 */
class JsonlStorage(outputDir: String) : CrawlStorage {

    private val logger = LoggerFactory.getLogger(JsonlStorage::class.java)
    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(Instant::class.java, InstantAdapter())
        .create()

    private class InstantAdapter : JsonSerializer<Instant>, JsonDeserializer<Instant> {
        override fun serialize(src: Instant, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
            JsonPrimitive(src.toString())

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Instant =
            Instant.parse(json.asString)
    }

    private val file: File
    private val writer: BufferedWriter
    private var recordCount = 0

    init {
        val dir = File(outputDir)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val timestamp = Instant.now().toString().replace(":", "-").substringBefore(".")
        file = File(dir, "crawl-$timestamp.jsonl")
        writer = BufferedWriter(FileWriter(file, true))
        logger.info("Storage initialized: {}", file.absolutePath)
    }

    @Synchronized
    override fun save(result: CrawlResult) {
        val json = gson.toJson(result)
        writer.write(json)
        writer.newLine()
        writer.flush()
        recordCount++
        logger.debug("Saved result for: {}", result.requestUrl)
    }

    override fun close() {
        writer.close()
        logger.info("Storage closed. {} records written to {}", recordCount, file.absolutePath)
    }

    override fun count(): Int = recordCount

    fun getFilePath(): String = file.absolutePath
}

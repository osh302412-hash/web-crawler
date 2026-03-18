package com.crawler

import com.crawler.config.CrawlConfig
import com.crawler.config.CrawlStrategy
import com.crawler.crawler.WebCrawler
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long

class CrawlCommand : CliktCommand(
    name = "web-crawler",
    help = "A learning-oriented web crawler with politeness controls"
) {
    private val seedUrls by argument(help = "Seed URLs to start crawling from").multiple()
    private val maxPages by option("--max-pages", help = "Maximum pages to crawl").int().default(50)
    private val maxDepth by option("--max-depth", help = "Maximum crawl depth").int().default(3)
    private val delay by option("--delay", help = "Per-host delay in milliseconds").long().default(1000)
    private val outputDir by option("--output", "-o", help = "Output directory").default("crawl-output")
    private val userAgent by option("--user-agent", help = "User-Agent header").default("LearningCrawler/1.0")
    private val noRobots by option("--no-robots", help = "Disable robots.txt checking").flag()
    private val sameDomainOnly by option("--same-domain", help = "Only crawl same domain").flag(default = true)
    private val dfs by option("--dfs", help = "Use DFS instead of BFS").flag()

    override fun run() {
        if (seedUrls.isEmpty()) {
            echo("Error: at least one seed URL is required", err = true)
            echo("Usage: web-crawler [OPTIONS] URL [URL...]")
            echo("Try --help for more information.")
            return
        }

        val config = CrawlConfig(
            seedUrls = seedUrls,
            maxPages = maxPages,
            maxDepth = maxDepth,
            perHostDelay = java.time.Duration.ofMillis(delay),
            outputDir = outputDir,
            userAgent = userAgent,
            respectRobotsTxt = !noRobots,
            sameDomainOnly = sameDomainOnly,
            crawlStrategy = if (dfs) CrawlStrategy.DFS else CrawlStrategy.BFS,
        )

        echo("Starting crawl with ${seedUrls.size} seed URL(s)...")
        echo("Config: maxPages=$maxPages, maxDepth=$maxDepth, delay=${delay}ms, sameDomain=$sameDomainOnly")

        val crawler = WebCrawler(config)
        val stats = crawler.crawl()

        echo("\nCrawl complete!")
        echo(stats.toString())
    }
}

fun main(args: Array<String>) = CrawlCommand().main(args)

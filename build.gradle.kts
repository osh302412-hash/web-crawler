plugins {
    kotlin("jvm") version "1.9.22"
    application
}

group = "com.crawler"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // HTML parsing
    implementation("org.jsoup:jsoup:1.17.2")

    // JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.11")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // CLI argument parsing
    implementation("com.github.ajalt.clikt:clikt:4.2.2")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.assertj:assertj-core:3.25.1")
}

application {
    mainClass.set("com.crawler.MainKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.crawler.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

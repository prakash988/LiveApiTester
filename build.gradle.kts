import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

intellij {
    pluginName.set(providers.gradleProperty("pluginName").get())
    version.set(providers.gradleProperty("platformVersion").get())
    type.set(providers.gradleProperty("platformType").get())
    plugins.set(listOf("com.intellij.java"))
    downloadSources.set(true)
    updateSinceUntilBuild.set(true)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set(providers.gradleProperty("pluginSinceBuild").get())
        untilBuild.set(providers.gradleProperty("pluginUntilBuild").get())
        pluginDescription.set(
            """
            <p><b>LiveApiTester</b> — AI-Powered Live API Testing Plugin for IntelliJ IDEA.</p>
            <p>Like Postman/Bruno but embedded directly in the IDE, powered by GitHub Models AI and full IntelliJ Debugger Integration.</p>
            <ul>
              <li>Live HTTP client supporting GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS</li>
              <li>AI-powered error explanation and test generation via GitHub Models (GPT-4o, Claude, Llama, Mistral)</li>
              <li>🐛 Send &amp; Debug — auto-sets breakpoints on matching controller methods</li>
              <li>▶️ Start/Stop backend services directly from the plugin</li>
              <li>Spring Boot &amp; JAX-RS endpoint scanner</li>
              <li>Collections, environments, and request history</li>
              <li>Variable interpolation with {{variable}} syntax</li>
              <li>Auth support: Bearer, Basic, API Key</li>
              <li>Keyboard shortcuts: Ctrl+Enter (send), Ctrl+Shift+Enter (debug), Escape (cancel)</li>
              <li>Copy as cURL, response time color coding, auth header masking</li>
            </ul>
            """.trimIndent()
        )
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN") ?: "")
        privateKey.set(System.getenv("PRIVATE_KEY") ?: "")
        password.set(System.getenv("PRIVATE_KEY_PASSWORD") ?: "")
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN") ?: "")
    }

    buildSearchableOptions {
        enabled = false
    }
}

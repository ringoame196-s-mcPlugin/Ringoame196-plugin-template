import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties

val minecraftVersion: String by project
val pluginVersion: String by project
val fullVersion = "$minecraftVersion-$pluginVersion"

val sharedProps = Properties().apply {
    val propFile = rootDir.parentFile.resolve("server-config.properties")
    if (propFile.exists()) {
        load(propFile.inputStream())
    }
}

val deployPath = sharedProps.getProperty("deployPath")
val serverIp = sharedProps.getProperty("serverIp")
val serverPort = sharedProps.getProperty("serverPort")

plugins {
    kotlin("jvm") version "2.0.20"
    id("com.gradleup.shadow") version "8.3.7"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
    id("org.jmailen.kotlinter") version "4.4.0"
}

group = "com.github.ringoame196"
version = fullVersion

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$minecraftVersion-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

detekt {
    toolVersion = "1.23.6"
    source.setFrom("src/main/kotlin", "src/main/java")
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    ignoreFailures = false
}

tasks.withType<ShadowJar> {
    archiveBaseName.set(project.name)
    archiveVersion.set("")
    archiveClassifier.set("")
    relocate("kotlin", "${project.group}.libs.kotlin")
    relocate("org.intellij.lang.annotations", "${project.group}.libs.org.intellij.lang.annotations")
    relocate("org.jetbrains.annotations", "${project.group}.libs.org.jetbrains.annotations")
}

// 自動デプロイタスク（JARコピー & サーバーAPI通知）
val deployPlugin by tasks.registering {
    group = "deployment"
    description = "ビルドされたJARをサーバープラグインフォルダへコピーし、通知APIを呼び出します"

    dependsOn("shadowJar")

    val isCI = System.getenv("CI") != null
    val copyDirPath = if (isCI) {
        layout.buildDirectory.dir("deploy").get().asFile.path
    } else {
        deployPath ?: layout.buildDirectory.dir("dummy").get().asFile.path
    }
    val targetDir = file(copyDirPath)

    doLast {
        if (deployPath.isNullOrBlank() || serverIp.isNullOrBlank() || serverPort.isNullOrBlank()) {
            logger.lifecycle("server-config.propertiesが未設定のため、プラグインの自動デプロイとAPI通知をスキップします。")
            return@doLast
        }

        if (isCI) {
            logger.lifecycle("CI環境のため、プラグインの自動デプロイとAPI通知をスキップします。")
            return@doLast
        }

        if (!targetDir.exists() || !targetDir.isDirectory) {
            logger.warn("自動コピーをスキップしました: ディレクトリが存在しません (${targetDir.path})")
            return@doLast
        }

        val jarFile = layout.buildDirectory.file("libs/${project.name}.jar").get().asFile
        if (jarFile.exists()) {
            copy {
                from(jarFile)
                into(targetDir)
            }
            logger.lifecycle("プラグインをコピーしました: ${jarFile.name} -> ${targetDir.path}")
        } else {
            logger.error("コピー対象のJARファイルが見つかりません: ${jarFile.path}")
            return@doLast
        }

        val port = serverPort.toInt()
        val apiUrl = "http://$serverIp:$port/plugin?name=${project.name}"

        try {
            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                requestMethod = "GET"
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                logger.lifecycle("API Response: $response")
            } else {
                logger.warn("Server responded with error code: ${connection.responseCode}")
            }
            connection.disconnect()
        } catch (e: java.net.ConnectException) {
            logger.warn("Warning: サーバーに接続できません（オフラインの可能性があります）")
        } catch (e: java.net.SocketTimeoutException) {
            logger.warn("Warning: 接続がタイムアウトしました")
        } catch (e: Exception) {
            logger.warn("Warning: API通信で予期しないエラーが発生しました: ${e.message}")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

bukkit {
    main = "com.github.ringoame196.Main"
    apiVersion = "1." + minecraftVersion.split(".")[1]
    author = "ringoame196"
    website = "https://github.com/ringoame196-s-mcPlugin"
    description = "Minecraft plugin template"

    permissions {
        register("${project.name}.op") {
            description = "op権限"
            default = BukkitPluginDescription.Permission.Default.OP
        }
    }
}

tasks {
    build {
        dependsOn(shadowJar)
        finalizedBy(deployPlugin)
    }
}

tasks.register("printVersion") {
    doLast {
        println(fullVersion)
    }
}

tasks.register<SetupTask>("setup") {
    group = "setup"
    description = "setupタスク"
}

open class SetupTask : DefaultTask() {
    @TaskAction
    fun setup() {
        val targetBranch = "developer"

        println("Git ブランチを確認・作成しています: $targetBranch ...")

        val process = ProcessBuilder("git", "switch", "-c", targetBranch)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            println("Successfully switched/created branch to $targetBranch")
            println(output)
        } else {
            if (output.contains("already exists") || output.contains("既に存在")) {
                println("ブランチ '$targetBranch' は既に存在するため、切り替えを試みます...")
                val fallbackProcess = ProcessBuilder("git", "switch", targetBranch)
                    .redirectErrorStream(true)
                    .start()
                val fallbackOutput = fallbackProcess.inputStream.bufferedReader().use { it.readText() }
                if (fallbackProcess.waitFor() == 0) {
                    println("Successfully switched to existing branch: $targetBranch")
                } else {
                    logger.error("ブランチの切り替えに失敗しました:\n$fallbackOutput")
                }
            } else {
                logger.error("ブランチの処理に失敗しました (Exit code: $exitCode):\n$output")
            }
        }

        // 🏷 タグ（GitHub Topics）を設定する処理を追加
        setupGitHubTopics()
    }

    private fun setupGitHubTopics() {
        try {
            val remoteProcess = ProcessBuilder("git", "remote", "get-url", "origin")
                .redirectErrorStream(true)
                .start()
            val remoteUrl = remoteProcess.inputStream.bufferedReader().use { it.readText() }.trim()
            remoteProcess.waitFor()

            val regex = Regex("github\\.com[:/]([^/]+)/([^/\\.]+)(?:\\.git)?")
            val match = regex.find(remoteUrl)
            if (match == null) {
                logger.warn("GitHub のリモートURLが取得できなかったため、Topics の設定をスキップします。")
                return
            }

            val (account, repo) = match.destructured
            val repoPath = "$account/$repo"
            val lowerProjectName = project.name.lowercase().replace("_", "-")

            println("GitHub Topics を設定しています: $repoPath ...")
            val process = ProcessBuilder(
                "gh", "repo", "edit", repoPath,
                "--add-topic", "minecraft",
                "--add-topic", "minecraft-plugin",
                "--add-topic", "paper",
                "--add-topic", "papermc",
                "--add-topic", "kotlin",
                "--add-topic", lowerProjectName
            )
                .inheritIO()
                .start()

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                println("✅ GitHub Topics を設定しました")
            } else {
                logger.warn("GitHub Topics の設定に失敗しました (Exit code: $exitCode)")
            }
        } catch (e: Exception) {
            logger.warn("GitHub Topics の設定中にエラーが発生しました: ${e.message}")
        }
    }
}
package be.ajuvercr.swls

import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LanguageServerManager


private val LOG = Logger.getInstance(SwlsServer::class.java)

private const val REPO = "SemanticWebLanguageServer/swls"
private const val PLAIN = "swls"
private val VERSION_REGEX = Regex("""version=(\S+)""")
private val STABLE_TAG = Regex("""swls-v\d.*""")

/** A GitHub release: [tag] to download assets from, [version] its binary reports. */
private data class Release(val tag: String, val version: String)

class SwlsFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider = SwlsServer(project)
    override fun createLanguageClient(project: Project): LanguageClientImpl = SwlsLanguageClient(project)
}

class SwlsServer(project: Project) : ProcessStreamConnectionProvider() {
    init {
        val settings = SwlsSettings.getInstance().state

        val swlsSettings = SwlsSettings.getInstance()
        if (settings.command.isNotBlank()) {
            val path = Paths.get(settings.command)
            if (!Files.exists(path)) {
                throw RuntimeException("Specified SWLS binary not found: ${settings.command}")
            }
            swlsSettings.resolvedBinary = settings.command
            super.setCommands(listOf(settings.command))
        } else {
            val binPath = getBinPath()

            if (Files.exists(binPath)) {
                LOG.info("Using existing binary at: $binPath")
                swlsSettings.resolvedBinary = binPath.toAbsolutePath().toString()
                super.setCommands(listOf(binPath.toAbsolutePath().toString()))
                if (settings.checkUpdate) checkForUpdatesAsync(binPath, project, settings.automaticUpdate, settings.channel)
            } else {
                var downloaded: Path? = null
                ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    {
                        try {
                            downloaded = downloadLatest(binPath, settings.channel)
                        } catch (e: Exception) {
                            LOG.error("Failed to download LSP binary", e)
                        }
                    },
                    "Downloading Semantic Web Language Server...",
                    false,
                    null
                )

                if (downloaded == null) {
                    throw RuntimeException("Could not download Semantic Web Language Server binary.")
                }

                swlsSettings.resolvedBinary = downloaded!!.toAbsolutePath().toString()
                super.setCommands(listOf(downloaded!!.toAbsolutePath().toString()))
            }
        }
    }

    override fun getInitializationOptions(rootUri: VirtualFile?): Any {
        val s = SwlsSettings.getInstance().state

        fun parseLines(raw: String): List<String> =
            raw.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val completionStrict = parseLines(s.completionStrict)
        val completionExcept = parseLines(s.completionExcept)
        val completion: Any = when {
            completionStrict.isNotEmpty() -> mapOf("strict" to completionStrict)
            completionExcept.isNotEmpty() -> mapOf("loose" to completionExcept)
            else -> s.completionMode
        }

        return mapOf(
            "log" to s.log,
            "turtle" to s.turtle,
            "trig" to s.trig,
            "jsonld" to s.jsonld,
            "sparql" to s.sparql,
            "ontologies" to parseLines(s.ontologies),
            "shapes" to parseLines(s.shapes),
            "disabled" to parseLines(s.disabled),
            "prefix_disabled" to parseLines(s.prefixDisabled),
            "completion" to completion,
        )
    }

    companion object {
        private fun getBinPath(): Path {
            val cacheDir = Paths.get(PathManager.getSystemPath(), "swls", "bin")
            Files.createDirectories(cacheDir)
            return cacheDir.resolve(getBinaryName())
        }

        private fun getTarget(): String {
            val osName = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()

            return when {
                osName.contains("win") && arch == "amd64"    -> "windows-x86_64.exe"
                osName.contains("win") && arch == "aarch64"  -> "windows-arm64.exe"
                osName.contains("linux") && arch == "amd64"  -> "linux-x86_64"
                osName.contains("linux") && arch == "aarch64" -> "linux-aarch64"
                osName.contains("mac") && arch == "x86_64"   -> "macos-x86_64"
                osName.contains("mac") && arch == "aarch64"  -> "macos-arm64"
                else -> throw RuntimeException("Unsupported platform: $osName-$arch")
            }
        }

        private fun getBinaryName(): String {
            val osName = System.getProperty("os.name").lowercase()
            return if (osName.contains("win")) "swls.exe" else "swls"
        }

        private fun githubApi(path: String): com.google.gson.JsonElement {
            val conn = (URL("https://api.github.com/repos/$REPO$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            if (conn.responseCode != 200) {
                throw RuntimeException("GitHub API $path: HTTP ${conn.responseCode}")
            }

            val payload = conn.inputStream.bufferedReader().readText()
            return JsonParser.parseString(payload)
        }

        private fun getLatestRelease(channel: String): Release {
            if (channel == "nightly") {
                // A single rolling release, rebuilt on every push to main. Its name is
                // `nightly-<short sha>`, which is also what the binary reports.
                val release = githubApi("/releases/tags/nightly").asJsonObject
                val name = release.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: throw RuntimeException("Nightly release has no name")
                return Release(tag = "nightly", version = name)
            }

            // Only main binary releases (swls-vX.Y.Z), not sub-crate tags
            // (swls-lang-*, swls-core-*), and no pre-releases.
            val latest = githubApi("/releases").asJsonArray
                .map { it.asJsonObject }
                .firstOrNull {
                    val tag = it.get("tag_name")?.takeIf { t -> t.isJsonPrimitive }?.asString
                    tag != null && STABLE_TAG.matches(tag) &&
                        it.get("prerelease")?.asBoolean != true &&
                        it.get("draft")?.asBoolean != true
                } ?: throw RuntimeException("No stable release found")

            val tag = latest.get("tag_name").asString
            return Release(tag = tag, version = normalizeVersion(tag))
        }

        /** `swls --version` may report "0.3.0" while stable tags are "swls-v0.3.0". */
        private fun normalizeVersion(version: String): String = version.removePrefix("swls-v")

        private fun getCurrentVersion(binaryPath: Path): String {
            return try {
                val process = ProcessBuilder(binaryPath.toAbsolutePath().toString(), "--version")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                val match = VERSION_REGEX.find(output)
                match?.groupValues?.get(1) ?: ""
            } catch (e: Exception) {
                LOG.info("Could not get version from binary: $e")
                ""
            }
        }

        private fun downloadLatest(binPath: Path, channel: String): Path {
            return downloadVersion(binPath, getLatestRelease(channel).tag)
        }

        private fun downloadVersion(binPath: Path, version: String): Path {
            Files.createDirectories(binPath.parent)
            val target = getTarget()
            val url = "https://github.com/$REPO/releases/download/$version/$PLAIN-$target"
            LOG.info("Downloading from $url to $binPath")

            val tmpPath = binPath.resolveSibling(binPath.fileName.toString() + ".tmp")
            try {
                URL(url).openStream().use { input ->
                    FileOutputStream(tmpPath.toFile()).use { output ->
                        input.copyTo(output)
                    }
                }

                val osName = System.getProperty("os.name").lowercase()
                if (!osName.contains("win")) {
                    tmpPath.toFile().setExecutable(true, false)
                }

                Files.move(tmpPath, binPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                Files.deleteIfExists(tmpPath)
                throw e
            }

            LOG.info("Downloaded $version")
            return binPath
        }

        private fun checkForUpdatesAsync(binPath: Path, project: Project, automaticUpdate: Boolean, channel: String) {
            Thread {
                try {
                    val currentVersion = normalizeVersion(getCurrentVersion(binPath))
                    val latest = getLatestRelease(channel)
                    val latestVersion = latest.version

                    if (currentVersion == latestVersion) {
                        LOG.info("Already at latest $channel version: $currentVersion")
                        return@Thread
                    }

                    LOG.info("Update available: $currentVersion → $latestVersion")

                    if (automaticUpdate) {
                        downloadVersion(binPath, latest.tag)
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("swls")
                            .createNotification(
                                "swls updated to $latestVersion.",
                                NotificationType.INFORMATION
                            )
                            .addAction(NotificationAction.createSimple("Restart Server") {
                                val manager = LanguageServerManager.getInstance(project)
                                manager.stop("swls")
                                manager.start("swls", LanguageServerManager.StartOptions().setForceStart(true))
                            })
                            .notify(project)
                    } else {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("swls")
                            .createNotification(
                                "swls $latestVersion is available.",
                                NotificationType.INFORMATION
                            )
                            .addAction(object : NotificationAction("Download & Restart") {
                                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                                    notification.expire()
                                    ProgressManager.getInstance().run(
                                        object : Task.Backgroundable(project, "Downloading swls $latestVersion...") {
                                            override fun run(indicator: ProgressIndicator) {
                                                downloadVersion(binPath, latest.tag)
                                                val manager = LanguageServerManager.getInstance(project)
                                                manager.stop("swls")
                                                manager.start("swls", LanguageServerManager.StartOptions().setForceStart(true))
                                            }
                                        }
                                    )
                                }
                            })
                            .notify(project)
                    }
                } catch (e: Exception) {
                    LOG.warn("Background update check failed: $e")
                }
            }.apply {
                name = "swls-update-check"
                isDaemon = true
                start()
            }
        }
    }
}

// enabling the semanticTokens plugin is required for semantic tokens support
class SwlsLanguageClient(project: Project) : LanguageClientImpl(project) {
}

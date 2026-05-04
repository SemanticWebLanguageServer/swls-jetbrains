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
import com.intellij.openapi.progress.ProgressManager


private val LOG = Logger.getInstance(SwlsServer::class.java)

private const val REPO = "SemanticWebLanguageServer/swls"
private const val PLAIN = "swls"
private val VERSION_REGEX = Regex("""version=(\S+)""")

class SwlsFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider = SwlsServer(project)
    override fun createLanguageClient(project: Project): LanguageClientImpl = SwlsLanguageClient(project)
}

class SwlsServer(project: Project) : ProcessStreamConnectionProvider() {
    init {
        val binPath = getBinPath()

        if (Files.exists(binPath)) {
            // Binary exists — launch immediately, check for updates in the background
            LOG.info("Using existing binary at: $binPath")
            super.setCommands(listOf(binPath.toAbsolutePath().toString()))
            checkForUpdatesAsync(binPath, project)
        } else {
            // No binary yet — must download before we can start
            var downloaded: Path? = null
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                {
                    try {
                        downloaded = downloadLatest(binPath)
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

            super.setCommands(listOf(downloaded!!.toAbsolutePath().toString()))
        }
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

        private fun getLatestRelease(): String {
            val conn = (URL("https://api.github.com/repos/$REPO/releases").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            if (conn.responseCode != 200) {
                throw RuntimeException("Failed to fetch releases: HTTP ${conn.responseCode}")
            }

            val payload = conn.inputStream.bufferedReader().readText()
            val releases = JsonParser.parseString(payload).asJsonArray
            val latest = releases.firstOrNull {
                val tag = it.asJsonObject.get("tag_name")?.asString
                tag != null && tag.startsWith("swls-")
            } ?: throw RuntimeException("No valid release found")

            return latest.asJsonObject.get("tag_name").asString
        }

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

        private fun downloadLatest(binPath: Path): Path {
            val latestVersion = getLatestRelease()
            return downloadVersion(binPath, latestVersion)
        }

        private fun downloadVersion(binPath: Path, version: String): Path {
            Files.createDirectories(binPath.parent)
            val target = getTarget()
            val url = "https://github.com/$REPO/releases/download/$version/$PLAIN-$target"
            LOG.info("Downloading from $url to $binPath")

            URL(url).openStream().use { input ->
                FileOutputStream(binPath.toFile()).use { output ->
                    input.copyTo(output)
                }
            }

            val osName = System.getProperty("os.name").lowercase()
            if (!osName.contains("win")) {
                binPath.toFile().setExecutable(true, false)
            }

            LOG.info("Downloaded $version")
            return binPath
        }

        private fun checkForUpdatesAsync(binPath: Path, project: Project) {
            Thread {
                try {
                    val currentVersion = getCurrentVersion(binPath)
                    val latestVersion = getLatestRelease()

                    if (currentVersion == latestVersion) {
                        LOG.info("Already at latest version: $currentVersion")
                        return@Thread
                    }

                    LOG.info("Update available: $currentVersion → $latestVersion")
                    downloadVersion(binPath, latestVersion)

                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("swls")
                        .createNotification(
                            "swls updated to $latestVersion. Restart IDE to apply.",
                            NotificationType.INFORMATION
                        )
                        .notify(project)
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
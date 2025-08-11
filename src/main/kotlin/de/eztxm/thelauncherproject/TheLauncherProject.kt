package de.eztxm.thelauncherproject

import de.eztxm.thelauncherproject.service.MicrosoftAuthenticationService
import de.eztxm.thelauncherproject.service.MinecraftLauncherService
import de.eztxm.thelauncherproject.service.ProfileManager
import de.eztxm.thelauncherproject.service.VersionManagerService
import de.eztxm.thelauncherproject.web.WebViewBridge
import java.net.CookieHandler
import java.net.CookieManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import netscape.javascript.JSObject

class TheLauncherProject : javafx.application.Application() {
    private lateinit var minecraftLauncherService: MinecraftLauncherService
    private lateinit var microsoftAuthenticationService: MicrosoftAuthenticationService
    private lateinit var versionManagerService: VersionManagerService
    private lateinit var profileManager: ProfileManager

    override fun start(primaryStage: Stage) {
        // Configure persistent app directories and cookies early
        val baseDir = resolveAppDir()
        System.setProperty("launcher.baseDir", baseDir.toString())
        Files.createDirectories(baseDir.resolve("logs"))
        Files.createDirectories(baseDir.resolve("cache"))
        Files.createDirectories(baseDir.resolve("runtime"))
        CookieHandler.setDefault(CookieManager())

        minecraftLauncherService = MinecraftLauncherService()
        microsoftAuthenticationService = MicrosoftAuthenticationService()

        // Initialize managers bound to the baseDir
        versionManagerService = VersionManagerService(baseDir)
        profileManager = ProfileManager(baseDir)

        val webView = WebView()
        val webEngine: WebEngine = webView.engine

        webEngine.isJavaScriptEnabled = true

        // Robust resource loading with a fallback
        val indexUrl = javaClass.getResource("/index.html")
        if (indexUrl != null) {
            webEngine.load(indexUrl.toExternalForm())
        } else {
            webEngine.loadContent(
                    """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"><title>Missing UI</title></head>
                <body>
                    <h2>index.html not found</h2>
                    <p>Place /index.html on the classpath (src/main/resources/index.html).</p>
                </body></html>
                """.trimIndent()
            )
        }

        val bridge =
                WebViewBridge(
                        minecraftLauncherService,
                        microsoftAuthenticationService,
                        versionManagerService,
                        profileManager
                )

        // Defer bridge injection until the page has fully loaded
        webEngine.loadWorker.stateProperty().addListener { _, _, newState ->
            if (newState == Worker.State.SUCCEEDED) {
                try {
                    val window = webEngine.executeScript("window") as JSObject
                    window.setMember("app", bridge)
                    // Signal readiness and pass selected profile name to UI
                    val selectedProfile = profileManager.getSelectedProfileName() ?: ""
                    webEngine.executeScript(
                            """
                        try {
                          window.dispatchEvent(new CustomEvent('appReady', { detail: { selectedProfile: ${if (selectedProfile.isEmpty()) "null" else "'$selectedProfile'"} } }));
                          if (!document.getElementById('native-status')) {
                            var el=document.createElement('div');
                            el.id='native-status';
                            el.style='position:fixed;bottom:8px;right:8px;font:12px sans-serif;color:#777;pointer-events:none;';
                            el.textContent='App Ready';
                            document.body.appendChild(el);
                          }
                        } catch(e) { /* no-op */ }
                        """.trimIndent()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
                println("WebView load failed: ${webEngine.loadWorker.exception?.message}")
                webEngine.loadContent(
                        "<h3>Failed to load UI.</h3><pre>${webEngine.loadWorker.exception}</pre>"
                )
            }
        }

        // Surface JS alerts to the JVM console
        webEngine.onAlert = javafx.event.EventHandler { evt -> println("JS Alert: ${evt.data}") }

        // Surface exceptions if they occur during load
        webEngine.loadWorker.exceptionProperty().addListener { _, _, newException ->
            newException?.let { it.printStackTrace() }
        }

        val scene = Scene(webView, 800.0, 600.0)
        primaryStage.scene = scene
        primaryStage.title = "Minecraft Launcher in Kotlin"
        primaryStage.minWidth = 800.0
        primaryStage.minHeight = 600.0
        primaryStage.centerOnScreen()
        primaryStage.show()

        primaryStage.setOnCloseRequest {
            try {
                stop()
            } finally {
                Platform.exit()
            }
        }
    }

    // Resolve platform-specific app data directory
    private fun resolveAppDir(): Path {
        val appName = "thelauncherproject"
        val home = System.getProperty("user.home")
        val base: Path =
                when {
                    System.getProperty("os.name").lowercase().contains("win") ->
                            Paths.get(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming")
                                    .resolve(appName)
                    System.getProperty("os.name").lowercase().contains("mac") ->
                            Paths.get(home, "Library", "Application Support", appName)
                    else ->
                            Paths.get(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share")
                                    .resolve(appName)
                }
        return base
    }

    override fun stop() {
        // Graceful shutdown if services support it
    }
}

fun main() {
    javafx.application.Application.launch(TheLauncherProject::class.java)
}

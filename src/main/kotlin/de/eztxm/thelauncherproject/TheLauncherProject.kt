package de.eztxm.thelauncherproject

import de.eztxm.thelauncherproject.rest.RestServer
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import java.net.CookieHandler
import java.net.CookieManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TheLauncherProject : javafx.application.Application() {

    override fun start(primaryStage: Stage) {
        val baseDir = resolveAppDir()
        System.setProperty("launcher.baseDir", baseDir.toString())
        Files.createDirectories(baseDir.resolve("logs"))
        Files.createDirectories(baseDir.resolve("cache"))
        Files.createDirectories(baseDir.resolve("runtime"))
        CookieHandler.setDefault(CookieManager())
        val restServer = RestServer(3020)
        restServer.start()
        val webView = WebView()
        val webEngine: WebEngine = webView.engine
        webEngine.isJavaScriptEnabled = true
        val indexUrl = javaClass.getResource("/dist/index.html")
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
        webEngine.onAlert = javafx.event.EventHandler { evt -> println("JS Alert: ${evt.data}") }
        webEngine.loadWorker.exceptionProperty().addListener { _, _, newException ->
            newException?.printStackTrace()
        }
        val scene = Scene(webView, 800.0, 600.0)
        primaryStage.scene = scene
        primaryStage.title = "The Launcher Project"
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

    private fun resolveAppDir(): Path {
        val appName = ".thelauncherproject"
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

    }
}

fun main() {
    javafx.application.Application.launch(TheLauncherProject::class.java)
}

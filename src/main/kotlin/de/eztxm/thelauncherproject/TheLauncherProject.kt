package de.eztxm.thelauncherproject

import de.eztxm.thelauncherproject.rest.RestServer
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.web.WebView
import javafx.stage.Stage

class TheLauncherProject : Application() {
    private val restServer = RestServer(7070)

    override fun start(stage: Stage) {
        restServer.start()
        Thread.sleep(500)
        val webView = WebView()
        val webEngine = webView.engine
        webEngine.load("http://localhost:7070/")
        val scene = Scene(webView, 1280.0, 720.0)
        stage.title = "TheLauncherProject"
        stage.scene = scene
        stage.show()
    }

    override fun stop() {
        restServer.stop()
        super.stop()
    }
}

fun main() {
    Application.launch(TheLauncherProject::class.java)
}

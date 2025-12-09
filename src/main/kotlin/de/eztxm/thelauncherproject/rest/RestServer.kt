package de.eztxm.thelauncherproject.rest

import io.javalin.Javalin
import io.javalin.http.staticfiles.Location

class RestServer(private val port: Int) {
    private lateinit var app: Javalin

    fun start() {
        app = Javalin.create { config ->
            config.staticFiles.add { staticFiles ->
                staticFiles.hostedPath = "/"
                staticFiles.directory = "/dist"
                staticFiles.location = Location.CLASSPATH
            }
            config.bundledPlugins.enableCors { cors ->
                cors.addRule { it.anyHost() }
            }
            config.router.mount {
                it.beforeMatched { ctx ->
                    // For SPA routing: serve index.html for non-file routes
                    val path = ctx.path()
                    if (!path.startsWith("/api") && 
                        !path.contains(".") && 
                        path != "/" &&
                        !path.startsWith("/assets")) {
                        ctx.redirect("/")
                    }
                }
            }
        }.start(port)
        
        // API endpoints
        app.get("/api/test") { ctx ->
            ctx.json(mapOf("message" to "Hello from Javalin!"))
        }
        
        println("Javalin server started on http://localhost:$port")
        println("UI available at http://localhost:$port")
    }

    fun stop() {
        if (::app.isInitialized) {
            app.stop()
        }
    }
}

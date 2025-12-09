package de.eztxm.thelauncherproject.rest

import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
import kotlinx.coroutines.runBlocking

class RestServer(private val port: Int) {
    private lateinit var app: Javalin
    private val msAuth = MicrosoftAuth()

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
                    val path = ctx.path()
                    if (!path.startsWith("/api") &&
                        !path.startsWith("/callback") &&
                        !path.contains(".") &&
                        path != "/" &&
                        !path.startsWith("/assets")) {
                        ctx.redirect("/")
                    }
                }
            }
        }.start(port)
        
        // Test endpoint
        app.get("/api/test") { ctx ->
            ctx.json(mapOf("message" to "Hello from Javalin!"))
        }
        
        // Microsoft Auth - authorization code flow (native client)
        app.get("/api/auth/login") { ctx ->
            try {
                val (state, url) = msAuth.startAuthFlow()
                ctx.json(
                    mapOf(
                        "success" to true,
                        "state" to state,
                        "url" to url,
                        "message" to "Browser opened. After authentication you will be redirected back automatically."
                    )
                )
            } catch (e: Exception) {
                ctx.status(500).json(mapOf(
                    "success" to false,
                    "error" to e.message
                ))
            }
        }

        // Submit code returned from native client redirect
        app.post("/api/auth/submit") { ctx ->
            val code = ctx.formParam("code") ?: ctx.queryParam("code")
            val state = ctx.formParam("state") ?: ctx.queryParam("state")

            if (code == null || state == null) {
                ctx.status(400).json(mapOf(
                    "success" to false,
                    "error" to "Missing code or state"
                ))
                return@post
            }

            try {
                val result = runBlocking {
                    msAuth.handleAuthCode(code, state)
                }

                ctx.json(mapOf(
                    "success" to true,
                    "uuid" to result.uuid,
                    "username" to result.username
                ))
            } catch (e: Exception) {
                e.printStackTrace()
                ctx.status(500).json(mapOf(
                    "success" to false,
                    "error" to e.message
                ))
            }
        }

        // OAuth redirect callback
        app.get("/callback") { ctx ->
            val code = ctx.queryParam("code")
            val state = ctx.queryParam("state")
            val error = ctx.queryParam("error")
            val errorDescription = ctx.queryParam("error_description")

            if (error != null) {
                ctx.status(400).html("""
                    <html>
                        <head>
                            <title>Authentication failed</title>
                            <script>
                                window.opener?.postMessage({
                                    type: 'auth_error',
                                    error: '$error',
                                    description: '${errorDescription ?: ""}'
                                }, '*');
                                setTimeout(() => window.close(), 1500);
                            </script>
                        </head>
                        <body style="font-family: Arial; text-align: center; padding: 40px;">
                            <h1>Authentication failed</h1>
                            <p>${errorDescription ?: error}</p>
                        </body>
                    </html>
                """.trimIndent())
                return@get
            }

            if (code == null || state == null) {
                ctx.status(400).html("""
                    <html>
                        <head>
                            <title>Authentication failed</title>
                            <script>
                                window.opener?.postMessage({
                                    type: 'auth_error',
                                    error: 'missing_code_or_state',
                                    description: 'Missing code or state parameter.'
                                }, '*');
                                setTimeout(() => window.close(), 1500);
                            </script>
                        </head>
                        <body style="font-family: Arial; text-align: center; padding: 40px;">
                            <h1>Authentication failed</h1>
                            <p>Missing code or state parameter.</p>
                            <p>Please retry the login.</p>
                        </body>
                    </html>
                """.trimIndent())
                return@get
            }

            try {
                val result = runBlocking { msAuth.handleAuthCode(code, state) }

                ctx.html("""
                    <html>
                        <head>
                            <title>Authentication Successful</title>
                            <script>
                                window.opener?.postMessage({
                                    type: 'auth_success',
                                    uuid: '${result.uuid}',
                                    username: '${result.username}'
                                }, '*');
                                setTimeout(() => window.close(), 1000);
                            </script>
                        </head>
                        <body style="font-family: Arial; text-align: center; padding: 40px;">
                            <h1>Authentication Successful</h1>
                            <p>Welcome, ${result.username}!</p>
                            <p>You can close this window.</p>
                        </body>
                    </html>
                """.trimIndent())
            } catch (e: IllegalStateException) {
                ctx.status(400).html("""
                    <html>
                        <head>
                            <title>Authentication failed</title>
                            <script>
                                window.opener?.postMessage({
                                    type: 'auth_error',
                                    error: 'invalid_state',
                                    description: '${e.message ?: "Invalid state"}'
                                }, '*');
                                setTimeout(() => window.close(), 1500);
                            </script>
                        </head>
                        <body style="font-family: Arial; text-align: center; padding: 40px;">
                            <h1>Authentication failed</h1>
                            <p>${e.message}</p>
                        </body>
                    </html>
                """.trimIndent())
            } catch (e: Exception) {
                e.printStackTrace()
                ctx.status(500).html("""
                    <html>
                        <head>
                            <title>Authentication failed</title>
                            <script>
                                window.opener?.postMessage({
                                    type: 'auth_error',
                                    error: 'server_error',
                                    description: '${e.message ?: "Unexpected error"}'
                                }, '*');
                                setTimeout(() => window.close(), 1500);
                            </script>
                        </head>
                        <body style="font-family: Arial; text-align: center; padding: 40px;">
                            <h1>Authentication failed</h1>
                            <p>${e.message}</p>
                        </body>
                    </html>
                """.trimIndent())
            }
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

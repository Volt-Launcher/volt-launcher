package de.eztxm.thelauncherproject

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket

object ProtocolHandler {
    private var serverSocket: ServerSocket? = null
    private var callbackHandler: ((String) -> Unit)? = null
    
    fun startListening(port: Int = 7071, onCallback: (String) -> Unit) {
        callbackHandler = onCallback
        
        Thread {
            try {
                serverSocket = ServerSocket(port)
                println("Protocol handler listening on port $port")
                
                while (!Thread.currentThread().isInterrupted) {
                    val client = serverSocket?.accept() ?: break
                    
                    Thread {
                        try {
                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                            val requestLine = reader.readLine()
                            
                            if (requestLine != null && requestLine.startsWith("GET ")) {
                                val path = requestLine.split(" ")[1]
                                
                                // Send HTTP response
                                val response = """
                                    HTTP/1.1 200 OK
                                    Content-Type: text/html
                                    
                                    <html>
                                    <body>
                                        <h1>Authentication Successful!</h1>
                                        <p>You can close this window and return to the application.</p>
                                        <script>window.close();</script>
                                    </body>
                                    </html>
                                """.trimIndent()
                                
                                client.getOutputStream().write(response.toByteArray())
                                client.getOutputStream().flush()
                                
                                // Notify callback
                                callbackHandler?.invoke(path)
                            }
                            
                            client.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            } catch (e: Exception) {
                if (!Thread.currentThread().isInterrupted) {
                    e.printStackTrace()
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
    
    fun stop() {
        serverSocket?.close()
        serverSocket = null
    }
}

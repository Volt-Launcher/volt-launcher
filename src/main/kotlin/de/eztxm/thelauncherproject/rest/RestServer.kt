package de.eztxm.thelauncherproject.rest

import io.javalin.Javalin

class RestServer(val port: Int) : Thread() {
    lateinit var server: Javalin

    override fun run() {
        server = Javalin.create()
        server.start(port)
    }
}
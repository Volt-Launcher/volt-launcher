package de.eztxm.thelauncherproject.web

import de.eztxm.thelauncherproject.service.MicrosoftAuthenticationService
import de.eztxm.thelauncherproject.service.MinecraftLauncherService
import de.eztxm.thelauncherproject.service.ProfileManager
import de.eztxm.thelauncherproject.service.VersionManagerService

class WebViewBridge(
        private val minecraftLauncherService: MinecraftLauncherService,
        private val microsoftAuthenticationService: MicrosoftAuthenticationService,
        private val versionManagerService: VersionManagerService,
        private val profileManager: ProfileManager
) {
    /** Wird vom Frontend aufgerufen, um Minecraft zu starten. */
    /** Wird vom Frontend aufgerufen, um Minecraft zu starten. */
    fun startMinecraft() {
        javafx.application.Platform.runLater { minecraftLauncherService.startMinecraftGame() }
    }

    /** Wird vom Frontend aufgerufen, um Microsoft Login zu starten. */

    /** Wird vom Frontend aufgerufen, um Microsoft Login zu starten. */
    fun startMicrosoftLogin() {
        // Asynchrone Ausführung in separatem Thread um UI-Thread nicht zu blockieren
        Thread {
            try {
                // Da authenticateWithMicrosoft() noch nicht implementiert ist, 
                // simulieren wir das Verhalten
                println("Starte Microsoft-Authentifizierung...")

                // Hier würde normalerweise die echte Authentifizierung stattfinden:
                // val result = microsoftAuthenticationService.authenticateWithMicrosoft()

                // Simuliere erfolgreichen Login für Testzwecke
                javafx.application.Platform.runLater {
                    onLoginSuccess("TestUser")
                }

                // Alternativ: Fehler-Simulation
                // javafx.application.Platform.runLater {
                //     handleLoginError("Microsoft-Authentifizierung noch nicht implementiert")
                // }

            } catch (e: Exception) {
                javafx.application.Platform.runLater {
                    handleLoginError("Authentifizierung fehlgeschlagen: ${e.message}")
                }
            }
        }.start()
    }

    private fun handleLoginError(errorMessage: String) {
        // Fehler an JavaScript Frontend weiterleiten
        println("Login-Fehler: $errorMessage")
    }
    /**
     * Beispiel-Methode, um Login-Daten vom Backend ans Frontend zu senden, kann bei Bedarf
     * implementiert werden.
     */
    @Suppress("unused")
    fun onLoginSuccess(userName: String) {
        // Hier kannst du JavaScript aufrufen, um Frontend zu informieren (optional)
    }

    // Version listing
    @Suppress("unused")
    fun listMojangVersionsJson(): String {
        println("WebViewBridge: listMojangVersionsJson() aufgerufen")

        // EINFACHER TEST: Gib erstmal statische Versionen zurück um sicherzustellen dass die Bridge funktioniert
        val staticVersions = """[
            {"id":"1.21.4","type":"release","releaseTime":"2024-12-03T13:23:00+00:00"},
            {"id":"1.21.3","type":"release","releaseTime":"2024-10-23T12:20:46+00:00"},
            {"id":"1.21.1","type":"release","releaseTime":"2024-08-08T11:05:53+00:00"},
            {"id":"1.21","type":"release","releaseTime":"2024-06-13T09:24:03+00:00"},
            {"id":"1.20.6","type":"release","releaseTime":"2024-04-29T14:58:12+00:00"},
            {"id":"1.20.4","type":"release","releaseTime":"2024-12-07T12:03:22+00:00"},
            {"id":"1.20.2","type":"release","releaseTime":"2023-09-21T12:04:18+00:00"},
            {"id":"1.20.1","type":"release","releaseTime":"2023-06-12T12:25:13+00:00"},
            {"id":"1.19.4","type":"release","releaseTime":"2023-03-14T12:56:18+00:00"},
            {"id":"1.19.2","type":"release","releaseTime":"2022-08-05T11:57:05+00:00"}
        ]"""

        println("WebViewBridge: Returning static versions JSON")
        println("WebViewBridge: JSON length: ${staticVersions.length}")
        return staticVersions

        /* TODO: Später aktivieren wenn statische Versionen funktionieren
        return try {
            val list = versionManagerService.listMojangVersions()
            println("WebViewBridge: Loaded ${list.size} versions from Mojang")

            val sb = StringBuilder("[")
            list.forEachIndexed { i, v ->
                if (i > 0) sb.append(',')
                sb.append("{\"id\":\"")
                        .append(esc(v.id))
                        .append("\",")
                        .append("\"type\":\"")
                        .append(esc(v.type))
                        .append("\",")
                        .append("\"releaseTime\":\"")
                        .append(esc(v.releaseTime))
                        .append("\"}")
            }
            sb.append(']')
            val result = sb.toString()
            println("WebViewBridge: Returning JSON with ${list.size} versions")
            result
        } catch (e: Exception) {
            println("WebViewBridge: Fehler beim Laden der Mojang-Versionen: ${e.message}")
            e.printStackTrace()
            staticVersions
        }
        */
    }

    @Suppress("unused")
    fun listInstalledVersionsJson(): String {
        try {
            val list = versionManagerService.listInstalledVersions()
            val sb = StringBuilder("[")
            list.forEachIndexed { i, v ->
                if (i > 0) sb.append(',')
                sb.append("{\"id\":\"")
                        .append(esc(v.id))
                        .append("\",")
                        .append("\"hasJson\":")
                        .append(v.hasJson)
                        .append(',')
                        .append("\"hasJar\":")
                        .append(v.hasJar)
                        .append('}')
            }
            sb.append(']')
            return sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return "[]"
        }
    }

    @Suppress("unused")
    fun listLoadersFor(mcVersion: String): String {
        try {
            return versionManagerService.listLoadersFor(mcVersion).joinToString(
                            prefix = "[",
                            postfix = "]"
                    ) { "\"${esc(it)}\"" }
        } catch (e: Exception) {
            e.printStackTrace()
            return "[\"vanilla\"]"
        }
    }

    // Install selected loader for a given Minecraft version
    @Suppress("unused")
    fun installVersion(loader: String, mcVersionOrNeo: String): String {
        println("WebViewBridge: Installing $loader version $mcVersionOrNeo")
        return try {
            when (loader.lowercase()) {
                "vanilla" -> versionManagerService.installVanilla(mcVersionOrNeo)
                "fabric", "farbic" ->
                        versionManagerService.installFabric(mcVersionOrNeo) // support typo
                "quilt" -> versionManagerService.installQuilt(mcVersionOrNeo)
                "forge" -> versionManagerService.installForge(mcVersionOrNeo)
                "neoforge" -> versionManagerService.installNeoForge(mcVersionOrNeo)
                else -> error("Unknown loader: $loader")
            }
            """{"ok":true}"""
        } catch (e: Exception) {
            e.printStackTrace()
            """{"ok":false,"error":"${esc(e.message ?: "failed")}"}"""
        }
    }

    // Profiles
    @Suppress("unused")
    fun getProfilesJson(): String {
        try {
            val profiles = profileManager.getProfiles()
            val sb = StringBuilder("[")
            profiles.forEachIndexed { i, p ->
                if (i > 0) sb.append(',')
                sb.append("{\"name\":\"")
                        .append(esc(p.name))
                        .append("\",")
                        .append("\"loader\":\"")
                        .append(esc(p.loader))
                        .append("\",")
                        .append("\"version\":\"")
                        .append(esc(p.version))
                        .append("\"}")
            }
            sb.append(']')
            return sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return "[]"
        }
    }

    @Suppress("unused")
    fun upsertProfileJson(profileJson: String): String {
        return try {
            profileManager.upsertFromJson(profileJson)
            """{"ok":true}"""
        } catch (e: Exception) {
            e.printStackTrace()
            """{"ok":false,"error":"${esc(e.message ?: "failed")}"}"""
        }
    }

    @Suppress("unused")
    fun selectProfile(name: String): String {
        return try {
            profileManager.setSelectedProfile(name)
            """{"ok":true}"""
        } catch (e: Exception) {
            e.printStackTrace()
            """{"ok":false,"error":"${esc(e.message ?: "failed")}"}"""
        }
    }

    @Suppress("unused")
    fun getSelectedProfileJson(): String? {
        try {
            val p = profileManager.getSelectedProfile() ?: return null
            return "{\"name\":\"${esc(p.name)}\",\"loader\":\"${esc(p.loader)}\",\"version\":\"${esc(p.version)}\"}"
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // Launch the game with the selected profile
    @Suppress("unused")
    fun launchGame(profileName: String): String {
        return try {
            val profile =
                    profileManager.getProfiles().firstOrNull { it.name == profileName }
                            ?: error("Profile not found: $profileName")

            // Call the launcher service to launch the game
            minecraftLauncherService.launchGame(profileName, profile.version, profile.loader)

            """{"ok":true}"""
        } catch (e: Exception) {
            e.printStackTrace()
            """{"ok":false,"error":"${esc(e.message ?: "Failed to launch game")}"}"""
        }
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}

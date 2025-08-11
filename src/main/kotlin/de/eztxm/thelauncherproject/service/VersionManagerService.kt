package de.eztxm.thelauncherproject.service

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.streams.toList

class VersionManagerService(baseDir: Path) {
    private val base = baseDir
    private val game = base.resolve("runtime").resolve(".minecraft")
    private val versionsDir = game.resolve("versions")
    private val librariesDir = game.resolve("libraries")
    private val assetsDir = game.resolve("assets")
    private val cacheDir = base.resolve("cache")

    init {
        Files.createDirectories(versionsDir)
        Files.createDirectories(librariesDir)
        Files.createDirectories(assetsDir.resolve("objects"))
        Files.createDirectories(cacheDir)
    }

    data class VersionSummary(val id: String, val type: String, val releaseTime: String)
    data class InstalledVersion(val id: String, val hasJson: Boolean, val hasJar: Boolean)

    fun listMojangVersions(): List<VersionSummary> {
        println("VersionManagerService: Starting to fetch Mojang versions...")
        return try {
            val jsonText = Downloader.getText("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
            println("VersionManagerService: Successfully downloaded manifest, size: ${jsonText.length}")

            // Einfaches JSON-Parsing ohne externe Bibliothek
            val versions = parseVersionsFromJson(jsonText)
            println("VersionManagerService: Parsed ${versions.size} versions")
            versions
        } catch (e: Exception) {
            println("VersionManagerService: Fehler beim Laden der Versionen: ${e.message}")
            e.printStackTrace()

            // Fallback mit statischen Versionen
            listOf(
                VersionSummary("1.21.4", "release", "2024-12-03T13:23:00+00:00"),
                VersionSummary("1.21.3", "release", "2024-10-23T12:20:46+00:00"),
                VersionSummary("1.21.1", "release", "2024-08-08T11:05:53+00:00"),
                VersionSummary("1.21", "release", "2024-06-13T09:24:03+00:00"),
                VersionSummary("1.20.6", "release", "2024-04-29T14:58:12+00:00"),
                VersionSummary("1.20.4", "release", "2024-12-07T12:03:22+00:00"),
                VersionSummary("1.20.2", "release", "2023-09-21T12:04:18+00:00"),
                VersionSummary("1.20.1", "release", "2023-06-12T12:25:13+00:00"),
                VersionSummary("1.19.4", "release", "2023-03-14T12:56:18+00:00"),
                VersionSummary("1.19.2", "release", "2022-08-05T11:57:05+00:00")
            )
        }
    }

    private fun parseVersionsFromJson(jsonText: String): List<VersionSummary> {
        val versions = mutableListOf<VersionSummary>()

        // Suche nach dem "versions" Array im JSON
        val versionsStart = jsonText.indexOf("\"versions\":[")
        if (versionsStart == -1) return emptyList()

        val arrayStart = jsonText.indexOf('[', versionsStart)
        val arrayEnd = findMatchingBracket(jsonText, arrayStart, '[', ']')
        if (arrayEnd == -1) return emptyList()

        val versionsArray = jsonText.substring(arrayStart + 1, arrayEnd)

        // Parse jedes Versions-Objekt
        var currentPos = 0
        while (currentPos < versionsArray.length) {
            val objStart = versionsArray.indexOf('{', currentPos)
            if (objStart == -1) break

            val objEnd = findMatchingBracket(versionsArray, objStart, '{', '}')
            if (objEnd == -1) break

            val objText = versionsArray.substring(objStart, objEnd + 1)

            val id = extractJsonString(objText, "id")
            val type = extractJsonString(objText, "type")
            val releaseTime = extractJsonString(objText, "releaseTime")

            if (id != null && type != null && releaseTime != null) {
                versions.add(VersionSummary(id, type, releaseTime))
            }

            currentPos = objEnd + 1
        }

        return versions
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        val match = regex.find(json)
        return match?.groupValues?.get(1)
    }

    private fun findMatchingBracket(text: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until text.length) {
            val char = text[i]

            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString && char == open -> depth++
                !inString && char == close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    fun listInstalledVersions(): List<InstalledVersion> {
        if (!Files.isDirectory(versionsDir)) return emptyList()
        return Files.list(versionsDir).use { s ->
            s
                    .filter { Files.isDirectory(it) }
                    .map { dir ->
                        val id = dir.fileName.toString()
                        InstalledVersion(
                                id,
                                Files.exists(dir.resolve("$id.json")),
                                Files.exists(dir.resolve("$id.jar"))
                        )
                    }
                    .toList()
        }
    }

    fun listLoadersFor(@Suppress("UNUSED_PARAMETER") mcVersion: String): List<String> =
            listOf("vanilla", "fabric", "quilt", "forge", "neoforge")

    // Vereinfachte Install-Methoden für jetzt - funktionale Implementation würde echtes JSON-Parsing benötigen
    fun installVanilla(versionId: String) {
        println("Installing vanilla version: $versionId (stub implementation)")
        // Stub implementation - würde echtes JSON-Parsing und Downloads benötigen
    }

    fun installFabric(mcVersion: String) {
        println("Installing Fabric for: $mcVersion (stub implementation)")
        // Stub implementation
    }

    fun installQuilt(mcVersion: String) {
        println("Installing Quilt for: $mcVersion (stub implementation)")
        // Stub implementation
    }

    fun installForge(mcVersion: String) {
        println("Installing Forge for: $mcVersion (stub implementation)")
        // Stub implementation
    }

    fun installNeoForge(mcOrNeo: String) {
        println("Installing NeoForge for: $mcOrNeo (stub implementation)")
        // Stub implementation
    }
}

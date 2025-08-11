package de.eztxm.thelauncherproject.service

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.streams.asSequence

class ProfileManager(baseDir: Path) {
    private val root = baseDir
    private val file = root.resolve("profiles.txt")
    private val selectedFile = root.resolve("selected_profile.txt")

    data class LauncherProfile(val name: String, val loader: String, val version: String)

    fun getProfiles(): List<LauncherProfile> {
        if (!Files.exists(file)) return emptyList()
        return Files.lines(file).use { lines ->
            lines.asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val p = line.split('|')
                    if (p.size >= 3) LauncherProfile(p[0], p[1], p[2]) else null
                }
                .toList()
        }
    }

    fun upsertFromJson(profileJson: String) {
        println("Upserting profile from JSON: $profileJson")
        fun grab(key: String): String {
            val regex = """"$key"\s*:\s*"([^"]*)"""".toRegex()
            val m = regex.find(profileJson) ?: error("Missing field: $key")
            return m.groupValues[1]
        }
        upsert(LauncherProfile(grab("name"), grab("loader"), grab("version")))
    }

    fun upsert(p: LauncherProfile) {
        println("Upserting profile: ${p.name}")
        val all = getProfiles().toMutableList()
        val idx = all.indexOfFirst { it.name.equals(p.name, ignoreCase = true) }
        if (idx >= 0) all[idx] = p else all.add(p)
        Files.createDirectories(file.parent)
        Files.writeString(file, buildString {
            all.forEach { append(it.name).append('|').append(it.loader).append('|').append(it.version).append('\n') }
        })
    }

    fun setSelectedProfile(name: String) {
        println("Setting selected profile: $name")
        Files.createDirectories(selectedFile.parent)
        Files.writeString(selectedFile, name)
    }

    fun getSelectedProfile(): LauncherProfile? {
        val name = getSelectedProfileName() ?: return null
        return getProfiles().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun getSelectedProfileName(): String? =
            if (Files.exists(selectedFile)) Files.readString(selectedFile).trim().ifEmpty { null }
            else null
}

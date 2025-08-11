package de.eztxm.thelauncherproject.service

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object Downloader {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun getText(url: String): String {
        println("Downloading text from: $url")
        try {
            val request = HttpRequest.newBuilder(URI(url))
                .GET()
                .header("User-Agent", "TheLauncherProject/1.0")
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() !in 200..299) {
                throw RuntimeException("HTTP ${response.statusCode()} for $url")
            }

            return response.body()
        } catch (e: Exception) {
            println("Error downloading from $url: ${e.message}")
            throw e
        }
    }

    fun downloadTo(url: String, dest: Path, sha1: String? = null) {
        println("Downloading file from: $url to $dest")
        try {
            // Prüfe ob Datei bereits existiert und SHA1 stimmt
            if (Files.exists(dest) && sha1 != null && verifySha1(dest, sha1)) {
                println("File exists and SHA1 matches, skipping download")
                return
            }

            Files.createDirectories(dest.parent)

            val request = HttpRequest.newBuilder(URI(url))
                .GET()
                .header("User-Agent", "TheLauncherProject/1.0")
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())

            if (response.statusCode() !in 200..299) {
                throw RuntimeException("HTTP ${response.statusCode()} for $url")
            }

            Files.write(dest, response.body())

            // Verifiziere SHA1 nach dem Download
            if (sha1 != null && !verifySha1(dest, sha1)) {
                throw RuntimeException("SHA1 mismatch for $dest")
            }

            println("Successfully downloaded: $url -> $dest")
        } catch (e: Exception) {
            println("Error downloading file from $url: ${e.message}")
            throw e
        }
    }

    private fun verifySha1(path: Path, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-1")
        val actual = digest.digest(Files.readAllBytes(path))
            .joinToString("") { "%02x".format(it) }
        return actual.equals(expected, ignoreCase = true)
    }
}

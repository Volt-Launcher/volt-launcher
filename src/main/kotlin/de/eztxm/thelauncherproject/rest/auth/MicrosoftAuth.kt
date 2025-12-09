package de.eztxm.thelauncherproject.rest.auth

import com.microsoft.aad.msal4j.AuthorizationCodeParameters
import com.microsoft.aad.msal4j.AuthorizationRequestUrlParameters
import com.microsoft.aad.msal4j.IAuthenticationResult
import com.microsoft.aad.msal4j.PublicClientApplication
import com.microsoft.aad.msal4j.Prompt
import com.microsoft.aad.msal4j.ResponseMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class MicrosoftTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val scope: String,
    val refresh_token: String? = null
)

@Serializable
data class XboxLiveAuthResponse(
    val Token: String,
    val DisplayClaims: XboxDisplayClaims
)

@Serializable
data class XboxDisplayClaims(
    val xui: List<XboxUserInfo>
)

@Serializable
data class XboxUserInfo(
    val uhs: String
)

@Serializable
data class MinecraftProfileResponse(
    val id: String,
    val name: String
)

@Serializable
data class MinecraftAuthResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int
)

@Serializable
data class AuthResult(
    val uuid: String,
    val username: String
)

class MicrosoftAuth {
    // Public client with PKCE; Xbox/Minecraft requires MSA scopes in the consumers tenant
    private val clientId = "312b6922-bc5f-4eb8-919b-c5c4cd5d9944"
    private val redirectUri = "http://localhost:7070/callback"
    private val authority = "https://login.microsoftonline.com/consumers/"
    private val scopes = setOf("XboxLive.signin", "offline_access")

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val msalApp by lazy {
        PublicClientApplication.builder(clientId)
            .authority(authority)
            .build()
    }

    data class PendingAuth(
        val state: String,
        val codeVerifier: String,
        var completed: Boolean = false
    )

    private val pendingAuthStates = ConcurrentHashMap<String, PendingAuth>()

    fun startAuthFlow(): Pair<String, String> {
        val state = UUID.randomUUID().toString()
        val codeVerifier = generateCodeVerifier()
        pendingAuthStates[state] = PendingAuth(state, codeVerifier)
        val authUrl = buildAuthUrl(state, codeVerifier)
        return Pair(state, authUrl)
    }

    private fun buildAuthUrl(state: String, codeVerifier: String): String {
        val codeChallenge = codeChallengeFrom(codeVerifier)
        val params = AuthorizationRequestUrlParameters
            .builder(redirectUri, scopes)
            .responseMode(ResponseMode.QUERY)
            .prompt(Prompt.SELECT_ACCOUNT)
            .state(state)
            .codeChallenge(codeChallenge)
            .codeChallengeMethod("S256")
            .build()

        return msalApp.getAuthorizationRequestUrl(params).toString()
    }

    fun checkAuthStatus(state: String): PendingAuth? {
        return pendingAuthStates[state]
    }

    suspend fun handleAuthCode(code: String, state: String): AuthResult {
        val pendingAuth = pendingAuthStates[state]
            ?: throw IllegalStateException("Invalid state parameter")

        try {
            val msToken = acquireMicrosoftToken(code, pendingAuth.codeVerifier)
            val xboxToken = authenticateWithXboxLive(msToken.accessToken())
            val xstsToken = getXSTSToken(xboxToken.Token)
            val mcToken = authenticateWithMinecraft(xstsToken.DisplayClaims.xui[0].uhs, xstsToken.Token)
            val profile = getMinecraftProfile(mcToken.access_token)

            val result = AuthResult(
                uuid = profile.id,
                username = profile.name
            )

            pendingAuth.completed = true
            pendingAuthStates.remove(state)
            return result
        } catch (e: Exception) {
            pendingAuthStates.remove(state)
            throw e
        }
    }

    private fun acquireMicrosoftToken(code: String, codeVerifier: String): IAuthenticationResult {
        val params = AuthorizationCodeParameters
            .builder(code, URI.create(redirectUri))
            .scopes(scopes)
            .codeVerifier(codeVerifier)
            .build()

        return msalApp.acquireToken(params).get()
    }
    
    private fun authenticateWithXboxLive(accessToken: String): XboxLiveAuthResponse {
        val jsonBody = """
            {
                "Properties": {
                    "AuthMethod": "RPS",
                    "SiteName": "user.auth.xboxlive.com",
                    "RpsTicket": "d=$accessToken"
                },
                "RelyingParty": "http://auth.xboxlive.com",
                "TokenType": "JWT"
            }
        """.trimIndent()
        
        val request = Request.Builder()
            .url("https://user.auth.xboxlive.com/user/authenticate")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to authenticate with Xbox Live: ${response.body?.string()}")
            }
            return json.decodeFromString(response.body!!.string())
        }
    }
    
    private fun getXSTSToken(xboxToken: String): XboxLiveAuthResponse {
        val jsonBody = """
            {
                "Properties": {
                    "SandboxId": "RETAIL",
                    "UserTokens": ["$xboxToken"]
                },
                "RelyingParty": "rp://api.minecraftservices.com/",
                "TokenType": "JWT"
            }
        """.trimIndent()
        
        val request = Request.Builder()
            .url("https://xsts.auth.xboxlive.com/xsts/authorize")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to get XSTS token: ${response.body?.string()}")
            }
            return json.decodeFromString(response.body!!.string())
        }
    }
    
    private fun authenticateWithMinecraft(userHash: String, xstsToken: String): MinecraftAuthResponse {
        val jsonBody = """
            {
                "identityToken": "XBL3.0 x=$userHash;$xstsToken"
            }
        """.trimIndent()
        
        val request = Request.Builder()
            .url("https://api.minecraftservices.com/authentication/login_with_xbox")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to authenticate with Minecraft: ${response.body?.string()}")
            }
            return json.decodeFromString(response.body!!.string())
        }
    }
    
    private fun getMinecraftProfile(accessToken: String): MinecraftProfileResponse {
        val request = Request.Builder()
            .url("https://api.minecraftservices.com/minecraft/profile")
            .get()
            .header("Authorization", "Bearer $accessToken")
            .build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to get Minecraft profile: ${response.body?.string()}")
            }
            return json.decodeFromString(response.body!!.string())
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun codeChallengeFrom(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}

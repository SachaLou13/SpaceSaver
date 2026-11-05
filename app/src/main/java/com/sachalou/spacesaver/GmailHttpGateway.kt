package com.sachalou.spacesaver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GmailHttpGateway(private val accessToken: String) : GmailGateway {
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private suspend fun request(path: String, method: String = "GET"): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL("https://gmail.googleapis.com/gmail/v1/users/me/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) {
                // Never expose response bodies, tokens or message metadata in logs/errors.
                throw GmailFailure(when (status) {
                    401 -> "L’autorisation Google a expiré. Reconnecte le compte et relance l’analyse."
                    403 -> "Accès Gmail refusé. Vérifie l’autorisation, l’activation de l’API Gmail et le compte de test Google Cloud."
                    404 -> "Ce message n’est plus disponible."
                    429 -> "Limite Google atteinte. Attends une minute puis relance l’analyse."
                    else -> "Google est momentanément indisponible (HTTP $status). Réessaie plus tard."
                }, status)
            }
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }
    override suspend fun profile(): String = request("profile?fields=emailAddress").getString("emailAddress")

    override suspend fun list(query: String, pageToken: String?): GmailPage {
        val result = request("messages?maxResults=100&includeSpamTrash=false&q=${encode(query)}" +
            (pageToken?.let { "&pageToken=${encode(it)}" } ?: ""))
        val messages = result.optJSONArray("messages")
        return GmailPage((0 until (messages?.length() ?: 0)).map { messages!!.getJSONObject(it).getString("id") },
            result.optString("nextPageToken").takeIf { it.isNotBlank() }, result.optInt("resultSizeEstimate"))
    }
    override suspend fun message(id: String): GmailMessage {
        val result = request("messages/${encode(id)}?format=metadata&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=List-Id&metadataHeaders=List-Unsubscribe" +
            "&fields=id,internalDate,sizeEstimate,labelIds,payload/headers")
        val headers = result.optJSONObject("payload")?.optJSONArray("headers")
        fun header(name: String): String = (0 until (headers?.length() ?: 0))
            .map { headers!!.getJSONObject(it) }.firstOrNull { it.optString("name").equals(name, true) }?.optString("value") ?: ""
        val labels = result.optJSONArray("labelIds")
        return GmailMessage(result.getString("id"), header("Subject").ifBlank { "(Sans objet)" },
            header("From"), result.optLong("internalDate"), result.optLong("sizeEstimate").coerceAtLeast(0),
            (0 until (labels?.length() ?: 0)).map { labels!!.getString(it) }.toSet(),
            hasSubscriptionHint(header("List-Id"), header("List-Unsubscribe")))
    }
    override suspend fun trash(id: String) {
        val result = request("messages/${encode(id)}/trash?fields=id,labelIds", "POST")
        val labels = result.optJSONArray("labelIds")
        if (result.optString("id") != id || (0 until (labels?.length() ?: 0)).none { labels!!.getString(it) == "TRASH" }) {
            throw GmailFailure("Google n’a pas confirmé la mise à la corbeille. Relance l’analyse pour vérifier.")
        }
    }
}

package com.subrosa.app.data.client

import com.subrosa.app.data.crypto.Crypto
import com.subrosa.app.domain.model.Client
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Local-only client records under filesDir/clients/, encrypted at rest via [Crypto]. No cloud. */
class ClientStore(
    private val dir: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun save(client: Client) {
        if (!dir.exists()) dir.mkdirs()
        File(dir, "${client.id}.json").writeBytes(Crypto.encrypt(json.encodeToString(client).toByteArray()))
    }

    fun list(): List<Client> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { runCatching { json.decodeFromString<Client>(Crypto.readText(it)) }.getOrNull() }
            .sortedByDescending { it.createdAtMs }

    fun get(id: String): Client? =
        runCatching { json.decodeFromString<Client>(Crypto.readText(File(dir, "$id.json"))) }.getOrNull()

    fun delete(id: String) {
        runCatching { File(dir, "$id.json").delete() }
    }
}

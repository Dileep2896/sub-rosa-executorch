package com.subrosa.app.data.session

import com.subrosa.app.data.crypto.Crypto
import com.subrosa.app.domain.model.MatterType
import com.subrosa.app.domain.model.Notes
import com.subrosa.app.domain.model.SessionStatus
import com.subrosa.app.domain.model.Transcript
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedSession(
    val id: String,
    val clientId: String? = null,
    val caseId: String? = null,
    val matterType: MatterType,
    val transcript: Transcript,
    val notes: Notes?,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val createdAtMs: Long,
    val updatedAtMs: Long = createdAtMs,
)

/**
 * Local-only session persistence: one file per session under filesDir/sessions/. The JSON is
 * **encrypted at rest** with a Keystore-held AES-GCM key (see [Crypto]). Saving the same id
 * overwrites it — how a living session updates across passes. No cloud, ever.
 */
class SessionStore(
    private val dir: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun save(session: SavedSession) {
        if (!dir.exists()) dir.mkdirs()
        File(dir, "${session.id}.json").writeBytes(Crypto.encrypt(json.encodeToString(session).toByteArray()))
    }

    fun list(): List<SavedSession> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { runCatching { json.decodeFromString<SavedSession>(Crypto.readText(it)) }.getOrNull() }
            .sortedByDescending { it.updatedAtMs }

    fun delete(id: String) {
        runCatching { File(dir, "$id.json").delete() }
    }
}

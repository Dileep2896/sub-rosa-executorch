package com.subrosa.app.data.cases

import com.subrosa.app.data.crypto.Crypto
import com.subrosa.app.domain.model.Case
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Local-only case (matter) records under filesDir/cases/, encrypted at rest via [Crypto]. */
class CaseStore(
    private val dir: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun save(case: Case) {
        if (!dir.exists()) dir.mkdirs()
        File(dir, "${case.id}.json").writeBytes(Crypto.encrypt(json.encodeToString(case).toByteArray()))
    }

    fun list(): List<Case> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { runCatching { json.decodeFromString<Case>(Crypto.readText(it)) }.getOrNull() }
            .sortedByDescending { it.updatedAtMs }

    fun get(id: String): Case? =
        runCatching { json.decodeFromString<Case>(Crypto.readText(File(dir, "$id.json"))) }.getOrNull()

    fun delete(id: String) {
        runCatching { File(dir, "$id.json").delete() }
    }
}

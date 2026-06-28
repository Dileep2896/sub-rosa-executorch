package com.subrosa.app.data.docs

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.subrosa.app.data.crypto.Crypto
import java.io.File

/** A document attached to a client, backed by an encrypted local file copy. */
data class DocumentRef(
    val file: File,
    val displayName: String,
    val addedAtMs: Long,
    val sizeBytes: Long,
)

/**
 * Full file attachment, local-only and **encrypted at rest** ([Crypto]). Picked files are copied and
 * encrypted into filesDir/documents/<clientId>/. Opening decrypts to a short-lived cache file and
 * hands a FileProvider content-uri to a viewer app.
 */
class DocumentStore(private val app: Application, private val root: File) {

    fun list(clientId: String): List<DocumentRef> {
        val dir = File(root, clientId)
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile }
            .map { f -> DocumentRef(f, displayName(f.name), f.lastModified(), f.length()) }
            .sortedByDescending { it.addedAtMs }
    }

    fun add(clientId: String, uri: Uri): Boolean = runCatching {
        val name = queryName(uri) ?: "document"
        val dir = File(root, clientId).apply { mkdirs() }
        val data = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        File(dir, "${System.currentTimeMillis()}__${sanitize(name)}").writeBytes(Crypto.encrypt(data))
        true
    }.getOrDefault(false)

    fun delete(doc: DocumentRef) {
        runCatching { doc.file.delete() }
    }

    /** Remove all documents for an owner (used when a case is deleted). */
    fun deleteAll(ownerId: String) {
        runCatching { File(root, ownerId).deleteRecursively() }
    }

    fun open(doc: DocumentRef) {
        runCatching {
            val plain = Crypto.decrypt(doc.file.readBytes())
            val tmpDir = File(app.cacheDir, "doc_open").apply { mkdirs() }
            val tmp = File(tmpDir, doc.displayName).apply { writeBytes(plain) }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", tmp)
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeOf(doc.displayName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.startActivity(
                Intent.createChooser(view, "Open document").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun displayName(fileName: String): String = fileName.substringAfter("__", fileName)
    private fun sanitize(s: String): String = s.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    private fun queryName(uri: Uri): String? =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "txt" -> "text/plain"
        "doc", "docx" -> "application/msword"
        else -> "*/*"
    }
}

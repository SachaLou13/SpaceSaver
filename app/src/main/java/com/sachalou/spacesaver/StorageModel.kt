package com.sachalou.spacesaver

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class StorageFile(val uri: Uri, val name: String, val path: String, val size: Long,
    val mime: String, val deletable: Boolean) {
    val category get() = FileCategory.fromMime(mime)
}
data class ScanResult(val files: List<StorageFile>, val skipped: Int, val limited: Boolean)

class StorageModel(application: Application) : AndroidViewModel(application) {
    private val resolver = application.contentResolver
    private val preferences = application.getSharedPreferences("folders", 0)
    var folder by mutableStateOf(preferences.getString("uri", null)?.let(Uri::parse)); private set
    var files by mutableStateOf<List<StorageFile>>(emptyList()); private set
    var selected by mutableStateOf<Set<Uri>>(emptySet()); private set
    var busy by mutableStateOf(false); private set
    var deleting by mutableStateOf(false); private set
    var scanned by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    private var task: Job? = null

    fun toggle(file: StorageFile) {
        if (!busy && file.deletable) selected = if (file.uri in selected) selected - file.uri else selected + file.uri
    }
    fun clearSelection() { selected = emptySet() }
    fun report(text: String) { message = text }
    fun choose(uri: Uri) {
        if (busy) return
        // Persist only the permissions actually granted by the provider.
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) {
            try { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* Session-only grant remains usable. */ }
        }
        val old = folder
        folder = uri
        preferences.edit().putString("uri", uri.toString()).apply()
        if (old != null && old != uri) {
            resolver.persistedUriPermissions.filter { it.uri == old }.forEach { permission ->
                val flags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                    (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                runCatching { resolver.releasePersistableUriPermission(old, flags) }
            }
        }
        files = emptyList(); selected = emptySet(); scanned = false
        scan()
    }
    fun cancel() { if (!deleting) task?.cancel() }
    fun scan() {
        val root = folder ?: return
        if (busy) return
        busy = true; message = null; selected = emptySet()
        task = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { readTree(root) }
                files = result.files.sortedByDescending { it.size }; scanned = true
                message = when {
                    result.limited -> "Analyse partielle : limite de 50 000 fichiers ou 10 000 dossiers atteinte. Choisis un dossier plus petit."
                    result.skipped > 0 -> "Analyse partielle : ${result.skipped} dossier(s) inaccessible(s)."
                    files.isEmpty() -> "Aucun fichier accessible dans ce dossier."
                    else -> "${files.size} fichiers analysés. Les tailles inconnues sont affichées à 0 o."
                }
            } catch (e: CancellationException) { message = "Analyse annulée."; throw e }
            catch (_: Exception) { message = "Impossible de lire ce dossier. Sélectionne-le à nouveau pour autoriser l’accès." }
            finally { busy = false }
        }
    }
    private suspend fun readTree(tree: Uri): ScanResult {
        val pending = ArrayDeque<Pair<String, String>>()
        pending.add(DocumentsContract.getTreeDocumentId(tree) to "")
        val visited = mutableSetOf<String>()
        val result = mutableListOf<StorageFile>()
        var skipped = 0
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_FLAGS)
        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            if (visited.size >= 10_000 || result.size >= 50_000) return ScanResult(result, skipped, true)
            val (id, path) = pending.removeFirst()
            if (!visited.add(id)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            try {
                val cursor = resolver.query(children, projection, null, null, null)
                if (cursor == null) { skipped++; continue }
                cursor.use {
                    while (it.moveToNext()) {
                        coroutineContext.ensureActive()
                        val childId = it.getString(0)
                        val name = it.getString(1) ?: "Sans nom"
                        val mime = it.getString(2) ?: "application/octet-stream"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (pending.size + visited.size >= 10_000) return ScanResult(result, skipped, true)
                            pending.add(childId to "$path$name/")
                        } else {
                            if (result.size >= 50_000) return ScanResult(result, skipped, true)
                            result.add(StorageFile(DocumentsContract.buildDocumentUriUsingTree(tree, childId),
                                name, path, if (it.isNull(3)) 0 else it.getLong(3).coerceAtLeast(0), mime,
                                it.getInt(4) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0))
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { skipped++ }
        }
        return ScanResult(result.distinctBy { it.uri }, skipped, false)
    }
    fun deleteSelected() {
        if (busy || selected.isEmpty()) return
        val targets = files.filter { it.uri in selected && it.deletable }
        busy = true; deleting = true; message = null
        task = viewModelScope.launch {
            try {
                val removed = withContext(Dispatchers.IO) {
                    targets.filter { file ->
                        runCatching { DocumentsContract.deleteDocument(resolver, file.uri) }.getOrDefault(false)
                    }.map { it.uri }.toSet()
                }
                files = files.filterNot { it.uri in removed }; selected = emptySet()
                message = "${removed.size} fichier(s) supprimé(s)." +
                    if (removed.size < targets.size) " ${targets.size - removed.size} suppression(s) refusée(s)." else ""
            } finally { busy = false; deleting = false }
        }
    }
}

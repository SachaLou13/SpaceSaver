package com.sachalou.spacesaver

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class GmailModel : ViewModel() {
    var rules by mutableStateOf(GmailRules()); private set
    var account by mutableStateOf<String?>(null); private set
    var rows by mutableStateOf<List<GmailMessage>>(emptyList()); private set
    var selected by mutableStateOf<Set<String>>(emptySet()); private set
    var busy by mutableStateOf(false); private set
    var cleaning by mutableStateOf(false); private set
    var notice by mutableStateOf<String?>(null); private set
    var progress by mutableStateOf(""); private set
    var hasResults by mutableStateOf(false); private set
    var more by mutableStateOf(false); private set
    var inspected by mutableStateOf(0); private set
    var stopRequested by mutableStateOf(false); private set
    private var gateway: GmailGateway? = null
    private var frozen: FrozenGmailRules? = null
    private var pageToken: String? = null
    private var job: Job? = null

    fun report(text: String) { notice = text }
    private fun clearResults() {
        rows = emptyList(); selected = emptySet(); pageToken = null
        more = false; hasResults = false; frozen = null; inspected = 0
    }
    fun changeRules(next: GmailRules) {
        if (busy) return
        rules = next; clearResults(); notice = null
    }
    fun connect(token: String) {
        if (busy) return
        clearResults(); account = null; gateway = null; busy = true; notice = null
        job = viewModelScope.launch {
            try {
                val client = GmailHttpGateway(token)
                account = client.profile()
                gateway = client
                notice = "Compte connecté. Choisis tes règles, puis lance l’analyse."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { notice = errorText(e) }
            finally { busy = false }
        }
    }
    fun disconnect() {
        if (busy) return
        gateway = null; account = null; clearResults()
        notice = "Session locale fermée. Pour retirer aussi l’autorisation Google, utilise « Gérer les accès Google »."
    }
    fun toggle(id: String) {
        if (!busy && rows.any { it.id == id }) selected = if (id in selected) selected - id else selected + id
    }
    fun selectLoaded() { if (!busy) selected = rows.map { it.id }.toSet() }
    fun clearSelection() { if (!busy) selected = emptySet() }
    fun stop() {
        if (cleaning) stopRequested = true else job?.cancel()
    }
    fun scan(append: Boolean = false) {
        val client = gateway ?: return
        if (busy || !rules.valid || append && (!more || inspected >= 500)) return
        if (!append) { clearResults(); frozen = rules.freeze() }
        val searchRules = frozen ?: return
        val token = if (append) pageToken else null
        busy = true; notice = null; progress = "Recherche des messages…"
        job = viewModelScope.launch {
            try {
                val page = client.list(searchRules.query(), token)
                val remaining = (500 - inspected).coerceAtLeast(0)
                val ids = page.ids.distinct().filterNot { id -> rows.any { it.id == id } }.take(remaining)
                for (chunk in ids.chunked(5)) {
                    val details = coroutineScope {
                        chunk.map { id -> async {
                            try { client.message(id) }
                            catch (e: GmailFailure) { if (e.status == 404) null else throw e }
                        } }.awaitAll()
                    }
                    inspected += chunk.size
                    rows = (rows + details.filterNotNull().filter(searchRules::matches)).distinctBy { it.id }
                        .sortedBy { it.dateMillis }
                    progress = "$inspected messages examinés…"
                }
                pageToken = page.nextPage
                more = page.nextPage != null
                hasResults = true
                notice = when {
                    inspected >= 500 && more -> "Aperçu limité à 500 messages examinés. Après un nettoyage, relance l’analyse pour continuer."
                    more -> "D’autres résultats sont disponibles. Charge la suite pour les examiner."
                    rows.isEmpty() -> "Aucun message ne correspond aux règles et aux exclusions."
                    else -> "Analyse terminée. Rien n’a été déplacé."
                }
            } catch (e: CancellationException) {
                clearResults(); notice = "Analyse annulée. Aucun mail modifié."; throw e
            } catch (e: Exception) {
                clearResults(); notice = errorText(e)
                if (e is GmailFailure && e.status == 401) { gateway = null; account = null }
            } finally { busy = false; progress = "" }
        }
    }
    fun trashSelection() {
        val client = gateway ?: return
        val email = account ?: return
        val searchRules = frozen ?: return
        if (busy || selected.isEmpty()) return
        val ids = rows.filter { it.id in selected }.map { it.id }.toSet()
        busy = true; cleaning = true; stopRequested = false; notice = null
        progress = "Vérification du compte…"
        job = viewModelScope.launch {
            try {
                val result = trashReviewed(client, email, ids, searchRules, { stopRequested }) { done, total ->
                    progress = "$done / $total messages traités"
                }
                clearResults()
                notice = "${result.moved.size} mail(s) mis à la corbeille ; ${result.skipped.size} conservé(s) après vérification." +
                    (if (result.stopped) " Opération arrêtée." else "") +
                    (result.error?.let { " $it" } ?: "") +
                    (if (result.uncertainId != null) " Le dernier déplacement n’a pas pu être confirmé : vérifie la corbeille Gmail." else "") +
                    " Relance l’analyse pour actualiser les résultats."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { clearResults(); notice = errorText(e) }
            finally { busy = false; cleaning = false; stopRequested = false; progress = "" }
        }
    }
    private fun errorText(e: Exception) = (e as? GmailFailure)?.publicMessage
        ?: "Connexion impossible. Vérifie Internet et réessaie."
}

package com.sachalou.spacesaver

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.modify"

@Composable
fun GmailScreen(model: GmailModel) {
    val context = LocalContext.current
    val client = remember(context) { Identity.getAuthorizationClient(context) }
    var authorizing by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val size: (Long) -> String = { Formatter.formatFileSize(context, it) }
    val chosen = model.rows.filter { it.id in model.selected }
    val enabled = !model.busy && !authorizing
    val rules = model.rules

    fun accept(result: AuthorizationResult) {
        authorizing = false
        val token = result.accessToken
        if (token.isNullOrBlank() || !result.grantedScopes.contains(GMAIL_SCOPE)) {
            model.report("L’accès Gmail n’a pas été accordé. Aucun mail n’a été modifié.")
        } else model.connect(token)
    }
    fun authError(error: Exception) {
        authorizing = false
        val code = (error as? ApiException)?.statusCode
        model.report(if (code == 10) "Connexion Google à configurer pour cet APK. Consulte « Aide connexion »."
            else "Connexion Google non aboutie${code?.let { " (code $it)" } ?: ""}. Réessaie ou consulte « Aide connexion ».")
    }
    val authorizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            authorizing = false
            model.report("Connexion annulée. Aucun mail n’a été modifié.")
        } else try { accept(client.getAuthorizationResultFromIntent(result.data!!)) }
        catch (e: Exception) { authError(e) }
    }
    fun connect() {
        authorizing = true
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(GMAIL_SCOPE))).build()
        client.authorize(request).addOnSuccessListener { result ->
            try {
                if (result.hasResolution()) {
                    val pending = result.pendingIntent ?: throw IllegalStateException("No authorization intent")
                    authorizationLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else accept(result)
            } catch (e: Exception) { authError(e) }
        }.addOnFailureListener { authError(it) }
    }
    fun openLink(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { model.report("Aucun navigateur disponible.") }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (chosen.isNotEmpty()) Surface(tonalElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${chosen.size} mail(s) sélectionné(s) · ~${size(chosen.sumOf { it.sizeEstimate })}")
                    Button(onClick = { confirm = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text("Mettre à la corbeille")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("S P A C E S A V E R", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                Text("Stop paying to store digital junk.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Fais le tri dans Gmail avant de payer pour plus de stockage.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("TON COMPTE GOOGLE", style = MaterialTheme.typography.labelMedium)
                        if (model.account == null) {
                            Text("Connecte Gmail pour analyser tes anciens mails.", style = MaterialTheme.typography.titleMedium)
                            Text("SpaceSaver lit l’objet, l’expéditeur, la date et la taille des messages. Seuls les mails que tu sélectionnes et confirmes sont mis à la corbeille. Aucun mail n’est envoyé.",
                                style = MaterialTheme.typography.bodySmall)
                            Button(onClick = ::connect, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                Text(if (authorizing) "Connexion…" else "Connecter Gmail")
                            }
                        } else {
                            Text(model.account!!, style = MaterialTheme.typography.titleMedium)
                            Text("Connexion directe à Google · Aucun serveur SpaceSaver", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = model::disconnect, enabled = enabled) { Text("Fermer la session") }
                        }
                        TextButton(onClick = { openLink("https://myaccount.google.com/connections") }, enabled = enabled) { Text("Gérer les accès Google") }
                        TextButton(onClick = { openLink("https://github.com/SachaLou13/SpaceSaver/blob/main/docs/GOOGLE_SETUP.md") }, enabled = enabled) { Text("Aide connexion") }
                    }
                }
            }
            item {
                Text("Tes règles de nettoyage", style = MaterialTheme.typography.titleLarge)
                Text("Active une règle ou combine les deux : un mail doit correspondre à au moins une règle.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RuleSwitch("Promotions / Réseaux sociaux", rules.categoriesEnabled, enabled) {
                            model.changeRules(rules.copy(categoriesEnabled = it))
                        }
                        if (rules.categoriesEnabled) {
                            RuleCheck("Promotions", rules.promotions, enabled) { model.changeRules(rules.copy(promotions = it)) }
                            RuleCheck("Réseaux sociaux", rules.social, enabled) { model.changeRules(rules.copy(social = it)) }
                            AgePicker(rules.categoryYears, enabled) { model.changeRules(rules.copy(categoryYears = it)) }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RuleSwitch("Tous les anciens mails", rules.allEnabled, enabled) { model.changeRules(rules.copy(allEnabled = it)) }
                        if (rules.allEnabled) {
                            Text("Toutes les catégories, avec les exclusions ci-dessous.", style = MaterialTheme.typography.bodySmall)
                            AgePicker(rules.allYears, enabled) { model.changeRules(rules.copy(allYears = it)) }
                        }
                    }
                }
            }
            item {
                Text("À conserver", style = MaterialTheme.typography.titleMedium)
                RuleCheck("Favoris (étoile)", rules.protectStarred, enabled) { model.changeRules(rules.copy(protectStarred = it)) }
                RuleCheck("Mails marqués importants", rules.protectImportant, enabled) { model.changeRules(rules.copy(protectImportant = it)) }
                RuleCheck("Mails non lus", rules.protectUnread, enabled) { model.changeRules(rules.copy(protectUnread = it)) }
                Text("Les messages envoyés, brouillons, spams et mails déjà à la corbeille sont toujours exclus.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { model.scan() }, enabled = enabled && model.account != null && rules.valid, modifier = Modifier.fillMaxWidth()) {
                    Text("Analyser sans rien supprimer")
                }
                if (!rules.valid) Text("Active au moins une règle et une catégorie si nécessaire.", color = MaterialTheme.colorScheme.error)
            }
            if (model.busy || authorizing) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (authorizing) "Autorisation Google…" else model.progress.ifBlank { "Connexion à Gmail…" })
                if (model.busy) TextButton(onClick = model::stop, enabled = !model.stopRequested) {
                    Text(if (model.cleaning) "Arrêter après le message en cours" else "Annuler")
                }
            }
            model.notice?.let { message -> item {
                Card(Modifier.fillMaxWidth()) { Text(message, Modifier.padding(16.dp)) }
            } }
            if (model.hasResults || model.rows.isNotEmpty()) {
                item {
                    Text("${model.rows.size} mails dans cet aperçu", style = MaterialTheme.typography.titleLarge)
                    Text("Volume estimé : ${size(model.rows.sumOf { it.sizeEstimate })}. Ce n’est pas encore de l’espace libéré.", style = MaterialTheme.typography.bodyMedium)
                    Text("Les mails à la corbeille occupent encore du stockage. Leur suppression définitive se gère dans Gmail ; ils y sont normalement supprimés après 30 jours.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = model::selectLoaded, enabled = enabled && model.rows.isNotEmpty()) {
                        Text("Sélectionner les ${model.rows.size} mails chargés")
                    }
                    if (model.selected.isNotEmpty()) TextButton(onClick = model::clearSelection, enabled = enabled) { Text("Tout désélectionner") }
                }
                items(model.rows, key = { it.id }) { mail ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(12.dp)) {
                            Checkbox(checked = mail.id in model.selected, onCheckedChange = { model.toggle(mail.id) }, enabled = enabled)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(mail.subject, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(mail.sender, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                Text("${DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(mail.dateMillis))} · ~${size(mail.sizeEstimate)}",
                                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                Text(when {
                                    "CATEGORY_PROMOTIONS" in mail.labels -> "Promotions"
                                    "CATEGORY_SOCIAL" in mail.labels -> "Réseaux sociaux"
                                    else -> "Autre catégorie"
                                }, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (model.more && model.inspected < 500) item {
                    OutlinedButton(onClick = { model.scan(append = true) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Examiner les 100 suivants") }
                }
            }
            item {
                Text("V2 · Gmail + téléphone. Google Drive arrivera dans une prochaine version.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
    if (confirm && chosen.isNotEmpty()) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Mettre ${chosen.size} mails à la corbeille ?") },
        text = { Text("Compte : ${model.account}\n\nVolume estimé : ${size(chosen.sumOf { it.sizeEstimate })}.\n\nSeuls les messages sélectionnés seront déplacés, après une nouvelle vérification des règles. Les autres messages d’une conversation restent en place. Tu peux restaurer ces mails depuis la corbeille Gmail avant leur suppression définitive.") },
        confirmButton = { TextButton(onClick = { confirm = false; model.trashSelection() }, enabled = enabled) { Text("Confirmer la mise à la corbeille") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } }
    )
}

@Composable
private fun RuleSwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f).padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
@Composable
private fun RuleCheck(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label, Modifier.weight(1f).padding(top = 12.dp))
    }
}
@Composable
private fun AgePicker(years: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Text("Plus anciens que", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { onChange(years - 1) }, enabled = enabled && years > 1) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$years an${if (years > 1) "s" else ""}", Modifier.padding(top = 14.dp), fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { onChange(years + 1) }, enabled = enabled && years < 30) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

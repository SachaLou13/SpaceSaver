package com.sachalou.spacesaver

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF87E8C1), onPrimary = Color(0xFF003828),
                background = Color(0xFF0C151E), surface = Color(0xFF142330),
                surfaceVariant = Color(0xFF203440), secondary = Color(0xFFA8C9FF)
            )) { SpaceSaverRoot() }
        }
    }
}

@Composable
private fun SpaceSaverRoot() {
    var tab by rememberSaveable { mutableStateOf(0) }
    val gmail: GmailModel = viewModel()
    val local: StorageModel = viewModel()
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Gmail") }, enabled = !gmail.busy && !local.busy)
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Téléphone") }, enabled = !gmail.busy && !local.busy)
            }
            Box(Modifier.weight(1f)) {
                if (tab == 0) GmailScreen(gmail) else SpaceSaver(local)
            }
        }
    }
}

@Composable
private fun SpaceSaver(model: StorageModel = viewModel()) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let(model::choose)
    }
    var filter by remember { mutableStateOf<FileCategory?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val size: (Long) -> String = { Formatter.formatFileSize(context, it) }
    val storage = remember(model.busy) { runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull() }
    val visible = remember(model.files, filter, query) {
        model.files.filter { (filter == null || it.category == filter) && it.name.contains(query, true) }
    }
    val selectedFiles = model.files.filter { it.uri in model.selected }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (selectedFiles.isNotEmpty()) Surface(tonalElevation = 6.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    Text("${selectedFiles.size} sélectionné(s) · ${size(selectedFiles.sumOf { it.size })}")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = model::clearSelection, enabled = !model.busy) { Text("Annuler") }
                        Button(onClick = { confirm = true }, enabled = !model.busy, modifier = Modifier.weight(1f)) {
                            Text("Supprimer la sélection")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("S P A C E S A V E R", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(14.dp))
                Text("Stop paying to store digital junk.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Tes fichiers. Ton espace. Ton choix.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("STOCKAGE INTERNE", style = MaterialTheme.typography.labelMedium)
                        Text(storage?.let { "${size(it.availableBytes)} libres" } ?: "Stockage indisponible",
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        storage?.let {
                            LinearProgressIndicator(progress = { (1f - it.availableBytes.toFloat() / it.totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp))
                            Text("${size(it.totalBytes - it.availableBytes)} utilisés sur ${size(it.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Text("Explorer un dossier", style = MaterialTheme.typography.titleLarge)
                Text("Choisis un dossier, par exemple DCIM ou Pictures. Seul ce dossier et ses sous-dossiers seront analysés.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(model.folder) }, enabled = !model.busy) { Text("Choisir un dossier") }
                    if (model.folder != null) OutlinedButton(onClick = model::scan, enabled = !model.busy) { Text("Actualiser") }
                }
                model.folder?.let {
                    Text("Dossier : ${android.provider.DocumentsContract.getTreeDocumentId(it)}",
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (model.busy) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (model.deleting) "Suppression en cours…" else "Analyse en cours…")
                if (!model.deleting) TextButton(onClick = model::cancel) { Text("Arrêter l’analyse") }
            }
            model.message?.let { text -> item {
                Text(text, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium)
            } }
            if (model.scanned) {
                item {
                    Text("${size(model.files.sumOf { it.size })} dans ce dossier", style = MaterialTheme.typography.titleLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Tout") }) }
                        items(FileCategory.entries.toList()) { category ->
                            val bytes = model.files.filter { it.category == category }.sumOf { it.size }
                            FilterChip(selected = filter == category, onClick = { filter = category }, label = { Text("${category.label} · ${size(bytes)}") })
                        }
                    }
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Rechercher un fichier") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Text("${visible.size} fichiers · du plus volumineux au plus petit", style = MaterialTheme.typography.bodySmall)
                }
                if (visible.isEmpty()) item { Text("Aucun fichier à afficher.") }
                items(visible, key = { it.uri.toString() }) { file ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(12.dp)) {
                            Checkbox(checked = file.uri in model.selected, onCheckedChange = { model.toggle(file) },
                                enabled = !model.busy && file.deletable)
                            Column(Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${file.category.label} · ${size(file.size)}", color = MaterialTheme.colorScheme.primary)
                                if (file.path.isNotEmpty()) Text(file.path, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, file.mime)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                    }.onFailure { model.report("Aucune application disponible pour ouvrir ce fichier.") }
                                }, enabled = !model.busy) { Text("Ouvrir") }
                                if (!file.deletable) Text("Lecture seule", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            } else if (!model.busy) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Reprends le contrôle", style = MaterialTheme.typography.titleMedium)
                        Text("Repère les vidéos lourdes, retrouve tes documents et supprime uniquement ce que tu as choisi.")
                        Text("Sans compte · Sans publicité · Aucune donnée envoyée par SpaceSaver", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (confirm && selectedFiles.isNotEmpty()) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("Supprimer définitivement ?") },
        text = { Text("${selectedFiles.size} fichier(s), soit ${size(selectedFiles.sumOf { it.size })}. Cette opération est irréversible : SpaceSaver n’a pas de corbeille. Si ce dossier est synchronisé, la suppression peut aussi se répercuter dans le cloud.") },
        confirmButton = { TextButton(onClick = { confirm = false; model.deleteSelected() }) { Text("Supprimer définitivement") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Conserver") } }
    )
}

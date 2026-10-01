package pt.vcc.parking.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.vcc.parking.R
import pt.vcc.parking.data.local.HistoryRetention
import pt.vcc.parking.history.HistoryExportFormat
import pt.vcc.parking.history.HistoryMessage
import pt.vcc.parking.history.HistoryUiState
import pt.vcc.parking.history.HistoryViewModel

/**
 * Liga o [HistoryViewModel] ao ecra e trata do seletor de ficheiros.
 *
 * A exportacao usa o `Storage Access Framework`: o ficheiro vai para onde o
 * utilizador escolher, sem pedir permissao de armazenamento para nada.
 */
@Composable
fun HistoryRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val geoJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(HistoryExportFormat.GeoJson.mimeType),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.export(HistoryExportFormat.GeoJson) { content ->
            withContext(Dispatchers.IO) {
                val stream = checkNotNull(context.contentResolver.openOutputStream(uri))
                stream.use { it.write(content.toByteArray()) }
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(HistoryExportFormat.Csv.mimeType),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.export(HistoryExportFormat.Csv) { content ->
            withContext(Dispatchers.IO) {
                val stream = checkNotNull(context.contentResolver.openOutputStream(uri))
                stream.use { it.write(content.toByteArray()) }
            }
        }
    }

    HistoryScreen(
        uiState = uiState,
        modifier = modifier,
        onBack = onBack,
        onLoadMore = viewModel::loadMore,
        onDelete = viewModel::delete,
        onUndoDelete = viewModel::undoDelete,
        onMessageDismissed = viewModel::dismissMessage,
        onRetentionSelected = viewModel::setRetention,
        onDeleteAll = viewModel::deleteAll,
        onExport = { format ->
            val name = viewModel.suggestedFileName(format)
            when (format) {
                HistoryExportFormat.GeoJson -> geoJsonLauncher.launch(name)
                HistoryExportFormat.Csv -> csvLauncher.launch(name)
            }
        },
    )
}

/**
 * Lista do historico (`vp-10-history`).
 *
 * Os registos sao agrupados por dia e carregados por paginas: um historico com
 * centenas de linhas nao cabe numa lista plana nem numa unica leitura.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onDelete: (id: Long) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onMessageDismissed: () -> Unit = {},
    onRetentionSelected: (HistoryRetention) -> Unit = {},
    onExport: (HistoryExportFormat) -> Unit = {},
    onDeleteAll: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    var detailId by remember { mutableStateOf<Long?>(null) }
    var confirmingDeleteId by remember { mutableStateOf<Long?>(null) }

    val detail = uiState.entries.firstOrNull { it.id == detailId }
    val messageText = uiState.message?.let { historyMessageText(it) }
    val undoLabel = stringResource(R.string.history_action_undo)

    // A remocao de um registo so fica definitiva quando a `Snackbar` fecha sem
    // ser anulada; e por isso que o resultado dela volta sempre ao ViewModel.
    LaunchedEffect(uiState.message, messageText) {
        val text = messageText ?: return@LaunchedEffect
        val undoable = uiState.message is HistoryMessage.Deleted

        val result = snackbarHostState.showSnackbar(
            message = text,
            actionLabel = undoLabel.takeIf { undoable },
        )

        if (result == SnackbarResult.ActionPerformed) onUndoDelete() else onMessageDismissed()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.history_action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val content = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when {
            uiState.loading -> HistoryPlaceholder(
                text = stringResource(R.string.history_loading),
                modifier = content,
                showProgress = true,
            )

            uiState.isEmpty -> HistoryEmpty(
                modifier = content,
                retention = uiState.retention,
                onRetentionSelected = onRetentionSelected,
            )

            else -> HistoryList(
                uiState = uiState,
                modifier = content,
                onLoadMore = onLoadMore,
                onOpenDetail = { detailId = it },
                onDeleteRequested = { confirmingDeleteId = it },
                onRetentionSelected = onRetentionSelected,
                onExport = onExport,
                onDeleteAll = onDeleteAll,
            )
        }
    }

    detail?.let { entry ->
        HistoryDetailSheet(
            entry = entry,
            onDismiss = { detailId = null },
            onDelete = {
                detailId = null
                confirmingDeleteId = entry.id
            },
        )
    }

    confirmingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmingDeleteId = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDeleteId = null
                        onDelete(id)
                    },
                ) {
                    Text(stringResource(R.string.history_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDeleteId = null }) {
                    Text(stringResource(R.string.history_action_cancel))
                }
            },
            title = { Text(stringResource(R.string.history_delete_confirm_title)) },
            text = { Text(stringResource(R.string.history_delete_confirm_message)) },
        )
    }
}

@Composable
private fun HistoryList(
    uiState: HistoryUiState,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    onOpenDetail: (Long) -> Unit = {},
    onDeleteRequested: (Long) -> Unit = {},
    onRetentionSelected: (HistoryRetention) -> Unit = {},
    onExport: (HistoryExportFormat) -> Unit = {},
    onDeleteAll: () -> Unit = {},
) {
    val days = remember(uiState.entries) {
        uiState.entries.groupBy { startOfDay(it.parkedAtMillis) }
    }

    LazyColumn(modifier = modifier) {
        item(key = COUNT_KEY) {
            Text(
                text = stringResource(R.string.history_count, uiState.totalCount),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        days.forEach { (dayMillis, entries) ->
            item(key = "day-$dayMillis") { HistoryDayHeader(dayMillis) }

            items(entries, key = { it.id }) { entry ->
                HistoryItemRow(
                    entry = entry,
                    onClick = { onOpenDetail(entry.id) },
                    onDelete = { onDeleteRequested(entry.id) },
                )
            }
        }

        if (uiState.canLoadMore) {
            item(key = LOAD_MORE_KEY) {
                OutlinedButton(
                    onClick = onLoadMore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.history_action_load_more))
                }
            }
        }

        item(key = PRIVACY_KEY) {
            HistoryPrivacySection(
                retention = uiState.retention,
                exporting = uiState.exporting,
                onRetentionSelected = onRetentionSelected,
                onExport = onExport,
                onDeleteAll = onDeleteAll,
            )
        }
    }
}

/**
 * Sem registos, explicar como aparece o primeiro vale mais do que uma lista
 * vazia; a retencao continua acessivel porque e uma escolha previa.
 */
@Composable
private fun HistoryEmpty(
    modifier: Modifier = Modifier,
    retention: HistoryRetention = HistoryRetention.Default,
    onRetentionSelected: (HistoryRetention) -> Unit = {},
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.history_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.history_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HistoryPrivacySection(
            retention = retention,
            canExport = false,
            onRetentionSelected = onRetentionSelected,
        )
    }
}

@Composable
private fun HistoryPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        if (showProgress) CircularProgressIndicator()
        Text(text = text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun historyMessageText(message: HistoryMessage): String = when (message) {
    is HistoryMessage.Deleted -> stringResource(R.string.history_deleted)
    is HistoryMessage.Cleared -> stringResource(R.string.history_cleared, message.count)
    HistoryMessage.Exported -> stringResource(R.string.history_exported)
    HistoryMessage.ExportFailed -> stringResource(R.string.history_export_failed)
}

private const val COUNT_KEY = "count"
private const val LOAD_MORE_KEY = "load-more"
private const val PRIVACY_KEY = "privacy"

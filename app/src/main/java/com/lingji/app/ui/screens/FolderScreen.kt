package com.lingji.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lingji.app.R
import com.lingji.app.domain.model.Subject
import com.lingji.app.ui.components.GlassOutlinedTextField
import com.lingji.app.ui.components.LingjiDialog
import com.lingji.app.ui.components.LingjiDialogConfirmButton
import com.lingji.app.ui.components.LingjiDialogDismissButton
import com.lingji.app.ui.components.SubjectCard
import com.lingji.app.ui.viewmodel.SubjectViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    viewModel: SubjectViewModel,
    folderId: String,
    onSubjectClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val folder = uiState.folders.find { it.id == folderId }
    val folderSubjects = uiState.subjects.filter { it.folderId == folderId }
        .sortedByDescending { it.orderIndex }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var renameSubjectId by remember { mutableStateOf<String?>(null) }
    var renameDefault by remember { mutableStateOf("") }
    var exportSubject by remember { mutableStateOf<Subject?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val subject = exportSubject ?: return@rememberLauncherForActivityResult
        exportSubject = null
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                viewModel.exportSubject(subject, uri)
                Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "__add__") {
                    AddSubjectCard(
                        folderId = folderId,
                        onAdd = { title, type, fid ->
                            viewModel.addSubject(title, type, fid)
                        }
                    )
                }
                items(folderSubjects, key = { it.id }) { subject ->
                    val index = folderSubjects.indexOf(subject)
                    SubjectCard(
                        subject = subject,
                        onClick = { onSubjectClick(subject.id) },
                        onDelete = { viewModel.deleteSubject(subject.id) },
                        onRename = { renameSubjectId = subject.id; renameDefault = subject.title },
                        onExport = {
                            exportSubject = subject
                            exportLauncher.launch(viewModel.buildExportFileName(subject.title))
                        },
                        onCopyExport = {
                            scope.launch {
                                try {
                                    val encoded = viewModel.exportSubjectToText(subject)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(subject.title, encoded))
                                    Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, context.getString(R.string.copy_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onMoveToTop = { viewModel.moveSubjectToTop(subject.id) },
                        onMoveUp = { viewModel.moveSubjectUp(subject.id) },
                        onMoveDown = { viewModel.moveSubjectDown(subject.id) },
                        canMoveUp = index > 0,
                        canMoveDown = index < folderSubjects.lastIndex,
                        onRemoveFromFolder = { viewModel.removeSubjectFromFolder(subject.id) }
                    )
                }
            }
        }
    }

    renameSubjectId?.let { id ->
        var title by remember(id) { mutableStateOf(renameDefault) }
        LingjiDialog(
            onDismissRequest = { renameSubjectId = null },
            title = { Text(stringResource(R.string.rename_subject)) },
            text = {
                GlassOutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.new_title)) },
                    singleLine = true
                )
            },
            confirmButton = {
                LingjiDialogConfirmButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        if (title.isNotBlank()) viewModel.renameSubject(id, title)
                        renameSubjectId = null
                    }
                )
            },
            dismissButton = {
                LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { renameSubjectId = null }
                )
            }
        )
    }
}

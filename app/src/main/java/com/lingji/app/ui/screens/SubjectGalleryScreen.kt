package com.lingji.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingji.app.R
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
import com.lingji.app.ui.components.GlassOutlinedTextField
import com.lingji.app.ui.components.SubjectCard
import com.lingji.app.ui.components.SubjectCardMinHeight
import com.lingji.app.ui.components.TimeDisplay
import com.lingji.app.ui.theme.NotoSerifCJKsc
import com.lingji.app.ui.viewmodel.SubjectViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectGalleryScreen(
    viewModel: SubjectViewModel,
    onSubjectClick: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var renameSubjectId by remember { mutableStateOf<String?>(null) }
    var renameDefault by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = viewModel.importSubject(uri)
            Toast.makeText(
                context,
                if (ok) context.getString(R.string.import_success) else context.getString(R.string.import_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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
                Toast.makeText(
                    context,
                    context.getString(R.string.export_success),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    context.getString(R.string.export_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = 16.dp),
                title = {
                    Text(
                        text = stringResource(R.string.gallery_title),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = NotoSerifCJKsc,
                            fontSize = 40.sp,
                            letterSpacing = (-0.03).sp
                        )
                    )
                },
                actions = {
                    TimeDisplay(
                        modifier = Modifier.padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }) {
                        Text(stringResource(R.string.import_label))
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.subjects.isEmpty()) {
                EmptySubjectState(onCreate = { showAddDialog = true })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "__add__") {
                        AddSubjectCard(
                            onAdd = { title, type ->
                                viewModel.addSubject(title, type)
                            }
                        )
                    }
                    items(uiState.subjects, key = { it.id }) { subject ->
                        val index = uiState.subjects.indexOf(subject)
                        SubjectCard(
                            subject = subject,
                            onClick = { onSubjectClick(subject.id) },
                            onDelete = { viewModel.deleteSubject(subject.id) },
                            onRename = {
                                renameSubjectId = subject.id
                                renameDefault = subject.title
                            },
                            onExport = {
                                exportSubject = subject
                                val fileName = subject.title
                                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                    .takeIf { it.isNotBlank() } ?: "notebook"
                                exportLauncher.launch("$fileName.ling")
                            },
                            onMoveToTop = { viewModel.moveSubjectToTop(subject.id) },
                            onMoveUp = { viewModel.moveSubjectUp(subject.id) },
                            onMoveDown = { viewModel.moveSubjectDown(subject.id) },
                            canMoveUp = index > 0,
                            canMoveDown = index < uiState.subjects.lastIndex
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSubjectDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, type ->
                viewModel.addSubject(title, type)
                showAddDialog = false
            }
        )
    }

    renameSubjectId?.let { id ->
        var title by remember(id) { mutableStateOf(renameDefault) }
        AlertDialog(
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
                TextButton(
                    onClick = {
                        if (title.isNotBlank()) viewModel.renameSubject(id, title)
                        renameSubjectId = null
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameSubjectId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptySubjectState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Book,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_subjects),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onCreate) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.new_subject), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun AddSubjectCard(
    onAdd: (String, SubjectType) -> Unit,
    modifier: Modifier = Modifier
) {
    var isCreating by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SubjectType.FRAGMENT) }
    val focusRequester = remember { FocusRequester() }
    val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    LaunchedEffect(isCreating) {
        if (isCreating) focusRequester.requestFocus()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SubjectCardMinHeight)
            .dashedBorder(strokeColor)
            .padding(1.dp)
            .clickable(
                enabled = !isCreating,
                onClick = { isCreating = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        border = null
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (isCreating) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        decorationBox = { innerTextField ->
                            Box {
                                if (title.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.new_subject_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeChip(
                            label = stringResource(R.string.fragment_mode),
                            selected = type == SubjectType.FRAGMENT,
                            onClick = { type = SubjectType.FRAGMENT },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TypeChip(
                            label = stringResource(R.string.notebook_mode),
                            selected = type == SubjectType.NOTEBOOK,
                            onClick = { type = SubjectType.NOTEBOOK },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(
                            onClick = {
                                isCreating = false
                                title = ""
                                type = SubjectType.FRAGMENT
                            }
                        ) { Text(stringResource(R.string.cancel)) }
                        TextButton(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onAdd(title.trim(), type)
                                    isCreating = false
                                    title = ""
                                    type = SubjectType.FRAGMENT
                                }
                            },
                            enabled = title.isNotBlank()
                        ) { Text(stringResource(R.string.confirm)) }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.new_subject),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = modifier
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = modifier
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Modifier.dashedBorder(color: androidx.compose.ui.graphics.Color) = drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        ),
        cornerRadius = CornerRadius(16.dp.toPx())
    )
}

@Composable
private fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, SubjectType) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var isNotebook by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_subject)) },
        text = {
            Column {
                GlassOutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.hint_subject_title)) },
                    singleLine = true
                )
                TextButton(onClick = { isNotebook = !isNotebook }) {
                    Text(if (isNotebook) "模式：笔记本" else "模式：碎片")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(title, if (isNotebook) SubjectType.NOTEBOOK else SubjectType.FRAGMENT)
                    onDismiss()
                },
                enabled = title.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

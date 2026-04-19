package com.glucoplan.app.ui.dishes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.glucoplan.app.domain.model.Pan
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PansScreen(
    viewModel: DishesViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val pans by viewModel.pans.collectAsStateWithLifecycle()
    var editPan by remember { mutableStateOf<Pan?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.loadPans() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Кастрюли") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { padding ->
        if (pans.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(120.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Kitchen, null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Кастрюли не добавлены", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Нажмите + чтобы добавить", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 8.dp, top = padding.calculateTopPadding() + 8.dp,
                    end = 8.dp, bottom = 8.dp
                )
            ) {
                items(pans, key = { it.id }) { pan ->
                    ListItem(
                        leadingContent = {
                            if (pan.photoPath != null && File(pan.photoPath).exists()) {
                                AsyncImage(
                                    model = File(pan.photoPath), contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small)
                                )
                            } else {
                                Icon(Icons.Default.Kitchen, null, Modifier.size(48.dp))
                            }
                        },
                        headlineContent = { Text(pan.name) },
                        supportingContent = { Text("${pan.weight.toInt()} г") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editPan = pan }) {
                                    Icon(Icons.Default.Edit, null)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        pan.photoPath?.let { File(it).takeIf { f -> f.exists() }?.delete() }
                                        viewModel.deletePan(pan.id)
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, null)
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAdd || editPan != null) {
        PanEditDialog(
            pan = editPan,
            onDismiss = { showAdd = false; editPan = null },
            onSave = { pan ->
                scope.launch {
                    viewModel.savePan(pan)
                    viewModel.loadPans()
                    showAdd = false
                    editPan = null
                }
            }
        )
    }
}

@Composable
fun PanEditDialog(pan: Pan?, onDismiss: () -> Unit, onSave: (Pan) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(pan?.name ?: "") }
    var weight by remember { mutableStateOf(pan?.weight?.toString() ?: "0") }
    var photoPath by remember { mutableStateOf(pan?.photoPath) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val dir = File(context.filesDir, "pan_photos").also { d -> d.mkdirs() }
            val dest = File(dir, "${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(it)?.use { inp ->
                dest.outputStream().use { out -> inp.copyTo(out) }
            }
            photoPath = dest.absolutePath
        }
    }

    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) photoPath = pendingCameraPath
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pan == null) "Новая кастрюля" else "Редактировать") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = weight, onValueChange = { weight = it },
                    label = { Text("Вес (г)") }, suffix = { Text("г") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Галерея")
                    }
                    OutlinedButton(
                        onClick = {
                            val dir = File(context.filesDir, "pan_photos").also { it.mkdirs() }
                            val file = File(dir, "${System.currentTimeMillis()}.jpg")
                            pendingCameraPath = file.absolutePath
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            cameraLauncher.launch(uri)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Камера")
                    }
                }
                photoPath?.let { path ->
                    if (File(path).exists()) {
                        AsyncImage(
                            model = File(path), contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(80.dp).clip(MaterialTheme.shapes.medium)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                onSave(Pan(
                    id = pan?.id ?: 0, name = name.trim(),
                    weight = weight.toDoubleOrNull() ?: 0.0,
                    photoPath = photoPath
                ))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

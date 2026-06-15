package com.lingji.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

class ImagePickerState {
    var pendingCameraUri: Uri? by mutableStateOf(null)
}

@Composable
fun rememberImagePickerState(): ImagePickerState = remember { ImagePickerState() }

@Composable
fun PageImagePicker(
    state: ImagePickerState,
    onImagePicked: (base64: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showSourceChooser by remember { mutableStateOf(true) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val base64 = uriToBase64(context, it)
            if (base64 != null) {
                onImagePicked(base64)
            }
        }
        onDismiss()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            state.pendingCameraUri?.let { uri ->
                val base64 = uriToBase64(context, uri)
                if (base64 != null) {
                    onImagePicked(base64)
                }
            }
        }
        state.pendingCameraUri = null
        onDismiss()
    }

    if (showSourceChooser) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("添加图片") },
            text = {
                Column {
                    Text("选择图片来源", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val uri = createTempImageUri(context)
                                state.pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                                showSourceChooser = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Text("拍照", modifier = Modifier.padding(start = 8.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                galleryLauncher.launch("image/*")
                                showSourceChooser = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Text("相册", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        )
    }
}

private fun createTempImageUri(context: Context): Uri {
    val file = File.createTempFile("page_img_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

private fun uriToBase64(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bitmap = BitmapFactory.decodeStream(input) ?: return null
            val scaled = scaleBitmap(bitmap, 1200)
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
            val bytes = output.toByteArray()
            "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxDimension && height <= maxDimension) return bitmap
    val ratio = width.toFloat() / height.toFloat()
    val newWidth: Int
    val newHeight: Int
    if (width > height) {
        newWidth = maxDimension
        newHeight = (maxDimension / ratio).toInt()
    } else {
        newHeight = maxDimension
        newWidth = (maxDimension * ratio).toInt()
    }
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

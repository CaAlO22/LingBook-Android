package com.lingji.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lingji.app.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

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
    val scope = rememberCoroutineScope()
    var showSourceChooser by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }

    fun handleImage(uri: Uri, onComplete: () -> Unit = {}) {
        isProcessing = true
        scope.launch {
            val base64 = withContext(Dispatchers.IO) { uriToBase64(context, uri) }
            if (base64 != null) {
                onImagePicked(base64)
            }
            isProcessing = false
            onComplete()
            onDismiss()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handleImage(uri)
        } else {
            onDismiss()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = state.pendingCameraUri
        if (success && uri != null) {
            handleImage(uri) { state.pendingCameraUri = null }
        } else {
            state.pendingCameraUri = null
            onDismiss()
        }
    }

    val launchCamera: () -> Unit = {
        val uri = createTempImageUri(context)
        state.pendingCameraUri = uri
        try {
            cameraLauncher.launch(uri)
        } catch (e: SecurityException) {
            Toast.makeText(
                context,
                context.getString(R.string.camera_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
            state.pendingCameraUri = null
            onDismiss()
        }
        showSourceChooser = false
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.camera_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
            onDismiss()
        }
    }

    if (isProcessing) {
        LingjiDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.add_image_title)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = stringResource(R.string.processing_image),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        )
    } else if (showSourceChooser) {
        LingjiDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.add_image_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.choose_image_source),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    launchCamera()
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Text(stringResource(R.string.camera), modifier = Modifier.padding(start = 8.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                galleryLauncher.launch("image/*")
                                showSourceChooser = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Text(stringResource(R.string.gallery), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            dismissButton = {
                LingjiDialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
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

internal fun uriToBase64(context: Context, uri: Uri): String? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 960)
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bitmap = BitmapFactory.decodeStream(input, null, options) ?: return null
            val scaled = scaleBitmap(bitmap, 960)
            // 无损 PNG 压缩，彻底消除 JPEG 块状伪影；PNG 通用性最好，所有视觉模型均支持
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
            val bytes = output.toByteArray()
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            "data:image/png;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var inSampleSize = 1
    var halfWidth = width / 2
    var halfHeight = height / 2
    while (halfWidth / inSampleSize >= maxDimension || halfHeight / inSampleSize >= maxDimension) {
        inSampleSize *= 2
    }
    return inSampleSize.coerceAtLeast(1)
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
        newHeight = max(1, (maxDimension / ratio).toInt())
    } else {
        newHeight = maxDimension
        newWidth = max(1, (maxDimension * ratio).toInt())
    }
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

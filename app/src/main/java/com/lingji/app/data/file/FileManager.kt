package com.lingji.app.data.file

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.lingji.app.domain.model.Subject
import com.lingji.app.util.Codec82
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    suspend fun importSubject(uri: Uri): Subject? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val text = stream.bufferedReader().readText()
                val decoded = Codec82.decode(text)
                gson.fromJson(gson.toJson(decoded), Subject::class.java)
            }
        }.getOrNull()
    }

    suspend fun exportSubject(subject: Subject, uri: Uri) = withContext(Dispatchers.IO) {
        val json = gson.toJson(subject)
        val encoded = Codec82.encode(json)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(encoded.toByteArray(Charsets.UTF_8))
        }
    }

    suspend fun exportPlainJson(subject: Subject, uri: Uri) = withContext(Dispatchers.IO) {
        val json = gson.toJson(subject)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
        }
    }
}

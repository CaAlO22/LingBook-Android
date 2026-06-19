package com.lingji.app.data.file

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.lingji.app.domain.model.Subject
import com.lingji.app.domain.model.SubjectType
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
                importSubjectFromText(text)
            }
        }.getOrNull()
    }

    suspend fun importSubjectFromText(text: String): Subject? = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = text.trim()
            val decoded = Codec82.decode(trimmed)
            val jsonTree = gson.toJsonTree(decoded)
            if (jsonTree.isJsonObject) {
                val obj = jsonTree.asJsonObject
                if (!obj.has("type")) {
                    obj.addProperty("type", SubjectType.FRAGMENT.name)
                }
                gson.fromJson(obj, Subject::class.java)
            } else {
                gson.fromJson(jsonTree, Subject::class.java)
            }
        }.getOrNull()
    }

    suspend fun exportSubject(subject: Subject, uri: Uri) = withContext(Dispatchers.IO) {
        val encoded = encodeSubject(subject)
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("无法打开输出流")
        stream.use {
            it.write(encoded.toByteArray(Charsets.UTF_8))
        }
    }

    suspend fun encodeSubject(subject: Subject): String = withContext(Dispatchers.IO) {
        Codec82.encodeBase64(subject)
    }

    suspend fun exportPlainJson(subject: Subject, uri: Uri) = withContext(Dispatchers.IO) {
        val json = gson.toJson(subject)
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("无法打开输出流")
        stream.use {
            it.write(json.toByteArray(Charsets.UTF_8))
        }
    }
}

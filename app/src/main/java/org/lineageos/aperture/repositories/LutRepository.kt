/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.repositories

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.lineageos.aperture.models.Lut
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class LutRepository(private val context: Context) {
    private val lutDir = File(context.filesDir, "luts")

    private val _luts = MutableStateFlow<List<Lut>>(emptyList())
    val luts = _luts.asStateFlow()

    init {
        if (!lutDir.exists()) {
            lutDir.mkdirs()
        }
        refreshLuts()
    }

    fun refreshLuts() {
        val files = lutDir.listFiles { _, name -> name.endsWith(".cube") } ?: emptyArray()
        val lutList = files.map {
            Lut(
                id = it.nameWithoutExtension, // Simple ID for now
                name = it.nameWithoutExtension,
                file = it
            )
        }.sortedBy { it.name }
        _luts.value = lutList
    }

    suspend fun importLut(uri: Uri, name: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileName = name ?: getFileNameFromUri(uri) ?: "imported_lut_${System.currentTimeMillis()}"
            val safeName = fileName.replace("[^a-zA-Z0-9\.\-]".toRegex(), "_").let {
                if (it.endsWith(".cube")) it else "$it.cube"
            }
            val destFile = File(lutDir, safeName)

            context.contentResolver.openInputStream(uri)?.use {
                FileOutputStream(destFile).use {
                    it.copyTo(output)
                }
            }
            refreshLuts()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun removeLut(lut: Lut) = withContext(Dispatchers.IO) {
        if (lut.file.exists()) {
            lut.file.delete()
        }
        refreshLuts()
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        // Simple extraction, can be improved with DocumentFile if needed
        return uri.lastPathSegment
    }
}

package com.liveapitester.util

import com.intellij.openapi.project.Project
import java.io.File

object StorageUtil {
    const val STORAGE_DIR = ".liveapitester"

    fun getStorageDir(project: Project): File {
        val dir = File(project.basePath ?: System.getProperty("user.home"), STORAGE_DIR)
        dir.mkdirs()
        return dir
    }

    fun getStorageFile(project: Project, fileName: String): File =
        File(getStorageDir(project), fileName)
}

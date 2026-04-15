package com.liveapitester.history

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.liveapitester.http.ApiRequest
import com.liveapitester.http.ApiResponse
import com.liveapitester.settings.LiveApiTesterSettings
import com.liveapitester.util.StorageUtil
import java.io.File

@Service(Service.Level.PROJECT)
class HistoryManager(private val project: Project) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val entries: MutableList<HistoryEntry> = mutableListOf()

    init {
        loadHistory()
    }

    fun getEntries(): List<HistoryEntry> = entries.toList()

    fun addEntry(request: ApiRequest, response: ApiResponse) {
        val entry = HistoryEntry(
            request = request,
            statusCode = response.statusCode,
            statusText = response.statusText,
            responseTimeMs = response.responseTimeMs,
            responseSizeBytes = response.responseSizeBytes,
            responseBody = response.body
        )
        entries.add(0, entry)

        val maxHistory = LiveApiTesterSettings.getInstance().maxHistory
        while (entries.size > maxHistory) {
            entries.removeAt(entries.size - 1)
        }
        saveHistory()
    }

    fun clearHistory() {
        entries.clear()
        saveHistory()
    }

    private fun getStorageFile(): File = StorageUtil.getStorageFile(project, "history.json")

    private fun loadHistory() {
        val file = getStorageFile()
        if (file.exists()) {
            try {
                val type = object : TypeToken<List<HistoryEntry>>() {}.type
                val loaded: List<HistoryEntry> = gson.fromJson(file.readText(), type) ?: emptyList()
                entries.clear()
                entries.addAll(loaded)
            } catch (e: Exception) {
                entries.clear()
            }
        }
    }

    private fun saveHistory() {
        try {
            getStorageFile().writeText(gson.toJson(entries))
        } catch (e: Exception) {
            // Ignore save errors silently
        }
    }

    companion object {
        fun getInstance(project: Project): HistoryManager =
            project.getService(HistoryManager::class.java)
    }
}

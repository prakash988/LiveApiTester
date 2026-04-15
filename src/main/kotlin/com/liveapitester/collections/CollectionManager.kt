package com.liveapitester.collections

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class CollectionManager(private val project: Project) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val collections: MutableList<ApiCollection> = mutableListOf()

    init {
        loadCollections()
    }

    fun getCollections(): List<ApiCollection> = collections.toList()

    fun addCollection(collection: ApiCollection) {
        collections.add(collection)
        saveCollections()
    }

    fun removeCollection(collectionId: String) {
        collections.removeIf { it.id == collectionId }
        saveCollections()
    }

    fun updateCollection(collection: ApiCollection) {
        val index = collections.indexOfFirst { it.id == collection.id }
        if (index >= 0) {
            collections[index] = collection
            saveCollections()
        }
    }

    fun addRequestToCollection(collectionId: String, savedRequest: SavedRequest) {
        val collection = collections.find { it.id == collectionId } ?: return
        collection.requests.add(savedRequest)
        saveCollections()
    }

    fun removeRequestFromCollection(collectionId: String, requestId: String) {
        val collection = collections.find { it.id == collectionId } ?: return
        collection.requests.removeIf { it.id == requestId }
        saveCollections()
    }

    private fun getStorageFile(): File {
        val baseDir = File(project.basePath ?: System.getProperty("user.home"), ".liveapitester")
        baseDir.mkdirs()
        return File(baseDir, "collections.json")
    }

    private fun loadCollections() {
        val file = getStorageFile()
        if (file.exists()) {
            try {
                val type = object : TypeToken<List<ApiCollection>>() {}.type
                val loaded: List<ApiCollection> = gson.fromJson(file.readText(), type) ?: emptyList()
                collections.clear()
                collections.addAll(loaded)
            } catch (e: Exception) {
                collections.clear()
            }
        }
    }

    private fun saveCollections() {
        try {
            getStorageFile().writeText(gson.toJson(collections))
        } catch (e: Exception) {
            // Ignore save errors silently
        }
    }

    companion object {
        fun getInstance(project: Project): CollectionManager =
            project.getService(CollectionManager::class.java)
    }
}

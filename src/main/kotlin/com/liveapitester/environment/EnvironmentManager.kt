package com.liveapitester.environment

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class EnvironmentManager(private val project: Project) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val environments: MutableList<Environment> = mutableListOf()
    private var activeEnvironmentId: String? = null

    init {
        loadEnvironments()
    }

    fun getEnvironments(): List<Environment> = environments.toList()

    fun getActiveEnvironment(): Environment? =
        environments.find { it.id == activeEnvironmentId }

    fun getActiveVariables(): Map<String, String> =
        getActiveEnvironment()?.variables ?: emptyMap()

    fun setActiveEnvironment(environmentId: String?) {
        activeEnvironmentId = environmentId
        saveEnvironments()
    }

    fun addEnvironment(environment: Environment) {
        environments.add(environment)
        saveEnvironments()
    }

    fun removeEnvironment(environmentId: String) {
        environments.removeIf { it.id == environmentId }
        if (activeEnvironmentId == environmentId) {
            activeEnvironmentId = environments.firstOrNull()?.id
        }
        saveEnvironments()
    }

    fun updateEnvironment(environment: Environment) {
        val index = environments.indexOfFirst { it.id == environment.id }
        if (index >= 0) {
            environments[index] = environment
            saveEnvironments()
        }
    }

    private fun getStorageFile(): File {
        val baseDir = File(project.basePath ?: System.getProperty("user.home"), ".liveapitester")
        baseDir.mkdirs()
        return File(baseDir, "environments.json")
    }

    data class EnvironmentData(
        val environments: List<Environment>,
        val activeEnvironmentId: String?
    )

    private fun loadEnvironments() {
        val file = getStorageFile()
        if (file.exists()) {
            try {
                val data = gson.fromJson(file.readText(), EnvironmentData::class.java)
                environments.clear()
                environments.addAll(data.environments)
                activeEnvironmentId = data.activeEnvironmentId
            } catch (e: Exception) {
                environments.clear()
            }
        }
        if (environments.isEmpty()) {
            val defaultEnv = Environment(name = "Development", variables = mutableMapOf(
                "base_url" to "http://localhost:8080",
                "api_version" to "v1"
            ))
            environments.add(defaultEnv)
            activeEnvironmentId = defaultEnv.id
        }
    }

    private fun saveEnvironments() {
        try {
            val data = EnvironmentData(environments, activeEnvironmentId)
            getStorageFile().writeText(gson.toJson(data))
        } catch (e: Exception) {
            // Ignore save errors silently
        }
    }

    companion object {
        fun getInstance(project: Project): EnvironmentManager =
            project.getService(EnvironmentManager::class.java)
    }
}

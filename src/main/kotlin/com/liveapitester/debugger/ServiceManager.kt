package com.liveapitester.debugger

import com.intellij.execution.ExecutionManager
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

class ServiceManager(private val project: Project) {

    fun listRunConfigurations(): List<RunnerAndConfigurationSettings> {
        return RunManager.getInstance(project).allSettings
    }

    fun startService(config: RunnerAndConfigurationSettings) {
        ApplicationManager.getApplication().invokeLater {
            ProgramRunnerUtil.executeConfiguration(config, DefaultRunExecutor.getRunExecutorInstance())
        }
    }

    fun startServiceDebug(config: RunnerAndConfigurationSettings) {
        ApplicationManager.getApplication().invokeLater {
            ProgramRunnerUtil.executeConfiguration(config, DefaultDebugExecutor.getDebugExecutorInstance())
        }
    }

    fun stopService(handler: ProcessHandler) {
        handler.destroyProcess()
    }

    fun restartService(config: RunnerAndConfigurationSettings, debugMode: Boolean) {
        val runningHandlers = getRunningProcessHandlers()
        runningHandlers.forEach { it.destroyProcess() }
        if (debugMode) {
            startServiceDebug(config)
        } else {
            startService(config)
        }
    }

    fun isRunning(config: RunnerAndConfigurationSettings): Boolean {
        return try {
            val runningProcesses = ExecutionManager.getInstance(project).getRunningProcesses()
            runningProcesses.any { !it.isProcessTerminated && !it.isProcessTerminating }
        } catch (e: Exception) {
            false
        }
    }

    fun getProcessHandler(config: RunnerAndConfigurationSettings): ProcessHandler? {
        return try {
            val runningProcesses = ExecutionManager.getInstance(project).getRunningProcesses()
            runningProcesses.firstOrNull { !it.isProcessTerminated && !it.isProcessTerminating }
        } catch (e: Exception) {
            null
        }
    }

    private fun getRunningProcessHandlers(): List<ProcessHandler> {
        return try {
            ExecutionManager.getInstance(project).getRunningProcesses().toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

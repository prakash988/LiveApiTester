package com.liveapitester.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.liveapitester.debugger.DebuggerService
import com.liveapitester.http.ApiRequest
import com.liveapitester.http.ApiResponse
import com.liveapitester.http.HttpExecutor
import com.liveapitester.scanner.EndpointInfo
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JSplitPane

class LiveApiTesterToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = LiveApiTesterPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    class LiveApiTesterPanel(private val project: Project) : JPanel(BorderLayout()) {

        private val requestPanel: RequestPanel
        private val responsePanel: ResponsePanel
        private val historyPanel: HistoryPanel
        private val collectionsPanel: CollectionsPanel
        private val environmentPanel: EnvironmentPanel
        private val debugToolbarPanel: DebugToolbarPanel

        @Volatile
        private var currentExecutor: HttpExecutor? = null

        init {
            requestPanel = RequestPanel(
                project = project,
                onSend = { request -> executeRequest(request) },
                onSendDebug = { request -> executeRequestDebug(request) }
            )
            responsePanel = ResponsePanel(project)
            historyPanel = HistoryPanel(project) { entry ->
                requestPanel.loadRequest(entry.request)
            }
            collectionsPanel = CollectionsPanel(project) { savedRequest ->
                requestPanel.loadRequest(savedRequest.request)
            }
            environmentPanel = EnvironmentPanel(project)
            debugToolbarPanel = DebugToolbarPanel(project)

            setupLayout()
        }

        private fun setupLayout() {
            val leftTabbedPane = javax.swing.JTabbedPane()
            leftTabbedPane.addTab("History", historyPanel)
            leftTabbedPane.addTab("Collections", collectionsPanel)
            leftTabbedPane.addTab("Environments", environmentPanel)
            leftTabbedPane.preferredSize = Dimension(300, 600)

            // Right panel: Debug toolbar (hidden by default) + Request on top + Response on bottom
            val rightInner = JPanel(BorderLayout())
            rightInner.add(debugToolbarPanel, BorderLayout.NORTH)
            val rightSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, requestPanel, responsePanel)
            rightSplit.resizeWeight = 0.45
            rightSplit.dividerSize = 6
            rightInner.add(rightSplit, BorderLayout.CENTER)

            val mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftTabbedPane, rightInner)
            mainSplit.resizeWeight = 0.3
            mainSplit.dividerSize = 6

            add(mainSplit, BorderLayout.CENTER)
        }

        private fun executeRequest(request: ApiRequest) {
            // Cancel any previous in-flight request
            currentExecutor?.cancel()

            responsePanel.showLoading()
            requestPanel.setSendButtonsEnabled(false)
            LiveApiTesterStatusBarWidget.update("LiveApiTester: Sending...")

            val executor = HttpExecutor()
            currentExecutor = executor
            requestPanel.setCurrentExecutor(executor)

            ApplicationManager.getApplication().executeOnPooledThread {
                val response = try {
                    val settings = com.liveapitester.settings.LiveApiTesterSettings.getInstance()
                    val envVars = com.liveapitester.environment.EnvironmentManager.getInstance(project).getActiveVariables()
                    executor.execute(request, settings, envVars)
                } catch (e: java.io.IOException) {
                    if (e.message?.contains("cancel") == true || e.message?.contains("Cancel") == true ||
                        e.message?.contains("CANCEL") == true) {
                        createErrorResponse("Request cancelled")
                    } else {
                        createErrorResponse(friendlyErrorMessage(e))
                    }
                } catch (e: IllegalArgumentException) {
                    createErrorResponse("Invalid URL: ${e.message}")
                } catch (e: Exception) {
                    createErrorResponse(friendlyErrorMessage(e))
                }

                ApplicationManager.getApplication().invokeLater {
                    if (response.statusText != "Cancelled") {
                        responsePanel.showResponse(request, response)
                    }
                    requestPanel.setSendButtonsEnabled(true)
                    requestPanel.setCurrentExecutor(null)
                    currentExecutor = null
                    LiveApiTesterStatusBarWidget.update("LiveApiTester: Ready")

                    try {
                        com.liveapitester.history.HistoryManager.getInstance(project).addEntry(request, response)
                        historyPanel.refresh()
                    } catch (e: Exception) {
                        // History save failure is non-critical
                    }
                }
            }
        }

        private fun executeRequestDebug(request: ApiRequest) {
            val debuggerService = project.getService(DebuggerService::class.java)
            if (debuggerService == null) {
                executeRequest(request)
                return
            }

            requestPanel.setSendButtonsEnabled(false)
            responsePanel.showLoading()
            LiveApiTesterStatusBarWidget.update("LiveApiTester: Debug Active")

            debuggerService.sendAndDebug(
                request = request,
                onStatus = { status ->
                    ApplicationManager.getApplication().invokeLater {
                        responsePanel.showStatusMessage(status)
                        debugToolbarPanel.updateStatus(status)
                    }
                },
                onResponse = { response ->
                    ApplicationManager.getApplication().invokeLater {
                        responsePanel.showResponse(request, response)
                        requestPanel.setSendButtonsEnabled(true)
                        val sessionInfo = debuggerService.getCurrentSessionInfo()
                        if (sessionInfo.isNotEmpty()) {
                            debugToolbarPanel.setDebugActive(true, sessionInfo["sessionName"], -1)
                            responsePanel.showDebugInfo(
                                sessionInfo["sessionName"] ?: "",
                                sessionInfo
                            )
                        }
                        LiveApiTesterStatusBarWidget.update(
                            if (debuggerService.isDebugSessionActive()) "LiveApiTester: Debug Active"
                            else "LiveApiTester: Ready"
                        )
                        try {
                            com.liveapitester.history.HistoryManager.getInstance(project).addEntry(request, response)
                            historyPanel.refresh()
                        } catch (e: Exception) {
                            // Non-critical
                        }
                    }
                },
                onError = { error ->
                    ApplicationManager.getApplication().invokeLater {
                        responsePanel.showResponse(request, createErrorResponse(error))
                        requestPanel.setSendButtonsEnabled(true)
                        LiveApiTesterStatusBarWidget.update("LiveApiTester: Ready")
                    }
                }
            )
        }

        private fun friendlyErrorMessage(e: Exception): String {
            val msg = e.message ?: "Unknown error"
            return when {
                msg.contains("Unable to resolve host") || msg.contains("nodename nor servname provided") ->
                    "DNS resolution failed. Check that the hostname is correct and you have internet access."
                msg.contains("Connection refused") ->
                    "Connection refused. Is the service running on the specified host and port?"
                msg.contains("connect timed out") || msg.contains("timeout") ->
                    "Connection timed out. The server took too long to respond."
                msg.contains("SSL") || msg.contains("ssl") || msg.contains("certificate") ->
                    "SSL/TLS error. You can disable SSL verification in Settings > Tools > LiveApiTester."
                else -> msg
            }
        }

        private fun createErrorResponse(message: String): ApiResponse {
            return ApiResponse(
                statusCode = 0,
                statusText = if (message == "Request cancelled") "Cancelled" else "Error",
                headers = emptyMap(),
                body = message,
                responseTimeMs = 0,
                responseSizeBytes = 0
            )
        }

        fun loadEndpoint(endpoint: EndpointInfo) {
            requestPanel.loadEndpoint(endpoint)
        }
    }
}

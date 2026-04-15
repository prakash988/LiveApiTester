package com.liveapitester.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.liveapitester.http.ApiRequest
import com.liveapitester.http.ApiResponse
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

        init {
            requestPanel = RequestPanel(project) { request -> executeRequest(request) }
            responsePanel = ResponsePanel()
            historyPanel = HistoryPanel(project) { entry ->
                requestPanel.loadRequest(entry.request)
            }
            collectionsPanel = CollectionsPanel(project) { savedRequest ->
                requestPanel.loadRequest(savedRequest.request)
            }
            environmentPanel = EnvironmentPanel(project)

            setupLayout()
        }

        private fun setupLayout() {
            // Left panel: History + Collections + Environments stacked vertically
            val leftTabbedPane = javax.swing.JTabbedPane()
            leftTabbedPane.addTab("History", historyPanel)
            leftTabbedPane.addTab("Collections", collectionsPanel)
            leftTabbedPane.addTab("Environments", environmentPanel)
            leftTabbedPane.preferredSize = Dimension(300, 600)

            // Right panel: Request on top, Response on bottom
            val rightSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, requestPanel, responsePanel)
            rightSplit.resizeWeight = 0.5
            rightSplit.dividerSize = 6

            // Main split: Left + Right
            val mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftTabbedPane, rightSplit)
            mainSplit.resizeWeight = 0.3
            mainSplit.dividerSize = 6

            add(mainSplit, BorderLayout.CENTER)
        }

        private fun executeRequest(request: ApiRequest) {
            responsePanel.showLoading()
            requestPanel.setSendButtonEnabled(false)

            com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                val response = try {
                    val settings = com.liveapitester.settings.LiveApiTesterSettings.getInstance()
                    val envVars = com.liveapitester.environment.EnvironmentManager.getInstance(project).getActiveVariables()
                    com.liveapitester.http.HttpExecutor().execute(request, settings, envVars)
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Unknown error"
                    createErrorResponse(errorMsg)
                }

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    responsePanel.showResponse(request, response)
                    requestPanel.setSendButtonEnabled(true)

                    try {
                        com.liveapitester.history.HistoryManager.getInstance(project).addEntry(request, response)
                        historyPanel.refresh()
                    } catch (e: Exception) {
                        // History save failure is non-critical
                    }
                }
            }
        }

        private fun createErrorResponse(message: String): ApiResponse {
            return ApiResponse(
                statusCode = 0,
                statusText = "Error",
                headers = emptyMap(),
                body = "Request failed: $message",
                responseTimeMs = 0,
                responseSizeBytes = 0
            )
        }

        fun loadEndpoint(endpoint: EndpointInfo) {
            requestPanel.loadEndpoint(endpoint)
        }
    }
}

package com.liveapitester.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.liveapitester.scanner.EndpointInfo
import com.liveapitester.scanner.EndpointScanner
import com.liveapitester.ui.LiveApiTesterToolWindowFactory
import javax.swing.JList
import javax.swing.ListSelectionModel

class ScanEndpointsAction : AnAction("Scan API Endpoints", "Scan project for REST endpoints", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Scanning API Endpoints...") {
            var endpoints: List<EndpointInfo> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Scanning for Spring Boot and JAX-RS endpoints..."
                endpoints = EndpointScanner(project).scanProject()
            }

            override fun onSuccess() {
                if (endpoints.isEmpty()) {
                    com.intellij.openapi.ui.Messages.showInfoMessage(
                        project,
                        "No REST endpoints found in the project.\n\nMake sure you have Spring Boot or JAX-RS annotations in your code.",
                        "No Endpoints Found"
                    )
                    return
                }

                showEndpointPickerPopup(e, endpoints)
            }
        })
    }

    private fun showEndpointPickerPopup(e: AnActionEvent, endpoints: List<EndpointInfo>) {
        val project = e.project ?: return

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(endpoints)
            .setTitle("REST Endpoints (${endpoints.size} found)")
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setRenderer(object : ColoredListCellRenderer<EndpointInfo>() {
                override fun customizeCellRenderer(
                    list: JList<out EndpointInfo>,
                    value: EndpointInfo,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean
                ) {
                    val methodColor = when (value.httpMethod.name) {
                        "GET" -> com.intellij.ui.JBColor(0x0099CC, 0x4DABCC)
                        "POST" -> com.intellij.ui.JBColor(0x00AA44, 0x33CC66)
                        "PUT" -> com.intellij.ui.JBColor(0xFF8800, 0xFFAA33)
                        "DELETE" -> com.intellij.ui.JBColor(0xCC2200, 0xFF4444)
                        "PATCH" -> com.intellij.ui.JBColor(0x8844AA, 0xAA66CC)
                        else -> com.intellij.ui.JBColor(0x666666, 0x888888)
                    }
                    append("[${value.httpMethod}] ", SimpleTextAttributes(
                        SimpleTextAttributes.STYLE_BOLD,
                        methodColor
                    ))
                    append(value.path, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("  ${value.className}#${value.methodName}",
                        SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                }
            })
            .setItemChosenCallback { endpoint ->
                loadEndpointIntoToolWindow(project, endpoint)
            }
            .createPopup()

        val component = e.inputEvent?.component
        if (component != null) {
            popup.showUnderneathOf(component)
        } else {
            popup.showInFocusCenter()
        }
    }

    private fun loadEndpointIntoToolWindow(
        project: com.intellij.openapi.project.Project,
        endpoint: EndpointInfo
    ) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("LiveApiTester") ?: return
        toolWindow.show()

        val contentManager = toolWindow.contentManager
        val content = contentManager.getContent(0) ?: return
        val component = content.component
        if (component is LiveApiTesterToolWindowFactory.LiveApiTesterPanel) {
            component.loadEndpoint(endpoint)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}

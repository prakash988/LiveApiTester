package com.liveapitester.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.liveapitester.debugger.DebuggerService
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

class DebugToolbarPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val statusLabel = JBLabel("No active debug session")
    private val stepOverBtn = JButton("Step Over")
    private val stepIntoBtn = JButton("Step Into")
    private val resumeBtn = JButton("Resume")
    private val stopBtn = JButton("Stop")
    private val goToSourceBtn = JButton("Go to Source")

    private var currentFilePath: String? = null
    private var currentLineNumber: Int = -1

    init {
        setupUI()
        setDebugActive(false, null, -1)
    }

    private fun setupUI() {
        border = BorderFactory.createTitledBorder("Debug Controls")

        stepOverBtn.icon = AllIcons.Actions.TraceOver
        stepIntoBtn.icon = AllIcons.Actions.TraceInto
        resumeBtn.icon = AllIcons.Actions.Resume
        stopBtn.icon = AllIcons.Actions.Suspend
        goToSourceBtn.icon = AllIcons.Actions.EditSource

        stepOverBtn.toolTipText = "Step Over (F8)"
        stepIntoBtn.toolTipText = "Step Into (F7)"
        resumeBtn.toolTipText = "Resume Program (F9)"
        stopBtn.toolTipText = "Stop Debug Session"
        goToSourceBtn.toolTipText = "Navigate to current breakpoint in editor"

        val debuggerService = project.getService(DebuggerService::class.java)

        stepOverBtn.addActionListener { debuggerService?.stepOver() }
        stepIntoBtn.addActionListener { debuggerService?.stepInto() }
        resumeBtn.addActionListener { debuggerService?.resume() }
        stopBtn.addActionListener {
            debuggerService?.stopDebugSession()
            setDebugActive(false, null, -1)
        }
        goToSourceBtn.addActionListener { navigateToSource() }

        val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
        buttonsPanel.add(stepOverBtn)
        buttonsPanel.add(stepIntoBtn)
        buttonsPanel.add(resumeBtn)
        buttonsPanel.add(stopBtn)
        buttonsPanel.add(JSeparator(SwingConstants.VERTICAL))
        buttonsPanel.add(goToSourceBtn)

        statusLabel.foreground = JBColor(java.awt.Color(0x007ACC), java.awt.Color(0x4DC4FF))

        add(statusLabel, BorderLayout.NORTH)
        add(buttonsPanel, BorderLayout.CENTER)
    }

    fun setDebugActive(active: Boolean, filePath: String?, lineNumber: Int) {
        currentFilePath = filePath
        currentLineNumber = lineNumber

        isVisible = active
        stepOverBtn.isEnabled = active
        stepIntoBtn.isEnabled = active
        resumeBtn.isEnabled = active
        stopBtn.isEnabled = active
        goToSourceBtn.isEnabled = active && filePath != null

        if (active && filePath != null && lineNumber >= 0) {
            statusLabel.text = "⏸ Paused at ${filePath.substringAfterLast("/")}:${lineNumber + 1}"
        } else if (active) {
            statusLabel.text = "🐛 Debug session active"
        } else {
            statusLabel.text = "No active debug session"
        }
    }

    fun updateStatus(text: String) {
        statusLabel.text = text
    }

    private fun navigateToSource() {
        val path = currentFilePath ?: return
        val line = currentLineNumber
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(path) ?: return
        OpenFileDescriptor(project, virtualFile, line, 0).navigate(true)
    }
}

package com.liveapitester.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.liveapitester.ai.AiService
import com.liveapitester.debugger.DebuggerService
import com.liveapitester.http.ApiRequest
import com.liveapitester.http.ApiResponse
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.table.DefaultTableModel

class ResponsePanel(private val project: Project? = null) : JPanel(BorderLayout()) {

    private val statusLabel = JBLabel("—")
    private val timeLabel = JBLabel("")
    private val sizeLabel = JBLabel("")
    private val bodyArea = JTextArea()
    private val headersTableModel = DefaultTableModel(arrayOf("Header", "Value"), 0)
    private val tabbedPane = JBTabbedPane()
    private val aiService = AiService()

    private var currentRequest: ApiRequest? = null
    private var currentResponse: ApiResponse? = null

    // Debug-related
    private val aiDebugBtn = JButton("🤖 AI Debug Analysis")
    private val debugInfoPanel = JPanel(BorderLayout())
    private val debugInfoLabel = JBLabel("")
    private var showAuthHeaders = false

    init {
        setupUI()
    }

    private fun setupUI() {
        // Status bar at the top
        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        statusPanel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border())

        statusPanel.add(JBLabel("Status:"))
        statusLabel.font = statusLabel.font.deriveFont(Font.BOLD)
        statusPanel.add(statusLabel)

        statusPanel.add(Box.createHorizontalStrut(16))
        statusPanel.add(JBLabel("Time:"))
        timeLabel.font = timeLabel.font.deriveFont(Font.BOLD)
        statusPanel.add(timeLabel)

        statusPanel.add(Box.createHorizontalStrut(16))
        statusPanel.add(JBLabel("Size:"))
        statusPanel.add(sizeLabel)

        // Body tab
        bodyArea.isEditable = false
        bodyArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        bodyArea.lineWrap = false
        tabbedPane.addTab("Body", JBScrollPane(bodyArea))

        // Headers tab — with auth masking toggle
        val headersTable = com.intellij.ui.table.JBTable(headersTableModel)
        headersTable.isEnabled = false
        val headersPanel = JPanel(BorderLayout())
        val toggleAuthBtn = JButton("👁 Show Auth")
        toggleAuthBtn.toolTipText = "Toggle visibility of Authorization header values"
        toggleAuthBtn.addActionListener {
            showAuthHeaders = !showAuthHeaders
            toggleAuthBtn.text = if (showAuthHeaders) "🙈 Hide Auth" else "👁 Show Auth"
            val req = currentRequest
            val resp = currentResponse
            if (req != null && resp != null) showResponse(req, resp)
        }
        val headersBtnPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2))
        headersBtnPanel.add(toggleAuthBtn)
        headersPanel.add(JBScrollPane(headersTable), BorderLayout.CENTER)
        headersPanel.add(headersBtnPanel, BorderLayout.SOUTH)
        tabbedPane.addTab("Headers", headersPanel)

        // AI action buttons
        val aiPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4))
        val copyBtn = JButton("Copy")
        val explainBtn = JButton("🤖 Explain Error")
        val suggestBtn = JButton("🤖 Suggest Tests")
        val generateBtn = JButton("🤖 Generate Body")

        copyBtn.toolTipText = "Copy response body to clipboard"
        explainBtn.toolTipText = "AI explains the error response"
        suggestBtn.toolTipText = "AI suggests test cases"
        generateBtn.toolTipText = "AI generates a sample request body"

        aiDebugBtn.toolTipText = "AI analyzes current debug session state (active when debugger is paused)"
        aiDebugBtn.isEnabled = false

        copyBtn.addActionListener { copyResponse() }
        explainBtn.addActionListener { explainError() }
        suggestBtn.addActionListener { suggestTests() }
        generateBtn.addActionListener { generateBody() }
        aiDebugBtn.addActionListener { analyzeDebugState() }

        aiPanel.add(copyBtn)
        aiPanel.add(explainBtn)
        aiPanel.add(suggestBtn)
        aiPanel.add(generateBtn)
        aiPanel.add(aiDebugBtn)

        // Debug info section
        debugInfoPanel.isVisible = false
        debugInfoPanel.border = BorderFactory.createTitledBorder("Debug Info")
        debugInfoLabel.font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        debugInfoPanel.add(JBScrollPane(debugInfoLabel), BorderLayout.CENTER)
        debugInfoPanel.preferredSize = Dimension(debugInfoPanel.preferredSize.width, 80)

        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.add(tabbedPane, BorderLayout.CENTER)
        bottomPanel.add(aiPanel, BorderLayout.SOUTH)

        add(statusPanel, BorderLayout.NORTH)
        add(bottomPanel, BorderLayout.CENTER)
        add(debugInfoPanel, BorderLayout.SOUTH)

        showEmpty()
    }

    fun showEmpty() {
        statusLabel.text = "—"
        statusLabel.foreground = JBColor.foreground()
        timeLabel.text = ""
        timeLabel.foreground = JBColor.foreground()
        sizeLabel.text = ""
        bodyArea.text = ""
        headersTableModel.rowCount = 0
        aiDebugBtn.isEnabled = false
        debugInfoPanel.isVisible = false
    }

    fun showLoading() {
        statusLabel.text = "Sending..."
        statusLabel.foreground = JBColor.foreground()
        timeLabel.text = ""
        timeLabel.foreground = JBColor.foreground()
        sizeLabel.text = ""
        bodyArea.text = ""
        headersTableModel.rowCount = 0
    }

    fun showStatusMessage(message: String) {
        statusLabel.text = message
        statusLabel.foreground = JBColor(Color(0xFF8800), Color(0xFFAA33))
    }

    fun showResponse(request: ApiRequest, response: ApiResponse) {
        currentRequest = request
        currentResponse = response

        val (statusColor, statusText) = when {
            response.statusCode == 0 -> Pair(JBColor(Color(0xCC2200), Color(0xFF5555)), "ERROR")
            response.statusCode in 200..299 -> Pair(JBColor(Color(0x009944), Color(0x33AA66)), "${response.statusCode} ${response.statusText}")
            response.statusCode in 300..399 -> Pair(JBColor(Color(0xFF8800), Color(0xFFAA33)), "${response.statusCode} ${response.statusText}")
            response.statusCode in 400..499 -> Pair(JBColor(Color(0xCC2200), Color(0xFF5555)), "${response.statusCode} ${response.statusText}")
            response.statusCode >= 500 -> Pair(JBColor(Color(0x990000), Color(0xDD3333)), "${response.statusCode} ${response.statusText}")
            else -> Pair(JBColor.foreground(), "${response.statusCode} ${response.statusText}")
        }

        statusLabel.text = statusText
        statusLabel.foreground = statusColor

        // Color-code response time
        val (timeColor, timeText) = when {
            response.responseTimeMs < 200 -> Pair(JBColor(Color(0x009944), Color(0x33AA66)), "${response.responseTimeMs}ms")
            response.responseTimeMs < 1000 -> Pair(JBColor(Color(0xFF8800), Color(0xFFAA33)), "${response.responseTimeMs}ms")
            else -> Pair(JBColor(Color(0xCC2200), Color(0xFF5555)), "${response.responseTimeMs}ms")
        }
        timeLabel.text = timeText
        timeLabel.foreground = timeColor

        sizeLabel.text = formatSize(response.responseSizeBytes)

        bodyArea.text = prettyPrint(response.body, response.headers["Content-Type"] ?: "")
        bodyArea.caretPosition = 0

        // Show headers with optional auth masking
        headersTableModel.rowCount = 0
        response.headers.forEach { (k, v) ->
            val displayValue = if (!showAuthHeaders && k.lowercase() == "authorization") {
                "${v.take(10)}...[hidden]"
            } else {
                v
            }
            headersTableModel.addRow(arrayOf(k, displayValue))
        }

        // Enable AI debug button if a debug session is active
        val debugActive = project?.let {
            try {
                project.getService(DebuggerService::class.java)?.isDebugSessionActive() == true
            } catch (e: Exception) { false }
        } ?: false
        aiDebugBtn.isEnabled = debugActive
    }

    fun showDebugInfo(breakpointLocation: String, variables: Map<String, String>) {
        val sb = StringBuilder()
        if (breakpointLocation.isNotBlank()) {
            sb.appendLine("📍 Breakpoint: $breakpointLocation")
        }
        if (variables.isNotEmpty()) {
            sb.appendLine("Variables:")
            variables.entries.take(10).forEach { (k, v) ->
                sb.appendLine("  $k = $v")
            }
        }
        debugInfoLabel.text = "<html><pre>${sb.toString().replace("<", "&lt;")}</pre></html>"
        debugInfoPanel.isVisible = true
        aiDebugBtn.isEnabled = true
        revalidate()
        repaint()
    }

    fun hideDebugInfo() {
        debugInfoPanel.isVisible = false
        aiDebugBtn.isEnabled = false
    }

    private fun prettyPrint(body: String, contentType: String): String {
        if (body.isBlank()) return ""

        return when {
            contentType.contains("json") || body.trimStart().startsWith("{") || body.trimStart().startsWith("[") -> {
                tryPrettyPrintJson(body)
            }
            contentType.contains("xml") || body.trimStart().startsWith("<") -> {
                body
            }
            else -> body
        }
    }

    private fun tryPrettyPrintJson(json: String): String {
        return try {
            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            val element = com.google.gson.JsonParser.parseString(json)
            gson.toJson(element)
        } catch (e: Exception) {
            json
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    private fun copyResponse() {
        val text = bodyArea.text
        if (text.isNotBlank()) {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
        }
    }

    private fun explainError() {
        val req = currentRequest ?: return
        val resp = currentResponse ?: return

        if (resp.statusCode in 400..599 || resp.statusCode == 0) {
            runAiAction("Explain Error") { aiService.explainError(req, resp) }
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Explain Error is available for 4xx and 5xx responses.",
                "Not an Error Response",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun suggestTests() {
        val req = currentRequest ?: return
        val resp = currentResponse ?: return
        runAiAction("Suggested Tests") { aiService.suggestTests(req, resp) }
    }

    private fun generateBody() {
        val req = currentRequest ?: return
        runAiAction("Generated Request Body") {
            aiService.generateRequestBody(req.url, req.method.name, "")
        }
    }

    private fun analyzeDebugState() {
        val req = currentRequest ?: return
        val resp = currentResponse

        val debuggerService = project?.getService(DebuggerService::class.java)
        val sessionInfo = debuggerService?.getCurrentSessionInfo() ?: emptyMap()
        val variables = sessionInfo
        val stackTrace = sessionInfo["stackTrace"] ?: ""
        val location = "${sessionInfo["sessionName"] ?: ""}"

        runAiAction("AI Debug Analysis") {
            aiService.analyzeDebugState(
                request = req,
                response = resp,
                stackTrace = stackTrace,
                variables = variables,
                sourceContext = location
            )
        }
    }

    private fun runAiAction(title: String, action: () -> String) {
        val loadingDialog = AiLoadingDialog(title)
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = try {
                action()
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            ApplicationManager.getApplication().invokeLater {
                loadingDialog.close(DialogWrapper.OK_EXIT_CODE)
                showAiResultDialog(title, result)
            }
        }
        loadingDialog.show()
    }

    private fun showAiResultDialog(title: String, content: String) {
        val dialog = object : DialogWrapper(true) {
            init {
                this.title = "🤖 $title"
                init()
            }

            override fun createCenterPanel(): JComponent {
                val textArea = JTextArea(content)
                textArea.isEditable = false
                textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                textArea.lineWrap = true
                textArea.wrapStyleWord = true
                val scrollPane = JBScrollPane(textArea)
                scrollPane.preferredSize = Dimension(700, 500)
                return scrollPane
            }
        }
        dialog.show()
    }

    private inner class AiLoadingDialog(title: String) : DialogWrapper(true) {
        init {
            this.title = "🤖 $title"
            init()
            isModal = false
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout())
            panel.border = BorderFactory.createEmptyBorder(20, 40, 20, 40)
            val label = JBLabel("Asking AI...")
            label.horizontalAlignment = SwingConstants.CENTER
            panel.add(label, BorderLayout.CENTER)
            panel.preferredSize = Dimension(300, 100)
            return panel
        }
    }
}

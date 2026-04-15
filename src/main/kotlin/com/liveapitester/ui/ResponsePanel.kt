package com.liveapitester.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.liveapitester.ai.AiService
import com.liveapitester.http.ApiRequest
import com.liveapitester.http.ApiResponse
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.table.DefaultTableModel

class ResponsePanel : JPanel(BorderLayout()) {

    private val statusLabel = JBLabel("—")
    private val timeLabel = JBLabel("")
    private val sizeLabel = JBLabel("")
    private val bodyArea = JTextArea()
    private val headersTableModel = DefaultTableModel(arrayOf("Header", "Value"), 0)
    private val tabbedPane = JBTabbedPane()

    private var currentRequest: ApiRequest? = null
    private var currentResponse: ApiResponse? = null

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
        statusPanel.add(timeLabel)

        statusPanel.add(Box.createHorizontalStrut(16))
        statusPanel.add(JBLabel("Size:"))
        statusPanel.add(sizeLabel)

        // Body tab
        bodyArea.isEditable = false
        bodyArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        bodyArea.lineWrap = false
        tabbedPane.addTab("Body", JBScrollPane(bodyArea))

        // Headers tab
        val headersTable = com.intellij.ui.table.JBTable(headersTableModel)
        headersTable.isEnabled = false
        tabbedPane.addTab("Headers", JBScrollPane(headersTable))

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

        copyBtn.addActionListener { copyResponse() }
        explainBtn.addActionListener { explainError() }
        suggestBtn.addActionListener { suggestTests() }
        generateBtn.addActionListener { generateBody() }

        aiPanel.add(copyBtn)
        aiPanel.add(explainBtn)
        aiPanel.add(suggestBtn)
        aiPanel.add(generateBtn)

        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.add(tabbedPane, BorderLayout.CENTER)
        bottomPanel.add(aiPanel, BorderLayout.SOUTH)

        add(statusPanel, BorderLayout.NORTH)
        add(bottomPanel, BorderLayout.CENTER)

        showEmpty()
    }

    fun showEmpty() {
        statusLabel.text = "—"
        statusLabel.foreground = JBColor.foreground()
        timeLabel.text = ""
        sizeLabel.text = ""
        bodyArea.text = ""
        headersTableModel.rowCount = 0
    }

    fun showLoading() {
        statusLabel.text = "Sending..."
        statusLabel.foreground = JBColor.foreground()
        timeLabel.text = ""
        sizeLabel.text = ""
        bodyArea.text = ""
        headersTableModel.rowCount = 0
    }

    fun showResponse(request: ApiRequest, response: ApiResponse) {
        currentRequest = request
        currentResponse = response

        // Status badge coloring
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
        timeLabel.text = "${response.responseTimeMs}ms"
        sizeLabel.text = formatSize(response.responseSizeBytes)

        // Pretty print response body
        bodyArea.text = prettyPrint(response.body, response.headers["Content-Type"] ?: "")
        bodyArea.caretPosition = 0

        // Headers
        headersTableModel.rowCount = 0
        response.headers.forEach { (k, v) -> headersTableModel.addRow(arrayOf(k, v)) }
    }

    private fun prettyPrint(body: String, contentType: String): String {
        if (body.isBlank()) return ""

        return when {
            contentType.contains("json") || body.trimStart().startsWith("{") || body.trimStart().startsWith("[") -> {
                tryPrettyPrintJson(body)
            }
            contentType.contains("xml") || body.trimStart().startsWith("<") -> {
                body // Keep as-is for XML for now
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
            runAiAction("Explain Error") { AiService().explainError(req, resp) }
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
        runAiAction("Suggested Tests") { AiService().suggestTests(req, resp) }
    }

    private fun generateBody() {
        val req = currentRequest ?: return
        runAiAction("Generated Request Body") {
            AiService().generateRequestBody(req.url, req.method.name, "")
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

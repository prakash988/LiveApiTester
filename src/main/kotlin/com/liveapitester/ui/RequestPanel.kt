package com.liveapitester.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.liveapitester.http.ApiRequest
import com.liveapitester.http.HttpMethod
import com.liveapitester.scanner.EndpointInfo
import java.awt.*
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.table.DefaultTableModel

class RequestPanel(
    private val project: Project,
    private val onSend: (ApiRequest) -> Unit
) : JPanel(BorderLayout()) {

    private val urlField = JBTextField("https://api.example.com/endpoint")
    private val methodCombo = JComboBox(HttpMethod.values())
    private val sendButton = JButton("Send")

    // Params tab
    private val paramsTableModel = DefaultTableModel(arrayOf("Key", "Value"), 0)
    private val paramsTable = JBTable(paramsTableModel)

    // Headers tab
    private val headersTableModel = DefaultTableModel(arrayOf("Key", "Value"), 0)
    private val headersTable = JBTable(headersTableModel)

    // Body tab
    private val bodyArea = JTextArea()
    private val contentTypeCombo = JComboBox(arrayOf(
        "application/json", "application/xml", "application/x-www-form-urlencoded",
        "text/plain", "none"
    ))

    // Auth tab
    private val authTypeCombo = JComboBox(arrayOf("None", "Bearer Token", "Basic Auth", "API Key"))
    private val authPanel = JPanel(CardLayout())
    private val bearerTokenField = JBTextField()
    private val basicUsernameField = JBTextField()
    private val basicPasswordField = JPasswordField()
    private val apiKeyNameField = JBTextField()
    private val apiKeyValueField = JBTextField()
    private val apiKeyAddToCombo = JComboBox(arrayOf("Header", "Query Param"))

    init {
        setupUI()
    }

    private fun setupUI() {
        // Top bar: method + URL + send button
        val topPanel = JPanel(BorderLayout(4, 0))
        topPanel.border = BorderFactory.createEmptyBorder(6, 6, 6, 6)

        methodCombo.preferredSize = Dimension(110, methodCombo.preferredSize.height)
        methodCombo.addActionListener { updateMethodColor() }

        sendButton.background = JBColor(Color(0x007ACC), Color(0x3592C4))
        sendButton.foreground = Color.WHITE
        sendButton.font = sendButton.font.deriveFont(Font.BOLD)
        sendButton.isFocusPainted = false
        sendButton.addActionListener { sendRequest() }

        val methodUrlPanel = JPanel(BorderLayout(4, 0))
        methodUrlPanel.add(methodCombo, BorderLayout.WEST)
        methodUrlPanel.add(urlField, BorderLayout.CENTER)

        topPanel.add(methodUrlPanel, BorderLayout.CENTER)
        topPanel.add(sendButton, BorderLayout.EAST)

        // Tabbed request options
        val tabbedPane = JBTabbedPane()
        tabbedPane.addTab("Params", createParamsTab())
        tabbedPane.addTab("Headers", createHeadersTab())
        tabbedPane.addTab("Body", createBodyTab())
        tabbedPane.addTab("Auth", createAuthTab())

        add(topPanel, BorderLayout.NORTH)
        add(tabbedPane, BorderLayout.CENTER)

        updateMethodColor()
    }

    private fun updateMethodColor() {
        val method = methodCombo.selectedItem as? HttpMethod
        val color = when (method) {
            HttpMethod.GET -> JBColor(Color(0x0099CC), Color(0x4DABCC))
            HttpMethod.POST -> JBColor(Color(0x009944), Color(0x33AA66))
            HttpMethod.PUT -> JBColor(Color(0xFF8800), Color(0xFFAA33))
            HttpMethod.DELETE -> JBColor(Color(0xCC2200), Color(0xFF4444))
            HttpMethod.PATCH -> JBColor(Color(0x8844AA), Color(0xAA66CC))
            else -> JBColor.foreground()
        }
        methodCombo.foreground = color
    }

    private fun createParamsTab(): JComponent {
        val panel = JPanel(BorderLayout())
        val decorator = ToolbarDecorator.createDecorator(paramsTable)
            .setAddAction { paramsTableModel.addRow(arrayOf("", "")) }
            .setRemoveAction {
                val row = paramsTable.selectedRow
                if (row >= 0) paramsTableModel.removeRow(row)
            }
        panel.add(decorator.createPanel(), BorderLayout.CENTER)
        return panel
    }

    private fun createHeadersTab(): JComponent {
        val panel = JPanel(BorderLayout())
        val decorator = ToolbarDecorator.createDecorator(headersTable)
            .setAddAction { headersTableModel.addRow(arrayOf("", "")) }
            .setRemoveAction {
                val row = headersTable.selectedRow
                if (row >= 0) headersTableModel.removeRow(row)
            }
        panel.add(decorator.createPanel(), BorderLayout.CENTER)
        return panel
    }

    private fun createBodyTab(): JComponent {
        val panel = JPanel(BorderLayout(0, 4))
        panel.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        val topBar = JPanel(FlowLayout(FlowLayout.LEFT))
        topBar.add(JBLabel("Content-Type:"))
        topBar.add(contentTypeCombo)

        bodyArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        bodyArea.lineWrap = false
        bodyArea.text = ""

        panel.add(topBar, BorderLayout.NORTH)
        panel.add(JBScrollPane(bodyArea), BorderLayout.CENTER)
        return panel
    }

    private fun createAuthTab(): JComponent {
        val panel = JPanel(BorderLayout(0, 4))
        panel.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        // Auth type selector
        val typePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        typePanel.add(JBLabel("Auth Type:"))
        typePanel.add(authTypeCombo)

        // Auth fields card panel
        val nonePanel = JPanel()
        nonePanel.add(JBLabel("No authentication"))

        val bearerPanel = buildFormPanel(listOf("Token:" to bearerTokenField))
        val basicPanel = buildFormPanel(listOf("Username:" to basicUsernameField, "Password:" to basicPasswordField))
        val apiKeyPanel = buildFormPanel(listOf("Key:" to apiKeyNameField, "Value:" to apiKeyValueField)) {
            add(JBLabel("Add To:"), GridBagConstraints().apply {
                gridx = 0; gridy = 2; anchor = GridBagConstraints.WEST
                insets = Insets(4, 0, 4, 8)
            })
            add(apiKeyAddToCombo, GridBagConstraints().apply {
                gridx = 1; gridy = 2; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
                insets = Insets(4, 0, 4, 0)
            })
        }

        authPanel.add(nonePanel, "None")
        authPanel.add(bearerPanel, "Bearer Token")
        authPanel.add(basicPanel, "Basic Auth")
        authPanel.add(apiKeyPanel, "API Key")

        authTypeCombo.addActionListener {
            val cardLayout = authPanel.layout as CardLayout
            cardLayout.show(authPanel, authTypeCombo.selectedItem.toString())
        }

        panel.add(typePanel, BorderLayout.NORTH)
        panel.add(authPanel, BorderLayout.CENTER)
        return panel
    }

    private fun buildFormPanel(
        fields: List<Pair<String, JComponent>>,
        extraSetup: JPanel.() -> Unit = {}
    ): JPanel {
        val panel = JPanel(GridBagLayout())
        val gc = GridBagConstraints()
        fields.forEachIndexed { index, (label, component) ->
            gc.gridx = 0; gc.gridy = index
            gc.anchor = GridBagConstraints.WEST
            gc.insets = Insets(4, 0, 4, 8)
            gc.fill = GridBagConstraints.NONE; gc.weightx = 0.0
            panel.add(JBLabel(label), gc)

            gc.gridx = 1; gc.gridy = index
            gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0
            gc.insets = Insets(4, 0, 4, 0)
            panel.add(component, gc)
        }
        panel.extraSetup()
        return panel
    }

    private fun sendRequest() {
        val request = buildRequest()
        onSend(request)
    }

    private fun buildRequest(): ApiRequest {
        val queryParams = mutableMapOf<String, String>()
        for (i in 0 until paramsTableModel.rowCount) {
            val key = paramsTableModel.getValueAt(i, 0)?.toString()?.trim() ?: ""
            val value = paramsTableModel.getValueAt(i, 1)?.toString()?.trim() ?: ""
            if (key.isNotBlank()) queryParams[key] = value
        }

        val headers = mutableMapOf<String, String>()
        for (i in 0 until headersTableModel.rowCount) {
            val key = headersTableModel.getValueAt(i, 0)?.toString()?.trim() ?: ""
            val value = headersTableModel.getValueAt(i, 1)?.toString()?.trim() ?: ""
            if (key.isNotBlank()) headers[key] = value
        }

        val authType = authTypeCombo.selectedItem?.toString() ?: "None"
        val authCredentials = mutableMapOf<String, String>()
        when (authType) {
            "Bearer Token" -> authCredentials["token"] = bearerTokenField.text.trim()
            "Basic Auth" -> {
                authCredentials["username"] = basicUsernameField.text.trim()
                authCredentials["password"] = String(basicPasswordField.password)
            }
            "API Key" -> {
                authCredentials["key"] = apiKeyNameField.text.trim()
                authCredentials["value"] = apiKeyValueField.text.trim()
                authCredentials["addTo"] = apiKeyAddToCombo.selectedItem?.toString() ?: "Header"
            }
        }

        val contentType = contentTypeCombo.selectedItem?.toString() ?: "application/json"
        val body = if (contentType == "none") null else bodyArea.text.ifBlank { null }

        return ApiRequest(
            method = methodCombo.selectedItem as HttpMethod,
            url = urlField.text.trim(),
            headers = headers,
            queryParams = queryParams,
            body = body,
            authType = authType,
            authCredentials = authCredentials,
            contentType = contentType
        )
    }

    fun loadRequest(request: ApiRequest) {
        urlField.text = request.url
        methodCombo.selectedItem = request.method
        updateMethodColor()

        paramsTableModel.rowCount = 0
        request.queryParams.forEach { (k, v) -> paramsTableModel.addRow(arrayOf(k, v)) }

        headersTableModel.rowCount = 0
        request.headers.forEach { (k, v) -> headersTableModel.addRow(arrayOf(k, v)) }

        bodyArea.text = request.body ?: ""
        contentTypeCombo.selectedItem = request.contentType

        authTypeCombo.selectedItem = request.authType
        bearerTokenField.text = request.authCredentials["token"] ?: ""
        basicUsernameField.text = request.authCredentials["username"] ?: ""
        basicPasswordField.text = request.authCredentials["password"] ?: ""
        apiKeyNameField.text = request.authCredentials["key"] ?: ""
        apiKeyValueField.text = request.authCredentials["value"] ?: ""
        apiKeyAddToCombo.selectedItem = request.authCredentials["addTo"] ?: "Header"
    }

    fun loadEndpoint(endpoint: EndpointInfo) {
        methodCombo.selectedItem = endpoint.httpMethod
        updateMethodColor()
        val baseUrl = com.liveapitester.environment.EnvironmentManager.getInstance(project)
            .getActiveVariables()["base_url"] ?: "http://localhost:8080"
        urlField.text = "$baseUrl${endpoint.path}"
    }

    fun setSendButtonEnabled(enabled: Boolean) {
        sendButton.isEnabled = enabled
        sendButton.text = if (enabled) "Send" else "Sending..."
    }
}

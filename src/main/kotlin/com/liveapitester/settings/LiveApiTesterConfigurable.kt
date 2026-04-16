package com.liveapitester.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.liveapitester.ai.AiService
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class LiveApiTesterConfigurable : Configurable {

    private var aiEndpointField: JBTextField? = null
    private var aiModelCombo: JComboBox<String>? = null
    private var apiKeyField: JBPasswordField? = null
    private var timeoutField: JSpinner? = null
    private var followRedirectsCheck: JBCheckBox? = null
    private var sslTrustAllCheck: JBCheckBox? = null
    private var maxHistoryField: JSpinner? = null

    override fun getDisplayName(): String = "LiveApiTester"

    override fun createComponent(): JComponent {
        val settings = LiveApiTesterSettings.getInstance()

        val panel = JPanel(GridBagLayout())
        val gc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 8, 4, 8)
        }

        var row = 0

        fun addRow(label: String, component: JComponent) {
            gc.gridx = 0; gc.gridy = row; gc.weightx = 0.3
            panel.add(JBLabel(label), gc)
            gc.gridx = 1; gc.gridy = row; gc.weightx = 0.7
            panel.add(component, gc)
            row++
        }

        // Section: AI Configuration
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2; gc.weightx = 1.0
        panel.add(JSeparator(), gc)
        gc.gridwidth = 1
        row++

        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2
        panel.add(JBLabel("<html><b>AI Configuration (GitHub Models)</b></html>"), gc)
        gc.gridwidth = 1
        row++

        aiEndpointField = JBTextField(settings.aiEndpoint)
        addRow("GitHub Models API Endpoint:", aiEndpointField!!)

        aiModelCombo = JComboBox(LiveApiTesterSettings.AVAILABLE_MODELS.toTypedArray())
        aiModelCombo!!.isEditable = true
        val currentModel = settings.aiModel
        if (LiveApiTesterSettings.AVAILABLE_MODELS.contains(currentModel)) {
            aiModelCombo!!.selectedItem = currentModel
        } else {
            aiModelCombo!!.selectedItem = currentModel
        }
        addRow("Model:", aiModelCombo!!)

        apiKeyField = JBPasswordField()
        addRow("GitHub Personal Access Token (PAT):", apiKeyField!!)

        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2
        panel.add(JBLabel("<html><i>PAT is stored securely in IntelliJ's PasswordSafe.<br>" +
            "Get your PAT from github.com/settings/tokens with 'models' or 'copilot' scope.</i></html>"), gc)
        gc.gridwidth = 1
        row++

        // Section: HTTP Configuration
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2; gc.weightx = 1.0
        panel.add(JSeparator(), gc)
        gc.gridwidth = 1
        row++

        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2
        panel.add(JBLabel("<html><b>HTTP Configuration</b></html>"), gc)
        gc.gridwidth = 1
        row++

        timeoutField = JSpinner(SpinnerNumberModel(settings.timeoutSeconds, 1, 300, 1))
        addRow("Timeout (seconds):", timeoutField!!)

        maxHistoryField = JSpinner(SpinnerNumberModel(settings.maxHistory, 10, 1000, 10))
        addRow("Max History Entries:", maxHistoryField!!)

        followRedirectsCheck = JBCheckBox("Follow Redirects", settings.followRedirects)
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2
        panel.add(followRedirectsCheck!!, gc)
        gc.gridwidth = 1
        row++

        sslTrustAllCheck = JBCheckBox("Trust All SSL Certificates (⚠ Dev Only)", settings.sslTrustAll)
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2
        panel.add(sslTrustAllCheck!!, gc)
        gc.gridwidth = 1
        row++

        // Filler
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2; gc.weighty = 1.0
        panel.add(JPanel(), gc)

        return panel
    }

    override fun isModified(): Boolean {
        val settings = LiveApiTesterSettings.getInstance()
        return aiEndpointField?.text != settings.aiEndpoint
                || aiModelCombo?.selectedItem?.toString() != settings.aiModel
                || (timeoutField?.value as? Int) != settings.timeoutSeconds
                || (maxHistoryField?.value as? Int) != settings.maxHistory
                || followRedirectsCheck?.isSelected != settings.followRedirects
                || sslTrustAllCheck?.isSelected != settings.sslTrustAll
                || apiKeyField?.password?.isNotEmpty() == true
    }

    override fun apply() {
        val settings = LiveApiTesterSettings.getInstance()
        settings.aiEndpoint = aiEndpointField?.text?.trim() ?: settings.aiEndpoint
        settings.aiModel = aiModelCombo?.selectedItem?.toString()?.trim() ?: settings.aiModel
        settings.timeoutSeconds = (timeoutField?.value as? Int) ?: settings.timeoutSeconds
        settings.maxHistory = (maxHistoryField?.value as? Int) ?: settings.maxHistory
        settings.followRedirects = followRedirectsCheck?.isSelected ?: settings.followRedirects
        settings.sslTrustAll = sslTrustAllCheck?.isSelected ?: settings.sslTrustAll

        val apiKey = apiKeyField?.password?.let { String(it) } ?: ""
        if (apiKey.isNotBlank()) {
            AiService.saveApiKey(apiKey)
            apiKeyField?.text = ""
        }
    }

    override fun reset() {
        val settings = LiveApiTesterSettings.getInstance()
        aiEndpointField?.text = settings.aiEndpoint
        aiModelCombo?.selectedItem = settings.aiModel
        timeoutField?.value = settings.timeoutSeconds
        maxHistoryField?.value = settings.maxHistory
        followRedirectsCheck?.isSelected = settings.followRedirects
        sslTrustAllCheck?.isSelected = settings.sslTrustAll
        apiKeyField?.text = ""
    }
}

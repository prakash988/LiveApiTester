package com.liveapitester.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.liveapitester.environment.Environment
import com.liveapitester.environment.EnvironmentManager
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

class EnvironmentPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val envCombo = JComboBox<String>()
    private var environments: List<Environment> = emptyList()

    init {
        setupUI()
        refresh()
    }

    private fun setupUI() {
        val topPanel = JPanel(BorderLayout(4, 4))
        topPanel.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        val labelPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        labelPanel.add(JBLabel("Active Environment:"))

        envCombo.addActionListener {
            val selected = envCombo.selectedItem?.toString()
            if (selected != null) {
                val env = environments.find { it.name == selected }
                if (env != null) {
                    EnvironmentManager.getInstance(project).setActiveEnvironment(env.id)
                }
            }
        }

        val editBtn = JButton(AllIcons.General.Settings)
        editBtn.toolTipText = "Edit Environments"
        editBtn.addActionListener { editEnvironments() }

        val addBtn = JButton(AllIcons.General.Add)
        addBtn.toolTipText = "New Environment"
        addBtn.addActionListener { addEnvironment() }

        labelPanel.add(envCombo)
        labelPanel.add(editBtn)
        labelPanel.add(addBtn)

        topPanel.add(labelPanel, BorderLayout.NORTH)

        // Variables display panel
        val variablesPanel = JPanel(BorderLayout())
        variablesPanel.border = BorderFactory.createTitledBorder("Active Variables (read-only)")

        val varsModel = DefaultTableModel(arrayOf("Variable", "Value"), 0)
        val varsTable = JBTable(varsModel)
        varsTable.isEnabled = false

        envCombo.addActionListener {
            varsModel.rowCount = 0
            val selected = envCombo.selectedItem?.toString()
            val env = environments.find { it.name == selected }
            env?.variables?.forEach { (k, v) -> varsModel.addRow(arrayOf("{{$k}}", v)) }
        }

        variablesPanel.add(JBScrollPane(varsTable), BorderLayout.CENTER)

        add(topPanel, BorderLayout.NORTH)
        add(variablesPanel, BorderLayout.CENTER)
    }

    private fun refresh() {
        val manager = EnvironmentManager.getInstance(project)
        environments = manager.getEnvironments()
        val activeEnv = manager.getActiveEnvironment()

        envCombo.removeAllItems()
        environments.forEach { envCombo.addItem(it.name) }

        if (activeEnv != null) {
            envCombo.selectedItem = activeEnv.name
        }
    }

    private fun addEnvironment() {
        val name = Messages.showInputDialog(
            project,
            "Environment name:",
            "New Environment",
            null
        )
        if (!name.isNullOrBlank()) {
            EnvironmentManager.getInstance(project).addEnvironment(Environment(name = name))
            refresh()
        }
    }

    private fun editEnvironments() {
        val selected = envCombo.selectedItem?.toString()
        val env = environments.find { it.name == selected } ?: run {
            Messages.showInfoMessage(project, "No environment selected.", "Edit Environment")
            return
        }

        val dialog = EnvironmentEditDialog(env)
        if (dialog.showAndGet()) {
            EnvironmentManager.getInstance(project).updateEnvironment(dialog.getUpdatedEnvironment())
            refresh()
        }
    }

    private inner class EnvironmentEditDialog(private val environment: Environment) : DialogWrapper(project) {
        private val tableModel = DefaultTableModel(arrayOf("Variable", "Value"), 0)

        init {
            title = "Edit Environment: ${environment.name}"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val table = JBTable(tableModel)
            environment.variables.forEach { (k, v) -> tableModel.addRow(arrayOf(k, v)) }

            val decorator = ToolbarDecorator.createDecorator(table)
                .setAddAction { tableModel.addRow(arrayOf("", "")) }
                .setRemoveAction {
                    val row = table.selectedRow
                    if (row >= 0) tableModel.removeRow(row)
                }

            val panel = decorator.createPanel()
            panel.preferredSize = Dimension(500, 350)
            return panel
        }

        fun getUpdatedEnvironment(): Environment {
            val variables = mutableMapOf<String, String>()
            for (i in 0 until tableModel.rowCount) {
                val key = tableModel.getValueAt(i, 0)?.toString()?.trim() ?: ""
                val value = tableModel.getValueAt(i, 1)?.toString()?.trim() ?: ""
                if (key.isNotBlank()) variables[key] = value
            }
            return environment.copy(variables = variables)
        }
    }
}

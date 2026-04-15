package com.liveapitester.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.liveapitester.history.HistoryEntry
import com.liveapitester.history.HistoryManager
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

class HistoryPanel(
    private val project: Project,
    private val onSelect: (HistoryEntry) -> Unit
) : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<HistoryEntry>()
    private val list = JList(listModel)
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        setupUI()
        refresh()
    }

    private fun setupUI() {
        list.cellRenderer = HistoryCellRenderer(dateFormat)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    val index = list.locationToIndex(e.point)
                    if (index >= 0) {
                        onSelect(listModel.getElementAt(index))
                    }
                }
            }
        })

        val toolbar = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2))
        val clearBtn = JButton("Clear")
        clearBtn.addActionListener {
            HistoryManager.getInstance(project).clearHistory()
            refresh()
        }
        toolbar.add(clearBtn)

        add(toolbar, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
    }

    fun refresh() {
        listModel.clear()
        HistoryManager.getInstance(project).getEntries().forEach { listModel.addElement(it) }
    }

    private class HistoryCellRenderer(
        private val dateFormat: SimpleDateFormat
    ) : JPanel(BorderLayout(4, 2)), ListCellRenderer<HistoryEntry> {

        private val methodLabel = JBLabel()
        private val urlLabel = JBLabel()
        private val statusLabel = JBLabel()
        private val timeLabel = JBLabel()
        private val timestampLabel = JBLabel()

        init {
            border = BorderFactory.createEmptyBorder(4, 6, 4, 6)

            methodLabel.font = methodLabel.font.deriveFont(Font.BOLD, 10f)
            methodLabel.preferredSize = Dimension(58, 20)
            methodLabel.horizontalAlignment = SwingConstants.CENTER
            methodLabel.isOpaque = true

            statusLabel.font = statusLabel.font.deriveFont(Font.BOLD, 10f)
            statusLabel.preferredSize = Dimension(40, 20)
            statusLabel.horizontalAlignment = SwingConstants.CENTER

            timeLabel.font = timeLabel.font.deriveFont(10f)
            timeLabel.foreground = JBColor.GRAY

            timestampLabel.font = timestampLabel.font.deriveFont(10f)
            timestampLabel.foreground = JBColor.GRAY

            urlLabel.font = urlLabel.font.deriveFont(11f)

            val leftPanel = JPanel(BorderLayout(4, 0))
            leftPanel.isOpaque = false
            leftPanel.add(methodLabel, BorderLayout.WEST)
            leftPanel.add(urlLabel, BorderLayout.CENTER)

            val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
            rightPanel.isOpaque = false
            rightPanel.add(statusLabel)
            rightPanel.add(timeLabel)
            rightPanel.add(timestampLabel)

            add(leftPanel, BorderLayout.CENTER)
            add(rightPanel, BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(
            list: JList<out HistoryEntry>,
            value: HistoryEntry,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val method = value.request.method.name
            methodLabel.text = method

            val (methodBg, methodFg) = when (method) {
                "GET" -> Pair(JBColor(Color(0xE3F2FD), Color(0x1A3A50)), JBColor(Color(0x0099CC), Color(0x4DABCC)))
                "POST" -> Pair(JBColor(Color(0xE8F5E9), Color(0x1A3A25)), JBColor(Color(0x009944), Color(0x33AA66)))
                "PUT" -> Pair(JBColor(Color(0xFFF3E0), Color(0x3A2A10)), JBColor(Color(0xFF8800), Color(0xFFAA33)))
                "DELETE" -> Pair(JBColor(Color(0xFFEBEE), Color(0x3A1A1A)), JBColor(Color(0xCC2200), Color(0xFF5555)))
                "PATCH" -> Pair(JBColor(Color(0xF3E5F5), Color(0x2A1A35)), JBColor(Color(0x8844AA), Color(0xAA66CC)))
                else -> Pair(JBColor.border(), JBColor.foreground())
            }
            methodLabel.background = methodBg
            methodLabel.foreground = methodFg

            val url = value.request.url
            urlLabel.text = if (url.length > 50) url.take(47) + "..." else url
            urlLabel.toolTipText = url

            val (statusColor, statusText) = when {
                value.statusCode == 0 -> Pair(JBColor(Color(0xCC2200), Color(0xFF5555)), "ERR")
                value.statusCode in 200..299 -> Pair(JBColor(Color(0x009944), Color(0x33AA66)), "${value.statusCode}")
                value.statusCode in 300..399 -> Pair(JBColor(Color(0xFF8800), Color(0xFFAA33)), "${value.statusCode}")
                value.statusCode >= 400 -> Pair(JBColor(Color(0xCC2200), Color(0xFF5555)), "${value.statusCode}")
                else -> Pair(JBColor.foreground(), "${value.statusCode}")
            }
            statusLabel.text = statusText
            statusLabel.foreground = statusColor

            timeLabel.text = "${value.responseTimeMs}ms"
            timestampLabel.text = dateFormat.format(Date(value.timestamp))

            background = if (isSelected) list.selectionBackground else list.background
            isOpaque = true
            return this
        }
    }
}

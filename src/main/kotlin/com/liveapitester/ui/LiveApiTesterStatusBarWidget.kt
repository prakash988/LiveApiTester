package com.liveapitester.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

class LiveApiTesterStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = LiveApiTesterStatusBarWidget.ID
    override fun getDisplayName(): String = "LiveApiTester"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = LiveApiTesterStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class LiveApiTesterStatusBarWidget(private val project: Project) : StatusBarWidget,
    StatusBarWidget.TextPresentation {

    companion object {
        const val ID = "LiveApiTesterStatusBar"

        @Volatile
        private var currentText: String = "LiveApiTester: Ready"

        private val instances = java.util.concurrent.CopyOnWriteArrayList<LiveApiTesterStatusBarWidget>()

        fun update(text: String) {
            currentText = text
            instances.forEach { widget ->
                try {
                    widget.statusBar?.updateWidget(ID)
                } catch (e: Exception) {
                    // Ignore update errors
                }
            }
        }
    }

    private var statusBar: StatusBar? = null

    init {
        instances.add(this)
    }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        instances.remove(this)
        statusBar = null
    }

    override fun getText(): String = currentText

    override fun getAlignment(): Float = 0f

    override fun getTooltipText(): String = "LiveApiTester — AI-Powered API Testing"

    override fun getClickConsumer(): Consumer<MouseEvent>? = null
}

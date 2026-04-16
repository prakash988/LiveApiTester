package com.liveapitester.debugger

import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.liveapitester.http.ApiRequest
import com.liveapitester.http.ApiResponse

class DebuggerService(private val project: Project) {

    private val activeBreakpoints = mutableListOf<XBreakpoint<*>>()

    fun findRunConfiguration(): RunnerAndConfigurationSettings? {
        return RunManager.getInstance(project).allSettings.firstOrNull()
    }

    fun startDebugSession(config: RunnerAndConfigurationSettings) {
        ApplicationManager.getApplication().invokeLater {
            ProgramRunnerUtil.executeConfiguration(config, DefaultDebugExecutor.getDebugExecutorInstance())
        }
    }

    fun setBreakpoint(method: PsiMethod): XBreakpoint<*>? {
        val containingFile = method.containingFile ?: return null
        val virtualFile = containingFile.virtualFile ?: return null

        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            ?: PsiDocumentManager.getInstance(project).getDocument(containingFile)
            ?: return null

        val lineNumber = document.getLineNumber(method.textOffset)
        if (lineNumber < 0) return null

        var result: XBreakpoint<*>? = null

        ApplicationManager.getApplication().runWriteAction {
            try {
                val javaLineType = XBreakpointType.EXTENSION_POINT_NAME.extensionList
                    .filterIsInstance<JavaLineBreakpointType>()
                    .firstOrNull()

                if (javaLineType != null) {
                    result = addLineBreakpointSafe(javaLineType, virtualFile.url, lineNumber)
                    result?.let { activeBreakpoints.add(it) }
                }
            } catch (e: Exception) {
                // Breakpoint setting may fail if type is not available
            }
        }

        return result
    }

    /**
     * Type-safe helper that adds a line breakpoint using the specific JavaLineBreakpointType.
     * The JavaLineBreakpointType is parameterized with JavaLineBreakpointProperties,
     * so this is safe and requires no unchecked cast.
     */
    private fun addLineBreakpointSafe(
        type: JavaLineBreakpointType,
        fileUrl: String,
        line: Int
    ): XBreakpoint<*>? {
        return try {
            val manager = XDebuggerManager.getInstance(project).breakpointManager
            manager.addLineBreakpoint(type, fileUrl, line, null)
        } catch (e: Exception) {
            null
        }
    }

    fun removeBreakpoint(breakpoint: XBreakpoint<*>) {
        ApplicationManager.getApplication().runWriteAction {
            try {
                XDebuggerManager.getInstance(project).breakpointManager.removeBreakpoint(breakpoint)
                activeBreakpoints.remove(breakpoint)
            } catch (e: Exception) {
                // Ignore removal errors
            }
        }
    }

    fun cleanupBreakpoints() {
        val toRemove = activeBreakpoints.toList()
        toRemove.forEach { removeBreakpoint(it) }
    }

    fun isDebugSessionActive(): Boolean {
        return try {
            XDebuggerManager.getInstance(project).debugSessions.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun getCurrentSessionInfo(): Map<String, String> {
        return try {
            val session = XDebuggerManager.getInstance(project).currentSession ?: return emptyMap()
            val info = mutableMapOf<String, String>()
            info["sessionName"] = session.sessionName
            info["isPaused"] = session.isPaused.toString()
            info
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun resume() {
        try {
            XDebuggerManager.getInstance(project).currentSession?.resume()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun stepOver() {
        try {
            XDebuggerManager.getInstance(project).currentSession?.stepOver(false)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun stepInto() {
        try {
            XDebuggerManager.getInstance(project).currentSession?.stepInto()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun stopDebugSession() {
        try {
            XDebuggerManager.getInstance(project).currentSession?.stop()
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Full "Send & Debug" workflow:
     * 1. Find controller method for the URL
     * 2. Set breakpoint on that method
     * 3. Start app in debug mode if not running
     * 4. Send the HTTP request
     * 5. Notify caller via callbacks
     */
    fun sendAndDebug(
        request: ApiRequest,
        onStatus: (String) -> Unit,
        onResponse: (ApiResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                invokeLater { onStatus("Finding endpoint...") }
                val resolver = EndpointMethodResolver(project)
                val resolved = try {
                    resolver.resolve(extractPath(request.url), request.method.name)
                } catch (e: Exception) {
                    null
                }

                if (resolved != null) {
                    invokeLater { onStatus("Setting breakpoint at ${resolved.filePath.substringAfterLast("/")}:${resolved.lineNumber + 1}...") }
                    ApplicationManager.getApplication().invokeAndWait {
                        setBreakpoint(resolved.psiMethod)
                    }
                } else {
                    invokeLater { onStatus("No controller method found — sending without breakpoint...") }
                }

                // Start debug session if not already active
                if (!isDebugSessionActive()) {
                    val config = findRunConfiguration()
                    if (config != null) {
                        invokeLater { onStatus("Starting debug session for '${config.name}'...") }
                        startDebugSession(config)
                        Thread.sleep(2000)
                    }
                }

                invokeLater { onStatus("Sending request...") }
                val settings = com.liveapitester.settings.LiveApiTesterSettings.getInstance()
                val envManager = com.liveapitester.environment.EnvironmentManager.getInstance(project)
                val response = com.liveapitester.http.HttpExecutor().execute(
                    request, settings, envManager.getActiveVariables()
                )

                invokeLater { onResponse(response) }

                if (isDebugSessionActive()) {
                    invokeLater { onStatus("Request sent — check debugger for breakpoint") }
                } else {
                    invokeLater { onStatus("Request complete") }
                }
            } catch (e: Exception) {
                invokeLater { onError("Debug send failed: ${e.message ?: "Unknown error"}") }
            }
        }
    }

    private fun extractPath(url: String): String {
        return try {
            val withoutProtocol = url.substringAfter("://")
            val pathStart = withoutProtocol.indexOf('/')
            if (pathStart >= 0) withoutProtocol.substring(pathStart).substringBefore("?")
            else "/"
        } catch (e: Exception) {
            "/"
        }
    }

    private fun invokeLater(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action)
    }
}

package com.liveapitester.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "LiveApiTesterSettings",
    storages = [Storage("LiveApiTesterSettings.xml")]
)
@Service(Service.Level.APP)
class LiveApiTesterSettings : PersistentStateComponent<LiveApiTesterSettings> {

    var aiEndpoint: String = "https://models.github.ai/inference/chat/completions"
    var aiModel: String = "gpt-4o"
    var timeoutSeconds: Int = 30
    var followRedirects: Boolean = true
    var sslTrustAll: Boolean = false
    var maxHistory: Int = 100

    override fun getState(): LiveApiTesterSettings = this

    override fun loadState(state: LiveApiTesterSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val AVAILABLE_MODELS = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "claude-3.5-sonnet",
            "Meta-Llama-3.1-405B-Instruct",
            "Mistral-Large"
        )

        fun getInstance(): LiveApiTesterSettings =
            ApplicationManager.getApplication().getService(LiveApiTesterSettings::class.java)
    }
}

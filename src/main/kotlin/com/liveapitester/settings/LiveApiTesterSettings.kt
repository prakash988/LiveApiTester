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

    var aiEndpoint: String = "https://api.openai.com/v1/chat/completions"
    var aiModel: String = "gpt-4"
    var timeoutSeconds: Int = 30
    var followRedirects: Boolean = true
    var sslTrustAll: Boolean = false
    var maxHistory: Int = 100

    override fun getState(): LiveApiTesterSettings = this

    override fun loadState(state: LiveApiTesterSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): LiveApiTesterSettings =
            ApplicationManager.getApplication().getService(LiveApiTesterSettings::class.java)
    }
}

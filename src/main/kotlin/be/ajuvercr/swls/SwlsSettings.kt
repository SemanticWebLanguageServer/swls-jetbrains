package be.ajuvercr.swls

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "SwlsSettings", storages = [Storage("swls.xml")])
class SwlsSettings : PersistentStateComponent<SwlsSettings.State> {
    data class State(
        var command: String = "",
        var checkUpdate: Boolean = true,
        var automaticUpdate: Boolean = false,
        var log: String = "debug",
        var turtle: Boolean = true,
        var trig: Boolean = true,
        var jsonld: Boolean = true,
        var sparql: Boolean = true,
        // newline-separated URLs
        var ontologies: String = "",
        var shapes: String = "",
        // newline-separated values
        var disabled: String = "",
        var prefixDisabled: String = "",
        var completionMode: String = "none",
        var completionStrict: String = "",
        var completionExcept: String = "",
    )

    // Set at runtime when the server starts; not persisted
    var resolvedBinary: String = ""

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(): SwlsSettings =
            ApplicationManager.getApplication().getService(SwlsSettings::class.java)
    }
}

package be.ajuvercr.swls

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.redhat.devtools.lsp4ij.LanguageServerManager
import javax.swing.JComponent

class SwlsSettingsConfigurable : Configurable {
    private var uiPanel: com.intellij.ui.dsl.builder.Panel? = null
    private var dialogPanel: com.intellij.openapi.ui.DialogPanel? = null

    private var command = ""
    private var checkUpdate = true
    private var automaticUpdate = false
    private var log = "debug"
    private var turtle = true
    private var trig = true
    private var jsonld = true
    private var sparql = false
    private var ontologies = ""
    private var shapes = ""
    private var disabled = ""
    private var prefixDisabled = ""
    private var completionMode = "none"
    private var completionStrict = ""
    private var completionExcept = ""

    override fun getDisplayName(): String = "Semantic Web Language Server"

    override fun createComponent(): JComponent {
        loadFromSettings()
        val built = panel {
            group("Server") {
                row("Binary path:") {
                    textFieldWithBrowseButton(
                        "Select SWLS Binary",
                        null,
                        FileChooserDescriptorFactory.createSingleFileDescriptor()
                    ).bindText(::command).align(AlignX.FILL)
                }.rowComment("Leave empty to use the automatically downloaded binary")
                val resolved = SwlsSettings.getInstance().resolvedBinary
                if (resolved.isNotBlank()) {
                    row("Active binary:") {
                        cell(com.intellij.ui.components.JBTextField(resolved).apply {
                            isEditable = false
                        }).align(AlignX.FILL)
                    }
                }
                row("Log level:") {
                    comboBox(listOf("error", "warn", "info", "debug", "trace"))
                        .bindItem({ log }) { log = it ?: "debug" }
                }
                row {
                    checkBox("Check for updates on startup").bindSelected(::checkUpdate)
                }
                row {
                    checkBox("Automatically install updates").bindSelected(::automaticUpdate)
                }
                row {
                    button("Restart Server") {
                        apply()
                        for (project in ProjectManager.getInstance().openProjects) {
                            val manager = LanguageServerManager.getInstance(project)
                            manager.stop("swls")
                            manager.start("swls", LanguageServerManager.StartOptions().setForceStart(true))
                        }
                    }
                }.rowComment("Picks up a newly downloaded binary or changed settings without restarting the IDE")
            }
            group("Languages") {
                row { checkBox("Turtle (.ttl)").bindSelected(::turtle) }
                row { checkBox("TriG (.trig)").bindSelected(::trig) }
                row { checkBox("JSON-LD (.jsonld)").bindSelected(::jsonld) }
                row { checkBox("SPARQL (.sq, .rq)").bindSelected(::sparql) }
            }
            group("Completion") {
                row("Mode:") {
                    comboBox(listOf("none", "loose", "strict"))
                        .bindItem({ completionMode }) { completionMode = it ?: "none" }
                }.rowComment("'strict' only suggests properties whose domain matches the subject type; 'loose' suggests all")
                row("Strict namespaces:") {
                    expandableTextField().bindText(::completionStrict).align(AlignX.FILL)
                }.rowComment("Newline-separated namespace prefixes that require a matching domain. Takes precedence over mode.")
                row("Except namespaces:") {
                    expandableTextField().bindText(::completionExcept).align(AlignX.FILL)
                }.rowComment("Newline-separated namespace prefixes always suggested regardless of domain. Takes precedence over mode.")
            }
            group("Ontologies & Shapes") {
                row("Extra ontologies:") {
                    expandableTextField().bindText(::ontologies).align(AlignX.FILL)
                }.rowComment("Newline-separated ontology URLs to import")
                row("Extra shapes:") {
                    expandableTextField().bindText(::shapes).align(AlignX.FILL)
                }.rowComment("Newline-separated shape URLs to import")
            }
            group("Advanced") {
                row("Disabled features:") {
                    expandableTextField().bindText(::disabled).align(AlignX.FILL)
                }.rowComment("Newline-separated features to disable (e.g. shapes)")
                row("Hidden prefixes:") {
                    expandableTextField().bindText(::prefixDisabled).align(AlignX.FILL)
                }.rowComment("Newline-separated prefix.cc prefixes to hide from completions")
            }
        }
        dialogPanel = built
        return built
    }

    override fun isModified(): Boolean = dialogPanel?.isModified() ?: false

    override fun apply() {
        dialogPanel?.apply()
        val state = SwlsSettings.getInstance().state
        state.command = command
        state.checkUpdate = checkUpdate
        state.automaticUpdate = automaticUpdate
        state.log = log
        state.turtle = turtle
        state.trig = trig
        state.jsonld = jsonld
        state.sparql = sparql
        state.ontologies = ontologies
        state.shapes = shapes
        state.disabled = disabled
        state.prefixDisabled = prefixDisabled
        state.completionMode = completionMode
        state.completionStrict = completionStrict
        state.completionExcept = completionExcept
    }

    override fun reset() {
        loadFromSettings()
        dialogPanel?.reset()
    }

    private fun loadFromSettings() {
        val state = SwlsSettings.getInstance().state
        command = state.command
        checkUpdate = state.checkUpdate
        automaticUpdate = state.automaticUpdate
        log = state.log
        turtle = state.turtle
        trig = state.trig
        jsonld = state.jsonld
        sparql = state.sparql
        ontologies = state.ontologies
        shapes = state.shapes
        disabled = state.disabled
        prefixDisabled = state.prefixDisabled
        completionMode = state.completionMode
        completionStrict = state.completionStrict
        completionExcept = state.completionExcept
    }
}

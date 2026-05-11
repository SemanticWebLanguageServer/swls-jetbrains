package be.ajuvercr.swls

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

private val RDF_ICON: Icon = IconLoader.getIcon("/icons/rdf.svg", SwlsFactory::class.java)

class TurtleFileType : LanguageFileType(PlainTextLanguage.INSTANCE) {
    override fun getName(): String = "Turtle"
    override fun getDescription(): String = "Turtle RDF file"
    override fun getDefaultExtension(): String = "ttl"
    override fun getIcon(): Icon = RDF_ICON
    override fun getDisplayName(): String = "Turtle"

    companion object {
        @JvmStatic
        val INSTANCE = TurtleFileType()
    }
}

class JsonLdFileType : LanguageFileType(PlainTextLanguage.INSTANCE) {
    override fun getName(): String = "JSON-LD"
    override fun getDescription(): String = "JSON-LD file"
    override fun getDefaultExtension(): String = "jsonld"
    override fun getIcon(): Icon = RDF_ICON
    override fun getDisplayName(): String = "JSON-LD"

    companion object {
        @JvmStatic
        val INSTANCE = JsonLdFileType()
    }
}

class SparqlFileType : LanguageFileType(PlainTextLanguage.INSTANCE) {
    override fun getName(): String = "SPARQL"
    override fun getDescription(): String = "SPARQL query file"
    override fun getDefaultExtension(): String = "sq"
    override fun getIcon(): Icon = RDF_ICON
    override fun getDisplayName(): String = "SPARQL"

    companion object {
        @JvmStatic
        val INSTANCE = SparqlFileType()
    }
}

class TrigFileType : LanguageFileType(PlainTextLanguage.INSTANCE) {
    override fun getName(): String = "TriG"
    override fun getDescription(): String = "TriG RDF file"
    override fun getDefaultExtension(): String = "trig"
    override fun getIcon(): Icon = RDF_ICON
    override fun getDisplayName(): String = "TriG"

    companion object {
        @JvmStatic
        val INSTANCE = TrigFileType()
    }
}

# SWLS – JetBrains Plugin

JetBrains IDE plugin for the [Semantic Web Language Server (SWLS)](https://github.com/SemanticWebLanguageServer/swls), providing language support for **Turtle**, **SPARQL**, and **JSON-LD**.

## Features

- Autocompletion for prefixes, IRIs, properties, and classes
- Real-time diagnostics and validation
- Semantic token highlighting
- Code formatting
- Inlay hints
- Go to definition and references
- SHACL shape validation
- Ontology-aware property suggestions

## Supported File Types

| Language | Extensions   |
|----------|-------------|
| Turtle   | `.ttl`      |
| JSON-LD  | `.jsonld`   |
| SPARQL   | `.sq`, `.rq`|

## Requirements

- IntelliJ-based IDE (IntelliJ IDEA, WebStorm, PyCharm, etc.) version 2023.3+
- [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij) plugin

## Installation

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com/) — search for **"SWLS"** or **"Semantic Web Language Server"**.

The plugin automatically downloads and manages the SWLS binary. Updates are checked in the background.

## Building from Source

```bash
# Run a sandboxed IDE with the plugin loaded
./gradlew runIde

# Build a distributable ZIP
./gradlew buildPlugin
# Output: build/distributions/swls-*.zip
```

## Links

- [SWLS Language Server](https://github.com/SemanticWebLanguageServer/swls)
- [VS Code Extension](https://github.com/SemanticWebLanguageServer/swls-vscode)
- [JetBrains Plugin Source](https://github.com/SemanticWebLanguageServer/swls-jetbrains)

package texteditor.data

import android.content.Context
import android.content.res.AssetManager
import android.os.SystemClock
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.dsl.languages
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry as TextMateThemeRegistry

object EditorLanguages {
    private const val TAG = "EditorLanguages"
    private const val GRAMMAR_DIR = "textmate/grammars"
    const val PLAIN_TEXT = ""
    private const val SCAN_LIMIT = 1_000_000
    private val fences = Pattern.compile(
        "^[ \\t]{0,3}(?:```+|~~~+)[ \\t]*([\\w+#.-]{1,32})", Pattern.MULTILINE
    )
    private val htmlTags = Pattern.compile("<[A-Za-z][A-Za-z0-9-]*(?:[ \\t/]|>)")

    private class Grammar(
        val name: String,
        val scope: String,
        val file: String,
        val displayName: String,
        val fileTypes: List<String>,
        val injectable: Boolean,
    ) {
        val isLanguage: Boolean
            get() = !injectable && !name.startsWith("alias-") && name !in includeOnlyGrammars
    }

    @Volatile
    private var grammars: List<Grammar> = emptyList()

    @Volatile
    private var languageScopes: List<String> = emptyList()

    @Volatile
    private var displayNames: Map<String, String> = emptyMap()

    @Volatile
    private var scopeByFileType: Map<String, String> = emptyMap()

    @Volatile
    private var scopeByLabel: Map<String, String> = emptyMap()

    @Volatile
    private var initialized = false
    private val registeredScopes = ConcurrentHashMap.newKeySet<String>()

    val isInitialized: Boolean
        get() = initialized

    val scopes: List<String>
        get() = languageScopes

    fun initialize(context: Context): Boolean {
        if (initialized) {
            Log.d(TAG, "Grammar index already loaded, reusing it")
            return true
        }
        synchronized(this) {
            if (initialized) return true
            val started = SystemClock.elapsedRealtime()
            val loaded = runCatching { readGrammars(context) }.onFailure {
                Log.e(
                    TAG, "Failed to read the bundled grammars", it
                )
            }.getOrNull() ?: return false
            val languageGrammars = loaded.filter { it.isLanguage }
            grammars = loaded
            languageScopes =
                languageGrammars.sortedBy { it.displayName.lowercase(Locale.ROOT) }.map { it.scope }
            displayNames = languageGrammars.associate { it.scope to it.displayName }
            scopeByFileType = buildFileTypes(languageGrammars)
            scopeByLabel = buildLabels(languageGrammars)
            initialized = languageScopes.isNotEmpty()
            Log.d(
                TAG,
                "Discovered ${loaded.size} TextMate grammars, ${languageGrammars.size} languages" + " in ${SystemClock.elapsedRealtime() - started}ms",
            )
            return initialized
        }
    }

    fun registerScope(scope: String?): Boolean {
        if (scope.isNullOrEmpty() || !initialized) return false
        synchronized(this) {
            if (scope in registeredScopes) return true
        }
        return registerNew(scope) != null
    }

    fun isScopeRegistered(scope: String?): Boolean =
        !scope.isNullOrEmpty() && scope in registeredScopes

    private fun registerNew(scope: String?): Grammar? {
        if (scope.isNullOrEmpty() || !initialized) return null
        synchronized(this) {
            val definition = registerGrammarLocked(scope) ?: return null
            applyThemeLocked()
            return definition
        }
    }

    private fun registerGrammarLocked(scope: String?): Grammar? {
        if (scope.isNullOrEmpty() || !initialized || scope in registeredScopes) return null
        val definition = grammars.firstOrNull { it.scope == scope } ?: return null
        val registered = runCatching {
            GrammarRegistry.getInstance().loadGrammars(
                languages {
                    language(definition.name) {
                        grammar = definition.file
                        scopeName = definition.scope
                    }
                }.build(),
            )
        }.onFailure {
            Log.e(TAG, "Failed to register ${definition.name}", it)
        }.isSuccess
        if (!registered) return null
        registeredScopes += scope
        Log.d(TAG, "Registered ${definition.name} (${definition.scope})")
        return definition
    }

    private fun applyThemeLocked() {
        val theme = TextMateThemeRegistry.getInstance().currentThemeModel
        if (theme === ThemeModel.EMPTY) return
        runCatching { GrammarRegistry.getInstance().setTheme(theme) }.onFailure {
            Log.e(
                TAG, "Failed to apply the TextMate theme", it
            )
        }
    }

    fun registerDocument(scope: String?, text: CharSequence?): Boolean {
        var added = registerNew(scope) != null
        if (text.isNullOrEmpty()) return added
        val limit = minOf(text.length, SCAN_LIMIT)
        val fences = fences.matcher(text)
        fences.region(0, limit)
        while (fences.find()) {
            added = registerNew(scopeForLabel(fences.group(1))) != null || added
        }
        val tags = htmlTags.matcher(text)
        tags.region(0, limit)
        if (tags.find()) {
            added = registerNew(scopeForLabel("html")) != null || added
        }
        return added
    }

    fun rebuildGrammar(scope: String?): Boolean {
        if (scope.isNullOrEmpty() || scope !in registeredScopes) return false
        val registry = GrammarRegistry.getInstance()
        return runCatching {
            registry.reloadGrammar(scope)
            registry.findGrammar(scope) != null
        }.getOrDefault(false)
    }

    fun scopeForLabel(label: String?): String? {
        if (label.isNullOrEmpty()) return null
        val key = label.lowercase(Locale.ROOT)
        scopeByFileType[key]?.let { return it }
        scopeByLabel[key]?.let { return it }
        val grammarName = fenceLabelGrammars[key] ?: return null
        return grammars.firstOrNull { it.name == grammarName }?.scope
    }

    fun scopeFor(fileName: String?): String? {
        if (!initialized) return null
        val path = fileName?.replace('\\', '/')?.lowercase(Locale.ROOT).orEmpty()
        if (path.isEmpty()) return null
        val types = scopeByFileType
        types.entries.firstOrNull { '/' in it.key && path.endsWith(it.key) }
            ?.let { return it.value }
        val name = path.substringAfterLast('/')
        if (name.isEmpty()) return null
        types[name]?.let { return it }
        types[name.substringBefore('.')]?.let { return it }
        var dot = name.indexOf('.')
        while (dot in 0 until name.lastIndex) {
            types[name.substring(dot + 1)]?.let { return it }
            dot = name.indexOf('.', dot + 1)
        }
        return null
    }

    fun displayName(scope: String?): String {
        if (scope.isNullOrEmpty()) return "Plain text"
        return displayNames[scope] ?: scope.substringAfterLast('.')
    }

    fun languageForScope(scope: String?): TextMateLanguage? {
        if (scope.isNullOrEmpty()) return null
        if (displayNames[scope] == null) return null
        if (!registerScope(scope)) return null
        return runCatching { TextMateLanguage.create(scope, true) }.onFailure {
            Log.e(TAG, "Failed to create a TextMate language for $scope", it)
        }.getOrNull()
    }

    private fun readGrammars(context: Context): List<Grammar> {
        EditorAssets.ensure(context)
        val assets = context.assets
        return assets.list(GRAMMAR_DIR).orEmpty().sorted()
            .filter { it.endsWith(".json", ignoreCase = true) }.mapNotNull { file ->
                runCatching { readGrammar(assets, file) }.onFailure {
                    Log.e(
                        TAG, "Failed to read $file", it
                    )
                }.getOrNull()
            }
    }

    private fun readGrammar(assets: AssetManager, file: String): Grammar? {
        var name: String? = null
        var scope: String? = null
        var displayName: String? = null
        var injectable = false
        val fileTypes = mutableListOf<String>()

        assets.open("$GRAMMAR_DIR/$file").bufferedReader().use { reader ->
            JsonReader(reader).use { json ->
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "name" -> name = json.readString()
                        "scopeName" -> scope = json.readString()
                        "displayName" -> displayName = json.readString()
                        "injectionSelector", "injectTo" -> {
                            injectable = true
                            json.skipValue()
                        }

                        "fileTypes" -> {
                            if (json.peek() == JsonToken.BEGIN_ARRAY) {
                                json.beginArray()
                                while (json.hasNext()) {
                                    if (json.peek() == JsonToken.STRING) {
                                        fileTypes += json.nextString()
                                    } else {
                                        json.skipValue()
                                    }
                                }
                                json.endArray()
                            } else {
                                json.skipValue()
                            }
                        }

                        else -> json.skipValue()
                    }
                }
                json.endObject()
            }
        }

        val grammarName = name ?: return null
        val scopeName = scope ?: return null
        return Grammar(
            name = grammarName,
            scope = scopeName,
            file = "$GRAMMAR_DIR/$file",
            displayName = displayName?.trim().orEmpty().ifEmpty { grammarName },
            fileTypes = fileTypes,
            injectable = injectable,
        )
    }

    private fun buildFileTypes(languageGrammars: List<Grammar>): Map<String, String> {
        val byName = languageGrammars.associate { it.name to it.scope }
        val types = mutableMapOf<String, String>()
        val typed = mutableSetOf<String>()
        for (grammar in languageGrammars) {
            for (raw in grammar.fileTypes) {
                val type = normalizeFileType(raw) ?: continue
                types.putIfAbsent(type, grammar.scope)
                typed += grammar.name
            }
        }
        for (grammar in languageGrammars) {
            if (grammar.name !in typed) types.putIfAbsent(grammar.name, grammar.scope)
        }
        for ((type, grammarName) in fileTypes) {
            byName[grammarName]?.let { types[type] = it }
        }
        return types
    }

    private fun buildLabels(languageGrammars: List<Grammar>): Map<String, String> = buildMap {
        for (grammar in languageGrammars) {
            putIfAbsent(grammar.name.lowercase(Locale.ROOT), grammar.scope)
            putIfAbsent(grammar.displayName.lowercase(Locale.ROOT), grammar.scope)
        }
    }

    private fun normalizeFileType(raw: String): String? {
        val type = raw.trim().lowercase(Locale.ROOT).removePrefix("*").removePrefix(".")
        return type.takeIf { it.isNotEmpty() && it !in ignoredFileTypes }
    }

    private fun JsonReader.readString(): String? {
        if (peek() == JsonToken.STRING) return nextString()
        skipValue()
        return null
    }

    private val fileTypes = mapOf(
        "ada" to "ada", "adb" to "ada", "ads" to "ada", "asm" to "asm", "automount" to "systemd",
        "bash" to "shellscript", "bat" to "bat", "bib" to "bibtex", "bibtex" to "bibtex",
        "brewfile" to "ruby", "bsdmakefile" to "make", "c" to "c", "cc" to "cpp",
        "cdc" to "cadence", "cjs" to "javascript", "clar" to "clarity", "clj" to "clojure",
        "cljc" to "clojure", "cljs" to "clojure", "cls" to "apex", "cmd" to "bat",
        "codeowners" to "codeowners", "coffee" to "coffee", "commit_editmsg" to "git-commit",
        "containerfile" to "docker", "cpp" to "cpp", "cs" to "csharp", "cson" to "coffee",
        "css" to "css", "csx" to "csharp", "cts" to "typescript", "cxx" to "cpp", "dart" to "dart",
        "dax" to "dax", "device" to "systemd", "diff" to "diff", "dockerfile" to "docker",
        "edn" to "clojure", "env" to "dotenv", "exrc" to "viml", "fennel" to "fennel",
        "fnl" to "fennel", "fs" to "fsharp", "fsi" to "fsharp", "fsx" to "fsharp",
        "ftl" to "fluent", "fx" to "hlsl", "fxh" to "hlsl", "gemfile" to "ruby",
        "gemspec" to "ruby", "git-rebase-todo" to "git-rebase", "gjs" to "glimmer-js",
        "gn" to "gn", "gni" to "gn", "gnumakefile" to "make", "go" to "go", "gradle" to "groovy",
        "groovy" to "groovy", "gsh" to "groovy", "gts" to "glimmer-ts", "guardfile" to "ruby",
        "gvimrc" to "viml", "gvy" to "groovy", "gy" to "groovy", "h" to "cpp",
        "handlebars" to "handlebars", "hbs" to "handlebars", "hh" to "cpp", "hlsl" to "hlsl",
        "hpp" to "cpp", "htm" to "html", "html" to "html", "hxx" to "cpp", "iced" to "coffee",
        "ini" to "ini", "ino" to "cpp", "j2" to "jinja", "jade" to "pug", "java" to "java",
        "jinja" to "jinja", "jinja2" to "jinja", "jl" to "julia", "js" to "javascript",
        "json" to "json", "jsonc" to "jsonc", "jsonnet" to "jsonnet", "jsx" to "jsx",
        "justfile" to "just", "ksh" to "shellscript", "less" to "less", "libsonnet" to "jsonnet",
        "ll" to "llvm", "ltx" to "latex", "lua" to "lua", "m" to "objective-c", "make" to "make",
        "makefile" to "make", "markdown" to "markdown", "md" to "markdown", "mdown" to "markdown",
        "mjs" to "javascript", "mk" to "make", "mm" to "objective-c", "mount" to "systemd",
        "mts" to "typescript", "nf" to "nextflow", "nu" to "nushell", "p6" to "raku",
        "patch" to "diff", "path" to "systemd", "php" to "php", "php3" to "php", "php4" to "php",
        "php5" to "php", "phtml" to "php", "pl" to "perl", "pl6" to "raku", "pm" to "perl",
        "pm6" to "raku", "pp" to "pascal", "ps1" to "powershell", "psd1" to "powershell",
        "psm1" to "powershell", "pug" to "pug", "py" to "python", "pyi" to "python",
        "pyw" to "python", "qmldir" to "qmldir", "racket" to "racket", "rake" to "ruby",
        "rakefile" to "ruby", "raku" to "raku", "rakumod" to "raku", "rb" to "ruby",
        "rkt" to "racket", "rktd" to "racket", "rktl" to "racket", "rprofile" to "r",
        "rs" to "rust", "rst" to "rst", "ru" to "ruby", "s" to "mipsasm", "sc" to "scala",
        "scope" to "systemd", "scrbl" to "racket", "scss" to "scss", "service" to "systemd",
        "sh" to "shellscript", "shader" to "shaderlab", "slice" to "systemd",
        "socket" to "systemd", "sql" to "sql", "ss" to "scheme", "ssh_config" to "ssh-config",
        "sshd_config" to "ssh-config", "sty" to "latex", "svg" to "xml", "swap" to "systemd",
        "talon" to "talonscript", "target" to "systemd", "tex" to "latex", "timer" to "systemd",
        "tres" to "gdresource", "ts" to "typescript", "tscn" to "gdresource", "tsx" to "tsx",
        "typ" to "typst", "typst" to "typst", "v" to "verilog", "vagrantfile" to "ruby",
        "vb" to "vb", "vbs" to "vb", "vh" to "verilog", "vim" to "viml", "vimrc" to "viml",
        "vine" to "vue-vine", "vue" to "vue", "vy" to "vyper", "wast" to "wasm", "wat" to "wasm",
        "wgsl" to "wgsl", "wiki" to "wikitext", "wikitext" to "wikitext", "wy" to "wenyan",
        "xhtml" to "html", "xml" to "xml", "xsl" to "xsl", "xslt" to "xsl", "yaml" to "yaml",
        "yml" to "yaml", "zsh" to "shellscript",
    )

    private val fenceLabelGrammars = mapOf(
        "c++" to "cpp",
        "c#" to "csharp",
        "golang" to "go",
        "objc" to "objective-c",
        "shell" to "shellscript",
        "sh-session" to "shellscript",
    )

    private val includeOnlyGrammars = setOf(
        "angular-html",
        "angular-ts",
        "cpp-macro",
        "html-derivative",
        "jinja-html",
        "nextflow-groovy",
        "ts-tags",
        "vue-html",
    )

    private val ignoredFileTypes = setOf(
        "conf",
        "gs",
        "inc",
        "script editor",
        "sublime-syntax",
        "syntax",
        "vsh",
        "yaml-tmlanguage",
    )
}

package com.sednium.localspaces.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * A real tokenizer-driven highlighter, not a flat monochrome code block.
 * Scoped to regex-based single-pass tokenization rather than a true
 * lexer/parser per language — enough to make code readable at chat-
 * bubble scale without pulling in a multi-megabyte grammar engine.
 */
object SyntaxHighlighter {

    fun highlight(code: String, language: String?, isDark: Boolean): AnnotatedString {
        // Code blocks always render on a near-black surface (see CodeBlockView) regardless
        // of the surrounding chat theme, so there is exactly one token palette to maintain.
        val palette = TokenPalette
        val lang = language?.lowercase()?.trim()
        return when (lang) {
            "json" -> highlightJson(code, palette)
            "xml", "html" -> highlightMarkup(code, palette)
            null, "", "text", "txt", "plain" -> buildAnnotatedString { withStyle(SpanStyle(color = palette.plain)) { append(code) } }
            else -> highlightGeneric(code, languageDefFor(lang), palette)
        }
    }

    // ---- token palette (GitHub-Dark-inspired, tuned for a near-black code surface) ----

    data class Palette(
        val plain: Color, val keyword: Color, val string: Color,
        val comment: Color, val number: Color, val type: Color, val punctuation: Color
    )

    val TokenPalette = Palette(
        plain = Color(0xFFE5E7EB), keyword = Color(0xFFFF7B72), string = Color(0xFFA5D6FF),
        comment = Color(0xFF8B949E), number = Color(0xFF79C0FF), type = Color(0xFFFFA657),
        punctuation = Color(0xFFC9D1D9)
    )

    // ---- per-language keyword sets ----

    private data class LanguageDef(
        val keywords: Set<String>,
        val lineComment: String?,
        val blockComment: Pair<String, String>?
    )

    private val KOTLIN_KW = setOf(
        "fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while", "do",
        "return", "break", "continue", "is", "as", "in", "out", "null", "true", "false", "this", "super",
        "import", "package", "private", "public", "protected", "internal", "open", "override", "abstract",
        "sealed", "data", "enum", "companion", "init", "constructor", "try", "catch", "finally", "throw",
        "suspend", "inline", "reified", "lateinit", "by", "typealias", "vararg", "operator", "infix", "const"
    )
    private val JAVA_KW = setOf(
        "public", "private", "protected", "class", "interface", "extends", "implements", "static", "final",
        "void", "int", "long", "double", "float", "boolean", "char", "byte", "short", "new", "return", "if",
        "else", "for", "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally",
        "throw", "throws", "import", "package", "this", "super", "null", "true", "false", "enum", "abstract"
    )
    private val PYTHON_KW = setOf(
        "def", "class", "if", "elif", "else", "for", "while", "return", "import", "from", "as", "try",
        "except", "finally", "raise", "with", "lambda", "yield", "pass", "break", "continue", "global",
        "nonlocal", "in", "is", "not", "and", "or", "None", "True", "False", "async", "await", "self"
    )
    private val JS_KW = setOf(
        "function", "const", "let", "var", "if", "else", "for", "while", "do", "return", "class", "extends",
        "import", "export", "default", "from", "as", "try", "catch", "finally", "throw", "new", "this",
        "super", "typeof", "instanceof", "null", "undefined", "true", "false", "async", "await", "yield",
        "switch", "case", "break", "continue", "static", "get", "set", "of", "in", "interface", "type",
        "implements", "enum", "namespace", "readonly", "public", "private", "protected"
    )
    private val SHELL_KW = setOf(
        "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "case", "esac", "function",
        "return", "exit", "export", "local", "echo", "set", "unset", "shift", "break", "continue", "test"
    )
    private val SQL_KW = setOf(
        "select", "from", "where", "join", "left", "right", "inner", "outer", "on", "group", "by", "order",
        "having", "insert", "into", "values", "update", "set", "delete", "create", "table", "alter", "drop",
        "primary", "key", "foreign", "references", "not", "null", "and", "or", "as", "distinct", "limit",
        "union", "all", "case", "when", "then", "end", "is", "in", "like", "between", "exists"
    )
    private val YAML_KW = setOf("true", "false", "null", "yes", "no")

    private fun languageDefFor(lang: String): LanguageDef = when (lang) {
        "kotlin", "kt", "kts" -> LanguageDef(KOTLIN_KW, "//", "/*" to "*/")
        "java" -> LanguageDef(JAVA_KW, "//", "/*" to "*/")
        "python", "py" -> LanguageDef(PYTHON_KW, "#", null)
        "javascript", "js", "typescript", "ts", "jsx", "tsx" -> LanguageDef(JS_KW, "//", "/*" to "*/")
        "c", "cpp", "c++", "csharp", "cs", "go", "rust", "rs", "swift", "dart" -> LanguageDef(JS_KW, "//", "/*" to "*/")
        "bash", "sh", "shell", "zsh" -> LanguageDef(SHELL_KW, "#", null)
        "sql" -> LanguageDef(SQL_KW, "--", null)
        "yaml", "yml" -> LanguageDef(YAML_KW, "#", null)
        else -> LanguageDef(emptySet(), "//", "/*" to "*/") // unknown language: still highlight strings/numbers/comments generically
    }

    // ---- generic C-like / keyword-driven tokenizer ----

    private val NUMBER_RE = """\b\d+(\.\d+)?[fFlLdD]?\b"""
    private val IDENT_RE = """\b[A-Za-z_][A-Za-z0-9_]*\b"""
    private val STRING_RE = """"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'"""

    private fun highlightGeneric(code: String, def: LanguageDef, palette: Palette): AnnotatedString {
        val patternParts = mutableListOf<String>()
        def.blockComment?.let { (open, close) -> patternParts += "(${Regex.escape(open)}[\\s\\S]*?${Regex.escape(close)})" }
        def.lineComment?.let { patternParts += "(${Regex.escape(it)}.*$)" }
        patternParts += "($STRING_RE)"
        patternParts += "($NUMBER_RE)"
        patternParts += "($IDENT_RE)"
        val pattern = Regex(patternParts.joinToString("|"), setOf(RegexOption.MULTILINE))

        return buildAnnotatedString {
            var last = 0
            for (m in pattern.findAll(code)) {
                if (m.range.first > last) append(code.substring(last, m.range.first))
                val text = m.value
                val color = when {
                    def.blockComment != null && text.startsWith(def.blockComment.first) -> palette.comment
                    def.lineComment != null && text.startsWith(def.lineComment) -> palette.comment
                    text.firstOrNull() == '"' || text.firstOrNull() == '\'' -> palette.string
                    text.firstOrNull()?.isDigit() == true -> palette.number
                    text in def.keywords -> palette.keyword
                    text.isNotEmpty() && text.first().isUpperCase() -> palette.type
                    else -> palette.plain
                }
                withStyle(SpanStyle(color = color, fontWeight = if (text in def.keywords) FontWeight.SemiBold else FontWeight.Normal)) {
                    append(text)
                }
                last = m.range.last + 1
            }
            if (last < code.length) append(code.substring(last))
        }
    }

    // ---- JSON: keys vs values vs literals, no language keywords involved ----

    private val JSON_TOKEN_RE = Regex(""""(?:\\.|[^"\\])*"|-?\d+(\.\d+)?([eE][+-]?\d+)?|\btrue\b|\bfalse\b|\bnull\b""")

    private fun highlightJson(code: String, palette: Palette): AnnotatedString = buildAnnotatedString {
        var last = 0
        for (m in JSON_TOKEN_RE.findAll(code)) {
            if (m.range.first > last) append(code.substring(last, m.range.first))
            val text = m.value
            val isKey = text.startsWith("\"") && code.getOrNull(m.range.last + 1)?.let {
                code.substring(m.range.last + 1).trimStart().firstOrNull() == ':'
            } == true
            val color = when {
                isKey -> palette.type
                text.startsWith("\"") -> palette.string
                text == "true" || text == "false" || text == "null" -> palette.keyword
                else -> palette.number
            }
            withStyle(SpanStyle(color = color)) { append(text) }
            last = m.range.last + 1
        }
        if (last < code.length) append(code.substring(last))
    }

    // ---- XML/HTML: tag names, attribute names, attribute string values, comments ----

    private val MARKUP_RE = Regex(
        """<!--[\s\S]*?-->|</?[A-Za-z][A-Za-z0-9:_-]*|"[^"]*"|'[^']*'|[A-Za-z_:][A-Za-z0-9_:-]*=|[<>/]"""
    )

    private fun highlightMarkup(code: String, palette: Palette): AnnotatedString = buildAnnotatedString {
        var last = 0
        for (m in MARKUP_RE.findAll(code)) {
            if (m.range.first > last) append(code.substring(last, m.range.first))
            val text = m.value
            val color = when {
                text.startsWith("<!--") -> palette.comment
                text.startsWith("</") || text.startsWith("<") -> palette.keyword
                text.endsWith("=") -> palette.type
                text.startsWith("\"") || text.startsWith("'") -> palette.string
                else -> palette.punctuation
            }
            withStyle(SpanStyle(color = color, fontStyle = if (text.startsWith("<!--")) FontStyle.Italic else FontStyle.Normal)) {
                append(text)
            }
            last = m.range.last + 1
        }
        if (last < code.length) append(code.substring(last))
    }
}

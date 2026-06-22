package com.sednium.localspaces.markdown

private val HEADING_RE = Regex("""^ {0,3}(#{1,6})\s+(.*?)\s*$""")
private val THEMATIC_RE = Regex("""^ {0,3}(-{3,}|\*{3,}|_{3,})\s*$""")
private val UNORDERED_RE = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val ORDERED_RE = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val TABLE_SEP_RE = Regex("""^\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?$""")

/** Top-level entry point: parses a full Markdown document into a block list. */
fun parseMarkdown(source: String): List<Block> = parseLines(source.replace("\r\n", "\n").split("\n"))

private data class ListMatch(val indent: Int, val ordered: Boolean, val number: Int?, val content: String)

private fun matchListItem(line: String): ListMatch? {
    ORDERED_RE.matchEntire(line)?.let {
        val (indent, num, content) = it.destructured
        return ListMatch(indent.length, true, num.toIntOrNull() ?: 1, content)
    }
    UNORDERED_RE.matchEntire(line)?.let {
        val (indent, content) = it.destructured
        return ListMatch(indent.length, false, null, content)
    }
    return null
}

private fun isFenceStart(line: String) = line.trimStart().startsWith("```")
private fun isBlockquoteStart(line: String) = line.trimStart().startsWith(">")
private fun isThematicBreak(line: String) = THEMATIC_RE.matches(line)
private fun isHeading(line: String) = HEADING_RE.matches(line)
private fun leadingSpaces(line: String) = line.takeWhile { it == ' ' }.length

private fun isTableStart(lines: List<String>, i: Int): Boolean {
    if (i + 1 >= lines.size) return false
    if (!lines[i].contains("|")) return false
    return TABLE_SEP_RE.matches(lines[i + 1].trim())
}

private fun parseLines(lines: List<String>): List<Block> {
    val blocks = mutableListOf<Block>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.isBlank() -> i++
            isFenceStart(line) -> {
                val (block, next) = parseCodeFence(lines, i)
                blocks += block; i = next
            }
            isThematicBreak(line) -> { blocks += Block.ThematicBreak; i++ }
            isHeading(line) -> { blocks += parseHeading(line); i++ }
            isBlockquoteStart(line) -> {
                val (block, next) = parseBlockquote(lines, i)
                blocks += block; i = next
            }
            isTableStart(lines, i) -> {
                val (block, next) = parseTable(lines, i)
                blocks += block; i = next
            }
            matchListItem(line) != null -> {
                val (block, next) = parseList(lines, i)
                blocks += block; i = next
            }
            else -> {
                val (block, next) = parseParagraph(lines, i)
                blocks += block; i = next
            }
        }
    }
    return blocks
}

private fun parseHeading(line: String): Block.Heading {
    val m = HEADING_RE.matchEntire(line)!!
    val level = m.groupValues[1].length
    val content = m.groupValues[2].trimEnd('#').trim()
    return Block.Heading(level, parseInline(content))
}

/** Consumes a ```lang ... ``` fence. An unterminated fence runs to end-of-document rather than vanishing. */
private fun parseCodeFence(lines: List<String>, start: Int): Pair<Block.CodeBlock, Int> {
    val lang = lines[start].trim().removePrefix("```").trim().ifBlank { null }
    val code = mutableListOf<String>()
    var j = start + 1
    while (j < lines.size && lines[j].trim() != "```") {
        code += lines[j]; j++
    }
    val next = if (j < lines.size) j + 1 else j
    return Block.CodeBlock(lang, code.joinToString("\n")) to next
}

/** Consumes consecutive `> ...` lines, strips the marker, and recursively parses what's left so a quote can hold any other block type. */
private fun parseBlockquote(lines: List<String>, start: Int): Pair<Block.BlockQuote, Int> {
    val inner = mutableListOf<String>()
    var j = start
    while (j < lines.size && isBlockquoteStart(lines[j])) {
        inner += lines[j].trimStart().removePrefix(">").removePrefix(" ")
        j++
    }
    return Block.BlockQuote(parseLines(inner)) to j
}

private fun parseTable(lines: List<String>, start: Int): Pair<Block.Table, Int> {
    fun splitRow(line: String): List<String> {
        var l = line.trim()
        if (l.startsWith("|")) l = l.substring(1)
        if (l.endsWith("|")) l = l.substring(0, l.length - 1)
        return l.split("|").map { it.trim() }
    }

    val headerCells = splitRow(lines[start])
    val sepCells = splitRow(lines[start + 1])
    val alignments = sepCells.map { cell ->
        val left = cell.startsWith(":")
        val right = cell.endsWith(":")
        when {
            left && right -> TableAlign.CENTER
            right -> TableAlign.RIGHT
            left -> TableAlign.LEFT
            else -> TableAlign.NONE
        }
    }

    val rawRows = mutableListOf<List<String>>()
    var j = start + 2
    while (j < lines.size && lines[j].contains("|") && lines[j].isNotBlank()) {
        rawRows += splitRow(lines[j]); j++
    }

    return Block.Table(
        header = headerCells.map { parseInline(it) },
        alignments = alignments,
        rows = rawRows.map { row -> row.map { parseInline(it) } }
    ) to j
}

/**
 * Gathers items at one indentation level, then recursively re-parses
 * each item's own (dedented) content — this is what lets a list item
 * contain a nested sub-list, a wrapped second line, or even its own
 * code fence, without a separate code path for each case.
 */
private fun parseList(lines: List<String>, start: Int): Pair<Block.ListBlock, Int> {
    val first = matchListItem(lines[start])!!
    val baseIndent = first.indent
    val ordered = first.ordered

    data class RawItem(val firstLine: String, val continuation: MutableList<String> = mutableListOf())
    val rawItems = mutableListOf(RawItem(first.content))
    var j = start + 1

    while (j < lines.size) {
        val line = lines[j]
        val m = matchListItem(line)
        when {
            m != null && m.ordered == ordered && m.indent <= baseIndent + 1 -> {
                rawItems += RawItem(m.content); j++
            }
            line.isBlank() -> {
                val peek = lines.getOrNull(j + 1)
                if (peek != null && (leadingSpaces(peek) > baseIndent || matchListItem(peek)?.let { it.indent <= baseIndent + 1 } == true)) {
                    rawItems.last().continuation += ""; j++
                } else break
            }
            leadingSpaces(line) > baseIndent -> { rawItems.last().continuation += line; j++ }
            else -> break
        }
    }

    val dedentWidth = baseIndent + 2
    val items = rawItems.map { raw ->
        val subLines = mutableListOf(raw.firstLine)
        raw.continuation.forEach { line ->
            subLines += if (line.length >= dedentWidth && line.take(dedentWidth).all { it == ' ' }) {
                line.substring(dedentWidth)
            } else line.trimStart()
        }
        ListItem(parseLines(subLines))
    }

    return Block.ListBlock(ordered, first.number ?: 1, items) to j
}

private fun parseParagraph(lines: List<String>, start: Int): Pair<Block.Paragraph, Int> {
    val collected = mutableListOf<String>()
    var j = start
    while (j < lines.size) {
        val line = lines[j]
        if (line.isBlank()) break
        if (isFenceStart(line) || isHeading(line) || isThematicBreak(line) ||
            isBlockquoteStart(line) || matchListItem(line) != null || isTableStart(lines, j)
        ) break
        collected += line; j++
    }
    return Block.Paragraph(parseInline(collected.joinToString("\n"))) to j
}

package com.sednium.localspaces.markdown

/**
 * Parses one block's worth of raw text into inline nodes. Runs as a
 * left-to-right single pass: at each position, the next "special"
 * character is dispatched to its own handler, which looks ahead for a
 * matching closer and recurses on the content in between (so `**a *b*
 * c**` nests an Italic inside a Bold correctly, and a link's display
 * text can itself contain emphasis).
 *
 * Code spans are handled first in the scan order and their contents are
 * never recursed into — backticks suppress markdown parsing inside them,
 * exactly as the CommonMark spec requires, which is what stops `` `**not
 * bold**` `` from being misread as emphasis.
 */
private val URL_START = Regex("""^https?://\S+""")
private val ESCAPABLE = "\\`*_{}[]()#+-.!~>".toSet()

fun parseInline(text: String): List<Inline> {
    val result = mutableListOf<Inline>()
    val plain = StringBuilder()
    var i = 0

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            result += Inline.Text(plain.toString())
            plain.clear()
        }
    }

    while (i < text.length) {
        val c = text[i]
        when {
            // Backslash escapes: \* \_ \` etc. become the literal character.
            c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE -> {
                plain.append(text[i + 1])
                i += 2
            }

            c == '`' -> {
                val tickRun = countRun(text, i, '`')
                val openEnd = i + tickRun
                val closeStart = findClosingTickRun(text, openEnd, tickRun)
                if (closeStart != -1) {
                    flushPlain()
                    result += Inline.Code(text.substring(openEnd, closeStart).trim())
                    i = closeStart + tickRun
                } else {
                    plain.append(c); i++
                }
            }

            c == '[' -> {
                val link = tryParseLink(text, i)
                if (link != null) {
                    flushPlain()
                    result += Inline.Link(parseInline(link.label), link.url)
                    i = link.endIndex
                } else {
                    plain.append(c); i++
                }
            }

            c == '*' || c == '_' -> {
                val run = countRun(text, i, c)
                val markerLen = minOf(run, 3)
                val closeStart = findClosingDelimiterRun(text, i + markerLen, c, markerLen)
                if (closeStart != -1) {
                    flushPlain()
                    val inner = parseInline(text.substring(i + markerLen, closeStart))
                    result += when (markerLen) {
                        1 -> Inline.Italic(inner)
                        2 -> Inline.Bold(inner)
                        else -> Inline.Bold(listOf(Inline.Italic(inner))) // *** = bold+italic
                    }
                    i = closeStart + markerLen
                } else {
                    plain.append(c.toString().repeat(run)); i += run
                }
            }

            c == '~' && i + 1 < text.length && text[i + 1] == '~' -> {
                val closeStart = text.indexOf("~~", i + 2)
                if (closeStart != -1) {
                    flushPlain()
                    result += Inline.Strikethrough(parseInline(text.substring(i + 2, closeStart)))
                    i = closeStart + 2
                } else {
                    plain.append(c); i++
                }
            }

            // Bare autolink: a raw http(s):// URL with no [text](url) wrapper.
            c == 'h' -> {
                val match = URL_START.find(text.substring(i))
                if (match != null) {
                    flushPlain()
                    val url = match.value.trimEnd('.', ',', ')', '!', '?')
                    result += Inline.Link(listOf(Inline.Text(url)), url)
                    i += url.length
                } else {
                    plain.append(c); i++
                }
            }

            c == '\n' -> {
                flushPlain()
                result += Inline.LineBreak
                i++
            }

            else -> { plain.append(c); i++ }
        }
    }
    flushPlain()
    return result
}

/** Counts how many consecutive copies of `ch` appear starting at `from`. */
private fun countRun(text: String, from: Int, ch: Char): Int {
    var n = 0
    while (from + n < text.length && text[from + n] == ch) n++
    return n
}

/** Finds the start index of the next run of exactly `len` backticks (a valid code-span closer). */
private fun findClosingTickRun(text: String, from: Int, len: Int): Int {
    var idx = from
    while (idx < text.length) {
        if (text[idx] == '`') {
            val run = countRun(text, idx, '`')
            if (run == len) return idx
            idx += run
        } else idx++
    }
    return -1
}

/**
 * Finds the start of the next run of `ch` of length >= `len`, skipping
 * over any backtick code span encountered along the way so emphasis
 * markers never get matched across one (mirrors the code-span-first
 * precedence rule above).
 */
private fun findClosingDelimiterRun(text: String, from: Int, ch: Char, len: Int): Int {
    var idx = from
    while (idx < text.length) {
        when {
            text[idx] == '`' -> {
                val tickRun = countRun(text, idx, '`')
                val close = findClosingTickRun(text, idx + tickRun, tickRun)
                idx = if (close != -1) close + tickRun else idx + tickRun
            }
            text[idx] == ch -> {
                val run = countRun(text, idx, ch)
                if (run >= len) return idx
                idx += run
            }
            else -> idx++
        }
    }
    return -1
}

private data class LinkMatch(val label: String, val url: String, val endIndex: Int)

/** Parses a `[label](url)` construct starting at the '[' index, or null if it isn't well-formed. */
private fun tryParseLink(text: String, start: Int): LinkMatch? {
    var depth = 1
    var i = start + 1
    val labelStart = i
    while (i < text.length && depth > 0) {
        when (text[i]) {
            '[' -> depth++
            ']' -> depth--
        }
        if (depth == 0) break
        i++
    }
    if (depth != 0 || i >= text.length) return null
    val label = text.substring(labelStart, i)

    var j = i + 1
    if (j >= text.length || text[j] != '(') return null
    j++
    val urlStart = j
    while (j < text.length && text[j] != ')') j++
    if (j >= text.length) return null
    val url = text.substring(urlStart, j).trim()
    return LinkMatch(label, url, j + 1)
}

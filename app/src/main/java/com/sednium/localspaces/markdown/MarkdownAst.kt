package com.sednium.localspaces.markdown

/**
 * A real (if intentionally scoped) Markdown AST — replaces the earlier
 * line-by-line regex pass with a proper two-stage parse: block structure
 * first (MarkdownBlockParser), inline spans second (MarkdownInlineParser),
 * then a single Composable walks the tree to render it (MarkdownView).
 *
 * Scope: ATX headings, fenced code blocks, blockquotes, ordered/unordered
 * lists (with one level of recursive nesting), thematic breaks, GFM pipe
 * tables, and paragraphs — everything the chat models in this app
 * actually produce. Full CommonMark compliance (setext headings, HTML
 * blocks, reference-style links, loose/tight list distinctions, etc.) is
 * out of scope by design; each gap is a self-contained addition to
 * MarkdownBlockParser without touching the renderer.
 */
sealed class Block {
    data class Heading(val level: Int, val inline: List<Inline>) : Block()
    data class Paragraph(val inline: List<Inline>) : Block()
    data class CodeBlock(val language: String?, val code: String) : Block()
    data class BlockQuote(val children: List<Block>) : Block()
    data class ListBlock(val ordered: Boolean, val startNumber: Int, val items: List<ListItem>) : Block()
    data object ThematicBreak : Block()
    data class Table(
        val header: List<List<Inline>>,
        val alignments: List<TableAlign>,
        val rows: List<List<List<Inline>>>
    ) : Block()
}

data class ListItem(val children: List<Block>)

enum class TableAlign { LEFT, CENTER, RIGHT, NONE }

sealed class Inline {
    data class Text(val text: String) : Inline()
    data class Bold(val children: List<Inline>) : Inline()
    data class Italic(val children: List<Inline>) : Inline()
    data class Strikethrough(val children: List<Inline>) : Inline()
    data class Code(val text: String) : Inline()
    data class Link(val children: List<Inline>, val url: String) : Inline()
    data object LineBreak : Inline()
}

package com.kitsugi.animelist.core.diagnostics

/** Keeps the header and the blocked main-thread stack instead of only ART runtime metadata. */
internal object AnrTraceSelector {
    fun select(lines: List<String>, maxLines: Int = 160): String {
        if (lines.size <= maxLines) return lines.joinToString("\n")
        val mainStart = lines.indexOfFirst { it.trimStart().startsWith("\"main\"") }
        val header = lines.take(12)
        if (mainStart < 0) return lines.take(maxLines).joinToString("\n")
        val mainBlock = lines.drop(mainStart).takeWhile { it.isNotBlank() }.take(maxLines - header.size - 2)
        return (header + listOf("", "--- main thread ---") + mainBlock).joinToString("\n")
    }
}

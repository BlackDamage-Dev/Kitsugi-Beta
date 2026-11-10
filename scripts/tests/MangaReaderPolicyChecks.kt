package com.kitsugi.animelist.audit

import com.kitsugi.animelist.ui.screens.manga.MangaReaderRegressionCases

fun main() {
    val count = MangaReaderRegressionCases.runAll { println("PASS $it") }
    println("$count manga reader policy groups passed (no Android rendering simulated).")
}

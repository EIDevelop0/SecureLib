package com.securelib.securecheck.check

import java.io.File

internal class FridaCheck : SecurityCheck {
    override val name: String = NAME

    override suspend fun evaluate(): Boolean {
        val fridaFound = File("/proc/self/maps").useLines { lines ->
            lines.any { line -> MARKERS.any { line.contains(it, ignoreCase = true) } }
        }
        return !fridaFound
    }

    companion object {
        const val NAME = "FridaCheck"

        // Substrings that appear in a memory map when Frida (server or gadget)
        // is injected into the process.
        private val MARKERS = listOf(
            "frida",
            "gum-js-loop",
            "gadget",
        )
    }
}

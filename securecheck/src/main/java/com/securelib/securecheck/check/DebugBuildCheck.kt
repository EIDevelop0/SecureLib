package com.securelib.securecheck.check

import android.content.Context
import android.content.pm.ApplicationInfo

internal class DebugBuildCheck(
    private val context: Context,
) : SecurityCheck {
    override val name: String = NAME

    override suspend fun evaluate(): Boolean {
        val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return !isDebuggable
    }

    companion object {
        const val NAME = "DebugBuildCheck"
    }
}

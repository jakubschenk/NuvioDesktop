package com.nuvio.app.desktop

import java.util.Locale
import kotlin.system.exitProcess

internal object DesktopAppRestarter {
    fun restart(): Boolean {
        val processInfo = ProcessHandle.current().info()
        val command = processInfo.command().orElse(null)
            ?.takeIf(String::isNotBlank)
            ?: return false
        val arguments = processInfo.arguments().orElse(emptyArray()).toList()

        return if (isWindows()) {
            restartOnWindows(command = command, arguments = arguments)
        } else {
            restartDirect(command = command, arguments = arguments)
        }
    }

    private fun restartOnWindows(
        command: String,
        arguments: List<String>,
    ): Boolean =
        try {
            val currentPid = ProcessHandle.current().pid()
            val workingDirectory = System.getProperty("user.dir") ?: "."
            val argumentList = arguments.joinToString(
                separator = ", ",
                prefix = "@(",
                postfix = ")",
                transform = ::powershellString,
            )
            val script = """
                ${'$'}ErrorActionPreference = 'SilentlyContinue'
                Wait-Process -Id $currentPid
                Start-Sleep -Milliseconds 250
                Start-Process -FilePath ${powershellString(command)} -ArgumentList $argumentList -WorkingDirectory ${powershellString(workingDirectory)}
            """.trimIndent()
            ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-WindowStyle",
                "Hidden",
                "-Command",
                script,
            ).start()
            DesktopRuntimeLog.info("restart helper spawned command=$command args=${arguments.size}")
            exitProcess(0)
        } catch (throwable: Throwable) {
            DesktopRuntimeLog.error("restart helper failed", throwable)
            false
        }

    private fun restartDirect(
        command: String,
        arguments: List<String>,
    ): Boolean =
        try {
            ProcessBuilder(listOf(command) + arguments).start()
            DesktopRuntimeLog.info("restart spawned command=$command args=${arguments.size}")
            exitProcess(0)
        } catch (throwable: Throwable) {
            DesktopRuntimeLog.error("restart failed", throwable)
            false
        }

    private fun powershellString(value: String): String =
        "'" + value.replace("'", "''") + "'"

    private fun isWindows(): Boolean =
        System.getProperty("os.name")
            ?.lowercase(Locale.US)
            ?.contains("windows") == true
}

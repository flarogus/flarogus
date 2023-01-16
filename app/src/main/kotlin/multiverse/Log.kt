package flarogus.multiverse

import flarogus.*
import flarogus.multiverse.state.StateManager
import flarogus.util.*
import java.io.*
import kotlinx.coroutines.*
import kotlinx.datetime.*

/** Logs stuff. Messages are evaluated lazily (only if the message will be sent) */
@SuppressWarnings("NOTHING_TO_INLINE")
object Log {
	var level = LogLevel.DEBUG
		set(level: LogLevel) {
			if (level.level > LogLevel.ERROR.level) throw IllegalArgumentException("illegal log level")
			field = level
		}
	/** Control sequence introducer. */
	val csi = "\u001B["
	val logDir = StateManager.flarogusDir.resolve("log").ensureDir()
	val stacktraceDir = logDir.resolve("stacktrace").ensureDir()
	val currentLogFile = run {
		val now = Clock.System.now().toJavaInstant().formatUTC()
		logDir.resolve("log-$now.txt")
	}
	var currentLogWriter = currentLogFile.printWriter()

	inline fun log(logLevel: LogLevel, crossinline message: () -> String) {
		if (logLevel.level < level.level) return
		
		val timezone = TimeZone.currentSystemDefault()
		val time = Vars.dateFormatter.format(
			Clock.System.now().toLocalDateTime(timezone).toJavaLocalDateTime())
		val msg = message()
		
		logLevel.color.let {
			// set the color
			val r = (it and 0xff0000) shr 16
			val g = (it and 0xff00) shr 8
			val b = it and 0xff
			print("${csi}38;2;${r};${g};${b}m") 
		}
		print("[$time][$logLevel]")
		print("${csi}0m") // reset the color
		println(": $msg")

		currentLogWriter.println("[$time][$logLevel] $msg")
	}
	
	inline fun lifecycle(crossinline message: () -> String) = log(LogLevel.LIFECYCLE, message)
	
	inline fun debug(crossinline message: () -> String) = log(LogLevel.DEBUG, message)
	
	inline fun info(crossinline message: () -> String) = log(LogLevel.INFO, message)
	
	inline fun error(crossinline message: () -> String) = log(LogLevel.ERROR, message)

	inline fun error(throwable: Throwable, crossinline message: () -> String) {
		val file = printStackTrace(throwable)

		log(LogLevel.ERROR) { "${message()}: $throwable. Stacktrace saved to ${file.absolutePath}."  }
	}

	inline fun force(crossinline message: () -> String) = log(LogLevel.FORCE, message)

	/** Saves the stacktrace to the stacktrace directory. Returns the created file. */
	fun printStackTrace(throwable: Throwable) = run {
		getStacktraceFile(throwable, Clock.System.now()).also {
			it.writeText(throwable.stackTraceToString())
		}
	}

	fun getStacktraceFile(throwable: Throwable, instant: Instant): File {
		val time = instant.toJavaInstant().formatUTC()
		val cls = throwable::class.java.simpleName.orEmpty()

		return stacktraceDir.resolve("stacktrace-$time-$cls.txt")
	}
	
	enum class LogLevel(val level: Int, val color: Int) {
		LIFECYCLE(0, 0xAFB42B), 
		DEBUG(1, 0x00897B), 
		INFO(2, 0x3F51B5), 
		ERROR(3, 0xF50057), 
		FORCE(4, 0x999999);
		
		companion object {
			fun of(level: Int) = values().find { it.level == level } ?: INFO
		}
	}	
}

/** Logs the message if [this] is true. Returns [this]. */
inline fun Boolean.andLog(
	logLevel: Log.LogLevel = Log.LogLevel.INFO,
	crossinline message: () -> String
) = apply {
	if (this) Log.log(logLevel, message)
}

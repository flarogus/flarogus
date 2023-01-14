package flarogus.multiverse

import flarogus.*
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
	
	inline fun log(logLevel: LogLevel, crossinline message: () -> String) {
		if (logLevel.level < level.level) return
		
		val timezone = TimeZone.currentSystemDefault()
		val time = Vars.dateFormatter.format(
			Clock.System.now().toLocalDateTime(timezone).toJavaLocalDateTime())
		
		println("[$time][$logLevel]: ${message()}")
	}
	
	inline fun lifecycle(crossinline message: () -> String) = log(LogLevel.LIFECYCLE, message)
	
	inline fun debug(crossinline message: () -> String) = log(LogLevel.DEBUG, message)
	
	inline fun info(crossinline message: () -> String) = log(LogLevel.INFO, message)
	
	inline fun error(crossinline message: () -> String) = log(LogLevel.ERROR, message)

	inline fun error(throwable: Throwable, crossinline message: () -> String) {
		log(LogLevel.ERROR) { "${message()}: $throwable"  }
		//printStackTrace(throwable)
	}

	inline fun force(crossinline message: () -> String) = log(LogLevel.FORCE, message)

	fun printStackTrace(throwable: Throwable) {
		// todo: print the stacktrace to a file
		throwable.printStackTrace()
	}
	
	enum class LogLevel(val level: Int) {
		LIFECYCLE(0), DEBUG(1), INFO(2), ERROR(3), FORCE(4);
		
		companion object {
			fun of(level: Int) = values().find { it.level == level } ?: INFO
		}
	}	
}

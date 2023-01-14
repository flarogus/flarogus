package flarogus.multiverse

import java.util.*
import kotlin.concurrent.*
import kotlinx.coroutines.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.channel.*
import flarogus.*
import flarogus.util.*

/** Logs stuff. Messages are evaluated lazily (only if the message will be sent) */
@SuppressWarnings("NOTHING_TO_INLINE")
object Log {
	var level = LogLevel.LIFECYCLE
		set(level: LogLevel) {
			if (level.level > LogLevel.ERROR.level) throw IllegalArgumentException("illegal log level")
			field = level 
		}
	
	inline fun log(logLevel: LogLevel, crossinline message: () -> String) {
		if (logLevel.level < level.level) return
		
		val prefix = if (logLevel == LogLevel.ERROR) "! ERROR !" else logLevel.toString()
		
		println("[$prefix]: ${message()}")
		// synchronized(buffer) {
		// 	buffer.add("**[$prefix]**: ${message()}".stripEveryone().take(1999))
		// }
	}
	
	inline fun lifecycle(crossinline message: () -> String) = log(LogLevel.LIFECYCLE, message)
	
	inline fun debug(crossinline message: () -> String) = log(LogLevel.DEBUG, message)
	
	inline fun info(crossinline message: () -> String) = log(LogLevel.INFO, message)
	
	inline fun error(crossinline message: () -> String) = log(LogLevel.ERROR, message)

	inline fun error(throwable: Throwable, crossinline message: () -> String) {
		log(LogLevel.ERROR) { "${message()}: $throwable"  }
		printStackTrace(throwable)
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

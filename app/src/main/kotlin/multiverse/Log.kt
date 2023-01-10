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
	var level = LogLevel.INFO
		set(level: LogLevel) {
			if (level.level > LogLevel.ERROR.level) throw IllegalArgumentException("illegal log level")
			field = level 
		}
	// val logChannel by lazy { Vars.client.unsafe.messageChannel(Channels.log) }

	// val buffer = ArrayList<String>()
	// lateinit var logTimer: Timer

	fun setup() {
		// logTimer = fixedRateTimer(daemon = true, period = 2000L) {
		// 	while (!buffer.isEmpty()) synchronized(buffer) {
		// 		val message = buildString {
		// 			while (!buffer.isEmpty() && length + buffer.first().length < 2000) {
		// 				val entry = buffer.removeFirst()
		// 				appendLine(entry.take(2000))
		// 			}
		// 		}
		// 	
		// 		runBlocking {
		// 			logChannel.createMessage {
		// 				content = message
		// 				allowedMentions()
		// 			}
		// 		}
		// 	}
		// }
	}
	
	inline fun sendLog(logLevel: LogLevel, crossinline message: () -> String) {
		if (logLevel.level < level.level) return
		
		val prefix = if (logLevel == LogLevel.ERROR) "! ERROR !" else logLevel.toString()
		
		println("[$prefix]: ${message()}")
		// synchronized(buffer) {
		// 	buffer.add("**[$prefix]**: ${message()}".stripEveryone().take(1999))
		// }
	}
	
	inline fun lifecycle(crossinline message: () -> String) = sendLog(LogLevel.LIFECYCLE, message);
	
	inline fun debug(crossinline message: () -> String) = sendLog(LogLevel.DEBUG, message);
	
	inline fun info(crossinline message: () -> String) = sendLog(LogLevel.INFO, message);
	
	inline fun error(crossinline message: () -> String) = sendLog(LogLevel.ERROR, message);

	inline fun force(crossinline message: () -> String) = sendLog(LogLevel.FORCE, message)
	
	enum class LogLevel(val level: Int) {
		LIFECYCLE(0), DEBUG(1), INFO(2), ERROR(3), FORCE(4);
		
		companion object {
			fun of(level: Int) = values().find { it.level == level } ?: INFO
		}
	}	
}

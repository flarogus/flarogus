package flarogus.multiverse

import kotlinx.coroutines.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.channel.*
import flarogus.*
import flarogus.util.*

/** Logs stuff. Messages are evaluated lazily (only if the message _will_ be sent) */
@SuppressWarnings("NOTHING_TO_INLINE")
object Log {
	
	var level = LogLevel.INFO
		set(level: LogLevel) { if (level.level > LogLevel.ERROR.level) throw IllegalArgumentException("illegal log level") else field = level }
	val logChannelId = Snowflake(942139405718663209UL)
	val logChannel by lazy { Vars.client.unsafe.messageChannel(logChannelId) }
	
	inline fun sendLog(logLevel: LogLevel, crossinline message: () -> String) {
		if (logLevel.level < level.level) return
		
		Vars.client.launch {
			try {
				val prefix = if (logLevel == LogLevel.ERROR) "! ERROR !" else logLevel.toString()
				
				logChannel.createMessage {
					content = "**[$prefix]**: ${message()}".stripEveryone().take(1999)
					allowedMentions()
				}
			} catch (e: Exception) {
				e.printStackTrace()
			}
		};
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

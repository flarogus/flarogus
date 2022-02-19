package flarogus.multiverse

import kotlinx.coroutines.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.entity.channel.*
import flarogus.*
import flarogus.util.*

/** Logs stuff. Messages are evaluated lazily (only if the message _will_ be sent) */
object Log {
	
	var level = LogLevel.INFO
	val logChannelId = Snowflake(942139405718663209UL)
	val logChannel by lazy { Vars.client.unsafe.messageChannel(logChannelId) }
	
	fun sendLog(logLevel: LogLevel, message: () -> String) = Vars.client.launch {
		if (logLevel.level < level.level) return@launch
		
		try {
			val prefix = if (logLevel == LogLevel.ERROR) "! error !" else logLevel.toString()
			
			logChannel.createMessage("**[$prefix]**: ${message()}".stripEveryone().take(1999))
		} catch (e: Exception) {
			e.printStackTrace()
		}
	};
	
	inline fun lifecycle(noinline message: () -> String) = sendLog(LogLevel.LIFECYCLE, message);
	
	inline fun debug(noinline message: () -> String) = sendLog(LogLevel.DEBUG, message);
	
	inline fun info(noinline message: () -> String) = sendLog(LogLevel.INFO, message);
	
	inline fun error(noinline message: () -> String) = sendLog(LogLevel.ERROR, message);
	
	enum class LogLevel(val level: Int) {
		LIFECYCLE(0), DEBUG(1), INFO(2), ERROR(3);
		
		companion object {
			fun of(level: Int) = values().find { it.level == level } ?: INFO
		}
	}
	
}
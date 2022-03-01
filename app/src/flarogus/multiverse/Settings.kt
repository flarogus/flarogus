package flarogus.multiverse

import java.io.*
import java.net.*
import kotlin.math.*
import kotlin.concurrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.event.message.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.*
import flarogus.util.*

typealias StateHandler = (SimpleMap) -> Unit

object Settings {

	val settingsPrefix = "@set"
	val settingsChannel = Snowflake(937781472394358784UL)
	
	val loadHandlers = ArrayList<StateHandler>(50)
	val saveHandlers = ArrayList<StateHandler>(50)
	
	val tempMap = HashMap<String, Any>()
	
	init {
		//add default handlers
		onLoad {
			it.getOrDefault("runWhitelist", null)?.readAsArray<ULong> { 
				Vars.runWhitelist.add(it)
			}
		}
		onSave { it["runWhitelist"] = Vars.runWhitelist.toTypedArray() }
		
		onLoad {
			it.getOrDefault("epoch", null)?.asOrNull<Long>() ?: System.currentTimeMillis()?.let { Vars.flarogusEpoch = it }
		}
		onSave { it["epoch"] = Vars.flarogusEpoch }
		
		onLoad {
			Log.LogLevel.of(it.getOrDefault("log", null)?.asOrNull<Int>() ?: -1)?.let { Log.level = it }
		}
		onSave { it["log"] = Log.level.level }
		
		onLoad {	
			//TODO: native nested map support?
			val k = it.getOrDefault("warnsK", null) as? Array<*> //array of ulong
			val v = it.getOrDefault("warnsV", null) as? Array<*> //array of arrays of string
			if (k != null && v != null) {
				k.readAsArrayIndexed<ULong> { i: Int, user ->
					val rules = v.getOrNull(i) ?: return@readAsArrayIndexed
					val id = Snowflake(user)
					
					//any invalid entries will just be logged
					rules?.readAsArray<String> {
						try {
							val r = it.split(':').let { RuleCategory.of(it[0].toInt(), it[1].toInt())!! }
							(Lists.warns.getOrDefault(id, null) ?: ArrayList<Rule>(3).also { Lists.warns[id] = it }).add(r)
						} catch (e: Exception) {
							Log.error { "Could not read warn entry: $e" }
						}
					}
				}
			}
		}
		onSave {
			it["warnsK"] = Lists.warns.keys.map { it.value }.toTypedArray()
			//todo: this is as inefficient as it looks like
			it["warnsV"] = Lists.warns.`values`.map { it.map { "${it.category}:${it.index}" }.toTypedArray() }.toTypedArray()
		}
	}
	
	fun onLoad(action: StateHandler) = loadHandlers.add(action);
	
	fun onSave(action: StateHandler) = saveHandlers.add(action);
	
	suspend fun updateState() {
		var found = false
		
		fetchMessages(settingsChannel) {
			if (it.author?.id?.value == Vars.botId && it.content.startsWith(settingsPrefix)) {
				val map = SimpleMapSerializer.deserialize(it.content.substring(settingsPrefix.length))
				
				//throwing an exception is perfectly fine here
				if (map.getOrDefault("ubid", Vars.ubid) as? String != Vars.ubid) {
					if (map.getOrDefault("started", 0L) as? Long ?: 0L > Vars.startedAt) {
						//shutdown if there's a newer instance running
						Multiverse.brodcastSystem {
							embed { description = "another instance is running, shutting the current one down" }
						}
						
						Vars.client.shutdown()
					} else {
						//notify handlers
						loadHandlers.forEach {
							try {
								it(map)
							} catch (e: Exception) {
								Log.error { "exception has occurred during state loading: $e" }
							}
						}
					}
				}
				
				//save state. this must NOT happen if the current state couldn't be loaded from this message, as that will lead to save corruption
				tempMap.clear()
				
				tempMap["ubid"] = Vars.ubid
				tempMap["started"] = Vars.startedAt
				saveHandlers.forEach {
					try {
						it(tempMap)
					} catch (e: Exception) {
						Log.error { "Exception has occurred during state saving: $e" }
					}
				}
				
				try {
					it.edit {
						val serialized = SimpleMapSerializer.serialize(tempMap)
						content = settingsPrefix + serialized
						
						if (serialized.length > 1200) Log.info { "WARNING: the length of serialized settings has exceeded 1200 symbols: ${serialized.length}" }
					}
				} catch (e: SimpleMapSerializer.DeserializationContext.MalformedInputException) {
					Log.error { "exception has occurred during settings serialization: $e" }
					throw e
				}
				
				found = true
				throw Error() //exit from fetchMessages if both loading and saving were succeful. peak comedy, yes. throwing an error on success.
			}
		}
		
		if (!found) {
			try {
				//this message will or will not be used on the next call of updateState()
				Vars.client.unsafe.messageChannel(settingsChannel).createMessage(settingsPrefix)
				
				//wait for 30 seconds and try again — this might have been a discord issue.
				//this instance must not start until we notify other instances about it's existence.
				delay(1000L * 30)
				updateState()
			} catch (ignored: Exception) {}
		}
	}

}
	
inline fun <reified T> Any.asOrNull(): T? = if (this is T) this else null;

inline fun <reified T> Any.readAsArray(crossinline reader: (T) -> Unit) {
	if (this is Array<*>) this.forEach { if (it is T) reader(it) }
}

inline fun <reified T> Any.readAsArrayIndexed(crossinline reader: (index: Int, T) -> Unit) {
	if (this is Array<*>) this.forEachIndexed { index: Int, item -> if (item is T) reader(index, item) }
}

inline fun <T> T.catchAny(block: (T) -> Unit): Unit {
	try {
		block(this)
	} catch (ignored: Exception) {}
}

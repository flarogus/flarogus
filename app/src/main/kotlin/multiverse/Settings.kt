package flarogus.multiverse

import java.io.*
import java.net.*
import kotlin.math.*
import kotlin.concurrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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

	/** Legacy */
	val settingsPrefix = "@set"
	val settingsChannel by lazy { Vars.client.unsafe.messageChannel(Snowflake(937781472394358784UL)) }
	var settingsMessage: Message? = null
	val fileStorageChannel by lazy { Vars.client.unsafe.messageChannel(Snowflake(949667466156572742UL)) }
	
	/** Legacy */
	val loadHandlers = ArrayList<StateHandler>(50)
	/** Legacy */
	val saveHandlers = ArrayList<StateHandler>(50)
	
	/** Legacy */
	val tempMap = HashMap<String, Any>()
	
	//legacy
	init {
		//add default handlers
		onLoad {
			it.getOrDefault("runWhitelist", null)?.readAsArray<ULong> { 
				Vars.runWhitelist.add(it)
			}
		}
		onSave { it["runWhitelist"] = Vars.runWhitelist.toTypedArray() }
		
		onLoad {
			it.getOrDefault("epoch", null)?.asOrNull<Long>()?.let { Vars.flarogusEpoch = it }
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
	
	/** Legeacy */
	fun onLoad(action: StateHandler) = loadHandlers.add(action);
	
	/** Legacy */
	fun onSave(action: StateHandler) = saveHandlers.add(action);
	
	suspend fun updateState() {
		settingsMessage = settingsChannel.messages.first { (it.content.startsWith("http") || it.content == "placeholder") && it.data.author.id.value == Vars.botId }
		
		if (settingsMessage == null) legacyUpdateState()
		
		settingsMessage?.let {
			if (it.content.startsWith("http")) {
				val stateContent = downloadFromCdn<String>(it.content)
				val state = Json.decodeFromString<State>(stateContent)
				
				if (state.ubid != Vars.ubid) {
					//load if the save is from older instance, shut down this instance if it's from newer, ignore otherwise
					if (state.startedAt > Vars.startedAt) {
						Multiverse.brodcastSystem { embed { description = "another instance is running, shutting the current one down" } }
						Vars.client.shutdown()
					} else {
						state.loadFromState()
					}
				}
			}
			
			val newState = State()
			val uploadedUrl = uploadToCdn(Json.encodeToString(newState))
			
			it.edit {
				content = uploadedUrl
			}
		}
	}
	
	/** Legacy method. left for backward compatibility */
	suspend internal fun legacyUpdateState() {
		var found = false
		
		fetchMessages(settingsChannel) {
			if (it.author?.id?.value == Vars.botId && it.content.startsWith(settingsPrefix)) {
				val map = SimpleMapSerializer.deserialize(it.content.substring(settingsPrefix.length))
				
				//throwing an exception is perfectly fine here
				if (map.getOrDefault("ubid", Vars.ubid) as? String != Vars.ubid) {
					if (map.getOrDefault("started", null) as Long > Vars.startedAt) {
						//shut down if there's a newer instance running
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
				
				it.edit {
					content = "placeholder"
				}
				
				settingsMessage = it
				
				/*
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
				*/
				throw Error() //exit from fetchMessages if both loading and saving were succeful. peak comedy, yes. throwing an error on success.
			}
		}
		
		if (!found) {
			try {
				//this message will or will not be used on the next call of updateState()
				settingsChannel.createMessage("placeholder")
				
				//wait 30 seconds and try again — this might have been a discord issue.
				//this instance must not start until we notify other instances about it's existence.
				delay(1000L * 30)
				updateState()
			} catch (ignored: Exception) {}
		}
	}
	
	/** Upload a string to the cdn. Returns the id of uploaded file */
	suspend fun uploadToCdn(data: String) = uploadToCdn(ByteArrayInputStream(data.toByteArray()));
	
	/** Upload the contents of the input stream to the cdn. returns the id of uploaded file */
	suspend fun uploadToCdn(data: InputStream): String {
		fileStorageChannel.createMessage {
			addFile("UPLOADED-DATA-${System.currentTimeMillis()}", data)
		}.also { return it.data.attachments.first().url }
	}
	
	/** Opposite of uploadToCdn(): downloads content from the uploaded file */
	inline suspend fun <reified T> downloadFromCdn(url: String) = Vars.client.resources.httpClient.get<HttpResponse>(url).receive<T>()

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

@kotlinx.serialization.Serializable
data class State(
	val ubid: String = Vars.ubid,
	val startedAt: Long = Vars.startedAt,
	var runWhitelist: Set<ULong> = Vars.runWhitelist,
	var epoch: Long = Vars.flarogusEpoch,
	var logLevel: Int = Log.level.level,
	var warns: Map<Snowflake, MutableList<Rule>> = Lists.warns
) {
	/** Updates the current bot state in accordance with this state */
	fun loadFromState() {
		Vars.runWhitelist.addAll(runWhitelist)
		Vars.flarogusEpoch = epoch
		Log.level = Log.LogLevel.of(logLevel)
		warns.forEach { user, warns ->
			Lists.warns[user] = warns
		}
	}
}

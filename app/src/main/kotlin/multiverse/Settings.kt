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

object Settings {

	val settingsChannel by lazy { Vars.client.unsafe.messageChannel(Snowflake(937781472394358784UL)) }
	var settingsMessage: Message? = null
	val fileStorageChannel by lazy { Vars.client.unsafe.messageChannel(Snowflake(949667466156572742UL)) }
	
	/** Tries to update the state. Retries up to $attempts - 1 times if an exception was thrown. */
	suspend fun updateState(attempts: Int = 3) {
		var attempt = 0
		while (true) {
			try {
				updateStateAttempt()
				break
			} catch (e: Exception) {
				if (++attempt >= attempts) throw e
			}
		}
	}
	
	/** Tries to update the state once */
	suspend fun updateStateAttempt() {
		settingsMessage = settingsChannel.messages.firstOrNull { (it.content.startsWith("http") || it.content == "placeholder") && it.data.author.id.value == Vars.botId }
		
		if (settingsMessage == null) {
			settingsMessage = settingsChannel.createMessage("placeholder")
		}
		
		settingsMessage?.let {
			if (it.content.startsWith("http")) {
				val stateContent = downloadFromCdn<String>(it.content)
				val state = Json.decodeFromString<State>(stateContent)
				
				if (state.ubid != Vars.ubid) {
					//load if the save is from older instance, shut down this instance if it's from newer, ignore otherwise
					if (state.startedAt > Vars.startedAt) {
						Log.info { "multiverse instance ${Vars.ubid} is shutting down (newer state was detected)" }
						Multiverse.brodcastSystem { embed { description = "another instance is running, shutting the current one down" } }
						Vars.client.shutdown()
					} else {
						state.loadFromState()
					}
				}
			}
			
			val newState = State()
			val newStateEncoded = Json { encodeDefaults = true }.encodeToString(newState)
			val uploadedUrl = uploadToCdn(newStateEncoded)
			
			it.edit {
				content = uploadedUrl
			}
		}
	}
	
	/** Uploads the providen string to cdn. Returns url of the uploaded file */
	suspend fun uploadToCdn(data: String) = uploadToCdn(ByteArrayInputStream(data.toByteArray()));
	
	/** Upload the contents of the input stream to cdn. returns url of the uploaded file */
	suspend fun uploadToCdn(data: InputStream): String {
		fileStorageChannel.createMessage {
			addFile("UPLOADED-DATA-${System.currentTimeMillis()}", data)
		}.also { return it.data.attachments.first().url }
	}
	
	/** Opposite of uploadToCdn(): downloads content from the uploaded file */
	inline suspend fun <reified T> downloadFromCdn(url: String) = Vars.client.resources.httpClient.get<HttpResponse>(url).receive<T>()

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

package flarogus.multiverse.state

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.*
import flarogus.Vars
import flarogus.multiverse.*
import flarogus.util.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.*

@OptIn(ExperimentalSerializationApi::class)
object StateManager {
	val flarogusDir = File(System.getProperty("user.home")).resolve(Config.dataDirectoryName).ensureDir()
	val backupDirectory = flarogusDir.resolve("backups").ensureDir()
	val dataFile = flarogusDir.resolve("data.bin").ensureFile()

	val fileStorageChannel by lazy { Vars.client.unsafe.messageChannel(Config.fileStorage) }
	val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = true
	}

	/** Last saved or loaded state */
	var lastState: StateSnapshot? = null
	/** Persistent data storage for temporary scripts. */
	val arbitraryData = HashMap<String, String>()

	/**
	 * Loads the current state from the provided file.
	 * Does not override the current state.
	 */
	suspend fun loadState(file: File = dataFile) =
		file.inputStream().use {
			json.decodeFromStream<StateSnapshot>(it).also { it.loadFromState() }
		}.also {
			lastState = it
		}

	/**
	 * Saves the state to the default file.
	 * Normally this is done every 60 seconds.
	 */
	suspend fun saveState(createBackup: Boolean = true) {
		if (createBackup && dataFile.exists()) {
			val instant = Clock.System.now()
			val backup = backupDirectory.resolve("data-$instant.bin").ensureFile()
			dataFile.copyTo(backup)
		}

		val newState = StateSnapshot()
		lastState = newState
		dataFile.outputStream().use {
			json.encodeToStream(newState, it)
		}
	}
	
	/** Uploads the providen string to cdn. Returns the url of the uploaded file */
	suspend fun uploadToCdn(data: String) = uploadToCdn(ByteArrayInputStream(data.toByteArray()))

	/** Upload the contents of the input stream to cdn. returns url of the uploaded file */
	suspend fun uploadToCdn(data: InputStream): String {
		fileStorageChannel.createMessage {
			addFile("UPLOADED-DATA-${System.currentTimeMillis()}", data)
		}.also { return it.data.attachments.first().url }
	}
	
	/** Opposite of uploadToCdn(): downloads content from the uploaded file */
	suspend inline fun <reified T> downloadFromCdn(url: String) =
		Vars.client.resources.httpClient.get(url).body<T>()
}

/** A utility class that stores the most important parts of the bot's state. Used for serialization. */
@kotlinx.serialization.Serializable
data class StateSnapshot(
	var runWhitelist: Set<Snowflake> = Vars.superusers,
	var epoch: Long = Vars.flarogusEpoch,
	var logLevel: Int = Log.level.level,
	val multiverse: Multiverse = Vars.multiverse,
	val arbitraryData: Map<String, String> = StateManager.arbitraryData
) {

	/** Updates the current bot state in accordance with this state. */
	fun loadFromState() {
		Vars.superusers.addAll(runWhitelist)
		Vars.flarogusEpoch = epoch
		Log.level = Log.LogLevel.of(logLevel)

		for ((k, v) in arbitraryData) {
			if (k !in StateManager.arbitraryData) {
				StateManager.arbitraryData[k] = v
			}
		}

		Vars.multiverse = multiverse
	}
}

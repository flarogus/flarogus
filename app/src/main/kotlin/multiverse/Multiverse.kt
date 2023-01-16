package flarogus.multiverse

import dev.kord.common.entity.*
import dev.kord.common.entity.optional.Optional
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.*
import dev.kord.core.event.message.*
import dev.kord.rest.builder.message.create.*
import flarogus.Vars
import flarogus.multiverse.Log.LogLevel.*
import flarogus.multiverse.entity.*
import flarogus.multiverse.state.*
import flarogus.util.*
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Manages the retraslantion of actions performed in one channel of the multiverse, aka a guild network,
 * into other channels.
 */
@Serializable
class Multiverse(
	val webhookName: String = "MultiverseWebhook",
	val systemName: String = "Multiverse",
	val systemAvatar: String = "https://cdn.discordapp.com/attachments/1045966829882970143/1045967037316481145/multiverse-system-v3.jpg",
	
	val history: ArrayList<Multimessage> = ArrayList(1000),
	val users: ArrayList<MultiversalUser> = ArrayList(90),
	val guilds: ArrayList<MultiversalGuild> = ArrayList(30),

	var lastBackup: Long = 0L,

	private val serviceData: MutableMap<String, MutableMap<String, String>> = HashMap()
) : CoroutineScope {
	/** If false, new messages will be ignored */
	@Transient
	var isRunning = false
	/** All registered services. */
	@Transient
	val services = ArrayList<MultiversalService>()
	/** All registered message filters. They're used by [MultiversalUser] to check whether a message cwn be sent. */
	@Transient
	val messageFilters = ArrayList<MultiversalUser.MessageFilter>()

	@Transient
	val rootJob = SupervisorJob()
	@Transient
	override val coroutineContext = rootJob + Dispatchers.Default
	@Transient
	private var modificationInterceptorJob: Job? = null
	@Transient
	private var deletionInterceptorJob: Job? = null
	@Transient
	private var findChannelsJob: Job? = null
	@Transient
	private var saveStateJob: Job? = null
	@Transient
	private var tickJob: Job? = null
	
	@Transient
	private val retranslationQueue = ArrayList<MultimessageDefinition>()
	@Transient
	private val modificationQueue = ArrayList<EventDefinition<MessageUpdateEvent>>()
	@Transient
	private val deletionQueue = ArrayList<EventDefinition<MessageDeleteEvent>>()

	@Transient
	val supportedMessageTypes = arrayOf(MessageType.Default, MessageType.Reply)

	suspend fun start() {
		services.forEach { it.onStart() }

		findChannels()

		 Vars.client.events
			.filterIsInstance<MessageDeleteEvent>()
			.filter { isRunning }
			.filter { event -> !isRetranslatedMessage(event.messageId) }
			.filter { event -> isMultiversalChannel(event.channelId) }
			.filter { event -> event.guildId != null && guildOf(event.guildId!!).let { it != null && !it.isForceBanned } }
			.onEach { event ->
				deletionQueue += EventDefinition(event, getConnectedGuilds().toMutableList())
			}
			.launchIn(this)
			.let { deletionInterceptorJob = it }
		
		Vars.client.events
			.filterIsInstance<MessageUpdateEvent>()
			.filter { isRunning }
			.filter { event -> !isRetranslatedMessage(event.messageId) }
			.filter { event -> isMultiversalChannel(event.message.channel.id) }
			.filter { event -> event.old?.content != null || event.new.content !is Optional.Missing } // discord sends fake update events sometimes
			.onEach { event ->
				modificationQueue += EventDefinition(event, getConnectedGuilds().toMutableList())
			}
			.launchIn(this)
			.also { modificationInterceptorJob = it }

		findChannelsJob = launch {
			while (true) {
				delay(1000L * 680) // finding channels is a costly operation
				findChannels()
			}
		}
		saveStateJob = launch {
			while (true) {
				// 2 backups per day
				val backup = System.currentTimeMillis() > lastBackup + 1000L * 60 * 60 * 12
				if (backup) {
					lastBackup = System.currentTimeMillis()
				}
				
				try {
					StateManager.saveState(backup)
					Log.lifecycle { "State saved" }
				} catch (e: Exception) {
					Log.error(e) { "Exception caught while attempting to save the state" }
				}
				delay(1000L * 30)
			}
		}
		tickJob = launch {
			while (true) {
				tick()
				delay(10L)
			}
		}

		isRunning = true
		services.forEach { it.onLoad() }
	}

	suspend fun stop() {
		isRunning = false
		services.forEach { it.onStop() }

		modificationInterceptorJob?.cancel()
		deletionInterceptorJob?.cancel()
		findChannelsJob?.cancel()
		saveStateJob?.cancel()
		tickJob?.cancel()
	}

	/** Updates all guilds flarogus is in and finds all accessible multiversal channels. */
	suspend fun findChannels() {
		try {
			Vars.client.rest.user.getCurrentUserGuilds().forEach {
				guildOf(it.id)?.update() //this will add an entry if it didn't exist
			}
		} catch (e: Exception) {
			Log.error(e) { "findChannels() failed" }
		}
	}

	/** Called when the multiverse should process a received message, */
	suspend fun onMessageReceived(event: MessageCreateEvent) {
		if (!isRunning || isOwnMessage(event.message)) return
		if (!guilds.any { it.channels.any { it.id == event.message.channel.id } }) return
		if (event.message.type !in supportedMessageTypes) return
		
		val user = userOf(event.message.data.author.id)
		val success = user?.onMultiversalMessage(event) ?: run {
			event.message.replyWith("No user associated with your user id was found!")
			return
		}

		services.forEach { it.onMessageReceived(event, success) }
	}

	/**
	 * Sends a message into every multiversal channel.
	 * Automatically adds the multimessage to the history.
	 */
	fun broadcastAsync(
		user: String? = null,
		avatar: String? = null,
		filter: (TextChannel) -> Boolean = { true },
		messageBuilder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	) = MultimessageDefinition(user, avatar, filter, messageBuilder, Multimessage(null, ArrayList(guilds.size))).also { def ->
		retranslationQueue.add(def)
		val candidates = guilds.filter { it.isWhitelisted && it.isValid && it.webhooks.isNotEmpty() }

		val now = System.currentTimeMillis()
		def.highPriority += candidates.filter {
			now - it.lastSent < 1000L * 60 * 60 * 3
		}
		def.mediumPriority += candidates.filter {
			now - it.lastSent in (1000L * 60 * 60 * 3)..(1000L * 60 * 60 * 24 * 3)
		}
		def.lowPriority += candidates.filter {
			now - it.lastSent > 1000L * 60 * 60 * 24 * 3
		}

		synchronized(history) {
			history.add(def.multimessage)
		}
	}

	/** Same as [broadcastAsync] but uses the system pfp & name */
	fun broadcastSystemAsync(message: suspend MessageCreateBuilder.(id: Snowflake) -> Unit) =
		broadcastAsync(systemName, systemAvatar, { true }, message)

	/** Returns a MultiversalUser with the given id, or null if it does not exist */
	suspend fun userOf(id: Snowflake): MultiversalUser? = 
		synchronized(users) { users.find { it.discordId == id } } ?: run {
			MultiversalUser(id).also { it.update() }.let {
				if (it.isValid) it.also {
					synchronized(users) { users.add(it) }
				} else null
			}
		}

	/** Returns a MultiversalGuild with the given id, or null if it does not exist */
	suspend fun guildOf(id: Snowflake): MultiversalGuild? =
		synchronized(guilds) { guilds.find { it.discordId == id } } ?: let {
			MultiversalGuild(id).also { it.update() }.let { 
				if (it.isValid) it.also {
					synchronized(guilds) { guilds.add(it) }
				} else null
			}
		}

	/** Must only be invoked after all guilds have been updated at least once. */
	fun getConnectedGuilds() =
		synchronized(guilds) {
			guilds.filter { it.isWhitelisted && it.isValid && !it.isForceBanned }
		}

	fun addService(service: MultiversalService) {
		service.multiverse = this
		services += service
	}

	fun addFilter(filter: MultiversalUser.MessageFilter) {
		messageFilters += filter
	}

	/** Peforms a single tick during which the most important action is determimed and executed. */
	suspend fun tick() {
		// firstly, if a message is to be deleted, it's retranslation/modification must be halted.
		// this shouldn't cause much lags since message deletion is a rare action
		deletionQueue.forEach { del ->
			val id = del.event.messageId

			retranslationQueue.removeAll {
				(it.multimessage.origin?.id == id).andLog(DEBUG) {
					"Message sent by ${it.user} was cancelled."
				}
			}
			modificationQueue.removeAll {
				(it.event.messageId == id).andLog(DEBUG) {
					val user = it.event.new.author.value?.id?.let { id -> users.find {
						it.discordId == id
					} }
					"Message modification of ${it.event.messageId} performed by $user was cancelled."
				}
			}
		}
		// we also need to remove and log all completed requests
		retranslationQueue.removeAll {
			it.isEmpty().andLog(DEBUG) {
				val totalTime = (System.currentTimeMillis() - it.creationTime) / 1000f
				"Message sent by ${it.user} was retranslated in $totalTime sec."
			}
		}
		modificationQueue.removeAll { def ->
			def.candidates.isEmpty().andLog(DEBUG) {
				val user = def.event.new.author.value?.id?.let { id -> users.find { it.discordId == id } }
				"Message ${def.event.messageId} was edited by ($user)."
			}
		}
		deletionQueue.removeAll { def ->
			def.candidates.isEmpty().andLog(DEBUG) {
				// avoid a possible api call
				val user = (def.multimessage.origin as? Message)?.author?.id?.let { id ->
					users.find { it.discordId == id }
				} ?: "<unknown>"
				"Message ${def.event.messageId} sent by $user was deleted by deleting the original message."
			}
		}

		// then, try to find a message with the highest priority to retranslate
		val definition =
			retranslationQueue.find { it.highPriority.isNotEmpty() }
			?: retranslationQueue.find { it.mediumPriority.isNotEmpty() }
			?: retranslationQueue.find { it.lowPriority.isNotEmpty() }

		// if there are retranslation candidates, retranslate them and return.
		if (definition != null) {
			val queue = with(definition) {
				highPriority.takeIf { it.isNotEmpty() }
				?: mediumPriority.takeIf { it.isNotEmpty() }
				?: lowPriority
			}

			coroutineScope {
				val messages = definition.multimessage.retranslated
				
				for (i in 0 until min(7, queue.size)) launch {
					val guild = queue[i]
					try {
						guild.send(
							username = definition.user,
							avatar = definition.avatar,
							filter = definition.filter,
							handler = { m, w -> 
								synchronized(messages) {
									messages.add(WebhookMessageBehavior(w, m))
								}
							},
							builder = definition.messageBuilder
						)
					} catch (e: Exception) {
						Log.error(e) { "An exception has occurred while retranslating a message to ${guild.name}" }
					}
				}
			}
			repeat(min(7, queue.size)) {
				queue.removeFirst()
			}

			return
		}

		// edits and deletions can be performed in any order
		// so we just perform them in each guild in order of recency
		deletionQueue.maxByOrNull { it.getImportance() }?.let { deletion ->
			try {
				val guild = deletion.candidates.maxBy { it.lastSent }.also(deletion.candidates::remove)

				if (!deletion.isInitialized) {
					deletion.multimessage = history.find { deletion.event.messageId in it } ?: run {
						Log.error { "Failed to modify ${deletion.event.messageId}: no corresponding multimessage" }
						deletionQueue.remove(deletion)
						return@let
					}
					deletion.isValidated = true
				}
				deletion.multimessage.retranslated.find { msg ->
					guild.channels.any { it.id == msg.channelId }
				}?.delete() ?: run {
					deletion.multimessage.origin?.delete()
				}
			} catch (e: Exception) {
				Log.error(e) { "Failed to delete ${deletion.event.messageId}" }
			}
		}
		modificationQueue.maxByOrNull { it.getImportance() }?.let { modification ->
			try {
				val newContent = modification.event.new.toRetranslatableContent()

				if (!modification.isInitialized) {
					val multimessage = history.find { modification.event.messageId in it } ?: run {
						Log.error { "Failed to modify ${modification.event.messageId}: no corresponding multimessage" }
						modificationQueue.remove(modification)
						return@let
					}
					// origin is already modified
					val oldContent = multimessage.retranslated.firstOrNull()?.asMessage()?.content
					// only the content can be modified. if it's intact, this is a false modification.
					if (newContent == oldContent) {
						modificationQueue.remove(modification)
						return
					}
					modification.isValidated = true
					modification.multimessage = multimessage
				}

				val guild = modification.candidates.maxBy { it.lastSent }.also(modification.candidates::remove)
				// find the message corresponding to this guild and edit it
				modification.multimessage.retranslated.find { msg ->
					guild.channels.any { it.id == msg.channelId }
				}?.edit {
					content = newContent
				}
			} catch (e: Exception) {
				Log.error(e) { "Failed to edit ${modification.event.messageId}" }
			}
		}
	}
	
	/** Returns whether this message was sent by flarogus. */
	fun isOwnMessage(message: Message): Boolean {
		return (message.author?.id == Vars.botId) ||
			(message.webhookId != null && isMultiversalWebhook(message.webhookId!!))
	}
	/** Returns whether this channel belongs to the multiverse. Does not guarantee that it is not banned. */
	fun isMultiversalChannel(channel: Snowflake) = guilds.any { it.channels.any { it.id == channel } }

	/** Returns whether this webhook belongs to the multiverse. */
	fun isMultiversalWebhook(webhook: Snowflake) = guilds.any { it.webhooks.any { it.id == webhook } }

	/** Returns whether a message with this id is a retranslated message */
	fun isRetranslatedMessage(id: Snowflake) = history.any { it.retranslated.any { it.id == id } }

	abstract class MultiversalService {
		/** Unique name of this service. Used to distinguish different instances. */
		abstract val name: String
		/** Initialised when this service is added to the multiverse. */
		lateinit var multiverse: Multiverse

		/** Called before the multiverse starts up. */
		open suspend fun onStart() {}
		/** Called after the multiverse starts up. */
		open suspend fun onLoad() {}
		/** Called when a multiversal message is received. */
		open suspend fun onMessageReceived(event: MessageCreateEvent, retranslated: Boolean) {}
		/** Called when the multiverse stops. */
		open suspend fun onStop() {}
		
		/** Saves data in the multiverse. */
		suspend fun saveData(key: String, value: String) {
			multiverse.serviceData.getOrPut(name) { mutableMapOf() }[key] = value
		}
		/** Loads data from the multiverse. */
		suspend fun loadData(key: String) = run {
			multiverse.serviceData.getOrElse(name) { null }?.getOrElse(key) { null }
		}
	}

	data class MultimessageDefinition(
		val user: String?, 
		val avatar: String?,
		val filter: (TextChannel) -> Boolean = { true },
		val messageBuilder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit,
		val multimessage: Multimessage
	) {
		@Volatile
		private var isCancelled: Boolean = false
		val creationTime = System.currentTimeMillis()

		/** Candidate guilds that have sent a message in the past 3 hours. */
		val highPriority = ArrayList<MultiversalGuild>()
		/** Candidate guilds that have sent a message in the past 3 days. */
		val mediumPriority = ArrayList<MultiversalGuild>()
		/** Candidate guilds thag haven't sent a message in the past 3 days. */
		val lowPriority = ArrayList<MultiversalGuild>()

		fun cancel() {
			isCancelled = true
		}

		suspend fun await(): Multimessage {
			while (!isEmpty()) {
				if (isCancelled) throw CancellationException("This multimessage was cancelled")
				delay(50L)
			}
			return multimessage
		}

		fun isEmpty() = lowPriority.isEmpty() && mediumPriority.isEmpty() && highPriority.isEmpty()
	}

	data class EventDefinition<T>(val event: T, val candidates: MutableList<MultiversalGuild>) {
		var isValidated = false
		/** Internal usage only. */
		lateinit var multimessage: Multimessage
		val isInitialized get() = isValidated && ::multimessage.isInitialized

		fun getImportance() = candidates.maxOf { it.lastSent }
	}
}

/** Same as [Multiverse.broadcastAsync], but this method awaits for the result. */
suspend fun Multiverse.broadcast(
	user: String? = null,
	avatar: String? = null,
	filter: (TextChannel) -> Boolean = { true },
	messageBuilder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
) = broadcastAsync(user, avatar, filter, messageBuilder).await()

/** Same as [Multiverse.broadcastSystemAsync], but this method awaits for the result. */
suspend fun Multiverse.broadcastSystem(message: suspend MessageCreateBuilder.(id: Snowflake) -> Unit) =
	broadcastSystemAsync(message).await()

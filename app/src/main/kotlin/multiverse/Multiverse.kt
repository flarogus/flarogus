package flarogus.multiverse

import flarogus.multiverse.entity.*
import flarogus.multiverse.state.Multimessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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

	/** A little bit of tomfoolery. */
	val markov: MarkovChain = MarkovChain(),
	var lastInfoMessage: Long = 0L,

	private val serviceData = mutableMapOf<MultiversalService, MutableMap<String, String>>()
) : CoroutineScope(rootJob) {
	/** If false, new messages will be ignored */
	@Transient
	var isRunning = false
	/** All registered services. */
	val services = ArrayList<MultiversalService>()
	/** All registered message filters. They're used by [MultiversalUser] to check whether a message cwn be sent. */
	val messageFilters = ArrayList<MultiversalUser.MessageFilter>()

	val rootJob = SupervisorJob()
	private var findChannelsJob: Job? = null
	private var updateStateJob: Job? = null
	private var tickJob: Job? = null
	
	private val retranslationQueue = LinkedList<MessageCreateEvent>()
	private val modificationQueue = LinkedList<EventDefinition<MessageUpdateEvent>>()
	private val deletionQueue = LinkedList<EventDefinition<MessageDeleteEvent>>()

	suspend fun start() {
		StateManager.updateState()
		services.forEach(MultiversalService::onStart)

		findChannelsJob = launch {
			while (true) {
				findChannels()
				delay(1000L * 180) // finding channels is a costly operation
			}
		}
		updateStateJob = launch {
			while (true) {
				StateManager.updateState()
				delay(1000L * 45)
			}
		}
		tickJob = launch {
			while (true) {
				tick()
				delay(10L)
			}
		}

		services.forEach(MultiversalService::onLoad)
	}

	suspend fun stop() {
		services.forEach(MultiversalService::onStop)
		findChannelsJob?.cancel()
		updateStateJob?.cancel()
		tickJob?.cancel()
	}

	fun findChannels() {
		Vars.client.rest.user.getCurrentUserGuilds().forEach {
			guildOf(it.id)?.update() //this will add an entry if it didn't exist
		}
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
	suspend fun userOf(id: Snowflake): MultiversalUser? = synchronized(users) {
		users.find { it.discordId == id } ?: run {
			MultiversalUser(id).also { it.update() }.let {
				if (it.isValid) it.also { users.add(it) } else null
			}
		}
	}

	/** Returns a MultiversalGuild with the given id, or null if it does not exist */
	suspend fun guildOf(id: Snowflake): MultiversalGuild? = synchronized(guilds) {
		guilds.find { it.discordId == id } ?: let {
			MultiversalGuild(id).also { it.update() }.let { 
				if (it.isValid) it.also { guilds.add(it) } else null
			}
		}
	}

	fun addService(service: MultiversalService) {
		service.multiverse = this
		services += service
	}

	fun addFilter(filter: MultiversalUser.MessageFilter) {
		filters += filter
	}

	/** Peforms a single tick during which the most important action is determimed and executed. */
	suspend fun tick() {
		// firstly, if a message is to be deleted, it's retranslation/modification must be halted.
		// this shouldn't cause much lags since message deletion is a rare action
		deletionQueue.forEach {
			val id = it.messageId

			retranslationQueue.removeAll { it.event.message.id == id }
			modificationQueue.removeAll { it.event.messageId == id }
		}
		// we also need to remove all completed requests
		retranslationQueue.removeAll { it.isEmpty() }
		modificationQueue.removeAll { it.candidates.isEmpty() }
		deletionQueue.removeAll { it.candidates.isEmpty() }

		// then, try to find a message with the highest priority to retranslate
		val definition =
			retranslationQueue.find { it.highPriority.isNotEmpty() } ?:
			retranslationQueue.find { it.mediumPriority.isNotEmpty() } ?:
			retranslationQueue.find { it.lowPriority.isNotEmpty() }

		// if there are retranslation candidates, retranslate them and return.
		if (candidates != null) {
			val queue = with(definition) {
				highPriority.takeIf { it.isNotEmpty() } ?:
					mediumPriority.takeIf { it.isNotEmpty() } ?:
					lowPriority
			}

			coroutineScope {
				for (guild in queue) launch {	
					try {
						val messages = definition.multimessage.retranslated
						guild.send(
							username = user,
							avatar = avatar,
							filter = filter,
							handler = { m, w -> 
								synchronized(messages) {
									messages.add(WebhookMessageBehavior(w, m))
								}
							},
							builder = messageBuilder
						)
					} catch (e: Exception) {
						Log.error { "An exception has occurred while retranslating a message to ${guild.name}: `$e`" }
					}
				}
			}

			def.complete(multimessage)

			return
		}

		// edits and deletions can be performed in any order
		// so we just perform them in each guild in order of recency
		deletionQueue.maxByOrNull { it.getImportance() }?.let { deletion ->
			val guild = deletion.candidates.maxBy { it.lastSent() }.also(deletion.candidates::remove)
			// find the message corresponding to this guild
			val multimessage = history.find { deletion.messageId in it }
			val message = multimessage.retranslated.find { it.
		}
		val modification = modificationQueue.maxByOrNull { it.getImportance() }
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

	companion object {	
		/**
		 * Files with size exceeding this limit will be sent in the form of links.
		 * Since i'm now hosting it on a device with horrible connection speed, it's set to 0. 
		 */
		const val maxFileSize = 1024 * 1024 * 0
	}

	abstract class MultiversalService {
		/** Initialised when this service is added to the multiverse. */
		lateinit var multiverse: Multiverse

		/** Called before the multiverse starts up. */
		suspend fun onStart() {}
		/** Called after the multiverse starts up. */
		suspend fun onLoad() {}
		/** Called when a multiversal message is received. */
		suspend fun onMessage(event: MessageCreateEvent, retranslated: Boolean) {}
		/** Called when the multiverse stops. */
		suspend fun onStop() {}
		
		/** Saves data in the multiverse. */
		suspend fun saveData(key: String, value: String) {
			serviceData.getOrPut(this) { mutableMapOf() }[key] = value
		}
		/** Loads data from the multiverse. */
		suspend fun loadData(key: String) = serviceData.getOrElse(this, null).getOrElse(key, null)
	}

	data class MultimessageDefinition(
		val user: String, 
		val avatar: String,
		val filter: (TextChannel) -> Boolean = { true },
		val messageBuilder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit,
		val multimessage: Multimessage
	) : CompletableDeferred<Multimessage>() {
		/** Candidate guilds that have sent a message in the past 3 hours. */
		val highPriority = ArrayList<MultiversalGuild>()
		/** Candidate guilds that have sent a message in the past 3 days. */
		val mediumPriority = ArrayList<MultiversalGuild>()
		/** Candidate guilds thag haven't sent a message in the past 3 days. */
		val lowPriority = ArrayList<MultiversalGuild>()

		fun isEmpty() = lowPriority.isEmpty() && mediumPriority.isEmpty() && highPriority.isEmpty()
	}

	data class EventDefinition<T>(val event: T, val candidates: ArrayList<MultiversalGuild>) {
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
suspend fun broadcastSystem(message: suspend MessageCreateBuilder.(id: Snowflake) -> Unit) =
	broadcastSystemAsync(message).await()

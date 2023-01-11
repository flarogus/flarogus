package flarogus.multiverse

import flarogus.multiverse.entity.*
import flarogus.multiverse.state.Multimessage
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

) {
	/** If false, new messages will be ignored */
	@Transient var isRunning = false
	/** All registered services. */
	val services = ArrayList<MultiversalService>()

	suspend fun start() {
		services.forEach(MultiversalService::onStart)

		

		services.forEach(MultiversalService::onLoad)
	}

	suspend fun stop() {
		services.forEach(MultiversalService::onStop)
	}

	fun addService(service: MultiversalService) {
		service.multiverse = this
		services += service
	}

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
		fun onStart() {}
		/** Called after the multiverse starts up. */
		fun onLoad() {}
		/** Called when a multiversal message is received. */
		fun onMessage(event: MessageCreateEvent, retranslated: Boolean) {}
		/** Called when the multiverse stops. */
		fun onStop() {}
		
		/** Saves data in the multiverse. */
		fun saveData(key: String, value: String) {
			serviceData.getOrPut(this) { mutableMapOf() }[key] = value
		}
		/** Loads data from the multiverse. */
		fun loadData(key: String) = serviceData.getOrElse(this, null).getOrElse(key, null)
	}
}

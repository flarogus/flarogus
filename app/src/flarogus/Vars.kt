package flarogus

import java.util.concurrent.*
import kotlin.random.*
import kotlinx.coroutines.*
import dev.kord.core.*

/** An object declaration that stores variables/constants that are shared across the whole application */
object Vars {
	
	/** The kord client instance */
	lateinit var client: Kord
	val supplier get() = client.defaultSupplier
	/** The unique bot id used for shutdown command */
	lateinit var ubid: String
	/** The moment the bot has started */
	var startedAt = -1L
	/** The start of flarogus epoch, aka the last hard reset */
	var flarogusEpoch = -1L
	
	/** Mnemotechnican#9967 — discord */
	val ownerId = 502871063223336990UL
	/** Flarogus#0233 — discord */
	val botId = 919995150502101042UL
	/** Bot prefix used for command handling */
	var prefix = "flarogus"
	
	val threadPool = Executors.newFixedThreadPool(5)
	
	/** Superusers that are allowed to do most things */
	val runWhitelist = mutableSetOf<ULong>(ownerId, 691650272166019164UL, 794686191467233280UL) //the first is smolkeys, second is real sushi
	
	fun loadState() {
		ubid = Random.nextInt(0, 1000000000).toString()
		startedAt = System.currentTimeMillis()
		flarogusEpoch = startedAt
	}
	
	fun saveState() {
		//?
	}
}

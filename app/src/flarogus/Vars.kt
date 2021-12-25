package flarogus

import java.util.concurrent.*
import dev.kord.core.*

/** An object declaration that stores variables/constants that are shared with the whole application */
object Vars {
	
	/** The kord client instance */
	lateinit var client: Kord
	/** The unique bot id used for shutdown command */
	lateinit var ubid: String
	/** The moment the bot has started */
	var startedAt = -1L
	
	/** Mnemotechnican#9967 — discord */
	val ownerId = 502871063223336990UL
	/** Bot prefix used for command handling */
	var prefix = "flarogus"
	
	val threadPool = Executors.newFixedThreadPool(5)
	
	/** Users that are allowed to execute kotlin scripts in application context */
	val runWhitelist = mutableListOf(ownerId)
	
}
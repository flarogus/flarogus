package flarogus

import dev.kord.core.*

/** An object declaration that stores variables/constants that are shared with the whole application */
object Vars {
	
	lateinit var client: Kord
	lateinit var ubid: String
	var startedAt = -1L
	
	val ownerId = 502871063223336990.toULong()
	var prefix = "flarogus"
	
	lateinit var lastProcess: Process
	
}
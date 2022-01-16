package flarogus

import java.io.*
import java.util.concurrent.*
import kotlin.random.*
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
	/** Flarogus#0233 — discord */
	val botId = 919995150502101042UL
	/** Bot prefix used for command handling */
	var prefix = "flarogus"
	
	val threadPool = Executors.newFixedThreadPool(5)
	
	/** Users that are allowed to execute kotlin scripts in application context */
	val runWhitelist = mutableListOf(ownerId, 691650272166019164UL)
	
	fun loadState() {
		ubid = Random.nextInt(0, 1000000000).toString()
		
		try {
			val file = File("saves/save.bin")
			
			if (file.exists()) {
				DataInputStream(file.inputStream()).use {
					startedAt = it.readLong()
					return
				}
			}
		} catch (e: IOException) {
			e.printStackTrace()
		}
		//fallback — should only occur if the file doesn't exist/cannot be read
		startedAt = System.currentTimeMillis()
	}
	
	fun saveState() {
		try {
			File("saves").mkdirs()
			DataOutputStream(File("saves/save.bin").outputStream()).use {
				it.writeLong(startedAt)
			}
		} catch (e: IOException) {
			e.printStackTrace()
		}
	}
	
}
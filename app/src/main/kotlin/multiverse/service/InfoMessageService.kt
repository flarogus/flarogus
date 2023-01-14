package flarogus.multiverse.service

import dev.kord.rest.builder.message.create.*
import flarogus.Vars
import flarogus.multiverse.*
import flarogus.multiverse.Multiverse.MultiversalService
import kotlinx.coroutines.*

class InfoMessageService : MultiversalService() {
	override val name = "info"
	val dataKey = "last-sent"
	var message = """
		***This channel is a part of the Multiverse. There's %d other channels.***
		Some of the available commands: 
		    - `!flarogus multiverse rules` - see the rules
		    - `!flarogus report` - report an issue or contact the admins
		    - `!flarogus multiverse help` - various commands
	""".trimIndent()

	var job: Job? = null

	override suspend fun onLoad() {
		job = multiverse.launch {
			delay(20000L)

			while (true) {
				val lastSent = loadData(dataKey)?.toLongOrNull() ?: 0L

				if (System.currentTimeMillis() > lastSent + 1000L * 60 * 60 * 24) {
					saveData(dataKey, System.currentTimeMillis().toString())

					val channelCount = multiverse.guilds.sumOf { it.channels.size }

					multiverse.broadcastSystem {
						embed { description = message.format(channelCount - 1) }
					}
					Log.info { "info message sent" }
				}

				delay(1000L)
			}
		}
	}

	override suspend fun onStop() {
		job?.cancel()
	}
}

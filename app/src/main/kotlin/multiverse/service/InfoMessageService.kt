package flarogus.multiverse.services

import flarogus.Vars
import flarogus.multiverse.Multiverse.MultiversalService

class InfoMessageService : MultiversalService {
	val dataKey = "last-sent"
	var message = """
		***This channel is a part of the Multiverse. There's %d other channels.***
		Some of the available commands: 
		    - `!flarogus multiverse rules` - see the rules
		    - `!flarogus report` - report an issue or contact the admins
		    - `!flarogus multiverse help` - various commands
	""".trimIndent()

	var job: Job? = null

	override suspend fun onStart() {
		job = multiverse.launch {
			delay(20000L)

			while (true) {
				val lastSent = loadData(dataKey)?.toLongOrNull() ?: 0L

				if (System.currentTimeMillis() > lastSent + 1000L * 60 * 60 * 24) {
					saveData(dataKey, System.currentTimeMillis())

					val channelCount = multiverse.guilds.reduce { acc, it -> if (!it.isForceBanned) acc + it.channels.size else acc }

					multiverse.broadcastSystem {
						embed { description = message.format(channelCount - 1) }
					}
				}

				delay(1000L)
			}
		}
	}

	override suspend fun onStop() {
		job?.cancel()
	}
}

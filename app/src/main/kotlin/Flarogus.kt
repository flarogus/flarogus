package flarogus

import java.util.*
import kotlin.concurrent.*;
import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.*;
import dev.kord.rest.request.*
import dev.kord.rest.ratelimit.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.*
import dev.kord.core.event.*
import dev.kord.core.event.message.*
import dev.kord.core.entity.*;
import flarogus.util.*;
import flarogus.commands.*;
import flarogus.multiverse.*

suspend fun main(vararg args: String) = runBlocking {
	val botToken = args.getOrNull(0)
	if (botToken == null) {
		println("[ERROR] no token specified")
		return@runBlocking
	}
	
	Vars.loadState()
	Vars.client = Kord(botToken) {
		requestHandler { KtorRequestHandler(it.httpClient, ParallelRequestRateLimiter(), token = botToken) }
	}
	
	Vars.client.events
		.filterIsInstance<MessageCreateEvent>()
		.filter { it.message.data.author.id.value != Vars.botId && it.message.data.webhookId.value == null }
		.onEach { 
			try {
				val isCommand = CommandHandler.handle(it)
				if (!isCommand) Multiverse.messageReceived(it)
			} catch (e: Exception) {
				Log.error { "an uncaught exception has occurred while evaluating a command ran by ${it.message.author?.tag}: $e" }
			}
		}.launchIn(Vars.client)
	
	initCommands()
	
	//shutdown after 5.5 hours. This should never happen: the instance should shut itself down after noticing that there's another instance running
	Timer(true).schedule(1000 * 60 * 60 * 5L + 1000 * 60 * 30L) {
		Vars.client.launch {
			Log.info { "a multiverse instance is shutting down" }
			
			Multiverse.brodcastSystem {
				embed { description = "This workflow job cannot be continued anymore. Shutting down." }
			}
			
			Vars.client.shutdown();
		}
	}
	
	launch {
		delay(15000L)
		try {
			Multiverse.start()
			Log.info { "mutliverse instance ${Vars.ubid} has started" }
		} catch (e: Exception) {
			Log.error { "FATAL EXCEPTION HAS OCCURRED DURING MULTIVERSE INTIALIZATION: `$e`" }
		}
	}
	
	Vars.client.login {
		presence { competing("execute `flarogus help` to see the list of available commands.") }
	}
}

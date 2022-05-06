package flarogus

import java.util.*
import kotlin.time.*
import kotlin.concurrent.*;
import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.*;
import dev.kord.common.entity.*
import dev.kord.rest.request.*
import dev.kord.rest.ratelimit.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.*
import dev.kord.core.event.*
import dev.kord.core.event.message.*
import dev.kord.core.entity.*;
import dev.kord.core.entity.channel.*
import flarogus.util.*;
import flarogus.commands.*;
import flarogus.multiverse.*

val autorunChannel = Snowflake(962823075357949982UL)

@OptIn(ExperimentalTime::class)
suspend fun main(vararg args: String) = runBlocking {
	val botToken = args.getOrNull(0)
	if (botToken == null) {
		println("[ERROR] no token specified")
		return@runBlocking
	}
	
	Vars.loadState()
	Vars.client = Kord(botToken) {
		// requestHandler { KtorRequestHandler(it.httpClient, ParallelRequestRateLimiter(), token = botToken) }
	}
	
	Vars.client.events
		.filterIsInstance<MessageCreateEvent>()
		.filter { it.message.data.author.id.value != Vars.botId && it.message.data.webhookId.value == null }
		.onEach { 
			try {
				val isCommand = CommandHandler.handle(it)
				if (!isCommand) {
					Vars.client.async {
						withTimeout(40.seconds) {
							Multiverse.messageReceived(it)
						}
					}
				}
			} catch (e: Throwable) {
				Log.error { "an uncaught exception has occurred while processing a message sent by ${it.message.author?.tag}: $e" }
			}
		}.launchIn(Vars.client)
	
	initCommands()
	
	//shutdown after 5.5 hours. This should never happen: the instance should shut itself down after noticing that there's another instance running
	Timer(true).schedule(1000 * 60 * 60 * 5L + 1000 * 60 * 30L) {
		Vars.client.launch {
			Log.info { "a multiverse instance is shutting down" }
			Vars.client.shutdown();
		}
	}
	
	launch {
		delay(15000L)
		try {
			var errors = 0

			// we can't allow it to not start up.
			while (!Multiverse.isRunning && Vars.client.isActive) {
				try {
					Multiverse.start()
				} catch (e: Exception) {
					errors++
					delay(3000L)
				}
			}

			Log.info { "mutliverse instance ${Vars.ubid} has started with $errors errors." }

			//execute all scripts defined in the autorun channel
			val engine = flarogus.commands.impl.engine
			val context = flarogus.commands.impl.context
			val imports = flarogus.commands.impl.defaultImports
			val codeRegex = flarogus.commands.impl.codeblockRegex
                        
			val output = buildString {
				appendLine("executing autorun scripts:")
				(Vars.supplier.getChannelOrNull(autorunChannel) as? TextChannel)?.messages?.toList()?.forEachIndexed { index, it ->
					append(index).append(": ")
                        
					val script = codeRegex.find(it.content)?.groupValues?.getOrNull(2) ?: it.content
                        
					val res = try { 
						engine.eval(imports + "\n" + script, context)?.let {
							if (it is Deferred<*>) it.await() else it
						}?.toString() ?: "null"
					} catch (e: Exception) {
						e.toString()
					}.take(200)
					appendLine(res)
				}
			}.take(1900)
                        
			Log.info { output }
		} catch (e: Throwable) {
			Log.error { "FATAL EXCEPTION HAS OCCURRED DURING MULTIVERSE INTIALIZATION: `$e`" }
		}
	}
	
	Vars.client.login {
		presence { competing("execute `flarogus help` to see the list of available commands.") }
	}
}

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
import dev.kord.core.behavior.*
import flarogus.util.*;
import flarogus.command.*;
import flarogus.command.builder.*
import flarogus.multiverse.*
import kotlin.time.Duration.Companion.seconds

const val IS_MULTIVERSE_ENABLED = true

@OptIn(ExperimentalTime::class)
suspend fun main(vararg args: String) {
	Vars.loadState()

	val botToken = args.getOrNull(0)
	if (botToken == null) {
		println("[ERROR] no token nor '--test' specified")
		exitProcess(1)
	} else if (botToken.trim() == "--test") {
		val arg = args.getOrNull(1) ?: throw IllegalArgumentException("no test command specified")
		require(args.size == 2) { "only two arguments must be specified in this mode" }

		val result = try { Vars.rootCommand(arg) } catch (e: Exception) { e.message }
		println(result?.toString()?.replace("`", ""))
		exitProcess(0)
	}
	
	Vars.client = Kord(botToken) {
		// requestHandler { KtorRequestHandler(it.httpClient, ParallelRequestRateLimiter(), token = botToken) }
	}
	
	Vars.client.events
		.filterIsInstance<MessageCreateEvent>()
		.filter { it.message.data.author.id != Vars.botId && it.message.data.webhookId.value == null }
		.onEach {
			Vars.client.launch {
				try {
					val isCommand = it.message.content.startsWith(Vars.rootCommand.name)

					if (isCommand) {
						val cropped = it.message.content.substring(Vars.rootCommand.name.length).trimStart()
						Vars.rootCommand(it.message, cropped)

						Log.debug { "User ${it.message.data.author.username} has executed a command: '$cropped'" }
					} else if (IS_MULTIVERSE_ENABLED) {
						Multiverse.messageReceived(it)
					}
				} catch (e: Throwable) {
					Log.error { "an uncaught exception has occurred while processing a message sent by ${it.message.author?.tag}: $e" }
				}
			}
		}.launchIn(Vars.client)
	
	//shutdown after 6 hours. This should never happen: the instance should shut itself down after noticing that there's another instance running
	Timer(true).schedule(1000 * 60 * 60 * 6L) {
		Vars.client.launch {
			Log.info { "a multiverse instance is shutting down" }
			Vars.client.shutdown();
		}
	}
	
	if (IS_MULTIVERSE_ENABLED) {
		Vars.client.launch {
			delay(10_000L)
			try {
				var errors = 0

				// we can't allow it to not start up.
				while (!Multiverse.isRunning && Vars.client.isActive) {
					try {
						Multiverse.start()
					} catch (e: Exception) {
						Log.error { e.toString() }
						errors++
						delay(3000L)
					}
				}

				Log.info { "mutliverse instance ${Vars.ubid} has started with $errors errors." }

				// execute all scripts defined in the autorun channel	
				val output = buildString {
					appendLine("executing autorun scripts:")
					(Vars.supplier.getChannelOrNull(Channels.autorun) as? TextChannel)?.messages?.toList()?.forEachIndexed { index, it ->
						append(index).append(": ")
				
						val script = Vars.codeblockRegex.find(it.content)?.groupValues?.getOrNull(2) ?: it.content
				
						val res = try { 
							Vars.scriptEngine.eval(Vars.defaultImports + "\n" + script, Vars.scriptContext)?.let {
								if (it is Deferred<*>) it.await() else it
							}?.toString() ?: "null"
						} catch (e: Exception) {
							e.toString()
						}.take(200)
						appendLine(res)
					}
				}.take(1900)
				
				Log.info { output }
				delay(10_000L)
				
				if (Multiverse.isRunning) {
					// something is wrong, this happens sometimes
					if (Multiverse.guilds.filter { it.isValid }.size < 3) {
						Log.error { "No valid guilds found! Forcibly updating all guilds!" }

						Multiverse.guilds.forEach {
							it.lastUpdate = 0L
							it.update()
						}
					}
				}
			} catch (e: Throwable) {
				Log.error { "FATAL EXCEPTION HAS OCCURRED DURING MULTIVERSE INTIALIZATION: `$e`" }
			}
		}
	}
	
	Vars.client.login {
		presence { competing("execute `!flarogus help` to see the list of available commands.") }
	}
}

package flarogus

import java.util.*
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
import flarogus.command.*;
import flarogus.command.builder.*
import flarogus.multiverse.*

val autorunChannel = Snowflake(962823075357949982UL)

suspend fun main(vararg args: String) {
	Vars.loadState()

	val botToken = args.getOrNull(0)
	if (botToken == null) {
		println("[ERROR] no token nor '--test' specified")
		return
	} else if (botToken.trim() == "--test") {
		val arg = args.getOrNull(1) ?: throw IllegalArgumentException("no test command specified")
		require(args.size == 2) { "only two arguments must be specified in this mode" }

		val result = try { Vars.rootCommand(arg) } catch (e: Exception) { e.message }
		println(result?.toString())
		return
	}
	
	Vars.client = Kord(botToken) {
		requestHandler { KtorRequestHandler(it.httpClient, ParallelRequestRateLimiter(), token = botToken) }
	}
	
	Vars.client.events
		.filterIsInstance<MessageCreateEvent>()
		.filter { it.message.data.author.id != Vars.botId && it.message.data.webhookId.value == null }
		.onEach { 
			try {
				val isCommand = it.message.content.startsWith(Vars.rootCommand.name)

				if (isCommand) {
					val cropped = it.message.content.substring(Vars.rootCommand.name.length).trimStart()
					Vars.rootCommand(it.message, cropped)
				} else {
					Multiverse.messageReceived(it)
				}
			} catch (e: Throwable) {
				Log.error { "an uncaught exception has occurred while evaluating a command ran by ${it.message.author?.tag}: $e" }
			}
		}.launchIn(Vars.client)
	
	//shutdown after 5.5 hours. This should never happen: the instance should shut itself down after noticing that there's another instance running
	Timer(true).schedule(1000 * 60 * 60 * 5L + 1000 * 60 * 30L) {
		Vars.client.launch {
			Log.info { "a multiverse instance is shutting down" }
			Vars.client.shutdown();
		}
	}
	
	Vars.client.launch {
		delay(15000L)
		try {
			Multiverse.start()
			Log.info { "mutliverse instance ${Vars.ubid} has started" }

			//execute all scripts defined in the autorun channel
			//val engine = flarogus.commands.impl.engine
			//val context = flarogus.commands.impl.context
			//val imports = flarogus.commands.impl.defaultImports
			//val codeRegex = flarogus.commands.impl.codeblockRegex
                        //
			//val output = buildString {
			//	appendLine("executing autorun scripts:")
			//	(Vars.supplier.getChannelOrNull(autorunChannel) as? TextChannel)?.messages?.toList()?.forEachIndexed { index, it ->
			//		append(index).append(": ")
                        //
			//		val script = codeRegex.find(it.content)?.groupValues?.getOrNull(2) ?: it.content
                        //
			//		val res = try { 
			//			engine.eval(imports + "\n" + script, context)?.let {
			//				if (it is Deferred<*>) it.await() else it
			//			}?.toString() ?: "null"
			//		} catch (e: Exception) {
			//			e.toString()
			//		}.take(200)
			//		appendLine(res)
			//	}
			//}.take(1900)
                        //
			//Log.info { output }
		} catch (e: Throwable) {
			Log.error { "FATAL EXCEPTION HAS OCCURRED DURING MULTIVERSE INTIALIZATION: `$e`" }
		}
	}
	
	Vars.client.login {
		presence { competing("execute `flarogus help` to see the list of available commands.") }
	}
}

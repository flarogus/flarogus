package flarogus

import kotlin.time.*
import kotlin.concurrent.*;
import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.*;
import dev.kord.common.entity.*
import dev.kord.core.*
import dev.kord.core.entity.channel.TextChannel
import dev.kord.gateway.*
import flarogus.util.*;
import flarogus.command.*;
import flarogus.command.builder.*
import flarogus.multiverse.*
import kotlin.system.exitProcess

const val IS_MULTIVERSE_ENABLED = true

suspend fun main(vararg args: String) {
	val botToken = args.getOrNull(0)
	if (botToken == null) {
		println("[ERROR] no token nor '--test' specified")
		exitProcess(1)
	} else if (botToken.trim() == "--test") {
		args.drop(1).map {
			runCatching {
				Vars.rootCommand(it)
			}.recover { e ->
				e.message ?: e
			}.getOrThrow().toString().replace("`", "")
		}.joinToString("\n").let { it: String ->
			println(it)
		}
		exitProcess(0)
	}

	Vars.launch(botToken)
	
	if (IS_MULTIVERSE_ENABLED) {
		Vars.client.launch {
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
				Log.info { "executing autorun scripts:" }
				(Vars.supplier.getChannelOrNull(Channels.autorun) as? TextChannel)?.messages?.toList()?.forEachIndexed { index, msg ->
					Log.info { "$index:" }
			
					val script = Vars.codeblockRegex.find(msg.content)?.groupValues?.getOrNull(2) ?: msg.content
			
					runCatching {
						Vars.scriptEngine.eval(Vars.defaultImports + "\n" + script, Vars.scriptContext)?.let {
							if (it is Deferred<*>) it.await() else it
						}.toString()
					}.recover { e ->
						e.message ?: e.toString()
					}.getOrThrow().take(200).let { it ->
						Log.info { it }
					}
				}

				delay(10_000L)
				if (Multiverse.isRunning) {
					// something is wrong, this happens sometimes
					if (Multiverse.guilds.filter { it.isValid }.size <= 0) {
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
	
	@OptIn(PrivilegedIntent::class)
	Vars.client.login {
        	intents += Intent.MessageContent

		presence { competing("execute `!flarogus help` to see the list of available commands.") }
	}
}

package flarogus.commands

import kotlinx.coroutines.*;
import dev.kord.core.event.message.*;
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import dev.kord.core.entity.*
import flarogus.*
import flarogus.util.*
import flarogus.commands.Command as FlarogusCommand
import flarogus.multiverse.*

/** Main command handler */
val CommandHandler = FlarogusCommandHandler(false, "flarogus")

/**
 * Handles discord chat commands 
 *
 * @param ignoreBots Whether to ignore bot users
 * @param generateHelp Whether to generate a help command
 */
open class FlarogusCommandHandler(
	val ignoreBots: Boolean = true,
	val prefix: String = "",
	val generateHelp: Boolean = true
) {
	val commands = ArrayList<FlarogusCommand>(50)
	
	/** Executed if the command wasn't found */
	var fallback: suspend MessageCreateEvent.(List<String>) -> Unit = {
		it.getOrNull(1)?.let {
			replyWith(message, "Command '${it.stripEveryone()}' does not exist. Please refer to the help subcommand of the current command.")
		} ?: replyWith(message, "No command specified! Please refer to the help subcommand of the current command.")
	}
	
	init {
		if (generateHelp) {
			tree("help", false) {
				register("tree") {
					val tree = buildString(500) {
						appendln("```")
						if (!prefix.isEmpty()) appendln(prefix)

						fun branch(level: Int, handler: FlarogusCommandHandler) {
							handler.commands.forEachIndexed { index, command ->
								repeat(level - 1) { append("┃ ") }
								append(if (index < handler.commands.size - 1) "┣" else "┗")
								append(if (command is Supercommand) "┳" else "━")
								append(command.fancyName)
								append('\n')

								if (command is Supercommand) {
									branch(level + 1, command.commands)
								}
							}
						}
						branch(1, this@FlarogusCommandHandler)
						append("\n```")
					}

					message.channel.createEmbed {
						title = "List of commands"
						description = tree
					}
				}
				.description("Show a tree-like help")

				registerDefault {
					message.channel.createEmbed {
						title = "List of commands"
						description = "Commands marked with [+] have subcommands. Use '<commandname> help' to see them."
						
						var hidden = 0
						for (command in commands) {
							if (!command.condition(this@registerDefault.message.author!!)) {
								hidden++
								continue;
							}
							field {
								name = command.fancyName

								value = command.description ?: "no description"
								`inline` = true
							}
						}
						
						if (hidden > 0) {
							footer { text = "there's [$hidden] commands you are not allowed to run" }
						}
					}
				}
			}
			.header("mode: [tree]?")
			.description("Show the list of commands. Provide 'tree' as an argument to view the commands in a tree-like form.")
		}
	}
	
	/** 
	 * Invokes the command handler associated with the first word (command name) in the message.
	 * Provides an array or arguments to the handler, 0th element is the source message without the command name 
	 *
	 * @return whether the handler has responded to this message
	 **/
	open suspend fun handle(event: MessageCreateEvent): Boolean {
		if (ignoreBots && event.message.author?.isBot ?: true) return false
		if (!event.message.content.startsWith(prefix)) return false
		
		val message = event.message.content.substring(prefix.length)
		val args = message.split(" ").filter { !it.isEmpty() }.toMutableList()
		
		val commandName = args.getOrNull(0) ?: DEFAULT_COMMAND
		args[0] = message.substring(commandName.length + 1)
		
		val command = commands.find { it.name.equals(commandName, true) }
		
		Vars.client.launch {
			if (command == null) {
				event.fallback(args)
			} else {
				val author = event.message.author
				if (author != null && command.condition(author)) {
					val handler = command.handler;
					try {
						event.handler(args)
						
						Log.debug { "${event.message.author?.tag} has successfully executed `$commandName`" }
					} catch (e: Exception) { //no exceptions on my watch
						if (e is CommandException && e.commandName == null) e.commandName = command.name
						replyWith(event.message, e.toString())
						
						if (e is CommandException) {
							Log.debug { "a command exception has occurred while executing command `$commandName` ran by ${event.message.author?.tag}: `$e`" }
						} else {
							e.printStackTrace()
							Log.error { "a fatal exception has occurred while executing command `$commandName` ran by ${event.message.author?.tag}:: `$e`" }
						}
					}
				} else {
					replyWith(event.message, "You are not allowed to run '${commandName.stripEveryone()}'.")
					
					Log.info { "${event.message.author?.tag} has tried to execute a command they don't have access to: `${commandName}`" }
				}
			}
		}
		
		return true
	}
	
	open fun register(command: FlarogusCommand): FlarogusCommand {
		commands.add(command)
		return command
	}
	
	/** Register a command on-spot */
	open fun register(name: String, handler: suspend MessageCreateEvent.(List<String>) -> Unit): FlarogusCommand {
		return FlarogusCommand(name, handler).also { commands.add(it) }
	}

	/** Register a command with the default name. This command will be called when there's no arguments specified when calling a supercommand */
	inline fun registerDefault(crossinline handler: suspend MessageCreateEvent.() -> Unit): FlarogusCommand {
		return register(DEFAULT_COMMAND, { handler() })
	}

	/** Register a supercommand on-spot */
	inline fun tree(name: String, generateHelp: Boolean = true, builder: FlarogusCommandHandler.() -> Unit): Supercommand {
		return Supercommand(name = name, generateHelp = generateHelp).also {
			it.commands.apply(builder)
			commands.add(it)
		}
	}

	open fun remove(name: String) {
		commands.removeAll { it.name == name }
	}

	companion object {
		val DEFAULT_COMMAND = "_default-command_"
	}
}


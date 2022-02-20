package flarogus.commands.impl

import java.io.*
import java.util.concurrent.*
import javax.script.*
import kotlinx.coroutines.*;
import flarogus.*
import flarogus.util.*
import flarogus.multiverse.*

val defaultImports = arrayOf(
	"flarogus.*", "flarogus.util.*", "flarogus.multiverse.*", "ktsinterface.*", "dev.kord.core.entity.*", "dev.kord.core.entity.channel.*",
	"dev.kord.common.entity.*", "dev.kord.rest.builder.*", "dev.kord.rest.builder.message.*", "dev.kord.rest.builder.message.create.*",
	"dev.kord.core.behavior.*", "dev.kord.core.behavior.channel.*", "kotlinx.coroutines.*"
).map { "import $it;" }.joinToString("")

val RunCommand = flarogus.commands.Command(
	handler = handler@ {
		val command = it[0]
		val begin = command.indexOf("<<")
		
		var isAdmin = false
		var stopAfter = 3000L
		var addImports = false
		
		val regex = "-([a-zA-Z0-9=]*)[\\s<]?".toRegex()
		var argument = regex.find(command.substring(0, if (begin == -1) command.length else begin - 1))
		while (argument != null) {
			val arg = argument.groupValues.getOrNull(1) ?: break
			
			when {
				arg == "su" -> {
					isAdmin = true
				}
				arg == "long" -> {
					stopAfter = 300000L
				}
				arg == "imports" -> {
					addImports = true
				}
				arg.startsWith("addSuperuser") -> {
					if (message.author?.id?.value != Vars.ownerId) throw IllegalAccessException("Only the bot owner can add superusers")
					val parts = arg.split("=")
					try {
						val id = parts.get(1).toULong()
						Vars.runWhitelist.add(id)
					} catch (e: Exception) {
						replyWith(message, "couldn't add this user: $e!")
					}
					return@handler;
				}
				else -> throw CommandException("run", "unknown argument: $arg")
			}
			
			argument = argument.next()
		}
		
		if (begin == -1) throw CommandException("run", "Invalid syntax! The correct one is 'run -arg1 -arg2 << some_script'. The script can be wrapped in a code block.")
		
		//try to find a code block, remove it if it's present
		var script = command.substring(begin + 2)
		val codeblock = "```([a-z]*)?((?s).*)```".toRegex().find(script)?.groupValues?.getOrNull(2)
		if (codeblock != null) script = codeblock
		if (addImports) script = "$defaultImports\n$script"
		
		//check for errors
		var errCount = 0
		var errors = ""
		fun illegal(cause: String, vararg illegals: String) {
			for (illegal in illegals) {
				if (script.contains(illegal)) {
					errCount++
					errors += "[ERROR]: '$illegal' $cause\n";
				}
			}
		}
		illegal(cause = "should not be used at all. Use `ktsinterface.launch` instead.", "runBlocking", "coroutineScope")
		
		if (message.author?.id?.value != Vars.ownerId) {
			illegal(cause = "can be used only by the owner", "runWhitelist")
		}
		
		/*if (!isAdmin) {
			illegal(
				cause = "can only be used in conjunction with argument '-su'!\n",
				
				"Thread", "System", "java.lang.Thread", "java.lang.System",
				"Class", "KClass", "::class", ".getClass", "ClassLoader",
				"dev.kord", "KtsObjectLoader", "ScriptEngine", "flarogus.",
				"java.io",
				"Process"
			)
		}*/
		if (errCount > 0) {
			throw CommandException("run", "$errCount errors:\n```\n${errors.take(1500)}\n```")
		}
		
		//execute
		if (isAdmin) {
			//application context
			val engine = ScriptEngineManager(Thread.currentThread().contextClassLoader).getEngineByExtension("kts");
			launch {
				try {
					val result = engine.eval(script)
					val resultString = when (result) {
						is Deferred<*> -> result.await().toString()
						null -> "no output"
						else -> result.toString()
					}
					replyWith(message, "```\n${resultString.take(1950)}\n```")
					
					Log.info { "${message.author?.tag} has successfully executed a kotlin script (see fetchMessage(${message.channel.id}UL, ${message.id}UL))" }
				} catch (e: Exception) { 
					val trace = if (e is ScriptException) e.toString() else e.cause?.stackTraceToString() ?: e.stackTraceToString()
					
					replyWith(message, "exception during execution:\n```\n${trace.take(1950)}\n```")
					e.printStackTrace()
				}
			}
		} else {
			//subprocess context
			var proc: Process? = null
			
			val thread = Vars.threadPool.submit {
				try {
					File("/tmp/scriptfile.kts").writeText(script)
					
					val parts = "kotlinc -script scriptfile.kts".split("\\s".toRegex())
					proc = ProcessBuilder(*parts.toTypedArray())
						.directory(File("/tmp"))
						.redirectOutput(ProcessBuilder.Redirect.PIPE)
						.redirectError(ProcessBuilder.Redirect.PIPE)
						.start()
					proc!!.waitFor(stopAfter, TimeUnit.MILLISECONDS)
					proc!!.errorStream.bufferedReader().use {
						val error = it.readText()
						proc!!.inputStream.bufferedReader().use {
							replyWith(message, "output${if (error != "") " and errors:" else ""}:\n```\n$error\n\n${it.readText()}\n```")
						}
					}
				} catch(e: IOException) {
					replyWith(message, e.toString())
				}
			}
			delay(stopAfter + 10000L) //additional 10 seconds
			thread.cancel(true)
			if (proc != null) proc!!.destroy()
		}
	},
	
	condition = flarogus.commands.Command.adminOnly,
	
	header = "-flags] << [arbitrary kotlin script: String",
	
	description = "Execute arbitrary kotlin script code and print it's output (or result in case of -su). Unless used with '-long' or '-su' argument, the execution time is limited to 3 seconds"
)

private class TimeoutException(message: String) : RuntimeException(message);

package flarogus.commands.impl

import javax.script.*
import kotlinx.coroutines.*;
import flarogus.*
import flarogus.util.*

val RunCommand = flarogus.commands.Command(
	handler = {
		val command = it[0]
		val begin = command.indexOf("<<")
		
		if (begin == -1) throw CommandException("run", "Invalid syntax! The correct one is 'run -arg1 -arg2 << some_script'. The script can be wrapped in a code block.")
		
		//try to find a code block, remove it if it's present
		val cbstart = command.indexOf("```")
		val cbend = command.lastIndexOf("```")
		val script = if (cbstart != -1 && cbend != -1 && cbstart != cbend && cbstart > begin) {
			command.substring(cbstart + 3, cbend - 1)
		} else {
			command.substring(begin + 2)
		}
		
		var isAdmin = false
		var stopAfter = 3000L
		
		val regex = "-([a-zA-Z0-9=]*)[\\s<]?".toRegex()
		var argument = regex.find(command.substring(0, begin - 1))
		while (argument != null) {
			val arg = argument.groupValues.getOrNull(1) ?: break
			
			when (arg) {
				"admin" -> {
					if (message.author?.id?.value != Vars.ownerId) throw IllegalAccessException("you are not allowed to use argument 'admin'!")
					isAdmin = true
				}
				"long" -> {
					if (message.author?.id?.value != Vars.ownerId) throw IllegalAccessException("you're not allowed to use argument '-long'!")
					stopAfter = 300000L
				}
				else -> throw CommandException("run", "unknown argument: $arg")
			}
			
			argument = argument.next()
		}
		
		//check for errors
		var errCount = 0
		var errors = ""
		fun illegal(cause: String, vararg illegals: String) {
			for (illegal in illegals) {
				if (script.contains(illegal)) {
					errCount++
					errors += "[ERROR]: '$illegal' $cause";
				}
			}
		}
		illegal(cause = "should not be used at all. Use `ktsinterface.KtsInterface.launch` instead.", "runBlocking", "coroutineScope")
		if (!isAdmin) {
			illegal(
				cause = "can only be used in conjunction with argument '-admin'!\n",
				
				"Thread", "System", "java.lang.Thread", "java.lang.System",
				"Class", "KClass", "::class", ".getClass", "ClassLoader",
				"dev.kord", "KtsObjectLoader", "ScriptEngine", "flarogus."
			)
		}
		if (errCount > 0) {
			throw CommandException("run", "$errCount errors:\n```\n${errors.take(1500)}\n```")
		}
		
		//execute
		val engine = ScriptEngineManager(Thread.currentThread().contextClassLoader).getEngineByExtension("kts");
		try {
			launch {
				var hasFinished = false
				//todo: doesn't work
				launch {
					delay(stopAfter)
					if (!hasFinished) {
						throw TimeoutException("[WARNING] The coroutine has been stopped due to exceeding the maximum execution time ($stopAfter ms)")
					}
				}
				launch {
					try {
						//this script must be run in a this coroutine
						ktsinterface.KtsInterface.lastScope = this
						val result = engine.eval(script)?.toString() ?: "null"
						replyWith(message, result)
					} catch (e: Exception) { 
						val trace = if (e is ScriptException) e.toString() else e.cause?.stackTraceToString() ?: e.stackTraceToString()
						
						replyWith(message, "exception during execution:\n```\n${trace}\n```")
						e.printStackTrace()
					}
					hasFinished = true
				}
			}
		} catch (timeout: TimeoutException) {
			println("killing the script coroutine")
			replyWith(message, timeout.toString())
		}
	},
	
	condition = { it.id.value == Vars.ownerId },
	
	header = "-flags] << [arbitrary kotlin script: String",
	
	description = "Execute an arbitrary kotlin script (kts) and print it's result. Unless used with '-long' (admin-only) argument, the execution time is limited to 3 seconds"
)

private class TimeoutException(message: String) : RuntimeException(message);
package flarogus.commands.impl
//load
import kotlinx.coroutines.*;
import de.swirtz.ktsrunner.objectloader.*
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
			command.substring(begin + 1)
		}
		
		println("executing a script with arguments:")
		
		var isAdmin = false
		var stopAfter = 3000L
		
		val regex = "-([a-z A-Z 0-9 =]*)[\\s<]".toRegex()
		var argument = regex.find(command.substring(0, begin - 1))
		while (argument != null) {
			val arg = argument?.groupValues?.getOrNull(1) ?: break
			print(" $arg,")
			
			when (arg) {
				"admin" -> {
					if (message.author?.id?.value != Vars.ownerId) throw IllegalAccessException("you are not allowed to use argument 'admin'!")
					isAdmin = true
				}
				"long" -> {
					if (!isAdmin) throw IllegalAccessException("'-long' can only be used after '-admin'!")
					stopAfter = 300000L
				}
				else -> throw CommandException("run", "unknown argument: $arg")
			}
			
			argument = argument?.next()
		}
		
		if (!isAdmin) {
			var errCount = 0
			var errors = ""
			fun illegal(vararg illegals: String) {
				for (illegal in illegals) {
					if (script.contains(illegal)) {
						errCount++
						errors += "[ERROR]: '$illegal' can only be used in conjunction with argument '-admin'!\n";
					}
				}
			}
			
			illegal("Thread", "System", "java.lang.Thread", "java.lang.System")
			illegal("Class", "KClass", "::class", ".getClass", "ClassLoader")
			illegal("dev.kord")
			illegal("KtsObjectLoader", "ScriptEngine")
			//todo: ?
			
			if (errCount > 0) {
				throw CommandException("run", "$errCount errors:\n```\n${errors.substring(0, 1500)}\n```")
			}
		}
		
		print("starting the thread...")
		val thread = object : Thread() {
			override fun run() {
				runBlocking {
					try {
						val result = KtsObjectLoader().load<Any>(script).toString()
						replyWith(message, result.toString())
					} catch (e: Exception) { 
						//commands MUST NOT create any uncaught exceptions. Exceptions in this thread wouldn't be caught.
						replyWith(message, "```\n${e.stackTraceToString()}\n```")
					}
				}
			}
		}
		thread.start()
		
		//the thread MUST be killed. no matter what.
		launch {
			val stop = Thread::class.java.getDeclaredMethod("stop0", Any::class.java);
			stop.setAccessible(true)
			delay(stopAfter)
			println("killing the script thread")
			stop.invoke(thread, ThreadDeath());
		}
	},
	
	condition = { it.id.value == Vars.ownerId },
	
	header = "-flags] << [arbitrary kotlin script: String",
	
	description = "Execute an arbitrary kotlin script (kts) and print it's result. Unless used with '-long' (admin-only) argument, the execution time is limited to 3 seconds"
)
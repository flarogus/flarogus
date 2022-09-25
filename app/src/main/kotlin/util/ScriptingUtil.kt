package flarogus.util

import dev.kord.core.entity.Message
import flarogus.Vars
import flarogus.util.*
import flarogus.multiverse.Log
import java.util.Vector
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.util.PropertiesCollection

@KotlinScript(
	fileExtension = "kscript.kts",
	compilationConfiguration = KScriptConfiguration::class
)
abstract class KScript {
	val message = triggeredByMessage

	companion object {
		/** Temporary variable to pass an argument to evalWithTemplate. */
		var triggeredByMessage: Message? = null
	}
}

object KScriptConfiguration: ScriptCompilationConfiguration({
	try {
		// import all classes from flarogus/kord - should always work on the jvm flarogus uses
		defaultImports(
			*ClassLoader::class.java.getDeclaredField("classes")
				.also { it.isAccessible = true }
				.get(Vars::class.java.classLoader)
				.cast<Vector<Class<*>>>()
				.filter { 
					it.name.startsWith("flarogus") 
						|| it.name.startsWith("dev.kord")
						|| (it.name.startsWith("kotlin") || "internal" !in it.name)
						&& !it.name.contains("$")
				}
				.map { it.kotlin }
				.toTypedArray()
		)
	} catch (e: Exception) {
		Log.error { "failed to import the loaded classes: $e" }
	}

	jvm {
		dependenciesFromCurrentContext(wholeClasspath = true)
	}

	refineConfiguration {
		//onAnnotations(DependsOn::class, Repository::class) {}
	}
})

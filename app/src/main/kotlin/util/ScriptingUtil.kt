package flarogus.util

import dev.kord.core.entity.Message
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.util.PropertiesCollection

@KotlinScript(
	fileExtension = "kscript.kts",
	compilationConfiguration = KScriptConfiguration::class
)
abstract class KScript {
	val test = ""
}

object KScriptConfiguration: ScriptCompilationConfiguration({
	//defaultImports(DependsOn::class, Repository::class)

	jvm {
		dependenciesFromCurrentContext(wholeClasspath = true)
	}
	refineConfiguration {
		//onAnnotations(DependsOn::class, Repository::class) {}
	}
})

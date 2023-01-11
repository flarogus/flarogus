plugins {
	kotlin("jvm") version "1.7.21"
	kotlin("plugin.serialization") version "1.7.21"
}

repositories {
	mavenCentral()
	maven("https://oss.sonatype.org/content/repositories/snapshots")
	maven("https://maven.pkg.github.com/mnemotechnician/markov-chain") {
		credentials {
			username = "Mnemotechnician"
			password = findProperty("github.token") as? String ?: System.getenv("GITHUB_TOKEN")
		}
	}
	//maven("https://jitpack.io")
}

dependencies {
	implementation(kotlin("reflect"))
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

	implementation(kotlin("scripting-common"))
	implementation(kotlin("scripting-jvm"))
	implementation(kotlin("scripting-jvm-host"))
	implementation(kotlin("scripting-compiler-embeddable"))

	implementation("dev.kord:kord-core:0.8.0-M17")
	implementation("org.sejda.webp-imageio:webp-imageio-sejda:0.1.0") // webp support for ImageIO
	implementation("info.debatty:java-string-similarity:2.0.0")

	implementation("com.github.mnemotechnician:markov-chain:1.0")

}

tasks.jar {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	
	manifest {
		attributes["Main-Class"] = "flarogus.FlarogusKt"
	}
	
	from(*configurations.runtimeClasspath.files.map { if (it.isDirectory()) it else zipTree(it) }.toTypedArray())
}

// a simple task that analyzes the generated jar-file and writes all noteworthy packages down into a file.
val collectDefaultImports by tasks.registering {
	dependsOn("jar")

	outputs.dir("$projectDir/src/main/resources/")

	doLast {
		val zip = zipTree("$buildDir/libs/app.jar")
		val packages = zip
			.getFiles()
			.asSequence()
			.filter { !it.isDirectory && it.name.endsWith(".class") && "$" !in it.name }
			.filter { !it.name.startsWith("META-INF") }
			.map {
				it.absolutePath.removeSuffix(".class")
					.substringAfter("$buildDir/tmp/expandedArchives/")
					.substringAfter("/") // temp archive folder
					.replace('/', '.')
					.substringBeforeLast('.') // turining class path into package path
					.let { it + ".*" }
			}
			.distinct()
			.joinToString("\n")

		File("$projectDir/src/main/resources/").let {
			it.mkdirs()
			it.resolve("import-classpath.txt").writeText(packages)
		}
	}
}

tasks.register("deploy") {
	dependsOn("jar")
	dependsOn(collectDefaultImports)
}


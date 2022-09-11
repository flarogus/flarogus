plugins {
	kotlin("jvm") version "1.7.20-Beta"
	kotlin("plugin.serialization") version "1.7.20-Beta"
	application
}

repositories {
	mavenCentral()
	maven("https://jitpack.io")
}

dependencies {
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
	implementation(kotlin("reflect"))
	
	/*implementation("org.jetbrains.kotlin:kotlin-script-runtime:1.6.10")
	implementation("org.jetbrains.kotlin:kotlin-script-util:1.6.10")
	implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.6.10")
	implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:1.6.10")
	implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:1.6.10")*/

	//runtimeOnly("org.jetbrains.kotlin:kotlin-main-kts:1.6.10")
	//runtimeOnly("org.jetbrains.kotlin:kotlin-scripting-jsr223:1.6.10")
	//implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223-embeddable:1.3.72")
	
	implementation(kotlin("scripting-common"))
	implementation(kotlin("scripting-jvm"))
	implementation(kotlin("scripting-jvm-host"))

	implementation("dev.kord:kord-core:0.8.0-M10")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
	
	// note for myself: DONT REMOVE THIS DEPENDENCY YOU DUMBFUCK!
	implementation("org.sejda.webp-imageio:webp-imageio-sejda:0.1.0")

	implementation("info.debatty:java-string-similarity:2.0.0")

}

tasks.compileKotlin {
	kotlinOptions {
		jvmTarget = "11"

		freeCompilerArgs += arrayOf(
			"-Xcontext-receivers"
		)
	}
}

tasks.jar {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	
	manifest {
		attributes["Main-Class"] = "flarogus.FlarogusKt"
	}
	
	from(*configurations.runtimeClasspath.files.map { if (it.isDirectory()) it else zipTree(it) }.toTypedArray())
}

tasks.register<Copy>("deploy") {
	dependsOn("jar")
	
	from("build/libs/app.jar")
	into("../build/")
	
	doLast {
		delete("build/libs/app.jar")
	}
}

application.apply {
	mainClass.set("flarogus.FlarogusKt")
}

//why
tasks.withType(JavaExec::class.java) {
	standardInput = System.`in`
}

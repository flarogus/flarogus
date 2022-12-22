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
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

	implementation(kotlin("reflect"))
	implementation(kotlin("script-runtime"))
	implementation(kotlin("script-util"))
	implementation(kotlin("compiler-embeddable"))
	implementation(kotlin("scripting-compiler-embeddable"))
	implementation(kotlin("scripting-jsr223"))

	implementation("dev.kord:kord-core:0.8.0-M17")
	implementation("org.sejda.webp-imageio:webp-imageio-sejda:0.1.0") // webp support for ImageIO
	implementation("info.debatty:java-string-similarity:2.0.0")

	implementation("com.github.mnemotechnician:markov-chain:unspecified")

}

tasks.compileKotlin {
	kotlinOptions.apply {
		jvmTarget = "11"
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

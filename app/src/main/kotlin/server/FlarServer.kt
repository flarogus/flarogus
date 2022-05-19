package flarogus.server

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import flarogus.multiverse.*

object FlarServer {
	fun launch(port: Int) {
		embeddedServer(CIO, port = port) {
			install(ContentNegotiation) {
				json()
			}

			routing {
				get("/") {
					Log.info { "Incoming GET request on uri: ${call.request.uri}" }
					call.respondText("AMOGUS!")
				}
			}
		}.start(wait = true)
	}
}

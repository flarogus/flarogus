package flarogus.multiverse.npc

import dev.kord.common.entity.*
import dev.kord.core.behavior.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.*
import flarogus.*
import flarogus.multiverse.*
import flarogus.multiverse.state.StateManager
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

private val assetStorage = Snowflake(1045966829882970143UL)
private val femboyMemesMarker = "{{femboy memes}}"
private val lastSentTimeKey = "femboy-meme-last-sent"

suspend fun loadFemboyMemes() {
	Vars.client.resources.httpClient.get("https://reddit.com/r/femboymemes/hot.json?limit=20")
		.body<JsonObject>()["data"]!!
		.jsonObject["children"]!!
		.jsonArray
		.map { it.jsonObject["data"]!!.jsonObject }
		.filterNot {
			it["stickied"]!!.jsonPrimitive.boolean || it["over_18"]!!.jsonPrimitive.boolean
		}
		.filter { it["url"]?.jsonPrimitive?.content?.startsWith("https://i.redd.it") ?: false }
		.map { it["url"]!!.jsonPrimitive.content }
		.take(10)
		.let { links ->
			val channel = Vars.client.getChannel(assetStorage) as TextChannel
			val targetMessage = channel.messages 
				.firstOrNull { it.content.startsWith(femboyMemesMarker) }
				?: channel.createMessage(femboyMemesMarker)
			
			targetMessage.edit { content = targetMessage.content + "\n" + links.joinToString("\n") { "<$it>" } }
		}
}

suspend fun postFemboyMeme() {
	suspend fun assetStorage() = Vars.restSupplier.getChannel(assetStorage) as TextChannel

	var targetMessage = assetStorage().messages.firstOrNull { it.content.startsWith(femboyMemesMarker) }
	if (targetMessage == null || targetMessage.content.removePrefix(femboyMemesMarker).isBlank()) {
		loadFemboyMemes()
		targetMessage = assetStorage().messages.first { it.content.startsWith(femboyMemesMarker) }
	}
	
	val links = targetMessage.content.removePrefix(femboyMemesMarker).trim().lines().toMutableList()

	val link = links.removeFirst().removeSurrounding("<", ">")
	Multiverse.broadcastSystem { content = link }
	targetMessage.edit { content = femboyMemesMarker + "\n" + links.joinToString("\n") }
}

suspend fun startFemboyPosting() {
	while (true) {
		var lastSent = StateManager.arbitraryData.getOrDefault(lastSentTimeKey, null)?.toLongOrNull() ?: 0L

		if (System.currentTimeMillis() - lastSent >= 1000L * 60 * 60 * 4) {
			StateManager.arbitraryData[lastSentTimeKey] = System.currentTimeMillis().toString()
			postFemboyMeme()
		}
		delay(5000L)
	}
}

package flarogus.multiverse.service

import flarogus.Vars
import flarogus.multiverse.*
import flarogus.multiverse.Multiverse.MultiversalService
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Reposts a picture from a random provided subreddit every [interval] ms. */
class RedditRepostService(
	val interval: Long = 1000L * 60 * 60 * 24,
	val subredditNames: List<String>
) : MultiversalService() {
	private lateinit var cachedName: String
	private var hash = 0
	override val name: String
		get() {
			if (!::cachedName.isInitialized || subredditNames.hashCode() != hash) {
				hash = subredditNames.hashCode()
				cachedName = "reddit" + subredditNames.joinToString(";") { it.take(5) }
			}
			return cachedName
		}

	private val lastSentKey = "last-sent"
	private val repostedCacheKey = "cache"
	private val subredditIndexKey = "index"
	private var job: Job? = null

	override suspend fun onLoad() {
		job = multiverse.launch {
			while (true) {
				var lastSent = loadData(lastSentKey)?.toLongOrNull() ?: 0L

				if (System.currentTimeMillis() - lastSent >= interval) {
					saveData(lastSentKey, System.currentTimeMillis().toString())
					postPicture()
					Log.debug { "reddit post sent" }
				}
				delay(5000L)
			}
		}
	}

	override suspend fun onStop() {
		job?.cancel()
	}

	/** Load and post a random picture in the multiverse. */
	suspend fun postPicture() {
 		val index = loadData(subredditIndexKey)?.toIntOrNull() ?: 1
		val subreddit = subredditNames[index % subredditNames.size]
		val (title, url) = loadPicture(subreddit)

		saveData(subredditIndexKey, (index + 1).toString())

		multiverse.broadcastSystem {
			content = "Title: [$title]($url)"
		}
	}

	/** Loads a random picture from the subreddit and returns a pair of (title, url). */
	suspend fun loadPicture(subreddit: String): Pair<String, String> {
		val sentPictures = loadData(repostedCacheKey)?.split(",").orEmpty().toMutableList()

		Vars.client.resources.httpClient.get("https://reddit.com/r/$subreddit/hot.json?limit=30")
			.body<JsonObject>()["data"]!!
			.jsonObject["children"]!!
			.jsonArray
			.asSequence()
			.map { it.jsonObject["data"]!!.jsonObject }
			.filterNot {
				it["stickied"]!!.jsonPrimitive.boolean || it["over_18"]!!.jsonPrimitive.boolean
			}
			.filter { it["url"]?.jsonPrimitive?.content?.startsWith("https://i.redd.it") ?: false }
			.filterNot {
				it["url"]!!.jsonPrimitive.content in sentPictures
			}
			.firstOrNull()
			?.let { post ->
				val url = post["url"]!!.jsonPrimitive.content
				val title = post["title"]!!.jsonPrimitive.content

				sentPictures.add(0, url)
				saveData(repostedCacheKey, sentPictures.take(15).joinToString(","))
				return title to url
			}
		return "no post this time :(" to "null"
	}
}

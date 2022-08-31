package flarogus.multiverse.entity

import flarogus.multiverse.*
import dev.kord.rest.request.KtorRequestException
import kotlin.time.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.time.Duration.Companion.seconds

@Serializable
@OptIn(ExperimentalTime::class)
abstract class MultiversalEntity {
	/** Whether this entity was forcibly banned */
	var isForceBanned = false

	@Transient
	var isValid = false
		protected set
	
	@Transient
	var lastUpdate = 0L

	suspend fun update() {
		try {
			withTimeout(30.seconds) {
				updateImpl()
			}
		} catch (e: Exception) {
			if (e !is KtorRequestException || e.status.code != 403) {
				Log.error { "Error when updating $this: $e" }
			} else {
				// multiversal entities are updated once per 8 minute,
				// so we put it 1 minute away from next update.
				lastUpdate = System.currentTimeMillis() - 1000 * 60 * 7
			}
		}
	}

	fun invalidate() {
		lastUpdate = 0L
	}

	
	abstract suspend fun updateImpl()
}

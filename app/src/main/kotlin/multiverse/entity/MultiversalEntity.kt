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
			if (e !is KtorRequestException || (e.status.code < 500 && e.status.code != 403)) {
				Log.error { "Error when updating $this: $e" }
			} else if (e.status.code != 403) {
				Log.error { "Server error when updating $this: $e" }
				lastUpdate = System.currentTimeMillis() - 1000 * 60 * 5
			} else {
				 // 403 is death
				 lastUpdate = Long.MAX_VALUE
			}
		}
	}

	// todo misleading name
	fun invalidate() {
		lastUpdate = 0L
	}
	
	abstract suspend fun updateImpl()
}

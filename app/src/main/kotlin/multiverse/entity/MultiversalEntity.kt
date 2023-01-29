package flarogus.multiverse.entity

import flarogus.multiverse.*
import flarogus.util.*
import dev.kord.rest.request.KtorRequestException
import kotlin.time.*
import kotlinx.coroutines.*
import kotlinx.serialization.*

@Serializable
abstract class MultiversalEntity {
	/** Whether this entity was forcibly banned */
	@SerialName("ban")
	var isForceBanned = false

	@Transient
	open var isValid = false
		protected set
	
	@SerialName("lu")
	var lastUpdate = 0L

	suspend fun update() {
		try {
			withTimeout(70.second) {
				updateImpl()
			}
		} catch (e: Exception) {
			if (e !is KtorRequestException || (e.status.code < 500 && e.status.code != 403)) {
				Log.error(e) { "Error when updating $this" }
			} else if (e.status.code != 403) {
				Log.error(e) { "Server error when updating $this" }
				lastUpdate = System.currentTimeMillis() - 5.minute
			} else {
				// 403 is death. the entity is almost certainly forever inaccessible.
				lastUpdate = System.currentTimeMillis() + 48.hour
				Log.error { "Received a response 403 while updating $this. Postponed the next update by 48 hours." }
			}
		}
	}

	/** Ensures the next update() call will actually update the entity. */
	fun invalidate() {
		lastUpdate = 0L
	}
	
	abstract suspend fun updateImpl()
}

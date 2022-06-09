package flarogus.multiverse.entity

import flarogus.multiverse.*
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
	var isValid = true
		protected set
	
	@Transient
	var lastUpdate = 0L

	suspend fun update() {
		try {
			withTimeout(30.seconds) {
				updateImpl()
			}
		} catch (e: Exception) {
			Log.error { "Error when updating $this: $e" }
		}
	}

	fun invalidate() {
		lastUpdate = 0L
	}

	
	abstract suspend fun updateImpl()
}

package flarogus.multiverse.entity

import kotlin.time.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.time.Duration.Companion.seconds

@Serializable
@OptIn(ExperimentalTime::class)
abstract class MultiversalEntity() {
	/** Whether this entity was forcibly banned */
	var isForceBanned = false

	@Transient
	var isValid = true
		protected set

	suspend fun update() {
		try {
			withTimeout(30.seconds) {
				updateImpl()
			}
		} catch (e: Exception) {
			println(e)
		}
	}
	
	abstract suspend fun updateImpl()
}

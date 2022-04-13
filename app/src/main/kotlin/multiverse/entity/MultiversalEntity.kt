package flarogus.multiverse.entity

import kotlinx.serialization.*

@Serializable
abstract class MultiversalEntity() {
	/** Whether this entity was forcibly banned */
	var isForceBanned = false
	var isValid = true
		protected set
	
	abstract suspend fun update()
}

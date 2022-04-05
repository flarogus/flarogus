package flarogus.multiverse.entity

import kotlinx.serialization.*

@Serializable
abstract class MultiversalEntity(
	val uuid: ULong
) {
	constructor() : this((System.currentTimeMillis() xor 0x5AAAAAAAAAAAAAAAL).toULong()) //funny.
}

package flarogus.multiverse.state

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MessageBehavior
import flarogus.Vars
import flarogus.multiverse.*
import flarogus.multiverse.entity.MultiversalUser.BankAccount.Transaction
import flarogus.multiverse.state.*
import flarogus.util.*
import kotlin.reflect.KClass
import kotlinx.datetime.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@OptIn(InternalSerializationApi::class)
fun <T: Any> serializer(clazz: KClass<T>): KSerializer<T> = clazz.serializer()

class RuleSerializer : KSerializer<Rule> {
	override val descriptor = PrimitiveSerialDescriptor("Rule", PrimitiveKind.STRING)
	
	override fun serialize(encoder: Encoder, value: Rule) {
		encoder.encodeString("${value.category}:${value.index}")
	}
	
	override fun deserialize(decoder: Decoder): Rule {
		val parts = decoder.decodeString().split(':')
		return RuleCategory.of(parts[0].toInt(), parts[1].toInt()) ?: Rule.UNKNOWN
	}
}

class TransactionSerializer : KSerializer<Transaction> {
	val incomingType = "i"
	val outgoingType = "o"
	override val descriptor = PrimitiveSerialDescriptor("Transaction", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: Transaction) {
		val (type, userId) = when (value) {
			is Transaction.IncomingTransaction -> incomingType to value.sender
			is Transaction.OutgoingTransaction -> outgoingType to value.receiver
		}
		encoder.encodeString("$type;${value.amount};$userId;${value.timestamp}")
	}
	
	override fun deserialize(decoder: Decoder): Transaction {
		val parts = decoder.decodeString().split(';')

		val timestamp = runCatching {
			parts[3].toInstant()
		}.getOrElse { Instant.fromEpochMilliseconds(0L) }

		return when (val type = parts[0]) {
			incomingType -> Transaction.IncomingTransaction(parts[1].toInt(), parts[2].toSnowflake(), timestamp)
			outgoingType -> Transaction.OutgoingTransaction(parts[1].toInt(), parts[2].toSnowflake(), timestamp)
			else -> error("unresolved transaction type alias: '$type'")
		}
	}
}

class MultimessageSerializer : KSerializer<Multimessage> {
	private val snowflakeSerializer = serializer(Snowflake::class)
	private val wmblistSerializer = ListSerializer(serializer(WebhookMessageBehavior::class))

	override val descriptor: SerialDescriptor = buildClassSerialDescriptor("flarogus.multiverse.state.Multimessage") {
		element("id", snowflakeSerializer.descriptor)
		element("ch", snowflakeSerializer.descriptor)
		element("ret", wmblistSerializer.descriptor)
	}
	
	override fun serialize(encoder: Encoder, value: Multimessage) = encoder.encodeStructure(descriptor) {
		if (value.origin != null) {
			encodeSerializableElement(descriptor, 0, snowflakeSerializer, value.origin!!.id)
			encodeSerializableElement(descriptor, 1, snowflakeSerializer, value.origin!!.channelId)
		}
		encodeSerializableElement(descriptor, 2, wmblistSerializer, value.retranslated)
	}
	
	override fun deserialize(decoder: Decoder): Multimessage = decoder.decodeStructure(descriptor) {
		var id: Snowflake? = null
		var channelId: Snowflake? = null
		var retranslated: List<WebhookMessageBehavior>? = null

		eachIndex(descriptor) {
			when (it) {
				0 -> id = decodeSerializableElement(descriptor, 0, snowflakeSerializer)
				1 -> channelId = decodeSerializableElement(descriptor, 1, snowflakeSerializer)
				2 -> retranslated = decodeSerializableElement(descriptor, 2, wmblistSerializer)
			}
		}

		val message = if (channelId != null && id != null) {
			MessageBehavior(channelId = channelId!!, messageId = id!!, kord = Vars.client)
		} else {
			null
		}
		Multimessage(message, retranslated!!.toMutableList())
	}
}

class WebhookMessageSerializer : KSerializer<WebhookMessageBehavior> {
	private val snowflakeSerializer = serializer(Snowflake::class)

	override val descriptor = PrimitiveSerialDescriptor("WebhookMessageBehavior", PrimitiveKind.STRING)
	
	override fun serialize(encoder: Encoder, value: WebhookMessageBehavior) {
		encoder.encodeString("${value.webhookId}:${value.channelId}:${value.id}")
	}
	
	override fun deserialize(decoder: Decoder): WebhookMessageBehavior {
		val parts = decoder.decodeString().split(':')
		
		return WebhookMessageBehavior(
			parts[0].toSnowflake(),
			parts[1].toSnowflake(),
			parts[2].toSnowflake()
		)
	}
}

fun CompositeDecoder.eachIndex(descriptor: SerialDescriptor, handler: (index: Int) -> Unit) {
	while (true) {
		val index = decodeElementIndex(descriptor)
		when {
			index >= 0 -> handler(index)
			index == CompositeDecoder.DECODE_DONE -> break
			else -> throw IllegalStateException("Unexpected index: $index")
		}
	}
}

package flarogus.multiverse.state

import kotlin.reflect.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.builtins.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.rest.builder.message.modify.*
import dev.kord.core.*
import dev.kord.core.supplier.*
import dev.kord.core.event.message.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.*
import flarogus.multiverse.*

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
inline fun <T: Any> serializer(clazz: KClass<T>): KSerializer<T> = clazz.serializer()

data class UniverseEntry(var webhook: Webhook?, val channel: TextChannel, var hasReported: Boolean = false)

@kotlinx.serialization.Serializable(with = HistoryEntrySerializer::class)
data class WebhookMessageBehavior(
	val webhookId: Snowflake,
	override val channelId: Snowflake,
	override val id: Snowflake,
	override val kord: Kord = Vars.client,
	override val supplier: EntitySupplier = Vars.supplier
) : MessageBehavior {
	constructor(webhook: Webhook, message: Message) : this(webhook.id, message.channelId, message.id, message.kord)
	
	suspend open fun getWebhook() = supplier.getWebhook(webhookId);
	
	suspend open inline fun edit(builder: WebhookMessageModifyBuilder.() -> Unit): Message {
		val webhook = getWebhook()
		return edit(webhookId = webhook.id, token = webhook.token!!, builder = builder) //have to specify the parameter name in order for kotlinc to understand me
	}
	
	override suspend open fun delete(reason: String?) {
		val webhook = getWebhook()
		delete(webhook.id, webhook.token!!, null)
	}
}

@kotlinx.serialization.Serializable(with = HistorySerializer::class)
data class Multimessage(
	val origin: MessageBehavior,
	val retranslated: List<WebhookMessageBehavior>
) {
	operator fun contains(other: MessageBehavior) = other.id == origin.id || retranslated.any { other.id == it.id };
	
	operator fun contains(other: Snowflake) = origin.id == other || retranslated.any { other == it.id };
}

class HistorySerializer : KSerializer<Multimessage> {
	private val snowflakeSerializer = serializer(Snowflake::class)
	private val wmblistSerializer = ListSerializer(serializer(WebhookMessageBehavior::class))

	override val descriptor: SerialDescriptor = buildClassSerialDescriptor("flarogus.multiverse.state.Multimessage") {
		element("id", snowflakeSerializer.descriptor)
		element("channelId", snowflakeSerializer.descriptor)
		element("retranslated", wmblistSerializer.descriptor)
	}
	
	override fun serialize(encoder: Encoder, value: Multimessage) = encoder.encodeStructure(descriptor) {
		encodeSerializableElement(descriptor, 0, snowflakeSerializer, value.origin.id)
		encodeSerializableElement(descriptor, 1, snowflakeSerializer, value.origin.channelId)
		encodeSerializableElement(descriptor, 2, wmblistSerializer, value.retranslated)
	};
	
	override fun deserialize(decoder: Decoder): Multimessage = decoder.decodeStructure(descriptor) {
		val id = decodeSerializableElement(descriptor, 0, snowflakeSerializer)
		val channelId = decodeSerializableElement(descriptor, 1, snowflakeSerializer)
		
		val message = MessageBehavior(channelId = channelId, messageId = id, kord = Vars.client)
		
		val retranslated = decodeSerializableElement(descriptor, 2, wmblistSerializer)
		return Multimessage(message, retranslated)
	}
}

class HistoryEntrySerializer : KSerializer<WebhookMessageBehavior> {
	private val snowflakeSerializer = serializer(Snowflake::class)

	override val descriptor: SerialDescriptor = buildClassSerialDescriptor("flarogus.multiverse.state.WebhookMessageBehavior") {
		element("webhookId", snowflakeSerializer.descriptor)
		element("channelId", snowflakeSerializer.descriptor)
		element("id", snowflakeSerializer.descriptor)
	}
	
	override fun serialize(encoder: Encoder, value: WebhookMessageBehavior) = encoder.encodeStructure(descriptor) {
		encodeSerializableElement(descriptor, 0, snowflakeSerializer, value.webhookId)
		encodeSerializableElement(descriptor, 1, snowflakeSerializer, value.channelId)
		encodeSerializableElement(descriptor, 2, snowflakeSerializer, value.id)
	};
	
	override fun deserialize(decoder: Decoder): WebhookMessageBehavior = decoder.decodeStructure(descriptor) {
		val webhookId = decodeSerializableElement(descriptor, 0, snowflakeSerializer)
		val channelId = decodeSerializableElement(descriptor, 1, snowflakeSerializer)
		val messageId = decodeSerializableElement(descriptor, 2, snowflakeSerializer)
		
		return WebhookMessageBehavior(webhookId, channelId, messageId)
	}
}

package flarogus.multiverse.state

import kotlin.reflect.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
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

val snowflakeSerializer = serializer(Snowflake::class)
val snowflakeDescriptor = snowflakeSerializer.descriptor

@OptIn(kotlinx.serialization.InternalSerializationApi::class)
inline fun <T: Any> serializer(clazz: KClass<T>): KSerializer<T> = clazz.serializer()

data class UniverseEntry(var webhook: Webhook?, val channel: TextChannel, var hasReported: Boolean = false)

@kotlinx.serialization.Serializable(with = HistorySerializer::class)
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

@kotlinx.serialization.Serializable
data class Multimessage(
	val origin: MessageBehavior,
	val retranslated: List<WebhookMessageBehavior>
) {
	operator fun contains(other: MessageBehavior) = other.id == origin.id || retranslated.any { other.id == it.id };
	
	operator fun contains(other: Snowflake) = origin.id == other || retranslated.any { other == it.id };
}

class HistorySerializer : KSerializer<WebhookMessageBehavior> {
	override val descriptor: SerialDescriptor = buildClassSerialDescriptor("HistorySerializer") {
		element("hook", snowflakeDescriptor)
		element("ch", snowflakeDescriptor)
		element("id", snowflakeDescriptor)
	}
	
	override fun serialize(encoder: Encoder, value: WebhookMessageBehavior) = encoder.encodeStructure(descriptor) {
		encodeSerializableElement(snowflakeDescriptor, 0, snowflakeSerializer, value.webhookId)
		encodeSerializableElement(snowflakeDescriptor, 1, snowflakeSerializer, value.channelId)
		encodeSerializableElement(snowflakeDescriptor, 2, snowflakeSerializer, value.id)
	};
	
	override fun deserialize(decoder: Decoder): WebhookMessageBehavior = decoder.decodeStructure(descriptor) {
		val webhookId = decodeSerializableElement(snowflakeDescriptor, 0, snowflakeSerializer)
		val channelId = decodeSerializableElement(snowflakeDescriptor, 1, snowflakeSerializer)
		val messageId = decodeSerializableElement(snowflakeDescriptor, 2, snowflakeSerializer)
		
		return WebhookMessageBehavior(webhookId, channelId, messageId)
	}
}


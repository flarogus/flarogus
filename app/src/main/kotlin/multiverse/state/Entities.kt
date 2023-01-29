package flarogus.multiverse.state

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.Webhook
import dev.kord.core.supplier.EntitySupplier
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import dev.kord.rest.builder.message.modify.WebhookMessageModifyBuilder
import flarogus.Vars
import flarogus.multiverse.state.MultimessageSerializer
import flarogus.multiverse.state.WebhookMessageSerializer
import kotlinx.coroutines.yield
import kotlinx.serialization.*

@Serializable(with = WebhookMessageSerializer::class)
data class WebhookMessageBehavior(
	val webhookId: Snowflake,
	override val channelId: Snowflake,
	override val id: Snowflake,
	override val kord: Kord = Vars.client,
	override val supplier: EntitySupplier = Vars.supplier
) : MessageBehavior {
	private var cachedToken: String? = null

	constructor(webhook: Webhook, message: MessageBehavior) : this(webhook.id, message.channelId, message.id, Vars.client)
	
	suspend fun getWebhook() = supplier.getWebhook(webhookId)

	suspend fun getToken() = cachedToken ?: run {
		getWebhook().token!!.also { cachedToken = it }
	}
	
	suspend inline fun edit(builder: WebhookMessageModifyBuilder.() -> Unit): Message {
		return edit(webhookId = webhookId, token = getToken(), builder = builder) //have to specify the parameter name in order for kotlinc to understand me
	}
	
	override suspend fun delete(reason: String?) {
		delete(webhookId, getToken(), null)
	}

	override fun toString(): String {
		return "WebhookMessageBehavior(webhookId=$webhookId, channelId=$channelId, id=$id)"
	}
}

@Serializable(with = MultimessageSerializer::class)
data class Multimessage(
	var origin: MessageBehavior? = null,
	val retranslated: MutableList<WebhookMessageBehavior>
) {
	suspend fun delete(deleteOrigin: Boolean) {
		retranslated.forEach { 
			try { it.delete() } catch (ignored: Exception) { }
			yield()
		}
		synchronized(retranslated) { retranslated.clear() }

		if (deleteOrigin) {
			try { origin?.delete() } catch (ignored: Exception) { }
			origin = null
		}
		synchronized(Vars.multiverse.history) {
			Vars.multiverse.history.remove(this)
		}
	}

	suspend inline fun edit(modifyOrigin: Boolean = false, crossinline builder: suspend MessageModifyBuilder.() -> Unit) {
		retranslated.forEach {
			try { it.edit { builder() } } catch (ignored: Exception) { }
			yield()
		}
		if (modifyOrigin) {
			try { origin?.edit { builder() } } catch (ignored: Exception) { }
		}
	}

	operator fun contains(other: MessageBehavior) = other.id == origin?.id || retranslated.any { other.id == it.id };
	
	operator fun contains(other: Snowflake) = origin?.id == other || retranslated.any { other == it.id };
}


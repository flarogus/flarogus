package flarogus.multiverse.service

import dev.kord.core.event.message.MessageCreateEvent
import com.github.mnemotechnician.markov.*
import flarogus.multiverse.Multiverse.MultiversalService


class MarkovChainService : MultiversalService() {
	override val name = "markov"
	lateinit var chain: MarkovChain
	val dataKey = "chain-serialized"

	override suspend fun onStart() {
		val serializedMarkov = loadData(dataKey) ?: run {
			chain = MarkovChain()
			return
		}
		chain = MarkovChain.deserializeFromString(serializedMarkov)
	}

	override suspend fun onMessageReceived(event: MessageCreateEvent, retranslated: Boolean) {
		if (!retranslated) return
		chain.train(event.message.content)

		saveData(dataKey, chain.serializeToString())
	}
}

package flarogus.multiverse.services

import com.github.mnemotechnician.markov.*
import flarogus.multiverse.Multiverse.MultiversalService


class MarkovChainService : MultiversalService() {
	lateinit val chain: MarkovChain
	val dataKey = "chain-serialized"

	override fun onStart() {
		val serializedMarkov = loadData(dataKey) ?: run {
			markov = MarkovChain()
			return
		}
		markov = MarkovChain.deserializeFromString(serializedMarkov)
	}

	override fun fun onMessage(event: MessageCreateEvent, retranslated: Boolean) {
		if (!retranslated) return
		chain.train(event.message.content)

		saveData(dataKey, chain.serializeToString())
	}
}

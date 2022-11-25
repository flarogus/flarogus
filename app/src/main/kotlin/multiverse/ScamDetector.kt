package multiverse

object ScamDetector {
	
	val trustedPatterns = arrayOf(
		"discord.com",
		"discord.gg",
		"discord.media",
		"discord.gift",
		"discordapp.com",
		"discordapp.net",
		"discordstatus.com"
	).map { """://$it""" } + arrayOf(
		"discord.js",
		"discord.py",
		"discord.gg/"
	)
	
	val scamRegex = arrayOf(
		"""https:\/\/[bd][il]?s[cс]?[аоao]?r?d\.[a-zA-Z0-9]+/[a-zA-Z0-9]*""",
		"""@everyone.+(nitro)?http://""",
		"""stea.*co.*\.ru""",
		"""http.*stea.*c.*\..*trad""",
		"""csgo.*kni[fv]e"""	,
		"""cs.?go.*inventory""",
		"""cs.?go.*cheat""",
		"""cheat.*cs.?go""",
		"""cs.?go.*skins""",
		"""skins.*cs.?go""",
		"""stea.*com.*partner""",
		"""скин.*partner""",
		"""steamcommutiny""",
		"""di.*\.gift.*nitro""",
		"""http.*disc.*gift.*\.""",
		"""free.*nitro.*http""",
		"""http.*free.*nitro.*""",
		"""nitro.*free.*http""",
		"""discord.*nitro.*free""",
		"""free.*discord.*nitro""",
		"""@everyone.*http""",
		"""http.*@everyone""",
		"""discordgivenitro""",
		"""http.*gift.*nitro""",
		"""http.*nitro.*gift""",
		"""http.*n.*gift""",
		"""бесплат.*нитро.*http""",
		"""нитро.*бесплат.*http""",
		"""nitro.*http.*disc.*nitro""",
		"""http.*click.*nitro""",
		"""http.*st.*nitro""",
		"""http.*nitro""",
		"""stea.*give.*nitro""",
		"""discord.*nitro.*steam.*get""",
		"""gift.*nitro.*http""",
		"""http.*discord.*gift""",
		"""discord.*nitro.*http""",
		"""personalize.*your*profile.*http""",
		"""nitro.*steam.*http""",
		"""steam.*nitro.*http""",
		"""nitro.*http.*d""",
		"""http.*d.*gift""",
		"""gift.*http.*d.*s""",
		"""discord.*steam.*http.*d""",
		"""nitro.*steam.*http""",
		"""steam.*nitro.*http""",
		"""dliscord.com""",
		"""free.*nitro.*http""",
		"""discord.*nitro.*http""",
		"""@everyone.*http""",
		"""http.*@everyone""",
		"""@everyone.*nitro""",
		"""nitro.*@everyone""",
		"""discord.*gi.*nitro"""
	).map { it.toRegex() }
	
	fun hasScam(message: String): Boolean {
		var processed = message.lowercase()
		
		trustedPatterns.forEach { processed = processed.replace(it, "--TRUSTED--") }
		scamRegex.forEach {
			if (it.matches(processed)) {
				return true
			}
		}
		return false
	}
}

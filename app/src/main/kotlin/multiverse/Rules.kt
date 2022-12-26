package flarogus.multiverse

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 * Contains all the multiversal rules.
 * New rules should ONLY be appended to the end, as doing otherwise will make existing warns invalid!
 */
enum class RuleCategory(val index: Int, val description: String, val rules: List<Rule>) {
	GENERAL(1, "list of multiversal rules", listOf(
		Rule(1, "Do not insult or harass other people. Hating people for belonging to some fandom is included."),
		Rule(1, "Do not spam and do not dump meaningless messages."),
		Rule(4, "Posting scam/phishing/malicious links is strictly prohibited."),
		Rule(2, "Do not post NSFW media."),
		Rule(2, "Do not advertise discord servers without asking for consent."),
		Rule(1, "Avoid speaking natural languages not spoken by other users."),
		Rule(10, "Spam raids are forbidden, any raider will be banned immediately without a right to appeal."),
		Rule(2, "Addition to r1: racism, fascism, nazism, homophobia, transphobia and other forms of discrimination are forbidden.")
	)),
	
	PUNISHMENT(2, "any of the following punishments can be applied by the admins.", listOf(
		Rule(-1, "A verbal warning or a message deletion."),
		Rule(-1, "A physical warning (the amount of warning points depends on the rule)."),
		Rule(-1, "A temporary ban (applied automatically when the user has 5 warn points)."),
		Rule(-1, "A permanent ban."),
		Rule(-1, "A special punishment based on the actions of the user.")
	)),

	ADDITIONAL(3, "notes", listOf(
		Rule(-1, "Multiversal admins are Mnemotechnician#9967, SMOLKEYS#4156, pineapple#7816"),
		Rule(-1, "You can contact the admins, report a violation or ask a question using `!flarogus report`.")
	));

	init {
		rules.forEachIndexed { ruleIndex: Int, it ->
			it.category = index
			it.index = ruleIndex
		}
	}

	override fun toString() = buildString {
		append(super.toString()).append(": ").append(description).append("\n\n")
		rules.forEachIndexed { i: Int, it -> append(it).append('\n') }
	};
	
	operator fun get(number: Int): Rule = (rules.getOrNull(number - 1) ?: throw IllegalArgumentException("rule '${super.toString()}.$index' doesn't exist!"))
	
	companion object {
		/** Returns a rule with the specified category and index */
		fun of(category: Int, rule: Int) = RuleCategory.values().find { it.index == category }?.get(rule + 1)
	}
}

@Serializable(with = RuleSerializer::class)
data class Rule(val points: Int = -1, val description: String) {
	//these two are initialised right after creation
	var category = -1
	var index = -1
	
	override fun toString() = buildString {
		append(category).append(".").append(index + 1).append(". ")
		append(description)
		if (points > 0) append(" [").append(points).append(" warning points]")
	};
	
	companion object {
		val UNKNOWN = Rule(-1, "unknown rule")
	}
}

class RuleSerializer : KSerializer<Rule> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RuleSerializer", PrimitiveKind.STRING)
	
	override fun serialize(encoder: Encoder, value: Rule) {
		encoder.encodeString("${value.category}:${value.index}")
	}
	
	override fun deserialize(decoder: Decoder): Rule {
		val parts = decoder.decodeString().split(':')
		return RuleCategory.of(parts[0].toInt(), parts[1].toInt()) ?: Rule.UNKNOWN
	}
}

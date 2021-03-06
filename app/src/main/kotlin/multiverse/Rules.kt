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
		Rule(1, "Do not insult or harrass other people. This also includes fandom-related hate, stop posting it for fuck's sake."),
		Rule(1, "Do not spam / flood the multiverse. Meme dumps are allowed as long as they don't disturb other users."),
		Rule(4, "Posting scam links is strictly prohibited and can result in an immediate ban, unless the link was successfully blocked by the filter."),
		Rule(2, "Avoid posting nsfw-content. Posting explicit images / videos / gifs is prohibited. Videos with a questionable preview are counted too."),
		Rule(2, "Do not advertise discord servers without consent."),
		Rule(1, "Avoid speaking foreign languages that other users can't understand (if they can understand it, it's fine) and do not encode text messages."),
		Rule(10, "Spam raids are forbidden, any raider is to be banned immediately."),
		Rule(2, "Addiction to r1: racism, fascism, nazism, homophobia, transphobia and other forms of discrimination are forbidden")
	)),
	
	ADDITIONAL(2, "notes", listOf(
		Rule(-1, "Multiversal admins are Mnemotechnician#9967, SMOLKEYS#4156, pineapple#7816, real sushi#0001."),
		Rule(-1, "Your multiversal channels are your responsibility, it doesn't matter whether you connect a general channel of a popular server or an admin-only channel, rules still apply."),
		Rule(-1, "The fact that 2 of 4 admins are furries __does not__ mean you can post yiff in multiverse!"),
		Rule(-1, "Personal animosity is not a valid reason for any form of punishment, if an admin does that, report it to owner")
	)),
	
	PUNISHMENT(3, "the following punishments can be applied by the admins", listOf(
		Rule(-1, "A verbal warning."),
		Rule(-1, "A physical warning (the amount of warning points depends on the rule)."),
		Rule(-1, "A temporary ban (applied automatically when the user has 5 warn points)."),
		Rule(-1, "A permanent ban.")
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
class Rule(val points: Int = -1, val description: String) {
	//these two are inited right after creation
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

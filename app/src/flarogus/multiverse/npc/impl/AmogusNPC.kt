package flarogus.multiverse.npc.impl

import kotlin.random.*
import dev.kord.core.*
import flarogus.multiverse.npc.*

class AmogusNPC : NPC(1000L * 60 * 10) {
	override val name = "local amogus"
	override val location = "oblivion settlement"
	override val avatar = "https://drive.google.com/uc?export=download&id=19jMVrZwOuWpe7vJ1Gb3Uj0tzi27kXeEY"
	
	override val dialog = buildDialog {
		- condition {
			If { it.contains(":sus:") } then "STOP " and random {
				- "POSTING"
				- "TALKING"
				- "SPEAKING"
			} and " ABOUT " and random {
				- "AMONG US"
				- "AMONGUS"
				- "AMOGUS"
				- "SUS"
			} and "! I" and random {
				- "'M TIRED OF SEEING IT"
				- " HATE IT"
				- " SEE IT EVERYWHERE"
			} and "!!!"
			
			If { it.contains("amogus") && it.length < 10 } then random {
				- "no" and random {
					- ""
					- " u"
					- "shut up"
					- " I'm ain't no " and random {
						- "impostor"
						- "impasta"
						- "sus"
					}
				} and run { "!".repeat(Random.nextInt(1, 5)) }
				
				- "s" and run { "us".repeat(Random.nextInt(1, 5)) } and " " and random {
					- "amogus"
					- "mogus"
					- "sugoma"
					- "momgus"
				}
			}
			
			If { it.contains("what") && it.contains("is") && it.contains("flarogus") } then random {
				- "flar"
				- "flarogus"
			} and " is my " and random {
				- "favorite bot"
				- "beloved"
				- "frien" and run { if (Random.nextInt(0, 1) == 1) "d" else "" }
			} and "!"
			
			//else
			If { true } then ""
		}
	}
}

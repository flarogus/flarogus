package flarogus.multiverse.npc.impl

import kotlin.random.*
import dev.kord.core.*
import flarogus.multiverse.npc.*

class AmogusNPC : NPC(1000L * 30 * 1) {
	override val name = "local amogus"
	override val location = "oblivion settlement"
	override val avatar = "https://drive.google.com/uc?export=download&id=1K6z_hf7xaNHcXkYiv3JtMZJCwBNU_ZRe"
	
	override val dialog = buildDialog {
		- condition {
			If { it.contains("amogus") && it.length < 15 } then random {
				- "no" and random {
					- ""
					- " u"
					- " shut up"
					- " go away"
				} and repeat(1, 5, "!")
				
				- "s" and repeat(1, 5, "us") and " " and random {
					- ""
					- "amogus"
					- "mogus"
					- "sugoma"
					- "momogus"
				}

				- "when the " and random {
					- "amogus"
					- "flarogus"
					- "impasta"
				} and " is " and random {
					- "sus"
					- "the impostor"
				}
			}
			
			If { it.contains("what", true) && it.contains("is", true) && it.contains("flarogus", true) } then random {
				- "flar"
				- "flarogus"
			} and " is my " and random {
				- "favorite bot"
				- "beloved"
				- "frien"
				- "friend"
			} and "!"
			
			If { isOwnMessage(lastProcessed?.referencedMessage) } then condition {
				//when someone replies something like "you're amogus"
				If { it.contains("you") && it.contains("amogus") } then random {
					- "that's a lie"
					- "lies"
					- "no"
				}
				
				//when someone asks something like "who are you"
				If { it.contains("who") && it.contains("you") } then "I'm " and random {
					- "your local amogus"
					- "just a normal fella"
					- "totally a human"
					- "a crewmate"
					- "an impostor"
					- "a fan of flarogus corporation"
				}
				
				If { it.contains("you") && (it.contains("sus") || it.contains("amogus")) } then random {
					- "shut up"
					- "no"
					- "no u"
				} and repeat(1, 5, "!")
				
				If { it.contains("shut") } then random {
					- "no"
					- "i won't"
					- "why"
					- "no u"
				}
				
				//shut up please
				If { it.contains(":sus:") } then random {
					- "I TOLD YOU TO STOP"
					- "don't you dare"
					- "staaahp"
					- "go away"
					- "touch grass"
					- "do you know anything else?!"
				}

				If { "lol" in it || "lmao" in it || "lmfao" in it } then random {
					- "what's funny"
					- "stop laughing"
					- "peak comedy."
				}
				
				//just a generic answer
				If { it.contains("you") } then random {
					- "idc"
					- "I don't care"
					- random {
						- "ok"
						- "and"
						- "ok and"
					}
				}
			}
			
			If { ":sus:" in it || ":amogus:" in it } then random {
				- ""
				
				- "STOP " and random {
					- "POSTING"
					- "TALKING"
					- "SPEAKING"
					- "REMINDING ME"
				} and " ABOUT " and random {
					- "AMONG US"
					- "AMONGUS"
					- "AMOGUS"
					- "SUS"
					- "THIS FUCKING EMOJI"
				} and "! I" and random {
					- "'M TIRED OF SEEING IT"
					- " HATE IT"
					- " SEE IT EVERYWHERE"
					- " HAVE TOLD YOU HUNDRES OF TIMES ALREADY"
					- " WILL MURDER YOU"
				} and "!!!"
				
				- "no"
				- "NO!"
				- "stop"

				- "will you ever " and random {
					- "stop"
					- "shut up"
					- "get banned"
				} and repeat(0, 3, "?")
				
				- "shut up" and random {
					- ""
					- " amogus poster"
					- " crewmate"
					- " impostor"
					- " mortal"
					- " before i ban you"
				} and repeat(1, 2, "!")
				
				- "s" and repeat(1, 7, "us")
				- "sussy"
				- "arkine moment"
				- run { phrasegen(6, 's', 's', 'u', 'u', 'a', 'm', 'o', 'g') } and repeat(1, 4, "!")
				- run { phrasegen(7, 'u', 's', 'o', 'm', 'g') } and run { phrasegen(3, '!', '?') }
			}
			
			//else
			If { true } then ""
		}
	}
}

inline fun phrasegen(length: Int, vararg letters: Char) = buildString {
	repeat(length) {
		append(letters.random())
	}
}

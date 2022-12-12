package flarogus.command.impl

import flarogus.command.builder.TreeCommandBuilder
import kotlin.random.Random

private const val bombEmoji = "💥"
private val numbers = arrayOf("0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣")

private const val maxW = 25
private const val maxH = 30

private const val openFlag = 0b10000

fun TreeCommandBuilder.addMinesweeperSubcommand() = subcommand<Unit?>("minesweeper") {
	discordOnly() // there's no way this command can be run in an anonymous way.

	description = "Play minesweeper in discord."

	arguments {
		default("width", "The width of the field. Defaults to 12. Max is $maxW.") { 12 }
		default("height", "The height of the field. Defaults to 8. Max ia $maxH.") { 18 }

		default("mines", "The number of mines on the field. Defaults to (w * h / 3.3).") {
			(args.arg<Int>("width") * args.arg<Int>("height") / 3.3f).toInt()
		}
	}

	action {
		val w = args.arg<Int>("width")
		val h = args.arg<Int>("height")
		val mines = args.arg<Int>("mines")

		val mineLimit = w * h - 9

		require(w > 3 && w <= maxW) { "Width must be in range of (3; $maxW]" }
		require(h > 3 && h <= maxH) { "Height must be in range of (3; $maxH]" }
		require(mines >= 0) { "A mine field can not contain a negative amount of times." }
		require(mines <= mineLimit) { "A ${w}x${h} mine field can not contain more than $mineLimit mines" }

		val freeTiles = ArrayList<Int>(25 * 25) //each value represents both x and y component. assuming these components won't exceed 255.
		val builder = StringBuilder(2000)
		
		//position that will be opened at the start
		val openX = Random.nextInt(1, w - 1)
		val openY = Random.nextInt(1, h - 1)
		
		//mine == -1, free == [0, 8] (number of mines), opened = [16, 24] (5th bit is active, other represent number of mines)
		val field = Array(w) { IntArray(h) }

		//first iteration: put all possible tiles in the list
		freeTiles.clear()
		for (x in 0 until w) {
			for (y in 0 until h) {
				if (Math.abs(x - openX) > 1 || Math.abs(y - openY) > 1) {
					freeTiles += packTile(x, y)
				}
			}
		}
		//second iteration: shuffle the list and put bombs on the field at the first ${mines} positions taken from the list
		freeTiles.shuffle()
		for (i in 0 until Math.min(mines, freeTiles.size)) {
			val tile = freeTiles.get(i)
			val x = unpackX(tile)
			val y = unpackY(tile)
			field[x][y] = -1 //put a mine
		}

		//third iteration: for each tile, count neighbouring bombs, assign their number to the tile
		for (x in 0 until w) {
			for (y in 0 until h) {
				if (field[x][y] == -1) continue; //there's a bomb already
				var count = 0
				neighbours(x, y) { tx, ty -> if (field.getOrNull(tx)?.getOrNull(ty) == -1) count++ }
				//check if the tile should be pre-open, add the "open" flag (0b10000) in that case
				if ((Math.abs(x - openX) <= 1) && Math.abs(y - openY) <= 1) count = count or openFlag
				
				field[x][y] = count
			}
		}

		//fourth iteration: construct the game field
		builder.apply {
			clear()

			if (w * h * 6 > 1930) {
				appendLine("warning: too many tiles, the message will be split into multiple")
			}

			appendLine("$mines mines").appendLine()
		
			for (y in 0 until h) {
				//send the current part of the game field if adding another row to it would cause an overflow
				val nextLength = length + w * 6 
				if (nextLength >= 1950) {
					originalMessage!!.channel.createMessage(builder.toString())
					clear()
				}
				
				for (x in 0 until w) {
					val value = field[x][y]
					
					if (value == -1) {
						append("||").append(bombEmoji).append("||")
					} else {
						val emoji = numbers[value and 0b1111]
						if (value and 0b10000 != 0) { //pre-open tile
							append(emoji)
						} else {
							append("||").append(emoji).append("||")
						}
					}
				}
				append('\n')
			}
			if (!isEmpty()) {
				originalMessage!!.channel.createMessage(toString())
				hasResponded = true
			}
		}
	}
}

private fun packTile(x: Int, y: Int): Int = ((x and 0xff) shl 8) or (y and 0xff);

private fun unpackX(tile: Int): Int = (tile shr 8) and 0xff;

private fun unpackY(tile: Int): Int = tile and 0xff;

/** very straightforward */
private inline fun neighbours(x: Int, y: Int, action: (Int, Int) -> Unit) {
	action(x + 1, y + 1)
	action(x    , y + 1)
	action(x - 1, y + 1)
	
	action(x + 1, y    )
	action(x - 1, y    )
	
	action(x + 1, y - 1)
	action(x    , y - 1)
	action(x - 1, y - 1)
}

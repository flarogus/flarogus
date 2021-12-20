package flarogus.commands.impl

import kotlin.random.*;
import kotlinx.coroutines.*;
import dev.kord.core.entity.*;
import dev.kord.core.event.message.*;
import flarogus.util.*;

private val bombEmoji = "💥"
private val numbers = arrayOf("0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣")
private val freeTiles = ArrayList<Int>(25 * 25) //each value represents both x and y component. assuming these components won't exceed 255.
private val builder = StringBuilder(2000)

private val maxW = 30
private val maxH = 30

val MinesweeperCommand = flarogus.commands.Command(
	handler = {
		val w = (it.getOrNull(1)?.toIntOrNull() ?: 12).coerceIn(5, maxW)
		val h = (it.getOrNull(2)?.toIntOrNull() ?: 12).coerceIn(5, maxH)
		val mines = (it.getOrNull(3)?.toIntOrNull() ?: (w * h / 4)).coerceIn(0, w * h - 8)
		
		//position that will be opened at the start
		val openX = Random.nextInt(1, w - 1)
		val openY = Random.nextInt(1, h - 1)
		
		//mine == -1, free == [0, 8] (number of mines), opened = [16, 24] (5th bit is active, other represent number of mines)
		val field = Array(w) { IntArray(h) }
		//first iteration: put all possible tiles into the list
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
		//third iteration: count neighbour bombs, assign the amount of neighbours next
		for (x in 0 until w) {
			for (y in 0 until h) {
				if (field[x][y] == -1) continue; //there's a bomb already
				var count = 0
				neighbours(x, y) { tx, ty -> if (field.getOrNull(tx)?.getOrNull(ty) == -1) count++ }
				//check if the tile should be pre-open, add the "open" flag (0b10000) in that case
				if ((Math.abs(x - openX) <= 1) && Math.abs(y - openY) <= 1) count = count or 0b10000
				
				field[x][y] = count
			}
		}
		
		if (w * h * 6 > 2000) {
			replyWith(message, "warning: too many tiles, the message will be split into multiple")
		}
		
		//fourth iteration: construct the game field
		builder.clear()
		launch {
			for (y in 0 until h) {
				//send the current part of the game field if adding another row to it would cause an overflow
				val nextLength = builder.length + w * 6
				if (nextLength >= 2000) {
					message.channel.createMessage(builder.toString())
					builder.clear()
				}
				
				for (x in 0 until w) {
					val value = field[x][y]
					
					if (value == -1) {
						builder.append("||").append(bombEmoji).append("||")
					} else {
						val emoji = numbers[value and 0b1111]
						if (value and 0b10000 == 16) { //pre-open tile
							builder.append(emoji)
						} else {
							builder.append("||").append(emoji).append("||")
						}
					}
				}
				builder.append('\n')
			}
			message.channel.createMessage(builder.toString())
		}
	},
	
	header = "width: Int?, height: Int?, mines: Int?",
	
	description = "Allows you to play minesweeper in discord. The maximum field size is ${maxW}x${maxH}, a 3x3 square in a random place is always opened"
);

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
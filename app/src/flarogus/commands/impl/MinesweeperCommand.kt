package flarogus.commands.impl

import kotlin.random.*;
import kotlinx.coroutines.*;
import dev.kord.core.entity.*;
import dev.kord.core.event.message.*;
import flarogus.*;

private val numbers = arrayOf("0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣")
private val freeTiles = ArrayList<Int>(25 * 25) //each value represents both x and y component. assuming values won't exceed 255.

private val maxW = 30
private val maxH = 30

val MinesweeperCommand = flarogus.commands.Command(
	handler = {
		val w = (it.getOrNull(1)?.toInt() ?: 12).coerceIn(5, maxW)
		val h = (it.getOrNull(2)?.toInt() ?: 12).coerceIn(5, maxH)
		val mines = (it.getOrNull(3)?.toInt() ?: 25).coerceIn(0, w * h - 8)
		
		//position that will be opened at the start
		val openX = Random.nextInt(1, w - 1)
		val openY = Random.nextInt(1, h - 1)
		
		//mine == -1, free == [0, 8] (number of mines), opened = [16, 24] (5th bit is active, other represent number of mines)
		val field = Array(w) { IntArray(h) }
		//first iteration: put all possible tiles into the list
		freeTiles.clear()
		for (x in 0 until w) {
			for (y in 0 until h) {
				freeTiles += packTile(x, y)
			}
		}
		//remove tiles next to the opened tile
		freeTiles.filter { Math.abs(unpackX(it) - openX) >= 1 || Math.abs(unpackY(it) - openY) >= 1 }
		//second iteration: shuffle the list and put bombs on the field at the first ${mines} positions taken from the list
		freeTiles.shuffle()
		for (i in 0 until mines) {
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
				neighbours(x, y) { _, _ -> count++ }
				//check if the tile should be pre-open, add the "open" flag (0b10000) in that case
				if ((Math.abs(x - openX) <= 1) && Math.abs(y - openY) <= 1) count = count or 0b10000
				
				field[x][y] = count
			}
		}
		//fourth iteration: construct the game field
		val string = buildString {
			for (x in 0 until w) {
				for (y in 0 until h) {
					val value = field[x][y]
					
					if (value == -1) {
						append("||💥||")
					} else {
						val emoji = numbers[value and 0b1111]
						if (value and 0b10000 == 1) { //pre-open tile
							append(emoji)
						} else {
							append("||$emoji||")
						}
					}
				}
				append('\n')
			}
		}
		
		replyWith(message, string)
	},
	
	header = "width: Int?, height: Int?, mines: Int?",
	
	description = "Allows you to play minesweeper in discord. The maximum field size is ${maxW}x${maxH}"
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
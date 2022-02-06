package flarogus.util

typealias SimpleMap = Map<String, Any>

/**
 * Serializes simple maps into discord-safe strings. Made for internal usage.
 * Supports Int, Long, UInt, ULong, String, Array as values.
 * arrays must not have > 65000 elements, but who would want to serialize such arrays into strings anyways?
 */
object SimpleMapSerializer {
	
	//constant types. these should NOT be modified, such action will make the serializer incompatible with older versions
	const val UNKNOWN = 0
	const val INT = 1
	const val LONG = 2
	const val UINT = 3
	const val ULONG = 4
	const val ARRAY = 5
	const val MAP = 6
	const val STRING = 7
	
	private var char = 1
	
	//semi-constant chars. should not be modified, new characters should be added at the end.
	val entrySplitter = (char++).toChar() //splits entries in top map
	val keyTypeSplitter = (char++).toChar() //splits key and value type of an entry
	val typeValueSplitter = (char++).toChar() //splits value type of an entry and the value itself
	val valueEnd = (char++).toChar()
	val arrayBegin = (char++).toChar()
	val arraySplitter = (char++).toChar()
	val arrayEnd = (char++).toChar()
	val mapBegin = (char++).toChar()
	val mapSplitter = (char++).toChar()
	val mapEnd = (char++).toChar()
	
	//serialization region
	/** 
	 * Serializes a map.
	 * @throws NullPointerException if the map contains null values
	 */
	fun serialize(from: SimpleMap): String {
		val b = StringBuilder(500)
		
		from.forEach { k, v ->
			b.writeMapEntry(k, v).append(entrySplitter)
		}
		
		return b.toString()
	}
	
	internal fun StringBuilder.writeMapEntry(k: String, v: Any): StringBuilder {
		append(k).append(keyTypeSplitter)
		
		return writeValue(v)
	}
	
	internal fun StringBuilder.writeValue(other: Any): StringBuilder {
		val type = typeOf(other)
		append('a' + type).append(typeValueSplitter)
		
		when (type) {
			INT -> append(other as Int)
			LONG -> append(other as Long)
			UINT -> append(other as UInt)
			ULONG -> append(other as ULong)
			STRING -> append(other as String)
			ARRAY -> append('0' + (other as Array<*>).size).writeArray(other)
			//MAP -> append('0' + (other as SimpleMap).size).writeMap(other as SimpleMap)
			
			else -> throw IllegalArgumentException("unsupported value type: ${other::class}")
		}
		
		return append(valueEnd)
	};
	
	internal fun StringBuilder.writeArray(other: Array<*>): StringBuilder {
		append(arrayBegin)
		
		other.forEach { 
			writeValue(it!!).append(arraySplitter)
		}
		
		return append(arrayEnd)
	}
	
	internal fun StringBuilder.writeMap(other: SimpleMap): StringBuilder {
		append(mapBegin)
		
		other.forEach { k, v ->
			writeMapEntry(k, v).append(mapSplitter)
		}
		
		return append(mapEnd)
	};
	
	fun typeOf(other: Any): Int = when (other) {
		is Int -> INT
		is Long -> LONG
		is UInt -> UINT
		is ULong -> ULONG
		is Array<*> -> ARRAY
		//is Map<*, *> -> MAP //can't use SimpleMap here.
		is String -> STRING
		else -> UNKNOWN
	};
	//serialization region end
	
	//deserialization region
	/** Deserializes a map */
	fun deserialize(from: String): SimpleMap {
		return DeserializationContext(from).deserialize()
	}
	
	internal class DeserializationContext(val input: String) {
		val output = HashMap<String, Any>(20)
		val tempbuilder = StringBuilder(50)
		var index = -1
		
		fun deserialize(): SimpleMap {
			//process top-level stuff
			while (index < input.length - 1) {
				//key
				tempbuilder.clear()
				
				while (true) {
					val c = input[++index]
					if (c == keyTypeSplitter) break
					if (c < ' ') throw UnexpectedSymbolException(c)
					tempbuilder.append(c)
				}
				
				//executed upon meeting a key value splitter
				
				output[tempbuilder.toString()] = processValue()
				
				expect(entrySplitter)
			}
			
			return output
		}
		
		//todo: repeating code?
		fun processValue(): Any {
			//type
			val type = (input[++index] - 'a').toInt()
			expect(typeValueSplitter)
			
			val value = when (type) {
				INT -> readPrimitive().toInt()
				LONG -> readPrimitive().toLong()
				UINT -> readPrimitive().toUInt()
				ULONG -> readPrimitive().toULong()
				ARRAY -> {
					val size = input[++index] - '0'
					val array = arrayOfNulls<Any>(size)
					var element = 0
					
					expect(arrayBegin)
					
					while (element < size) {
						array[element++] = processValue()
						expect(arraySplitter)
					}
					
					expect(arrayEnd)
					expect(valueEnd)
					array
				}
				/*MAP -> {
					idk
					expect(valueEnd)
				}*/
				STRING -> {
					tempbuilder.clear()
					
					while (true) {
						val c = input[++index]
						
						if (c == valueEnd) break
						
						tempbuilder.append(c)
					}
					
					tempbuilder.toString()
				}
				
				else -> throw RuntimeException("Unknown or unsupported value type: $type")
			};
			
			return value
		}
		
		/** Reads a primitive value (e.g. 38283) as a string and returns it */
		fun readPrimitive(): String {
			tempbuilder.clear()
			
			while (true) {
				val c = input[++index]
				
				if (c == valueEnd) break
				if (!c.isDigit() && c != '-' && c != '.') throw MalformedInputException("a non-digit character in a primitive value")
				
				tempbuilder.append(c)
				
				if (tempbuilder.length > 20) throw MalformedInputException("a primitive value contains more digits than a ULong can store")
			}
			
			return tempbuilder.toString()
		}
		
		/** Returns true if the next symbol is the expected symbol, throws an exception otherwise */
		fun expect(other: Char): Boolean {
			val c = input[++index]
			
			if (c != other) throw MalformedInputException("expected ${other.code}, found ${c.code}")
			
			return true
		}
		//deserialization region end
		
		open inner class MalformedInputException(cause: String) : RuntimeException("Malformed input string: $cause at char ${index + 1}")
		
		inner class UnexpectedSymbolException(symbol: Char) : MalformedInputException("unexpected symbol ${symbol.code} in the serialized string");
	}
}

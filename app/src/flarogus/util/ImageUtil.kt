package flarogus.util

import java.awt.image.*;

object ImageUtil {
	
	/** Creates a new BufferedImage containing the result of multiplying the source image by the multiplier image */
	fun multiply(target: BufferedImage, multiplier: BufferedImage): BufferedImage = operation(target, multiplier) { a, b -> a * b / 255 };
	
	/** Applies the providen operation to r, g and b components of each pixel of the target image
	 *  The first lambda argument is the target component, the second is the additional component. */
	inline fun operation(target: BufferedImage, additional: BufferedImage, op: (Int, Int) -> Int): BufferedImage {
		val new = BufferedImage(target.width, target.height, BufferedImage.TYPE_INT_ARGB);
		
		val xRatio = additional.width.toDouble() / target.width;
		val yRatio = additional.height.toDouble() / target.height
		for (x in 0 until target.width) {
			for (y in 0 until target.height) {
				val sourcecol = target.getRGB(x, y)
				val mulcol = additional.getRGB((x * xRatio).toInt(), (y * yRatio).toInt())
				
				val a = if ((sourcecol shr 24) and 0xff < 10 || (mulcol shr 24) and 0xff < 10) 0 else 255
				val r = op(((sourcecol and 0x00ff0000) shr 16), ((mulcol and 0x00ff0000) shr 16))
				val g = op(((sourcecol and 0x0000ff00) shr 8), ((mulcol and 0x0000ff00) shr 8))
				val b = op((sourcecol and 0x000000ff), (mulcol and 0x000000ff))
				
				val newcol = ((a and 0xff) shl 24) or ((r and 0xff) shl 16) or ((g and 0xff) shl 8) or (b and 0xff)
				new.setRGB(x, y, newcol)
			}
		}
		
		return new
	}
	
}
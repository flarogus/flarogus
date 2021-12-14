package flarogus.util

import java.awt.image.*;

object ImageUtil {
	
	/** Creates a new BufferedImage containing the result of multiplying the source image by the multiplier image */
	fun multiply(source: BufferedImage, multiplier: BufferedImage): BufferedImage {
		val new = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB);
		
		val xRatio = multiplier.width.toDouble() / source.width;
		val yRatio = multiplier.height.toDouble() / source.height
		for (x in 0 until source.width) {
			for (y in 0 until source.height) {
				val sourcecol = source.getRGB(x, y)
				val mulcol = multiplier.getRGB((x * xRatio).toInt(), (y * yRatio).toInt())
				val a = ((sourcecol and 0x7f000000) shr 24) * ((mulcol and 0x7f000000) shr 24)
				val r = ((sourcecol and 0x00ff0000) shr 16) * ((mulcol and 0x00ff0000) shr 16)
				val g = ((sourcecol and 0x0000ff00) shr 8) * ((mulcol and 0x0000ff00) shr 8)
				val b = 255//(sourcecol and 0x000000ff) * (mulcol and 0x000000ff)
				val newcol = ((a shl 24) and 0x7f000000) or ((r shl 16) and 0x00ff0000) or ((g shl 8) and 0x0000ff00) or (b and 0x000000ff)
				new.setRGB(x, y, newcol)
			}
		}
		
		return new
	}
	
}
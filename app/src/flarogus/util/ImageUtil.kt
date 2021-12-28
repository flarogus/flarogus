package flarogus.util

import java.awt.image.*;

object ImageUtil {
	
	/** Creates a new BufferedImage containing the result of multiplying the source image by the multiplier image */
	fun multiply(target: BufferedImage, multiplier: BufferedImage) = operation(target, multiplier) { a, b, _, _ -> a * b / 255 };
	
	/** Merges two images in a weird way */
	fun merge(target: BufferedImage, with: BufferedImage): BufferedImage = operation(target, with) { a, b, x, y ->
		val progress = Math.sin((x + y) / (target.width + target.height) * Math.PI / 2) //0 to 1 — sine
		print(progress); print(" ")
		(a * progress + b * (1 - progress)).toInt()
	};
	
	/** Applies the providen (argb, argb, x, y) operation to a, r, g and b components of each pixel of the target image */
	inline fun operation(target: BufferedImage, additional: BufferedImage, op: (Int, Int, Int, Int) -> Int): BufferedImage {
		val new = BufferedImage(target.width, target.height, BufferedImage.TYPE_INT_ARGB);
		
		val xRatio = additional.width.toDouble() / target.width;
		val yRatio = additional.height.toDouble() / target.height
		for (x in 0 until target.width) {
			for (y in 0 until target.height) {
				val sourcecol = target.getRGB(x, y)
				val mulcol = additional.getRGB((x * xRatio).toInt(), (y * yRatio).toInt())
				
				val a = op((sourcecol shr 24) and 0xff, (mulcol shr 24) and 0xff, x, y)
				val r = op((sourcecol shr 16) and 0xff, (mulcol shr 16) and 0xff, x, y)
				val g = op((sourcecol shr 8) and 0xff, (mulcol shr 8) and 0xff, x, y)
				val b = op(sourcecol and 0xff, mulcol and 0xff, x, y)
				
				val newcol = ((a and 0xff) shl 24) or ((r and 0xff) shl 16) or ((g and 0xff) shl 8) or (b and 0xff)
				new.setRGB(x, y, newcol)
			}
		}
		
		return new
	}
	
}
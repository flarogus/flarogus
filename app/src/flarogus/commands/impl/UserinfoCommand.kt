package flarogus.commands.impl

import java.io.*;
import java.net.*;
import java.awt.image.*;
import javax.imageio.*;
import kotlinx.coroutines.*;
import dev.kord.core.behavior.channel.*;
import dev.kord.core.entity.*;
import dev.kord.core.event.message.*;
import flarogus.util.*;

val avatarFrame = ImageIO.read({}::class.java.getResource("/frame.png") ?: throw RuntimeException("no avatar frame exist"))
val padding = 10;

val lines = ArrayList<String>(15)

/** result:
/------\ impostor#3661
|avatar| impostor: yes
\------/
*/
val UserinfoCommand = flarogus.commands.Command(
	handler = {
		val user = userOrAuthor(it.getOrNull(1), this)
		
		val userpfp = ImageIO.read(URL(user?.getRealAvatar()?.url ?: throw CommandException("userinfo", "could not retreive user avatar")))
		val cropped = ImageUtil.multiply(avatarFrame, userpfp);
		
		lines.clear()
		lines.add(user.tag)
		lines.add("impostor: " + if (user.discriminator.toInt() % 7 == 0) "yes" else "no")
		
		var graphics = cropped.createGraphics()
		val metrics = graphics.fontMetrics;
		
		val perLine = metrics.height + padding
		var width = 0;
		var height = padding * 2 + cropped.height
		lines.forEach {
			width = Math.max(width, padding + cropped.width + padding * 2 + metrics.stringWidth(it) + padding)
			height += perLine
		}
		graphics.dispose()
		
		val newImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		graphics = newImage.createGraphics()
		graphics.drawImage(cropped, padding, padding, null)
		
		for (i in 0 until lines.size) {
			graphics.drawString(lines[i], padding * 3 + cropped.width, padding + perLine * i)
		}
		
		ByteArrayOutputStream().use {
			ImageIO.write(newImage, "png", it);
			ByteArrayInputStream(it.toByteArray()).use {
				message.channel.createMessage {
					messageReference = message.id
					addFile("impostor.png", it)
				}
			}
		}
	},
	
	header = "userid: String?",
	
	description = "Displays info of the providen user. If no user id is providen"
)
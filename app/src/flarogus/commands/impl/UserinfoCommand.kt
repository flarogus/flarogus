package flarogus.commands.impl

import java.io.*;
import java.net.*;
import java.time.format.*;
import java.awt.*;
import java.awt.image.*;
import javax.imageio.*;
import kotlin.time.*;
import kotlinx.coroutines.*;
import dev.kord.core.behavior.channel.*;
import dev.kord.core.entity.*;
import dev.kord.core.event.message.*;
import dev.kord.common.entity.*;
import flarogus.util.*;

private val avatarFrame = ImageIO.read({}::class.java.getResource("/frame.png") ?: throw RuntimeException("no avatar frame exist"))
private val infoFont = Font("Courier New", Font.PLAIN, 17);
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.mm.dd HH:mm")
private val background = Color(30, 10, 40)
private val padding = 10;

private val lines = ArrayList<String>(15)

/** result:
/------\ impostor#3661
|avatar| impostor: yes
\------/
*/
@OptIn(kotlin.time.ExperimentalTime::class)
val UserinfoCommand = flarogus.commands.Command(
	handler = {
		val user = userOrAuthor(it.getOrNull(1), this)
		
		val userpfp = ImageIO.read(URL(user?.getAvatarUrl() ?: throw CommandException("userinfo", "could not retreive user avatar")))
		val cropped = ImageUtil.multiply(avatarFrame, userpfp);
		
		lines.apply {
			clear()
			add((if (user.isBot) "bot" else "user") + " info")
			add(user.tag)
			add("impostor: " + if (user.discriminator.toInt() % 7 == 0) "yes" else "no")
			add("user id: ${user.id}")
			//i wasted 3 hours of my life trying to figure out how to do these two. i utterly failed. fuck instant and other stuff.
			add("age: ${formatTime(user.id.timeMark.elapsedNow().toLong(DurationUnit.MILLISECONDS))}")
			//add("registered at ${dateFormatter.format(user.id.timestamp as java.time.temporal.TemporalAccessor)} UTC")
		}
		
		var graphics = cropped.createGraphics();
		val metrics = graphics.getFontMetrics(infoFont);
		
		val perLine = metrics.height + padding;
		var width = 0;
		var height = 0;
		lines.forEach {
			var w = padding + metrics.stringWidth(it) + padding
			if (height <= cropped.height + padding * 2) w += cropped.width + padding * 2 
			
			width = Math.max(width, w)
			height += perLine
		}
		height = Math.max(padding * 2 + cropped.height, height)
		graphics.dispose()
		
		val newImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		graphics = newImage.createGraphics()
		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

		
		graphics.setPaint(background)
		graphics.fillRect(0, 0, newImage.width, newImage.height)
		graphics.drawImage(cropped, padding, padding, null)
		
		graphics.setPaint(Color.WHITE)
		graphics.setFont(infoFont)
		for (i in 0 until lines.size) {
			var x = padding
			val y = perLine * (i + 1)
			if (y <= cropped.height + padding * 2) x += cropped.width + padding * 2
			
			graphics.drawString(lines[i], x, y)
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
		
		graphics.dispose()
	},
	
	header = "userid: String?",
	
	description = "Displays info of the providen user. If no user id is providen"
)
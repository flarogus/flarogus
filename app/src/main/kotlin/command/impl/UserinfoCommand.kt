package flarogus.command.impl

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
import flarogus.command.*
import flarogus.command.builder.*

private val avatarFrame = ImageIO.read({}::class.java.getResource("/frame.png") ?: throw RuntimeException("avatar frame is gone"))
private val infoFont = Font("Courier New", Font.PLAIN, 17);
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.mm.dd HH:mm")
private val background = Color(30, 10, 40)
private val padding = 10;

/** result:
/------\ impostor#3661
|avatar| impostor: yes
\------/ ...
*/
@OptIn(kotlin.time.ExperimentalTime::class)
fun TreeCommand.addUserinfoSubcommand() = subcommand<BufferedImage?>("userinfo") {
	description = "Display info of the providen user / bot."

	arguments {
		default<User>("user", "The user whose info you want to get. Can be an id or a mention. If not present, uses the caller instead.") {
			originalMessage?.asMessage()?.author?.asUser() ?: error("Anonymous caller can not call this command without a user id.")
		}
	}

	action {
		val user = args.arg<User>("user")
		
		val userpfp = ImageIO.read(URL(user?.getAvatarUrl() ?: throw CommandException("userinfo", "could not retreive user avatar")))
		val cropped = ImageUtil.multiply(avatarFrame, userpfp);

		val lines = ArrayList<String>(10)
		
		lines.apply {
			clear()
			add((if (user.isBot) "bot" else "user") + " info")
			add(user.tag)
			add("impostor: " + if (user.discriminator.toInt() % 7 == 0) "yes" else "no")
			add("user id: ${user.id}")

			add("age: ${formatTime(user.id.timeMark.elapsedNow().toLong(DurationUnit.MILLISECONDS))}")
			add("registered at ${dateFormatter.format(user.id.timeMark as java.time.temporal.TemporalAccessor)} UTC")
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
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		
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
		
		result(newImage)
		
		graphics.dispose()
	}
}

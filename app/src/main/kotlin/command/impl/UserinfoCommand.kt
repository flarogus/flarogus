package flarogus.command.impl

import java.net.*;
import java.time.*
import java.time.format.*;
import java.awt.*;
import java.awt.image.*;
import javax.imageio.*;
import kotlin.time.*;
import kotlinx.datetime.*
import dev.kord.core.entity.*;
import dev.kord.common.entity.*;
import flarogus.util.*;
import flarogus.multiverse.*
import flarogus.command.*
import flarogus.command.builder.*

private val avatarFrame = ImageIO.read({}::class.java.getResource("/frame.png") ?: throw RuntimeException("avatar frame is gone"))
private val infoFont = Font("Courier New", Font.PLAIN, 17);
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.mm.dd HH:mm")
private val timezone = ZoneId.of("Z")
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
		// we are searching manually because we don't want non-multiversal users there
		val multiversalUser = Multiverse.users.find { it.discordId == user.id }?.also { it.update() }
		
		val userpfp = ImageIO.read(URL(user.getAvatarUrl()))
		val cropped = ImageUtil.multiply(avatarFrame, userpfp);

		val lines = ArrayList<String>(10).apply {
			add((if (user.isBot) "Bot" else "User") + " info")
			add(user.tag)
			add("Impostor: " + if (user.discriminator.toInt() % 7 == 0) "yes" else "no")
			add("User id: ${user.id}")

			add("Age: ${formatTime(user.id.timeMark.elapsedNow().toLong(DurationUnit.MILLISECONDS))}")
			add("Registered at ${dateFormatter.format(user.id.timestamp.toJavaInstant().atZone(timezone))} UTC")

			add("---------")

			if (multiversalUser != null) {
				add("Messages in the multiverse: ${multiversalUser.totalSent}")
				add("Warning points: ${multiversalUser.warningPoints}")
				add("Is banned: ${if (multiversalUser.canSend()) "no" else "yes"}")
			} else {
				add("This user hasn't been in the multiverse yet.")
			}
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

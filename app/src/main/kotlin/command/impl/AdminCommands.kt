package flarogus.command.impl

import flarogus.command.*
import flarogus.command.builder.*

fun TreeCommand.addAdminSubtree() = adminSubtree("admin") {
	description = "Admin-only commands that allow to manage the multiverse"
}

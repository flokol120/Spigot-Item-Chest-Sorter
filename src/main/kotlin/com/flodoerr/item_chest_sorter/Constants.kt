package com.flodoerr.item_chest_sorter

import org.bukkit.ChatColor

const val BASE_COMMAND = "sorter"
val COMMANDS = listOf(listOf("add", "remove", "select", "reload"), listOf("receiver", "sender"))

val SENDER_HOE_NAME = "${ChatColor.DARK_PURPLE}right click sender chest"
val RECEIVER_HOE_NAME = "${ChatColor.DARK_GREEN}right click receiver chests"
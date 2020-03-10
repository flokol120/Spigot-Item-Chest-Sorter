package com.flodoerr.item_chest_sorter

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class Commands: CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if(sender is ConsoleCommandSender) {
            sender.sendMessage("this commands can only be used in-game")
            return true
        }
        if(args.size < 2) {
            return false
        }
        when(args[0].toLowerCase()) {
            "add" -> {
                when(args[1].toLowerCase()){
                    "sender" -> {
                        giveSenderSelectionTool(sender as Player)
                    }
                    "receiver" -> {
                        giveReceiverSelectionTool(sender as Player)
                    }
                    else -> return false
                }
            }
            "select" -> {
                return if(args[1].toLowerCase() == "sender") {
                    giveSenderSelectionTool(sender as Player)
                    true
                }else{
                    false
                }
            }
            // TODO: Add remove logic
            "remove" -> {
                when(args[1].toLowerCase()){
                    "sender" -> {

                    }
                    "receiver" -> {

                    }
                    else -> return false
                }
            }
            else -> return false
        }
        return true
    }

    private fun giveSenderSelectionTool(sender: Player) {
        val item = ItemStack(Material.WOODEN_HOE)
        val itemMeta = item.itemMeta!!
        // add some stuff to find the right item in the listener
        itemMeta.setDisplayName(SENDER_HOE_NAME)
        itemMeta.addEnchant(Enchantment.ARROW_FIRE, -1, true)
        item.itemMeta = itemMeta

        sender.inventory.addItem(item)

        sender.sendMessage("${ChatColor.GREEN}Here you go! Right click a single or double chest to add, remove or select a sender.")
    }

    private fun giveReceiverSelectionTool(sender: Player) {
        val item = ItemStack(Material.WOODEN_HOE)
        val itemMeta = item.itemMeta!!
        // add some stuff to find the right item in the listener
        itemMeta.setDisplayName(RECEIVER_HOE_NAME)
        itemMeta.addEnchant(Enchantment.ARROW_DAMAGE, -1, true)
        item.itemMeta = itemMeta

        sender.inventory.addItem(item)

        sender.sendMessage("${ChatColor.GREEN}Here you go! Right click a single or double chest to add or remove a receiver.")
    }
}
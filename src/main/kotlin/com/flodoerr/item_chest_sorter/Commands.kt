package com.flodoerr.item_chest_sorter

import com.flodoerr.item_chest_sorter.json.JsonHelper
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*


class Commands(private val db: JsonHelper, private val ics: ItemChestSorter): CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // commands can only be used by a player
        if (sender is ConsoleCommandSender) {
            sender.sendMessage("this commands can only be used in-game")
            return true
        }
        // switch block for the sub commands
        when (args[0].lowercase(Locale.getDefault())) {
            "add" -> {
                return getHoes(args, sender)
            }
            "select" -> {
                return if (args[1].lowercase(Locale.getDefault()) == "sender") {
                    val permission = "ics.select.sender"
                    if (sender.hasPermission(permission)) {
                        giveSenderSelectionTool(sender as Player)
                    } else {
                        showNoPermissionMessage(sender, permission)
                    }
                    true
                } else {
                    false
                }
            }
            "remove" -> {
                // if two args are passed, there is no remove command with an id
                if (args.size == 2) {
                    val permission = "ics.remove.sender"
                    val permission2 = "ics.remove.receiver"
                    return if (sender.hasPermission(permission) || sender.hasPermission(permission2)) {
                        getHoes(args, sender)
                    } else {
                        showNoPermissionMessage(sender, "$permission or $permission2")
                        true
                    }
                } else {
                    when {
                        // delete sender using id
                        args[1].lowercase(Locale.getDefault()) == "sender" -> {
                            val permission = "ics.remove.sender"
                            if (sender.hasPermission(permission)) {
                                if (db.removeSender(args[2])) {
                                    sender.sendMessage("${ChatColor.GREEN}Successfully deleted the sender chest.")
                                } else {
                                    sender.sendMessage("${ChatColor.RED}Error while deleting the sender chest with id ${args[2]}. This most likely means the id was not found.")
                                }
                            } else {
                                showNoPermissionMessage(sender, permission)
                            }
                            return true
                        }
                        // delete receiver using id
                        args[1].lowercase(Locale.getDefault()) == "receiver" -> {
                            val permission = "ics.remove.receiver"
                            if (sender.hasPermission(permission)) {
                                if (db.removeReceiver(args[2])) {
                                    sender.sendMessage("${ChatColor.GREEN}Successfully deleted the receiver chest.")
                                } else {
                                    sender.sendMessage("${ChatColor.RED}Error while deleting the receiver chest with id ${args[2]}. This most likely means the id was not found.")
                                }
                            } else {
                                showNoPermissionMessage(sender, permission)
                            }
                            return true
                        }
                        else -> {
                            return false
                        }
                    }
                }
            }
            "reload" -> {
                ics.reload()
                sender.sendMessage("${ChatColor.GREEN}Reload complete.")
                return true
            }
            else -> return false
        }
    }

    /**
     * determines which tool to send to the CommanSender
     * @param args arguments
     * @param sender command issuer
     * @return true if valid command was passed
     *
     * @author Flo Dörr
     */
    private fun getHoes(args: Array<out String>, sender: CommandSender): Boolean {
        when (args[1].lowercase(Locale.getDefault())) {
            "sender" -> {
                val permission = "ics.create.sender"
                if (sender.hasPermission(permission)) {
                    giveSenderSelectionTool(sender as Player)
                } else {
                    showNoPermissionMessage(sender, permission)
                }
            }
            "receiver" -> {
                val permission = "ics.create.receiver"
                if (sender.hasPermission(permission)) {
                    giveReceiverSelectionTool(sender as Player)
                } else {
                    showNoPermissionMessage(sender, permission)
                }
            }
            else -> return false
        }
        return true
    }

    /**
     * gives the modified sender hoe to the CommandSender
     * @param sender command issuer (Player)
     *
     * @author Flo Dörr
     */
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

    /**
     * gives the modified receiver hoe to the CommandSender
     * @param sender command issuer (Player)
     *
     * @author Flo Dörr
     */
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

    /**
     * shows the user a message if he has no permissions
     *
     * @param sender to send the message to
     * @param permission permission the user does not have
     *
     * @author Flo Dörr
     */
    private fun showNoPermissionMessage(sender: CommandSender, permission: String){
        sender.sendMessage("${ChatColor.RED}You do not have enough permissions to do this (${permission}).")
    }
}
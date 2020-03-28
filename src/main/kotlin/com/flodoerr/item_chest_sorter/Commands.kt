package com.flodoerr.item_chest_sorter

import com.flodoerr.item_chest_sorter.json.JsonHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack


class Commands(private val db: JsonHelper): CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // commands can only be used by a player
        if(sender is ConsoleCommandSender) {
            sender.sendMessage("this commands can only be used in-game")
            return true
        }
        // only accepts more than 1 arguments
        if(args.size < 2) {
            return false
        }
        // switch block for the sub commands
        when(args[0].toLowerCase()) {
            "add" -> {
                return getHoes(args, sender)
            }
            "select" -> {
                return if(args[1].toLowerCase() == "sender") {
                    giveSenderSelectionTool(sender as Player)
                    true
                }else{
                    false
                }
            }
            "remove" -> {
                // if two args are passed, there is no remove command with an id
                if (args.size == 2) {
                    return getHoes(args, sender)
                } else {
                    when {
                        // delete sender using id
                        args[1].toLowerCase() == "sender" -> {
                            GlobalScope.launch(Dispatchers.IO) {
                                if(db.removeSender(args[2])) {
                                    sender.sendMessage("${ChatColor.GREEN}Successfully deleted the sender chest.")
                                }else{
                                    sender.sendMessage("${ChatColor.RED}Error while deleting the sender chest with id ${args[2]}. This most likely means the id was not found.")
                                }
                            }
                            return true
                        }
                        // delete receiver using id
                        args[1].toLowerCase() == "receiver" -> {
                            GlobalScope.launch(Dispatchers.IO) {
                                if(db.removeReceiver(args[2])) {
                                    sender.sendMessage("${ChatColor.GREEN}Successfully deleted the receiver chest.")
                                }else{
                                    sender.sendMessage("${ChatColor.RED}Error while deleting the receiver chest with id ${args[2]}. This most likely means the id was not found.")
                                }
                            }
                            return true
                        }
                        else -> {
                            return false
                        }
                    }
                }
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
        when(args[1].toLowerCase()){
            "sender" -> {
                giveSenderSelectionTool(sender as Player)
            }
            "receiver" -> {
                giveReceiverSelectionTool(sender as Player)
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
}
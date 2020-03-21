package com.flodoerr.item_chest_sorter

import com.flodoerr.item_chest_sorter.json.*
import kotlinx.coroutines.*
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.DoubleChestInventory
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox

var currentSender: Sender? = null

class Listener(private val db: JsonHelper, private val main: ItemChestSorter): Listener {

    @EventHandler
    fun onPlayerInteractEvent(e: PlayerInteractEvent) {
        if(e.item !== null && e.item!!.itemMeta !== null && e.clickedBlock !== null) {
            val item = e.item!!
            val itemMeta = item.itemMeta!!

            val arrowLevel = itemMeta.getEnchantLevel(Enchantment.ARROW_DAMAGE)
            val fireLevel = itemMeta.getEnchantLevel(Enchantment.ARROW_FIRE)

            if(fireLevel == 65535 || arrowLevel == 65535) {

                val displayName = itemMeta.displayName

                val block = e.clickedBlock!!

                val isChest = block.blockData is org.bukkit.block.data.type.Chest

                if(isChest) {
                    if(fireLevel == 65535 && displayName == SENDER_HOE_NAME) {
                        runBlocking {
                            handleSenderHoe(e.player, block)
                        }
                    }else if(arrowLevel == 65535 && displayName == RECEIVER_HOE_NAME) {
                        runBlocking {
                            handleReceiverHoe(e.player, block)
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    fun onInventoryCloseEvent(e: InventoryCloseEvent) {
        runBlocking {
            checkInventory(e.inventory, e.player)
        }
    }

    /**
     * checks the omitted inventory for a potential transfer of items
     * @param inventory Inventory of the sender chest
     * @param player player who triggered the event
     *
     * @author Flo Dörr
     */
    private suspend fun checkInventory(inventory: Inventory, player: HumanEntity) {
        if(inventory.location !== null) {
            // get the sender by the inventory location
            val sender = db.getSenderByCords(locationToCords(inventory.location!!))
            // if null chest is no sender chest
            if(sender !== null) {
                // get items in chest
                val contents = inventory.contents
                if(contents.isNotEmpty()) {
                    // loop through all chest slots
                    val receivers = sender.receiver

                    if(receivers.size > 0) {
                        val airReceiver = ArrayList<HashMap<String, Any?>>()
                        val realReceiver = ArrayList<HashMap<String, Any?>>()

                        for (receiver in receivers) {
                            val leftChest = player.world.getBlockAt(cordsToLocation(receiver.cords.left, inventory.location!!.world!!)).state as Chest
                            // get right chest if cords not null
                            val rightChest = if(receiver.cords.right != null) {
                                player.world.getBlockAt(cordsToLocation(receiver.cords.right!!, inventory.location!!.world!!)).state as Chest
                            }else{
                                null
                            }
                            // get block in item frame on chest
                            val block = getItemFromItemFrameNearChest(leftChest, rightChest)
                            // check if no item frame is placed and give hint to user
                            if (block != null) {
                                val map = HashMap<String, Any?>()
                                map["leftChest"] = leftChest
                                map["block"] = block
                                if(!block.type.isAir) {
                                    realReceiver.add(map)
                                }else{
                                    airReceiver.add(map)
                                }
                            }else{
                                player.sendMessage("${ChatColor.YELLOW}There is a receiver chest which has no item frame on it. Please but an item frame on a receiver chest, containing the target item/block. You can also leave the item frame empty to accept all items which could not be sorted.")
                            }
                        }

                        // left over items which cannot be sorted in a chest
                        val leftOverContent = ArrayList<ItemStack>()

                        for (content in contents) {
                            // get an itemstack if item cannot be sorted
                            val stack = handleItems(content, player, inventory, realReceiver)
                            if (stack != null) {
                                // add this stack to the leftOverContent List
                                leftOverContent.add(stack)
                            }
                        }

                        // if there are items which could not be sorted and there is at least one air chest (#1)
                        if(leftOverContent.size > 0 && airReceiver.size > 0) {
                            // send a message to the player
                            player.sendMessage("${ChatColor.YELLOW}Found item(s) which are/is not specified. Sorting into air chest, if there is enough space...")
                            // sort items into "air chests"
                            for (leftOver in leftOverContent) {
                                handleItems(leftOver, player, inventory, airReceiver)
                            }
                        }
                    }else {
                        // some ugly chat message :( ...
                        val m1 = TextComponent("There are no receivers configured yet. Use the ")
                        m1.color = net.md_5.bungee.api.ChatColor.YELLOW
                        val m2 = TextComponent("/ics add receiver ")
                        m2.isItalic = true
                        m2.color = net.md_5.bungee.api.ChatColor.GRAY
                        m2.isUnderlined = true
                        m2.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ics add receiver")
                        val m3 = TextComponent("command.")
                        m3.color = net.md_5.bungee.api.ChatColor.YELLOW
                        m1.addExtra(m2)
                        m1.addExtra(m3)
                        player.spigot().sendMessage(m1)
                    }
                }
            }
        }
    }

    /**
     * handles the items to move them to their desired chest
     * @param content ItemStack in the sender chest
     * @param player player who triggered the event
     * @param inventory Inventory of the sender chest
     * @param receivers receiver HashMap
     * @return null or itemstack. If null the item was sorted successfully. If not no chest was found for this item.
     *
     * @author Flo Dörr
     */
    private fun handleItems(content: ItemStack?, player: HumanEntity, inventory: Inventory, receivers: ArrayList<HashMap<String, Any?>>): ItemStack? {
        var notFound: ItemStack? = null
        if(content != null && content.amount > 0 && !content.type.isAir) {
            for (receiver in receivers) {
                val block = receiver["block"] as ItemStack?
                // First check if block == null or the bloc is air, if that is the case we have found an air chest
                // check if item in chest and item on frame are the same
                // check if we have an item set defined in the config.yml matching the item in the frame and in the sender chest
                if((block == null || block.type.isAir) || block.type == content.type || main.isItemInSet(content, block)) {
                    val leftChest = receiver["leftChest"] as Chest
                    // determine whether the chest has enough space to hold the items
                    val spaceResult = checkIfChestHasSpace(content, leftChest)
                    // if the spaceResult and the amount of items are the same, the chest is full with no space
                    // to put a single item. continue to find the next chest, if present.
                    if(spaceResult == content.amount) {
                        continue
                    }
                    // only put the amount of items in the receiver chest it can hold
                    if (spaceResult > 0) {
                        content.amount -= spaceResult
                        player.sendMessage("${ChatColor.DARK_GREEN}A chest is about to be full. ${ChatColor.YELLOW}$spaceResult${ChatColor.DARK_GREEN} items will be left over if no other chest with this item is defined.")
                    }

                    // move the items
                    moveItem(content, leftChest, player, inventory)

                    // if there were leftover items: add leftover items to sender chest and continue to find the next chest, if present.
                    if(spaceResult > 0) {
                        content.amount = spaceResult
                        inventory.addItem(content)
                        continue
                    }else{
                        // if all items fitted in the chest: No need to search for another, breaking...
                        // also there was a chest found for the items, resetting notFound...
                        notFound = null
                        break
                    }
                }else{
                    // so far no chest was found containing this item, remember it by assigning it to notFound
                    notFound = content
                }
            }
        }
        // return notFound can be null or an itemstack
        return notFound
    }

    /**
     * handles the items to move them to their desired chest
     * @param content ItemStack in the sender chest
     * @param sender Sender Object from the JSON file
     * @param player player who triggered the event
     * @param inventory Inventory of the sender chest
     *
     * @author Flo Dörr
     */
    private fun moveItem(content: ItemStack, leftChest: Chest, player: HumanEntity, inventory: Inventory) {
        // got some weird bugs with the amount... defining this var actually helped :thinking:
        val amount = content.amount
        // add to (left) chest. Do not use blockInventory as its only the leftChests inventory
        // inventory represents the whole potential double chest inventory
        leftChest.inventory.addItem(content)

        player.sendMessage("${ChatColor.GREEN}sorted ${ChatColor.YELLOW}${amount} ${ChatColor.GREEN}${ChatColor.AQUA}${getItemName(content)} ${ChatColor.GREEN}to your chest")
        //remove the item from the sender chest
        inventory.removeItem(content)
    }

    /**
     * gets the name of an item or block
     * @param item ItemStack
     * @return name of ItemStack
     *
     * @author Flo Dörr
     */
    private fun getItemName(item: ItemStack): String{
        // hack the item/block name together...
        return if(item.hasItemMeta()) {
            item.itemMeta!!.displayName
        }else{
            item.type.name.toLowerCase().replace("_", " ")
        }
    }

    /**
     * checks if a given chest has enough space to hold a given ItemStack
     * @param item ItemStack you want to add to the chest
     * @param chest chest you want to check
     * @return integer; > 0 = cannot find stack in chest; <=0 stack fits in chest
     *
     * @author Flo Dörr
     */
    private fun checkIfChestHasSpace(item: ItemStack, chest: Chest): Int {
        var count = item.amount
        for (stack in chest.inventory.contents) {
            if(stack == null) {
                count -= item.maxStackSize
            }else if(stack.type == item.type) {
                count -= item.maxStackSize - stack.amount
            }
        }
        return count
    }

    /**
     * gets the item frame on a chest using BoundingBox
     * @param leftChest left chest of a (double) chest
     * @param rightChest right chest of a double chest. If this is no double chest. right chest is null
     * @return ItemStack with the item in the item frame. Can be null if no item frame can be found
     */
    private fun getItemFromItemFrameNearChest(leftChest: Chest, rightChest: Chest?): ItemStack? {
        // get BoundingBox of chest and expand it by .1 to find the item frame
        val box = BoundingBox.of(leftChest.block)
        box.expand(.1)
        val entities = leftChest.world.getNearbyEntities(box)

        for (entity in entities) {
            if(entity is ItemFrame) {
                return entity.item
            }
        }

        return if(rightChest != null) {
            // frame not found on left sid, check right side recursively...
            getItemFromItemFrameNearChest(rightChest, null)
        }else{
            // no frame on chest
            null
        }
    }

    /**
     * converts Bukkits Location Object into Cords being used in the JSON file
     * @param location Location to be converted
     * @return Cord Object with the x, y and y coordinates of the location omitted
     *
     * @author Flo Dörr
     */
    private fun locationToCords(location: Location): Cords {
        return Cords(location.blockX, location.blockY, location.blockZ)
    }

    /**
     * converts Cords being used in the JSON file into Bukkits Location Object
     * @param cords Cords to be converted
     * @param world world in which those cords are
     * @return Location Object with the x, y and y coordinates and World of the Cords Object omitted
     *
     * @author Flo Dörr
     */
    private fun cordsToLocation(cords: Cords, world: World): Location {
        return Location(world, cords.x.toDouble(), cords.y.toDouble(), cords.z.toDouble())
    }

    /**
     * converts an inventory Object to a ChestLocation Object used in the json file
     * @param chest Inventory of a chest
     * @return ChestLocation with both sides of a double chest or the only side of a single chest
     *
     * @author Flo Dörr
     */
    private fun getChestLocation(chest: Inventory): ChestLocation {
        val left: Cords?
        var right: Cords? = null
        if(chest is DoubleChestInventory) {
            left = locationToCords(chest.leftSide.location!!)
            right = locationToCords(chest.rightSide.location!!)
        }else{
            left = locationToCords(chest.location!!)
        }
        return ChestLocation(left, right)
    }

    /**
     * handles the sender hoe behavior
     * @param player Player
     * @param block chest
     *
     * @author Flo Dörr
     */
    private suspend fun handleSenderHoe(player: Player, block: Block) {
        val existingSender = db.getSenderByCords(locationToCords(block.location))
        if(existingSender != null) {
            currentSender = existingSender
            player.sendMessage("${ChatColor.GREEN}Sender chest with id '${existingSender.sid}' is now selected")
            player.inventory.setItemInMainHand(null)
            player.sendMessage("${ChatColor.YELLOW}If you want to remove this chest instead, please click on the next message in the chat (!This also implies the deletion of the receiver chests!):")

            val message = TextComponent("delete")
            message.color = net.md_5.bungee.api.ChatColor.RED
            message.isUnderlined = true
            message.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ics remove sender ${existingSender.sid}")
            player.spigot().sendMessage(message)
        }else{
            val chestLocation = getChestLocation((block.state as Chest).inventory)
            var sid = "${chestLocation.left.x}~${chestLocation.left.y}~${chestLocation.left.z}~sender"
            sid += if(chestLocation.right != null) {
                "~double~chest"
            }else{
                "~chest"
            }
            val sender = Sender(sid, "Sender", chestLocation)
            currentSender = sender
            db.addSender(sender)

            player.inventory.setItemInMainHand(null)

            player.sendMessage("${ChatColor.GREEN}Successfully saved this chest as sender chest. Because you used the hoe tool the id was auto generated ${ChatColor.GRAY}(${sid})${ChatColor.GREEN}. This chest was also selected as current sender")
        }
    }

    /**
     * handles the receiver hoe behavior
     * @param player Player
     * @param block chest
     *
     * @author Flo Dörr
     */
    private suspend fun handleReceiverHoe(player: Player, block: Block) {
        if(currentSender != null) {
            val chestLocation = getChestLocation((block.state as Chest).inventory)
            val existingChest = db.getSavedChestFromCords(chestLocation.left)
            if(existingChest == null) {
                var rid = "${chestLocation.left.x}~${chestLocation.left.y}~${chestLocation.left.z}~receiver"
                rid += if(chestLocation.right != null) {
                    "~double~chest"
                }else{
                    "~chest"
                }
                db.addReceiverToSender(Receiver(rid, chestLocation), currentSender!!)
                player.sendMessage("${ChatColor.GREEN}Successfully saved this chest as a receiver chest. Because you used the hoe tool the id was auto generated (${rid}).")
            }else{
                if(existingChest.first != null) {
                    // some ugly chat message :( ...
                    val m1 = TextComponent("This is already a sender chest! If you want to delete this chest, ")
                    m1.color = net.md_5.bungee.api.ChatColor.RED
                    val m2 = TextComponent("click here.")
                    m2.color = net.md_5.bungee.api.ChatColor.RED
                    m2.isUnderlined = true
                    m2.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ics remove sender ${existingChest.first!!.sid}")
                    val m3 = TextComponent(" Warning! Deleting a sender chest will also delete all its receivers!")
                    m3.color = net.md_5.bungee.api.ChatColor.RED
                    m1.addExtra(m2)
                    m1.addExtra(m3)
                    player.spigot().sendMessage(m1)
                }else{
                    // some ugly chat message :( ...
                    val m1 = TextComponent("This is already a receiver chest! If you want to delete this chest, ")
                    m1.color = net.md_5.bungee.api.ChatColor.RED
                    val m2 = TextComponent("click here.")
                    m2.color = net.md_5.bungee.api.ChatColor.RED
                    m2.isUnderlined = true
                    m2.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ics remove receiver ${existingChest.second!!.rid}")
                    m1.addExtra(m2)
                    player.spigot().sendMessage(m1)
                }
            }
        }else{
            // some ugly chat message :( ...
            val m1 = TextComponent("Currently there is no sender chest selected. At first select a sender chest using ")
            m1.color = net.md_5.bungee.api.ChatColor.YELLOW
            val m2 = TextComponent("/sorter select sender")
            m2.isItalic = true
            m2.color = net.md_5.bungee.api.ChatColor.GRAY
            m2.isUnderlined = true
            m2.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sorter select sender")
            val m3 = TextComponent(" and right clicking a sender chest.")
            m3.color = net.md_5.bungee.api.ChatColor.YELLOW
            m1.addExtra(m2)
            m1.addExtra(m3)
            player.spigot().sendMessage(m1)
        }
    }
}
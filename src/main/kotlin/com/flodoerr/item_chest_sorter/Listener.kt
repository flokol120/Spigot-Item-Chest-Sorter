package com.flodoerr.item_chest_sorter

import com.flodoerr.item_chest_sorter.animation.animateItem
import com.flodoerr.item_chest_sorter.animation.animating
import com.flodoerr.item_chest_sorter.json.*
import kotlinx.coroutines.runBlocking
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.block.Container
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.DoubleChestInventory
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.schedule

class Listener(private val db: JsonHelper, private val main: ItemChestSorter): Listener {

    private var currentSender: Sender? = null

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerInteractEvent(e: PlayerInteractEvent) {
        if(e.item != null && e.item!!.itemMeta != null && e.clickedBlock != null && e.item?.type == Material.WOODEN_HOE) {
            val item = e.item!!
            val itemMeta = item.itemMeta!!

            val arrowLevel = itemMeta.getEnchantLevel(Enchantment.ARROW_DAMAGE)
            val fireLevel = itemMeta.getEnchantLevel(Enchantment.ARROW_FIRE)

            if(fireLevel == 65535 || arrowLevel == 65535) {

                val displayName = itemMeta.displayName

                val block = e.clickedBlock!!

                val isContainer = block.state is Container

                if(isContainer) {
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

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryCloseEvent(e: InventoryCloseEvent) {
        runBlocking {
            checkInventory(e.inventory, e.player)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryMoveItemEvent(e: InventoryMoveItemEvent) {
        runBlocking {
            if(e.destination.type == InventoryType.CHEST && e.source.type == InventoryType.HOPPER) {
                checkInventory(e.destination, null, e.item)
            }
        }
    }

    // some helper vars for the hacky workaround
    private var moving = false
    private var timer: Timer? = null
    /**
     * checks the omitted inventory for a potential transfer of items
     * @param inventory Inventory of the sender chest
     * @param player player who triggered the event
     * @param itemStack only needed when the function is called by onInventoryMoveItemEvent
     *
     * @author Flo Dörr
     */
    private suspend fun checkInventory(inventory: Inventory, player: HumanEntity? = null, itemStack: ItemStack? = null) {
        if(inventory.location != null) {
            // get the sender by the inventory location
            val sender = db.getSenderByCords(locationToCords(inventory.location!!))
            // if null chest is no sender chest
            if(sender != null) {
                moving = true
                // get items in chest
                val contents: Array<ItemStack?> = inventory.contents
                if(contents.isNotEmpty()) {
                    // loop through all chest slots
                    val receivers = sender.receiver

                    if(receivers.size > 0) {
                        val airReceiver = ArrayList<HashMap<String, Any?>>()
                        val realReceiver = ArrayList<HashMap<String, Any?>>()

                        // overwriting the animate all setting if too many items are about to be sent, as this can
                        // potentially crash the server!!
                        val overwriteAnimation = if(main.config.getBoolean("animation.animateAll", false)) {
                            var items = 0
                            for (content in contents){
                                if (content != null && !content.type.isAir) {
                                    items += content.amount
                                }
                            }
                            items >= (64 * 2)
                        }else{
                            false
                        }

                        val world = player?.world ?: inventory.location!!.world!!

                        for (receiver in receivers) {
                            val leftChest = world.getBlockAt(cordsToLocation(receiver.cords.left, world)).state as Container
                            // get right chest if cords not null
                            val rightChest = if(receiver.cords.right != null) {
                                inventory.location!!.world
                                world.getBlockAt(cordsToLocation(receiver.cords.right!!, world)).state as Container
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
                                val message = "${ChatColor.YELLOW}There is a receiver chest which has no item frame on it. Please but an item frame on a receiver chest, containing the target item/block. You can also leave the item frame empty to accept all items which could not be sorted."
                                if(player != null) {
                                    player.sendMessage(message)
                                }else{
                                    main.server.consoleSender.sendMessage(message)
                                }
                            }
                        }

                        // left over items which cannot be sorted in a chest
                        val leftOverContent = ArrayList<ItemStack?>()

                        if(realReceiver.size > 0) {
                            if(itemStack == null) {
                                for (content in contents) {
                                    // get an itemstack if item cannot be sorted
                                    val stack = handleItems(content, player, inventory, realReceiver, false, overwriteAnimation)
                                    if (stack != null) {
                                        // add this stack to the leftOverContent List
                                        leftOverContent.add(stack)
                                    }
                                }
                            }else{
                                val stack = handleItems(itemStack, player, inventory, realReceiver, true, overwriteAnimation)
                                if (stack != null) {
                                    // add this stack to the leftOverContent List
                                    leftOverContent.add(stack)
                                }
                            }
                        }else{
                            // add all items the leftOverContent if there are no realReceivers
                            if (itemStack == null) {
                                leftOverContent.addAll(contents)
                            }else{
                                leftOverContent.add(itemStack)
                            }
                        }
                        // if there are items which could not be sorted and there is at least one air chest (#1)
                        if(leftOverContent.size > 0 && airReceiver.size > 0) {
                            // sort items into "air chests"
                            var sendMessage = false
                            for (leftOver in leftOverContent) {
                                if(leftOver != null && !leftOver.type.isAir) {
                                    sendMessage = true
                                    handleItems(leftOver, player, inventory, airReceiver, itemStack != null, overwriteAnimation)
                                }
                            }
                            if(sendMessage) {
                                // only send a message to the player if there is more than air in the chest
                                val message = "${ChatColor.YELLOW}Found item(s) which are/is not specified. Sorting into air chest, if there is enough space..."
                                if(player != null) {
                                    player.sendMessage(message)
                                }else{
                                    main.server.consoleSender.sendMessage(message)
                                }
                            }
                        }
                    }else {
                        if(player != null) {
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
                        }else{
                            main.server.consoleSender.sendMessage("There are no receivers configured yet")
                        }
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
     * @param workaround toggles the workaround for onInventoryMoveItemEvent
     * @return null or itemstack. If null the item was sorted successfully. If not no chest was found for this item.
     *
     * @author Flo Dörr
     */
    private fun handleItems(content: ItemStack?, player: HumanEntity?, inventory: Inventory,
                            receivers: ArrayList<HashMap<String, Any?>>, workaround: Boolean = false, overwriteAnimation: Boolean): ItemStack? {
        var notFound: ItemStack? = null
        if(content != null && content.amount > 0 && !content.type.isAir) {
            for (receiver in receivers) {
                val block = receiver["block"] as ItemStack?
                // First check if block == null or the bloc is air, if that is the case we have found an air chest
                // check if item in chest and item on frame are the same
                // check if we have an item set defined in the config.yml matching the item in the frame and in the sender chest
                if((block == null || block.type.isAir) || block.type == content.type || main.isItemInSet(content, block)) {
                    val leftChest = receiver["leftChest"] as Container
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
                        val message = "${ChatColor.DARK_GREEN}A chest is about to be full. ${ChatColor.YELLOW}$spaceResult${ChatColor.DARK_GREEN} items will be left over if no other chest with this item is defined."
                        if(player != null) {
                            player.sendMessage(message)
                        }else{
                            main.server.consoleSender.sendMessage(message)
                        }
                    }
                    // move the items
                    moveItem(content, leftChest, player, inventory, workaround, overwriteAnimation)

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
     * @param leftChest left part of a chest
     * @param player player who triggered the event
     * @param inventory Inventory of the sender chest
     * @param workaround toggles the workaround for onInventoryMoveItemEvent
     *
     * @author Flo Dörr
     */
    private fun moveItem(content: ItemStack, leftChest: Container, player: HumanEntity?,
                         inventory: Inventory, workaround: Boolean = false, overwriteAnimation: Boolean) {
        // got some weird bugs with the amount... defining this var actually helped :thinking:
        val amount = content.amount
        // add to (left) chest. Do not use blockInventory as its only the leftChests inventory
        // inventory represents the whole potential double chest inventory
        leftChest.inventory.addItem(content)

        val message = "${ChatColor.GREEN}sorted ${ChatColor.YELLOW}${amount} ${ChatColor.GREEN}${ChatColor.AQUA}${getItemName(content)} ${ChatColor.GREEN}to your chest"
        if(player != null) {
            player.sendMessage(message)
        }else{
            main.server.consoleSender.sendMessage(message)
        }
        //remove the item from the sender chest
        inventory.removeItem(content)

        // calling InventoryMoveItemEvent as this behavior is very similar to vanilla hoppers, just remote
        Bukkit.getServer().pluginManager.callEvent(InventoryMoveItemEvent(inventory, content, leftChest.inventory, true))

        if(main.config.getBoolean("animation.enabled", false)) {
            val animationAmount = if(main.config.getBoolean("animation.animateAll", false)){
                if(overwriteAnimation || animating) {
                    1
                }else{
                    amount
                }
            }else{
                1
            }
            animateItem(content, inventory.location!!, leftChest.location, main, animationAmount)
        }

        // very ugly way of doing it but I could not find another way :(
        // deleting the leftover item in the sender chest when items are put into it by a hopper
        if(workaround) {
            moving = false
            timer?.cancel()
            timer?.purge()
            val inv = inventory.location!!.world!!.getBlockAt(inventory.location!!).state as Container
            timer = Timer("Workaround")
            timer!!.schedule(500) {
                if(!moving) {
                    if(inv is Chest) {
                        for (item in inv.blockInventory) {
                            if (content.isSimilar(item)) {
                                inv.blockInventory.removeItem(content)
                            }
                        }
                    } else {
                        for (item in inv.inventory) {
                            if (content.isSimilar(item)) {
                                inv.inventory.removeItem(content)
                            }
                        }
                    }
                    cancel()
                }else{
                    cancel()
                }
            }
        }else{
            moving = false
        }
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
    private fun checkIfChestHasSpace(item: ItemStack, chest: Container): Int {
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
    private fun getItemFromItemFrameNearChest(leftChest: Container, rightChest: Container?): ItemStack? {
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
            val chestLocation = getChestLocation((block.state as Container).inventory)
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
            val chestLocation = getChestLocation((block.state as Container).inventory)
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
            val sender = db.getSender()
            if(sender.size == 1) {
                currentSender = sender[0]
                player.sendMessage("${ChatColor.YELLOW}No sender was selected. Since there is only one sender configured yet, this sender was selected automatically")
                handleReceiverHoe(player, block)
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
}

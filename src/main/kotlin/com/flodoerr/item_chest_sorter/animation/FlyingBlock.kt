package com.flodoerr.item_chest_sorter.animation

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.lang.IndexOutOfBoundsException


// preventing to do too much animations
var animating = false

/**
 * animates an item from the sender chest to the target chest
 *
 * @param item the item to be animated
 * @param start from where the item should start
 * @param target the destination of the item
 * @param plugin the plugin instance used to spawn an async task
 * @param count how many items should be displayed?
 *
 * @author Flo Dörr (who does not like vectors at all)
 */
fun animateItem(item: ItemStack, start: Location, target: Location, plugin: JavaPlugin, count: Int) {
    for (i in 0.until(count)) {
        val armorLocation = start.clone()
        // one down because we are using the head
        armorLocation.y = armorLocation.y - .5

        // set armor stand to start location
        val stand: ArmorStand = start.world!!.spawn(armorLocation, ArmorStand::class.java)

        // make it invisible
        stand.isVisible = false
        stand.setGravity(false)
        // make it small (looks better imho)
        stand.isSmall = true
        // mute it so it does not make funny noises
        stand.isSilent = true
        // put item on head
        stand.equipment!!.helmet = item

        // get the vectors
        val startVector = start.toVector()
        val targetVector = target.toVector()

        // get the directional vector between start and target
        val directionalVector = targetVector.subtract(startVector)

        // animation period. 3 looks ok and will (hopefully) not overload the server (3 = every third tick the server makes)
        val period = 3L

        val delay = if(i == 0) {
            0
        }else{
            i * 2
        }

        // get the normalized vector (length of 1)
        val normalizedVector = directionalVector.clone().normalize()

        // get a "slower" vector of the normalized vector. I think .2 looks good
        val slowVector = normalizedVector.multiply(.2)

        // the distance between start and target
        val distance = directionalVector.length()

        // launch the animation asynchronously
        val animate = FlyingBlock(stand, slowVector, period).runTaskTimerAsynchronously(plugin, delay.toLong(), period)

        // get the animation duration this is the distance divided by the length of the "slower" normalized directional vector
        // and I add 6 because that looks better :P
        val duration = distance / slowVector.length() + 6

        // stop th animation after the duration and delete the armor stand
        object : BukkitRunnable() {
            override fun run() {
                animate.cancel()
                stand.remove()
                animating = false
            }
        }.runTaskLater(plugin, duration.toLong() + delay.toLong())
    }
}


class FlyingBlock(private val stand: ArmorStand, private val normalizedVector: Vector, private val period: Long): BukkitRunnable() {

    override fun run() {
        animating = true
        // calculate the addition to be made. Also taking the period into account (to keep the speed the same)
        val addition = normalizedVector.clone().multiply(period.toInt())
        // get the new location by adding the addition to the current ArmorStand location
        val newLocation = stand.location.add(addition)
        // teleport to the new location
        try {
            stand.teleport(newLocation)
        }catch (_: IndexOutOfBoundsException){ }
    }
}
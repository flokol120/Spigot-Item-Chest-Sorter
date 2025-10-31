package com.flodoerr.item_chest_sorter.animation

import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector

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
// Maximum number of concurrent animations to prevent server overload
private const val MAX_CONCURRENT_ANIMATIONS = 50

fun animateItem(
    item: ItemStack,
    start: Location,
    target: Location,
    plugin: JavaPlugin,
    count: Int
) {
    // Prevent too many concurrent animations
    if (animating && count > MAX_CONCURRENT_ANIMATIONS) {
        return
    }

    // Pre-calculate vectors and distances that are the same for all items
    val startVector = start.toVector()
    val targetVector = target.toVector()
    val directionalVector = targetVector.subtract(startVector)

    // Calculate normalized movement vector once
    val normalizedVector = directionalVector.clone().normalize()
    val slowVector = normalizedVector.multiply(.2)

    // Pre-calculate distance and base duration
    val distance = directionalVector.length()
    val baseDuration = distance / slowVector.length() + 6

    // animation period. 3 looks ok and will (hopefully) not overload the server (3 = every third
    // tick the server makes)
    val period = 3L

    // Create base armor location once
    val armorLocation =
        start.clone().apply {
            // one down because we are using the head
            y -= 0.5
        }

    for (i in 0.until(count)) {
        // create armor stand with pre-configured settings to avoid visibility flicker
        val stand: ArmorStand =
            start.world!!.spawn(armorLocation.clone(), ArmorStand::class.java) { armorStand ->
                // make it invisible immediately
                armorStand.isVisible = false
                armorStand.setGravity(false)
                // make it small (looks better imho)
                armorStand.isSmall = true
                // mute it so it does not make funny noises
                armorStand.isSilent = true
                // make it invulnerable to prevent any interference
                armorStand.isInvulnerable = true
                // prevent it from being affected by external forces
                armorStand.setAI(false)
                // put item on head
                armorStand.equipment!!.helmet = item
            }

        val delay = if (i == 0) 0L else (i * 2L)

        // launch the animation synchronously since we need to teleport entities on the main thread
        val animate =
            FlyingBlock(stand, slowVector.clone(), period).runTaskTimer(plugin, delay, period)

        // Schedule cleanup task
        FlyingBlockCleanup(animate, stand).runTaskLater(plugin, baseDuration.toLong() + delay)
    }
}

class FlyingBlock(
    private val stand: ArmorStand,
    normalizedVector: Vector,
    period: Long
) : BukkitRunnable() {

    // Pre-calculate the movement vector to avoid repeated calculations
    private val movement = normalizedVector.multiply(period.toInt())

    override fun run() {
        animating = true
        try {
            // Move the armor stand directly using the pre-calculated movement vector
            stand.teleport(stand.location.add(movement))
        } catch (_: IndexOutOfBoundsException) {
        }
    }
}

/** Dedicated cleanup class to handle animation cleanup */
class FlyingBlockCleanup(private val animation: BukkitTask, private val stand: ArmorStand) :
    BukkitRunnable() {
    override fun run() {
        animation.cancel()
        stand.remove()
        animating = false
    }
}

package com.flodoerr.item_chest_sorter

import com.flodoerr.item_chest_sorter.json.JsonHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.bukkit.event.HandlerList
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.StringUtil
import java.nio.file.Paths
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.fixedRateTimer

class ItemChestSorter: JavaPlugin() {

    private var saveInterval = 0L
    private var timer: Timer? = null

    private lateinit var db: JsonHelper
    private var setsRegex = ArrayList<ArrayList<Regex>>()
    
    override fun onEnable() {
        super.onEnable()

        createConfig()

        if(this.config.getBoolean("enabled")) {
            val performanceMode = config.getBoolean("performance", false)
            db = JsonHelper(dataFolder, server.consoleSender, performanceMode = performanceMode)

            if(performanceMode) {
                saveInterval = config.getInt("autoSaveInterval", 5) * 60L * 1000L
                timer = fixedRateTimer("automaticSave", false, saveInterval, saveInterval) {
                    runBlocking { db.saveJSON() }
                }
            }

            runBlocking {
                db.migrateMissingWorldNamesJSON(server.worlds[0].uid.toString())
            }

            registerCommands()

            registerListener()

            server.consoleSender.sendMessage("Item-Chest-Sorter loaded.")
        }else{
            server.consoleSender.sendMessage("Item-Chest-Sorter loaded in ghost mode! Nothing will work. If this is not intended look in the config.yml.")
        }
    }

    override fun onLoad() {
        super.onLoad()
        this.reloadConfig()
        server.consoleSender.sendMessage("Item-Chest-Sorter config reloaded.")
    }

    suspend fun reload() {
        if(db.saveJSON()) {
            server.consoleSender.sendMessage("Item-Chest-Sorter successfully saved its database")
            HandlerList.unregisterAll(this)
            this.reloadConfig()
            registerListener()
            server.consoleSender.sendMessage("Item-Chest-Sorter reloaded.")
        }else{
            server.consoleSender.sendMessage("Item-Chest-Sorter DID NOT successfully save its database before shutdown")
        }
    }

    override fun onDisable() {
        runBlocking(Dispatchers.IO) {
            if(db.saveJSON()) {
                server.consoleSender.sendMessage("Item-Chest-Sorter successfully saved its database before shutdown")
            }else{
                server.consoleSender.sendMessage("Item-Chest-Sorter DID NOT successfully save its database before shutdown")
            }
        }
        if(timer != null) {
            timer!!.cancel()
        }
        HandlerList.unregisterAll(this)
        server.consoleSender.sendMessage("Item-Chest-Sorter is going to stop...")
        super.onDisable()
    }

    /**
     * registers listener
     *
     * @author Flo Dörr
     */
    private fun registerListener() {
        if(config.getBoolean("enabled")) {
            //register listener
            server.pluginManager.registerEvents(Listener(db, this), this)
            // unregister event if so configured
            if(!config.getBoolean("sendFromHopperOrSender", false)) {
                InventoryMoveItemEvent.getHandlerList().unregister(this)
            }
            if(config.getBoolean("allowBreakOfChest", true)) {
                BlockBreakEvent.getHandlerList().unregister(this)
            }
        }
    }

    /**
     * registers commands and auto completion
     *
     * @author Flo Dörr
     */
    private fun registerCommands() {
        // register commands
        getCommand(BASE_COMMAND)!!.setExecutor(Commands(db, this))

        // register tab completer
        getCommand(BASE_COMMAND)!!.setTabCompleter { _, _, _, args ->
            val completions = ArrayList<String>()
            if(args.size == 1){
                StringUtil.copyPartialMatches(args[0], COMMANDS[0], completions)
            }else if(args.size == 2 && args[1] != "reload") {
                if (args[0].lowercase(Locale.getDefault()) == COMMANDS[0][2]) {
                    return@setTabCompleter listOf(COMMANDS[1][1])
                } else {
                    StringUtil.copyPartialMatches(args[1], COMMANDS[1], completions)
                }
            }
            return@setTabCompleter completions
        }
    }

    /**
     * creates the default config file if needed
     *
     * @author Flo Dörr
     */
    private fun createConfig() {
        val configFile = Paths.get(dataFolder.absolutePath, "config.yml").toFile()
        if(!configFile.exists()) {
            this.saveDefaultConfig()
        }
    }

    /**
     * gets the sets defined in the config.yml
     * I know that this is an ArrayList<ArrayList<String>> so suppressing the unchecked cast should be OK
     * @return 2d list of sets
     *
     * @author Flo Dörr
     */
    @Suppress("UNCHECKED_CAST")
    fun getSets(): ArrayList<ArrayList<String>> {
        val sets = this.config.getList("sets")
        return if(sets != null) {
            sets as ArrayList<ArrayList<String>>
        }else{
            ArrayList()
        }
    }

    /**
     * checks if both, the item in the chest and the item in the frame are in one set
     * @param itemInChest ItemStack in the chest
     * @param itemInItemFrame ItemStack in the item frame
     * @return true if both items are in one set
     *
     * @author Flo Dörr
     * @author corylulu
     */
    fun isItemInSet(itemInChest: ItemStack, itemInItemFrame: ItemStack): Boolean {
        val sets = getSets()

        if(this.config.getBoolean("enableSetsRegex", false)) {
            if(setsRegex.count() == 0){
                // Keeps regex in buffer
                setsRegex = ArrayList(sets.map {
                    set -> ArrayList(set.map {
                        p -> p.toRegex(RegexOption.IGNORE_CASE)
                    })
                })
            }
            // returns the matching set
            return setsRegex.any { s ->
                (s.any { p -> p.matches(itemInChest.type.key.key) }
                && s.any { p -> p.matches(itemInItemFrame.type.key.key) })
            }
        }else{
            for (set in sets) {
                if(set.contains(itemInChest.type.key.key) && set.contains(itemInItemFrame.type.key.key)) {
                    return true
                }
            }
        }

        return false
    }
}

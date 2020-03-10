package com.flodoerr.item_chest_sorter

import com.flodoerr.item_chest_sorter.json.JsonHelper
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.StringUtil

class ItemChestSorter: JavaPlugin() {

    private lateinit var db: JsonHelper

    override fun onEnable() {
        super.onEnable()
        db = JsonHelper(dataFolder, server.consoleSender)

        // register commands
        getCommand(BASE_COMMAND)!!.setExecutor(Commands())

        // register tab completer
        getCommand(BASE_COMMAND)!!.setTabCompleter { _, _, _, args ->
            val completions = ArrayList<String>()
            if(args.size == 1){
                StringUtil.copyPartialMatches(args[0], COMMANDS[0], completions)
            }else if(args.size == 2) {
                if(args[0].toLowerCase() == COMMANDS[0][2]) {
                    return@setTabCompleter listOf(COMMANDS[1][1])
                }else{
                    StringUtil.copyPartialMatches(args[1], COMMANDS[1], completions)
                }
            }
            return@setTabCompleter completions
        }

        //register listener
        server.pluginManager.registerEvents(Listener(db), this)

        server.consoleSender.sendMessage("Item-Chest-Sorter loaded.")
    }

    override fun onDisable() {
        super.onDisable()
    }
}
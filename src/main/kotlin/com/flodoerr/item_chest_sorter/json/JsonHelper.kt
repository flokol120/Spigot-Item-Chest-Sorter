package com.flodoerr.item_chest_sorter.json

import com.beust.klaxon.Klaxon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.World
import org.bukkit.command.ConsoleCommandSender
import java.io.File
import java.nio.file.Paths
import java.util.*
import kotlin.collections.ArrayList

class JsonHelper(dataFolder: File, private val commandSender: ConsoleCommandSender? = null) {

    private val jsonFile = Paths.get(dataFolder.absolutePath, "chests.json").toFile()
    private val doNotTouchFile = Paths.get(dataFolder.absolutePath, "README (don't touch the json file if you don't know what you are doing)").toFile()

    private var cachedJSON: JSON? = null

    init {
        if(!dataFolder.exists()){
            dataFolder.mkdir()
        }
        if(!jsonFile.exists() || jsonFile.readText() == ""){
            jsonFile.writeText(Klaxon().toJsonString(JSON()))
            commandSender?.sendMessage("created json file")
        }
        doNotTouchFile.writeText("Don't touch the json file, if you don't know what you are doing! Really. Don't do it. You may edit the config.yml.")
    }

    /**
     * adds a sender to the json file
     * @param sender sender to be added
     * @return true if added successfully
     *
     * @author Flo Dörr
     */
    suspend fun addSender(sender: Sender): Boolean {
        val json = getJSON()
        for (jsonSender in json.sender) {
            if(jsonSender.sid == sender.sid){
                return false
            }
        }
        json.sender.add(sender)
        return saveJSON(json)
    }

    /**
     * removes a sender by a sender object
     * @param sender sender to be removed
     * @return true if removed successfully
     *
     * @author Flo Dörr
     */
    suspend fun removeSender(sender: Sender): Boolean {
        return removeSender(sender.sid)
    }

    /**
     * removes a sender by a sender id
     * @param sid id of sender to be removed
     * @return true if removed successfully
     *
     * @author Flo Dörr
     */
    suspend fun removeSender(sid: String): Boolean {
        val json = getJSON()
        for (sender in json.sender){
            if(sid == sender.sid) {
                json.sender.remove(sender)
                saveJSON(json)
                return true
            }
        }
        return false
    }

    /**
     * return a sender object by a sender's id
     * @param sid id of the sender to find
     * @return Sender object if found, else null
     *
     * @author Flo Dörr
     */
    suspend fun getSenderById(sid: String): Sender? {
        val json = getJSON()
        for (sender in json.sender) {
            if(sender.sid == sid) {
                return sender
            }
        }
        return null
    }

    /**
     * return a sender object by a sender's cords
     * @param cords coordinates of the sender chest
     * @return Sender object if found, else null
     *
     * @author Flo Dörr
     */
    suspend fun getSenderByCords(cords: Cords): Sender? {
        val json = getJSON()
        for (sender in json.sender) {
            if(sender.cords.left == cords || sender.cords.right == cords) {
                return sender
            }
        }
        return null
    }

    /**
     * returns all saved sender
     * @return List of sender
     *
     * @author Flo Dörr
     */
    suspend fun getSender(): ArrayList<Sender> {
        return getJSON().sender
    }

    /**
     * adds a receiver to a sender
     * @param receiver to be added
     * @param sender to which the receiver should be added to
     * @return true if added successfully
     *
     * @author Flo Dörr
     */
    suspend fun addReceiverToSender(receiver: Receiver, sender: Sender): Boolean {
        return addReceiverToSender(receiver, sender.sid)
    }

    /**
     * adds a receiver to a sender by the sender's id
     * @param receiver to be added
     * @param sid sender id to which the receiver should be added to
     * @return true if added successfully
     *
     * @author Flo Dörr
     */
    suspend fun addReceiverToSender(receiver: Receiver, sid: String): Boolean {
        val json = getJSON()
        for (jsonSender in json.sender) {
            if(jsonSender.sid == sid) {
                for (jsonReceiver in jsonSender.receiver) {
                    if(jsonReceiver.rid == receiver.rid) {
                        return false
                    }
                }
                jsonSender.receiver.add(receiver)
                return saveJSON(json)
            }
        }
        return false
    }

    /**
     * removes a receiver by a receiver object
     * @param receiver receiver to be removed
     * @return true if removed successfully
     *
     * @author Flo Dörr
     */
    suspend fun removeReceiver(receiver: Receiver): Boolean {
        return removeReceiver(receiver.rid)
    }

    /**
     * removes a receiver by a receiver id
     * @param rid id of receiver to be removed
     * @return true if removed successfully
     *
     * @author Flo Dörr
     */
    suspend fun removeReceiver(rid: String): Boolean {
        val json = getJSON()
        for (sender in json.sender) {
            for (receiver in sender.receiver) {
                if(receiver.rid == rid) {
                    sender.receiver.remove(receiver)
                    saveJSON(json)
                    return true
                }
            }
        }
        return false
    }

    /**
     * return all receiver of a sender object
     * @param sender sender whose receiver should be returned
     * @return List of receiver
     *
     * @author Flo Dörr
     */
    suspend fun getReceiverFromSender(sender: Sender): ArrayList<Receiver>? {
        return getReceiverFromSender(sender.sid)
    }

    /**
     * return all receiver of a sender by its id
     * @param sid sender's id whose receiver should be returned
     * @return List of receiver
     *
     * @author Flo Dörr
     */
    suspend fun getReceiverFromSender(sid: String): ArrayList<Receiver>? {
        val sender = getSenderById(sid) ?: return null
        return sender.receiver
    }

    /**
     * searches through all chest (senders and receivers) and returns either a Sender or a Receiver
     * @param cords cords of the object to find
     * @return if no chest is found null
     *
     * @author Flo Dörr
     */
    suspend fun getSavedChestFromCords(cords: Cords): Pair<Sender?, Receiver?>? {
        val json = getJSON()
        val receivers = ArrayList<Receiver>()
        // first go through sender because it could be potentially faster
        for (sender in json.sender) {
            if (sender.cords.left == cords || sender.cords.right == cords) {
                return Pair(sender, null)
            }
            receivers.addAll(sender.receiver)
        }
        // if not in sender go through receiver
        for (receiver in receivers) {
            if (receiver.cords.left == cords || receiver.cords.right == cords) {
                return Pair(null, receiver)
            }
        }
        return null
    }

    /**
     * reads the json from disk if not already cached
     * @return JSON object
     *
     * @author Flo Dörr
     */
    private suspend fun getJSON(): JSON {
        return cachedJSON
            ?: withContext(Dispatchers.IO) {
                cachedJSON = Klaxon().parse<JSON>(jsonFile.readText())!!
                return@withContext cachedJSON!!
            }
    }

    /**
     * writes JSON object back to disk
     * @param json JSON object to be saved
     * @return true if saved successfully
     *
     * @author Flo Dörr
     */
    private suspend fun saveJSON(json: JSON): Boolean {
        return withContext(Dispatchers.IO) {
            jsonFile.writeText(
                Klaxon().toJsonString(
                    json
                )
            )
            cachedJSON = json
            return@withContext true
        }
    }

    /**
     * migrate json by setting the world param if this plugin was used before 1.6.0
     * @param defaultWorld UUID of the default world
     *
     * @author Flo Dörr
     */
    suspend fun migrateJSON(defaultWorld: String) {
        val json: JSON = getJSON()
        for (sender in json.sender){
            if(sender.cords.left.world == null){
                sender.cords.left.world = defaultWorld
                sender.cords.right?.world = defaultWorld
            }
            for(receiver in sender.receiver) {
                if(receiver.cords.left.world == null){
                    receiver.cords.left.world = defaultWorld
                    receiver.cords.right?.world = defaultWorld
                }
            }
        }
        saveJSON(json)
    }
}
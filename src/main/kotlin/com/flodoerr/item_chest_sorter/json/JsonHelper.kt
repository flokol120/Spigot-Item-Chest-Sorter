package com.flodoerr.item_chest_sorter.json

import com.beust.klaxon.Klaxon
import com.google.gson.JsonNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.command.ConsoleCommandSender
import java.io.File
import java.nio.file.Paths

class JsonHelper(dataFolder: File, private val commandSender: ConsoleCommandSender? = null) {

    private val jsonFile = Paths.get(dataFolder.absolutePath, "chests.json").toFile()
    private val doNotTouchFile = Paths.get(dataFolder.absolutePath, "README (don't touch the json file if you don't know what you are doing)").toFile()

    init {
        if(!dataFolder.exists()){
            dataFolder.mkdir()
        }
        if(!jsonFile.exists() || jsonFile.readText() == ""){
            jsonFile.writeText(Klaxon().toJsonString(JSON()))
            commandSender?.sendMessage("created json file")
        }
        doNotTouchFile.writeText("Don't touch the json file if you don't know what you are doing! Really. Don't do it. There is no configuration needed.")
    }

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

    suspend fun removeSender(sender: Sender): Boolean {
        val json = getJSON()
        for (jsonSender in json.sender){
            if(sender == jsonSender) {
                json.sender.remove(sender)
                saveJSON(json)
                return true
            }
        }
        return false
    }

    suspend fun getSenderById(sid: String): Sender? {
        val json = getJSON()
        for (sender in json.sender) {
            if(sender.sid == sid) {
                return sender
            }
        }
        return null
    }

    suspend fun getSenderByCords(cords: Cords): Sender? {
        val json = getJSON()
        for (sender in json.sender) {
            if(sender.cords.left == cords || sender.cords.right == cords) {
                return sender
            }
        }
        return null
    }

    suspend fun getSender(): ArrayList<Sender> {
        return getJSON().sender
    }

    suspend fun addReceiverToSender(receiver: Receiver, sender: Sender): Boolean {
        return addReceiverToSender(receiver, sender.sid)
    }

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

    suspend fun getReceiverFromSender(sender: Sender): ArrayList<Receiver>? {
        return getReceiverFromSender(sender.sid)
    }

    suspend fun getReceiverFromSender(sid: String): ArrayList<Receiver>? {
        val sender = getSenderById(sid) ?: return null
        return sender.receiver
    }

    private suspend fun getJSON(): JSON {
        return withContext(Dispatchers.IO) {
            return@withContext Klaxon().parse<JSON>(jsonFile.readText())!!
        }
    }

    private suspend fun saveJSON(json: JSON): Boolean {
        return withContext(Dispatchers.IO) {
            jsonFile.writeText(
                Klaxon().toJsonString(
                    json
                )
            )
            return@withContext true
        }
    }
}
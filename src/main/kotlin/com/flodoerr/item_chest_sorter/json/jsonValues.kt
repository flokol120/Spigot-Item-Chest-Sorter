package com.flodoerr.item_chest_sorter.json

data class Cords(var x: Int, var y: Int, var z: Int, var world: String)

data class ChestLocation(var left: Cords, var right: Cords? = null)

data class Sender(var sid: String, var name: String, var cords: ChestLocation, var receiver: ArrayList<Receiver> = ArrayList())

data class Receiver(var rid: String, var cords: ChestLocation)

data class JSON(var sender: ArrayList<Sender> = ArrayList())
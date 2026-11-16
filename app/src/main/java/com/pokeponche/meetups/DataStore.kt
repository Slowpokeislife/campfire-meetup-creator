package com.pokeponche.meetups

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Monster(val name: String, val sprite: String)
data class RSVPRecord(val id: String, val username: String, val displayName: String, val status: String)

data class Meetup(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "", var description: String = "", var category: String = "Community Day",
    var startDate: String = "", var startTime: String = "", var endDate: String = "", var endTime: String = "",
    var location: String = "Zócalo de Puebla", var bonuses: String = "", var species: String = "",
    var shinyAvailable: Boolean = false,
    var imagePath: String = "", var campfireUrl: String = "", var attendees: MutableList<String> = mutableListOf(),
    var checkedAt: String = "", var createdAt: Long = System.currentTimeMillis(),
    // Nullable distinguishes unavailable statistics from a real zero.
    var acceptedCount: Int? = null, var checkInCount: Int? = null,
    var rsvpRecords: MutableList<RSVPRecord> = mutableListOf(),
    var statsUpdatedAt: String = ""
)

class LocalRepository(private val context: Context) {
    private val file get() = File(context.filesDir, "meetups.json")
    private val settings get() = context.getSharedPreferences("meetups_settings", Context.MODE_PRIVATE)
    var clubName: String
        get() = settings.getString("club_name", "Pokeponche Puebla") ?: "Pokeponche Puebla"
        set(value) { settings.edit().putString("club_name", value).apply() }
    var defaultPlace: String
        get() = settings.getString("default_place", "Zócalo de Puebla") ?: "Zócalo de Puebla"
        set(value) { settings.edit().putString("default_place", value).apply() }
    var activeBackground: String
        get() = settings.getString("active_background", "") ?: ""
        set(value) { settings.edit().putString("active_background", value).apply() }
    var socialBackground: String
        get() = settings.getString("social_background", "") ?: ""
        set(value) { settings.edit().putString("social_background", value).apply() }
    var customEventTypes: List<String>
        get() = runCatching {
            val source=settings.getString("custom_event_types", "[]") ?: "[]"
            val arr=JSONArray(source)
            List(arr.length()) {index->arr.getString(index)}
        }.getOrDefault(emptyList())
        set(value) { settings.edit().putString("custom_event_types", JSONArray(value.distinct()).toString()).apply() }
    var backgroundNames: List<String>
        get() = settings.getString("background_names", "")!!.split('|').filter { it.isNotBlank() }
        set(value) { settings.edit().putString("background_names", value.joinToString("|")).apply() }

    fun loadPokemon(): List<Monster> = context.assets.open("pokemon_data_completa.csv").bufferedReader().use { reader ->
        reader.lineSequence().drop(1).mapNotNull { line ->
            val cells = parseCsvLine(line)
            if (cells.size > 5 && cells[1].isNotBlank() && cells[5].isNotBlank()) Monster(cells[1], cells[5]) else null
        }.toList()
    }
    fun backgrounds(): List<String> = backgroundNames
    // Una sola imagen activa por categoría: no acumular un historial de fondos.
    fun importBackground(uri: android.net.Uri, target: String = "active"): String {
        require(target == "active" || target == "social") { "Tipo de fondo inválido" }
        val directory = File(context.filesDir, "backgrounds").apply { mkdirs() }
        val destination = File(directory, "${target}_actual.png")
        val temporary = File(directory, "${target}_nuevo.png")
        context.contentResolver.openInputStream(uri)?.use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        } ?: error("No se pudo leer la imagen")
        // Sustituir solamente la imagen seleccionada, sin tocar las quedadas.
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            error("No se pudo reemplazar el fondo")
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        return destination.absolutePath
    }
    fun readAll(): MutableList<Meetup> = runCatching {
        val root = JSONArray(file.readText())
        MutableList(root.length()) { i ->
            val o = root.getJSONObject(i)
            Meetup(
                id=o.optString("id"), title=o.optString("title"), description=o.optString("description"),
                category=o.optString("category"), startDate=o.optString("startDate"), startTime=o.optString("startTime"),
                endDate=o.optString("endDate"), endTime=o.optString("endTime"), location=o.optString("location"),
                bonuses=o.optString("bonuses"), species=o.optString("species"), shinyAvailable=o.optBoolean("shinyAvailable",false), imagePath=o.optString("imagePath"),
                campfireUrl=o.optString("campfireUrl"),
                attendees=mutableListOf<String>().apply {
                    val a = o.optJSONArray("attendees") ?: JSONArray()
                    for (j in 0 until a.length()) add(a.optString(j))
                }, checkedAt=o.optString("checkedAt"), createdAt=o.optLong("createdAt"),
                acceptedCount=if(o.has("acceptedCount") && !o.isNull("acceptedCount")) o.optInt("acceptedCount") else null,
                checkInCount=if(o.has("checkInCount") && !o.isNull("checkInCount")) o.optInt("checkInCount") else null,
                rsvpRecords=mutableListOf<RSVPRecord>().apply {
                    val records=o.optJSONArray("rsvpRecords") ?: JSONArray()
                    for(j in 0 until records.length()) {
                        val r=records.optJSONObject(j) ?: continue
                        add(RSVPRecord(r.optString("id"),r.optString("username"),r.optString("displayName"),r.optString("status")))
                    }
                }, statsUpdatedAt=o.optString("statsUpdatedAt")
            )
        }
    }.getOrDefault(mutableListOf())
    fun saveAll(events: List<Meetup>) {
        val root = JSONArray()
        events.forEach { e ->
            root.put(JSONObject().apply {
                put("id", e.id); put("title", e.title); put("description", e.description)
                put("category", e.category); put("startDate", e.startDate); put("startTime", e.startTime)
                put("endDate", e.endDate); put("endTime", e.endTime); put("location", e.location)
                put("bonuses", e.bonuses); put("species", e.species); put("shinyAvailable",e.shinyAvailable); put("imagePath", e.imagePath)
                put("campfireUrl", e.campfireUrl); put("attendees", JSONArray(e.attendees))
                put("checkedAt", e.checkedAt); put("createdAt", e.createdAt)
                put("acceptedCount", e.acceptedCount ?: JSONObject.NULL)
                put("checkInCount", e.checkInCount ?: JSONObject.NULL)
                put("statsUpdatedAt", e.statsUpdatedAt)
                put("rsvpRecords", JSONArray().apply { e.rsvpRecords.forEach { r ->
                    put(JSONObject().apply { put("id",r.id);put("username",r.username);put("displayName",r.displayName);put("status",r.status) })
                } })
            })
        }
        val temp = File(context.filesDir, "meetups_tmp.json")
        temp.writeText(root.toString(2))
        if (!temp.renameTo(file)) { file.writeText(temp.readText()); temp.delete() }
    }
    fun delete(id: String) {
        // Do not remove the saved gallery image or the other meetup records.
        val remaining=readAll().filterNot { it.id==id }
        saveAll(remaining)
    }
    fun upsert(event: Meetup) {
        val all=readAll()
        val index=all.indexOfFirst { it.id == event.id }
        if (index >= 0) all[index]=event else all.add(event)
        saveAll(all)
    }
}

fun parseCsvLine(line: String): List<String> {
    val result=mutableListOf<String>(); val field=StringBuilder(); var quoted=false; var i=0
    while(i<line.length) {
        val c=line[i]
        when {
            c=='"' && quoted && i+1<line.length && line[i+1]=='"' -> { field.append('"'); i++ }
            c=='"' -> quoted=!quoted
            c==',' && !quoted -> {result.add(field.toString()); field.clear()}
            else -> field.append(c)
        }
        i++
    }
    result.add(field.toString())
    return result
}

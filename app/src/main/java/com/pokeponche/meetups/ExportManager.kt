package com.pokeponche.meetups

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exporta copias de los datos locales; no modifica ni elimina registros. */
object MeetupExporter {
    private fun safeCsv(value: String): String {
        // Avoid spreadsheet formula execution in exported user-generated content.
        val cleaned = value.replace("\u0000", "").replace("\r\n", "\n")
        val escaped = if (cleaned.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t')) "'${cleaned}" else cleaned
        return "\"${escaped.replace("\"", "\"\"")}\""
    }
    private fun row(vararg fields: String): String = fields.joinToString(",") { safeCsv(it) } + "\r\n"
    fun eventsCsv(events: List<Meetup>): ByteArray {
        val sb = StringBuilder("\uFEFF")
        sb.append(row("id", "titulo", "tipo", "fecha_inicio", "hora_inicio", "fecha_fin", "hora_fin", "lugar", "pokemon", "shiny", "bonos", "descripcion", "campfire_url", "checkins_descargados", "total_checkins", "fecha_extraccion", "fecha_creacion", "accepted_count", "check_in_count", "check_in_rate", "stats_updated_at"))
        events.forEach { e -> sb.append(row(e.id,e.title,e.category,e.startDate,e.startTime,e.endDate,e.endTime,e.location,e.species,e.shinyAvailable.toString(),e.bonuses,e.description,e.campfireUrl,(e.checkedAt.isNotBlank()).toString(),if(e.checkedAt.isBlank()) "" else e.attendees.size.toString(),e.checkedAt,e.createdAt.toString(),
            e.acceptedCount?.toString().orEmpty(),e.checkInCount?.toString().orEmpty(),
            if(e.acceptedCount != null && e.acceptedCount!! > 0 && e.checkInCount != null)
                "%.6f".format(java.util.Locale.ROOT, e.checkInCount!!.toDouble()/e.acceptedCount!!.toDouble()) else "",
            e.statsUpdatedAt)) }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
    private fun checkinsCsv(e: Meetup): ByteArray {
        val sb = StringBuilder("\uFEFF")
        sb.append(row("quedada_id", "campfire_url", "usuario", "estado"))
        if (e.checkedAt.isNotBlank()) e.attendees.forEach { name -> sb.append(row(e.id,e.campfireUrl,name,"CHECKED_IN")) }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
    private fun rsvpsCsv(e: Meetup): ByteArray {
        val sb=StringBuilder("\uFEFF")
        sb.append(row("quedada_id","member_id","username","display_name","rsvp_status","hizo_checkin","acepto_sin_checkin"))
        e.rsvpRecords.forEach { r -> sb.append(row(e.id,r.id,r.username,r.displayName,r.status,(r.status=="CHECKED_IN").toString(),(r.status=="ACCEPTED").toString())) }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
    fun exportEvents(context: Context, destination: Uri, events: List<Meetup>) {
        context.contentResolver.openOutputStream(destination)?.use { it.write(eventsCsv(events)) } ?: error("No se pudo escribir el CSV")
    }
    fun exportZip(context: Context, destination: Uri, events: List<Meetup>) {
        context.contentResolver.openOutputStream(destination)?.use { output ->
            ZipOutputStream(output).use { zip ->
                fun entry(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                entry("quedadas.csv",eventsCsv(events))
                events.forEachIndexed { index, e ->
                    // A unique index plus the local UUID prevents collisions.
                    val folder = "eventos/${index+1}_${e.id.take(12)}/"
                    entry(folder+"quedada.csv",eventsCsv(listOf(e)))
                    if (e.checkedAt.isNotBlank()) entry(folder+"checkins.csv",checkinsCsv(e))
                    if (e.rsvpRecords.isNotEmpty()) {
                        entry(folder+"rsvp_estados.csv",rsvpsCsv(e))
                        val noShows = e.rsvpRecords.filter { it.status=="ACCEPTED" }
                        val csv=StringBuilder("\uFEFF").apply {
                            append(row("quedada_id","member_id","username","display_name","estado"))
                            noShows.forEach { r -> append(row(e.id,r.id,r.username,r.displayName,r.status)) }
                        }
                        entry(folder+"aceptaron_sin_checkin.csv",csv.toString().toByteArray(Charsets.UTF_8))
                    }
                    else entry(folder+"SIN_DATOS_DE_ASISTENCIA.txt", "Esta quedada todavía no tiene registros de check-in guardados localmente.\n".toByteArray())
                }
            }
        } ?: error("No se pudo escribir el ZIP")
    }
}

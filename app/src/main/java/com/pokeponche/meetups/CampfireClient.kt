package com.pokeponche.meetups

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Descarga local usando la API pública de Campfire Tools. No requiere servidor propio. */
object CampfireClient {
    data class Result(
        val attendees: List<String>?, val note: String,
        val acceptedCount: Int? = null, val checkInCount: Int? = null,
        val records: List<RSVPRecord> = emptyList()
    )

    private val uuid = Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    )
    private val allowedHosts = setOf(
        "cmpf.re", "cmpf-tools.de", "www.cmpf-tools.de",
        "campfire.scopely.com", "campfire.onelink.me"
    )

    private fun validate(link: String): String {
        val value = link.trim()
        val url = runCatching { URL(value) }
            .getOrElse { error("Introduce un enlace completo que empiece con https://") }
        require(url.protocol.equals("https", ignoreCase = true)) {
            "El enlace debe usar HTTPS"
        }
        require(url.host.lowercase() in allowedHosts) {
            "El enlace no pertenece a Campfire o Campfire Tools"
        }
        return value
    }

    private fun request(url: String, method: String = "GET", payload: String? = null): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 12000
        connection.readTimeout = 20000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/json")
        return try {
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    private fun lookup(idOrLink: String): JSONArray {
        val endpoint = "https://cmpf-tools.de/api/events?events=" +
            URLEncoder.encode(idOrLink, "UTF-8")
        val (code, body) = request(endpoint)
        if (code !in 200..299) error("Campfire Tools respondió HTTP $code")
        return JSONArray(body)
    }

    fun fetch(link: String): Result {
        val validLink = validate(link)
        // An event UUID may appear in an ordinary Campfire link or a cmpf-tools event URL.
        // Short links (cmpf.re/...) must be sent in full to Campfire Tools.
        val directId = uuid.find(validLink)?.value
        val query = directId ?: validLink

        var events = lookup(query)
        if (events.length() == 0) {
            // Import the *original link* so the service can resolve short URLs.
            val (code, _) = request(
                "https://cmpf-tools.de/api/events",
                "POST",
                JSONArray().put(validLink).toString()
            )
            if (code !in 200..299) {
                error("Evento no encontrado; no fue posible importarlo (HTTP $code)")
            }
            // A successful import may be asynchronous; don't mark as downloaded yet.
            events = lookup(query)
            if (events.length() == 0) {
                return Result(null, "Importación solicitada. Intenta descargar de nuevo más tarde.")
            }
        }

        val event = events.getJSONObject(0)
        val members = event.optJSONArray("members")
            ?: return Result(null, "El evento se encontró, pero la API todavía no devolvió participantes.")

        val records = mutableListOf<RSVPRecord>()
        for (i in 0 until members.length()) {
            val member = members.optJSONObject(i) ?: continue
            val status = member.optString("rsvp_status").uppercase(java.util.Locale.ROOT)
            if (status !in setOf("CHECKED_IN", "ACCEPTED", "DECLINED")) continue
            records.add(RSVPRecord(
                id = member.optString("id"),
                username = member.optString("username"),
                displayName = member.optString("display_name"),
                status = status
            ))
        }
        // These represent two different RSVP statuses in the public API.
        // Do NOT silently treat CHECKED_IN as ACCEPTED or vice versa.
        val checked = records.filter { it.status == "CHECKED_IN" }
        val names = checked.mapNotNull { r ->
            (r.username.ifBlank { r.displayName }.ifBlank { r.id }).takeIf { it.isNotBlank() }
        }.distinctBy { it.lowercase() }
        // ACCEPTED means a person who said they would attend but did NOT check in.
        // The page's total Accepted includes both these people and CHECKED_IN.
        // Example: 296 checked in + 54 accepted without check-in = 350 accepted.
        val acceptedWithoutCheckin = records.count { it.status == "ACCEPTED" }
        val checkins = checked.size
        val computedAcceptedTotal = checkins + acceptedWithoutCheckin
        val rawAccepted = if (event.has("accepted") && !event.isNull("accepted")) event.optInt("accepted") else null
        val checkinsTotal = if (event.has("check_ins") && !event.isNull("check_ins")) event.optInt("check_ins")
            else if (event.has("checked_in") && !event.isNull("checked_in")) event.optInt("checked_in") else checkins
        // If the endpoint provides a comprehensive count, keep it. If it provides only
        // currently ACCEPTED members, combine it with CHECKED_IN instead.
        val acceptedTotal = when {
            rawAccepted == null -> computedAcceptedTotal
            rawAccepted >= checkinsTotal -> rawAccepted
            else -> checkinsTotal + rawAccepted
        }
        // Guard individual RSVP statuses as supplied; do not relabel them.
        return Result(names, "$checkinsTotal check-ins · $acceptedTotal aceptaron",
            acceptedTotal, checkinsTotal, records)
    }
}

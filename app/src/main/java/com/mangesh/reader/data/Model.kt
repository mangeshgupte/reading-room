package com.mangesh.reader.data

import org.json.JSONObject

/** A JSON null on Android's org.json comes back as the string "null" from optString; guard it. */
fun JSONObject.str(key: String, default: String = ""): String =
    if (has(key) && !isNull(key)) getString(key) else default

/** One reading-list entry as the Mac serves it, plus the phone's own fields. */
data class Entry(
    val id: Int,
    val path: String,
    val version: Int,
    val title: String,
    val project: String,
    val source: String,        // clip's domain; "" for a report
    val note: String,
    val words: Int,
    val minutes: Int,
    val by: String,
    val added: String,         // ISO date
    val state: String,         // queued | done | dropped
    val closed: String,
    val reason: String,
    val sent: Boolean,
    val rawHash: String,       // hash of the file on the Mac at last sync
    val missing: Boolean,
    // local
    val progress: Float = 0f,  // scroll fraction 0..1
    val lastOpened: Long = 0L, // epoch ms
    val htmlHash: String = "", // rawHash the cached HTML was rendered from; "" = not downloaded
) {
    val isClip: Boolean get() = source.isNotEmpty()
    val where: String get() = if (isClip) source else project
    val downloaded: Boolean get() = htmlHash.isNotEmpty()
    val started: Boolean get() = progress > 0.02f && progress < 0.97f
    val minutesLeft: Int get() = Math.round(minutes * (1f - progress)).coerceAtLeast(0)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("path", path); put("version", version); put("title", title)
        put("project", project); put("source", source); put("note", note); put("words", words)
        put("minutes", minutes); put("by", by); put("added", added); put("state", state)
        put("closed", closed); put("reason", reason); put("sent", sent); put("raw_hash", rawHash)
        put("missing", missing); put("progress", progress.toDouble()); put("lastOpened", lastOpened)
        put("htmlHash", htmlHash)
    }

    companion object {
        fun fromServer(o: JSONObject) = Entry(
            id = o.getInt("id"), path = o.str("path"), version = o.optInt("version", 1),
            title = o.str("title"), project = o.str("project"), source = o.str("source"),
            note = o.str("note"), words = o.optInt("words"), minutes = o.optInt("minutes"),
            by = o.str("by"), added = o.str("added"), state = o.str("state", "queued"),
            closed = o.str("closed"), reason = o.str("reason"), sent = o.optBoolean("sent"),
            rawHash = o.str("raw_hash"), missing = o.optBoolean("missing"),
        )

        fun fromJson(o: JSONObject) = fromServer(o).copy(
            progress = o.optDouble("progress", 0.0).toFloat(),
            lastOpened = o.optLong("lastOpened", 0L),
            htmlHash = o.str("htmlHash"),
        )
    }
}

/** A write the phone wants the Mac to apply: done / drop / comment. */
data class Action(
    val uuid: String,
    val type: String,
    val id: Int,
    val ts: String,            // ISO instant when the user did it
    val reason: String = "",
    val line: Int = 0,
    val hash: String = "",
    val text: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("uuid", uuid); put("type", type); put("id", id); put("ts", ts)
        if (reason.isNotEmpty()) put("reason", reason)
        if (type == "comment") { put("line", line); put("hash", hash); put("text", text) }
    }

    companion object {
        fun fromJson(o: JSONObject) = Action(
            uuid = o.str("uuid"), type = o.str("type"), id = o.getInt("id"), ts = o.str("ts"),
            reason = o.str("reason"), line = o.optInt("line"), hash = o.str("hash"), text = o.str("text"),
        )
    }
}

/** An action the Mac refused, kept with its reason so nothing is lost. */
data class Refused(val action: Action, val error: String) {
    fun toJson(): JSONObject = JSONObject().put("action", action.toJson()).put("error", error)

    companion object {
        fun fromJson(o: JSONObject) = Refused(Action.fromJson(o.getJSONObject("action")), o.str("error"))
    }
}

data class ActionResult(val uuid: String, val ok: Boolean, val message: String)

data class ReportPayload(val id: Int, val rawHash: String, val html: String, val assets: List<String>)

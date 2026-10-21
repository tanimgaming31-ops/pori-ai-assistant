package com.pori.assistant.brain.tools

import android.content.Context
import android.os.BatteryManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GetTimeTool : Tool {
    override val name = "get_time"
    override val description = "Returns the current date and time."
    override val inputSchema = schema()
    override suspend fun run(input: JSONObject): String {
        val f = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault())
        f.timeZone = TimeZone.getDefault()
        return f.format(Date()) + " (" + TimeZone.getDefault().id + ")"
    }
}

class GetBatteryTool(private val context: Context) : Tool {
    override val name = "get_battery_status"
    override val description = "Returns the phone battery percentage and whether it is charging."
    override val inputSchema = schema()
    override suspend fun run(input: JSONObject): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return "battery=$pct%, charging=$charging"
    }
}

class SetLanguageTool(private val onSet: suspend (String) -> Unit) : Tool {
    override val name = "set_speech_language"
    override val description =
        "Sets the language Pori listens for. Call this when the user asks to speak in a specific " +
        "language, or when their speech was clearly transcribed in the wrong language. " +
        "Use a BCP-47 tag such as hi-IN, ur-PK, ar-SA, id-ID, fil-PH, en-US, or 'auto' to auto-detect."
    override val inputSchema = schema(
        mapOf("tag" to ("string" to "BCP-47 language tag, or 'auto'")),
        listOf("tag")
    )
    override suspend fun run(input: JSONObject): String {
        val tag = input.optString("tag").trim()
        val ok = tag == "auto" || Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*").matches(tag)
        if (!ok) return "ERROR: invalid language tag"
        onSet(tag)
        return "Now listening for $tag"
    }
}

object BasicTools {
    fun all(context: Context): List<Tool> = listOf(GetTimeTool(), GetBatteryTool(context))
}

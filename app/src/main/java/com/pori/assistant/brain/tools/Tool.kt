package com.pori.assistant.brain.tools

import org.json.JSONArray
import org.json.JSONObject

/** Every feature is a Tool. The LLM picks one by name and schema; run() does the work. */
interface Tool {
    val name: String
    val description: String
    val inputSchema: JSONObject

    /** Return true for sensitive actions (send SMS, call, delete) to ask the user yes/no first. */
    val needsConfirmation: Boolean get() = false

    /** What is asked out loud when confirmation is needed. */
    fun confirmPrompt(input: JSONObject): String = "Should I run $name?"

    suspend fun run(input: JSONObject): String
}

class ToolRegistry {
    private val tools = LinkedHashMap<String, Tool>()

    fun register(tool: Tool) { tools[tool.name] = tool }
    fun registerAll(list: List<Tool>) = list.forEach(::register)
    fun get(name: String): Tool? = tools[name]
    val size get() = tools.size

    /** The `tools` array for the Claude Messages API. */
    fun toApiJson(): JSONArray = JSONArray().also { arr ->
        tools.values.forEach { t ->
            arr.put(JSONObject()
                .put("name", t.name)
                .put("description", t.description)
                .put("input_schema", t.inputSchema))
        }
    }
}

/** Small helper to build a JSON schema. */
fun schema(
    properties: Map<String, Pair<String, String>> = emptyMap(),
    required: List<String> = emptyList()
): JSONObject {
    val props = JSONObject()
    properties.forEach { (k, v) ->
        props.put(k, JSONObject().put("type", v.first).put("description", v.second))
    }
    return JSONObject()
        .put("type", "object")
        .put("properties", props)
        .put("required", JSONArray(required))
}

package com.pori.assistant.brain

import com.pori.assistant.brain.tools.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the conversation history and runs the tool-calling loop:
 * user text -> LLM -> (if tool_use: run tools, send results back) -> final answer.
 */
class Agent(
    private val llm: LlmClient,
    private val registry: ToolRegistry,
    private val system: String,
    /** Callback that asks yes/no before sensitive tools. */
    private val confirm: suspend (String) -> Boolean,
    private val onToolCall: (String) -> Unit = {}
) {
    private val history = JSONArray()

    suspend fun ask(userText: String): String {
        history.put(JSONObject().put("role", "user").put("content", userText))
        trimHistory()

        repeat(MAX_STEPS) {
            val resp = llm.send(system, history, registry.toApiJson())
            val content = resp.getJSONArray("content")
            history.put(JSONObject().put("role", "assistant").put("content", content))

            if (resp.optString("stop_reason") != "tool_use") return textOf(content)

            val results = JSONArray()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") != "tool_use") continue
                val name = block.getString("name")
                val input = block.optJSONObject("input") ?: JSONObject()
                results.put(
                    JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", block.getString("id"))
                        .put("content", runTool(name, input))
                )
            }
            history.put(JSONObject().put("role", "user").put("content", results))
        }
        return "Sorry, I could not finish that."
    }

    fun reset() {
        while (history.length() > 0) history.remove(0)
    }

    private suspend fun runTool(name: String, input: JSONObject): String {
        val tool = registry.get(name) ?: return "ERROR: unknown tool $name"
        onToolCall(name)
        if (tool.needsConfirmation && !confirm(tool.confirmPrompt(input))) {
            return "USER_DECLINED: the user did not give permission."
        }
        return try {
            tool.run(input)
        } catch (e: Exception) {
            "ERROR: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun textOf(content: JSONArray): String {
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            if (b.optString("type") == "text") sb.append(b.getString("text"))
        }
        return sb.toString().trim()
    }

    /** Trims from the front without breaking tool_use/tool_result pairs: only cut at a plain user text message. */
    private fun trimHistory() {
        while (history.length() > MAX_MESSAGES) {
            var cut = -1
            for (i in 1 until history.length()) {
                val m = history.getJSONObject(i)
                if (m.getString("role") == "user" && m.get("content") is String) { cut = i; break }
            }
            if (cut == -1) break
            repeat(cut) { history.remove(0) }
        }
    }

    companion object {
        const val MAX_STEPS = 8
        const val MAX_MESSAGES = 40
    }
}

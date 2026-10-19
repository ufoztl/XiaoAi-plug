package io.mo.xiaoaiplug.config

import org.json.JSONArray
import org.json.JSONObject

/** Stateless bridge between the existing chat tool-loop and OpenAI Responses API. */
internal object OpenAiResponsesCompat {
    fun requestBody(model: String, messages: JSONArray, toolsIn: JSONArray, noToolCalls: Boolean): JSONObject {
        val body = JSONObject().put("model", model).put("input", toInput(messages)).put("store", false)
        val tools = JSONArray()
        for (i in 0 until toolsIn.length()) {
            val f = toolsIn.optJSONObject(i)?.optJSONObject("function") ?: continue
            tools.put(JSONObject().put("type", "function")
                .put("name", f.optString("name")).put("description", f.optString("description"))
                .put("parameters", f.optJSONObject("parameters") ?: JSONObject())
                .put("strict", false))
        }
        if (tools.length() > 0) body.put("tools", tools)
            .put("tool_choice", if (noToolCalls) "none" else "auto")
        return body
    }

    fun toInput(messages: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until messages.length()) {
            val m = messages.optJSONObject(i) ?: continue
            when (val role = m.optString("role")) {
                "system", "developer", "user" ->
                    out.put(JSONObject().put("role", role).put("content", m.optString("content")))
                "assistant" -> {
                    val content = m.opt("content") as? String
                    if (!content.isNullOrBlank())
                        out.put(JSONObject().put("role", "assistant").put("content", content))
                    val calls = m.optJSONArray("tool_calls") ?: continue
                    for (j in 0 until calls.length()) {
                        val c = calls.optJSONObject(j) ?: continue
                        val f = c.optJSONObject("function") ?: continue
                        val args = when (val value = f.opt("arguments")) {
                            is JSONObject -> value.toString()
                            is String -> value
                            else -> "{}"
                        }
                        out.put(JSONObject().put("type", "function_call")
                            .put("call_id", c.optString("id", "call_$i-$j"))
                            .put("name", f.optString("name")).put("arguments", args))
                    }
                }
                "tool" -> out.put(JSONObject().put("type", "function_call_output")
                    .put("call_id", m.optString("tool_call_id"))
                    .put("output", m.optString("content")))
            }
        }
        return out
    }

    fun toChatMessage(response: JSONObject): JSONObject {
        val text = StringBuilder()
        val calls = JSONArray()
        val output = response.optJSONArray("output") ?: JSONArray()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            when (item.optString("type")) {
                "message" -> {
                    val parts = item.optJSONArray("content") ?: continue
                    for (j in 0 until parts.length()) {
                        val part = parts.optJSONObject(j) ?: continue
                        if (part.optString("type") == "output_text")
                            text.append(part.optString("text"))
                    }
                }
                "function_call" -> calls.put(JSONObject()
                    .put("id", item.optString("call_id", "call_$i")).put("type", "function")
                    .put("function", JSONObject().put("name", item.optString("name"))
                        .put("arguments", item.optString("arguments", "{}"))))
            }
        }
        return JSONObject().put("role", "assistant").put("content", text.toString()).apply {
            if (calls.length() > 0) put("tool_calls", calls)
        }
    }
}

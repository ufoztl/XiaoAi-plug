package io.mo.xiaoaiplug.config

/** Keep legacy OpenAI-compatible vendors on Chat Completions.
 * Only route known first-party OpenAI models through Responses.
 */
internal object OpenAiModelPolicy {
    private fun official(endpoint: String): Boolean =
        endpoint.lowercase(java.util.Locale.ROOT).contains("api.openai.com")

    fun useResponses(model: String, endpoint: String): Boolean {
        if (!official(endpoint)) return false
        val m = model.trim().lowercase(java.util.Locale.ROOT)
        return m.startsWith("gpt-6.1-sol") || m.startsWith("gpt-6-astra")
    }

    fun chatReasoningEffort(model: String): String? {
        val m = model.trim().lowercase(java.util.Locale.ROOT)
        return if (m.startsWith("gpt-6-luna") || m.startsWith("gpt-6-sol")) "none" else null
    }
}

package io.github.omjasoliya.localagent

import android.content.Context
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File

/** Thin wrapper around Google's on-device LiteRT-LM runtime (fully offline, .litertlm models). */
class Agent private constructor(private val engine: Engine) {

    /** Asks the model to pick exactly one package from [apps] for free-form [instruction]. */
    fun pickApp(instruction: String, apps: List<AppEntry>): PickResult {
        val catalog = apps.joinToString("\n") { "${it.label} => ${it.packageName}" }
        // The instruction comes first and the (possibly long) app catalog after, so a context
        // or output cutoff loses catalog tail, never the actual request.
        val prompt = """
            You control an Android phone. Reply with ONLY the package name of the single best
            matching app for the instruction, and nothing else. If nothing matches, reply with
            exactly: NONE

            Example:
            Instruction: open telegram
            Installed apps, as "Label => package.name":
            Camera => com.android.camera
            Telegram => org.telegram.messenger
            Package name: org.telegram.messenger

            Now do the real one.
            Instruction: $instruction

            Installed apps, as "Label => package.name":
            $catalog

            Package name:
        """.trimIndent()

        // Disabling thinking entirely was a guess that didn't pan out (still "None" with
        // near-greedy sampling too). Gemma 3's instruction-tuned models are plausibly trained
        // to reason before answering, so give it a real (bounded, not unlimited) thinking budget
        // and enough output room for both the reasoning and the final line.
        val config = ConversationConfig(
            thinkingConfig = ThinkingConfig(enableThinking = true, thinkingTokenBudget = 512),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.1),
            maxOutputToken = 1024,
        )
        android.util.Log.i(TAG, "prompt (${prompt.length} chars): $prompt")
        val raw = engine.createConversation(config).use { it.sendMessage(prompt).toString() }
        android.util.Log.i(TAG, "raw response: $raw")

        // The exact shape of Message.toString() isn't something we can pin down ahead of time,
        // so match by substring against the known package list instead of parsing it strictly.
        val match = apps
            .sortedByDescending { it.packageName.length } // avoid a short package matching inside a longer one
            .firstOrNull { raw.contains(it.packageName, ignoreCase = true) }
        return PickResult(raw = raw, match = match)
    }

    fun close() = engine.close()

    data class PickResult(val raw: String, val match: AppEntry?)

    companion object {
        private const val TAG = "LocalAgent"

        /** Returns a failure with the real exception text if [modelFile] can't be loaded. */
        fun load(ctx: Context, modelFile: File): Result<Agent> = try {
            val engine = Engine(EngineConfig(modelPath = modelFile.absolutePath))
            engine.initialize()
            Result.success(Agent(engine))
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "model load failed", e)
            Result.failure(e)
        }
    }
}

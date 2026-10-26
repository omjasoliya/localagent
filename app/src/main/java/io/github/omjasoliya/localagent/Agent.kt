package io.github.omjasoliya.localagent

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File

/** Thin wrapper around Google's on-device LiteRT-LM runtime (fully offline, .litertlm models). */
class Agent private constructor(private val engine: Engine) {

    /** Asks the model to pick exactly one package from [apps] for free-form [instruction]. */
    fun pickApp(instruction: String, apps: List<AppEntry>): PickResult {
        val catalog = apps.joinToString("\n") { "${it.label} => ${it.packageName}" }
        val prompt = """
            You control an Android phone. Below is the list of installed apps as "Label => package.name".
            The user will give an instruction. Reply with ONLY the package name of the single best matching
            app from the list, and nothing else. If nothing in the list matches, reply with exactly: NONE

            Apps:
            $catalog

            Instruction: $instruction
            Package name:
        """.trimIndent()

        val conversation = engine.createConversation()
        val raw = try {
            conversation.sendMessage(prompt).toString()
        } finally {
            conversation.close()
        }

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

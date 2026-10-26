package io.github.omjasoliya.localagent

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File

/** Thin wrapper around MediaPipe's on-device LLM Inference API (fully offline). */
class Agent private constructor(private val llm: LlmInference) {

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

        val raw = llm.generateResponse(prompt).trim()
        val pkg = raw.lineSequence().firstOrNull()?.trim().orEmpty()
        val match = apps.firstOrNull { it.packageName == pkg }
        return PickResult(raw = raw, match = match)
    }

    fun close() = llm.close()

    data class PickResult(val raw: String, val match: AppEntry?)

    companion object {
        /** Returns null if [modelFile] isn't a model MediaPipe can load. */
        fun load(ctx: Context, modelFile: File): Agent? = try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .build()
            Agent(LlmInference.createFromOptions(ctx, options))
        } catch (e: Exception) {
            null
        }
    }
}

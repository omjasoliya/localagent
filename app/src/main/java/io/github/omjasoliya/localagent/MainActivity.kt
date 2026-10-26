package io.github.omjasoliya.localagent

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private var agent: Agent? = null
    private lateinit var apps: List<AppEntry>

    private lateinit var modelCard: MaterialCardView
    private lateinit var modelTitle: TextView
    private lateinit var modelBody: TextView
    private lateinit var modelProgress: LinearProgressIndicator
    private lateinit var chooseModelButton: MaterialButton
    private lateinit var instruction: TextInputEditText
    private lateinit var runButton: MaterialButton
    private lateinit var thinkingSpinner: CircularProgressIndicator
    private lateinit var resultText: TextView
    private lateinit var debugText: TextView

    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) copyAndLoadModel(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        modelCard = findViewById(R.id.modelCard)
        modelTitle = findViewById(R.id.modelTitle)
        modelBody = findViewById(R.id.modelBody)
        modelProgress = findViewById(R.id.modelProgress)
        chooseModelButton = findViewById(R.id.chooseModelButton)
        instruction = findViewById(R.id.instruction)
        runButton = findViewById(R.id.runButton)
        thinkingSpinner = findViewById(R.id.thinkingSpinner)
        resultText = findViewById(R.id.resultText)
        debugText = findViewById(R.id.debugText)

        apps = AppCatalog.list(this)
        debugText.text = "Installed launchable apps: ${apps.size}"

        chooseModelButton.setOnClickListener {
            // Gemma's license is accepted on Google's site; this only picks the file already downloaded.
            pickModel.launch(arrayOf("*/*"))
        }
        runButton.setOnClickListener { runInstruction() }

        val savedModel = modelFile()
        if (savedModel.exists()) loadModel(savedModel)
    }

    override fun onDestroy() {
        super.onDestroy()
        agent?.close()
        io.shutdownNow()
    }

    private fun modelFile() = File(filesDir, "model.task")

    private fun copyAndLoadModel(uri: Uri) {
        modelProgress.visibility = View.VISIBLE
        modelBody.setText(R.string.copying_model)
        chooseModelButton.isEnabled = false
        io.execute {
            val dest = modelFile()
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                runOnUiThread { loadModel(dest) }
            } catch (e: Exception) {
                runOnUiThread {
                    modelProgress.visibility = View.GONE
                    chooseModelButton.isEnabled = true
                    modelBody.setText(R.string.model_load_failed)
                }
            }
        }
    }

    private fun loadModel(file: File) {
        modelProgress.visibility = View.VISIBLE
        modelBody.setText(R.string.loading_model)
        chooseModelButton.isEnabled = false
        io.execute {
            val result = Agent.load(this, file)
            runOnUiThread {
                modelProgress.visibility = View.GONE
                chooseModelButton.isEnabled = true
                result.onFailure { e ->
                    modelBody.setText(R.string.model_load_failed)
                    debugText.text = "${e.javaClass.simpleName}: ${e.message}"
                }
                result.onSuccess { loaded ->
                    agent = loaded
                    modelCard.visibility = View.GONE
                    runButton.isEnabled = true
                    resultText.text = getString(R.string.model_ready, file.length() / (1024 * 1024))
                }
            }
        }
    }

    private fun runInstruction() {
        val text = instruction.text?.toString()?.trim().orEmpty()
        val a = agent ?: return
        if (text.isEmpty()) return

        runButton.isEnabled = false
        thinkingSpinner.visibility = View.VISIBLE
        resultText.text = getString(R.string.thinking)
        debugText.text = ""

        io.execute {
            val result = a.pickApp(text, apps)
            runOnUiThread {
                thinkingSpinner.visibility = View.GONE
                runButton.isEnabled = true
                debugText.text = "Model said:\n${result.raw}"
                val match = result.match
                if (match == null) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.no_match_title)
                        .setMessage(getString(R.string.no_match_body, result.raw))
                        .setPositiveButton(R.string.ok_got_it, null)
                        .show()
                    resultText.text = ""
                } else {
                    resultText.text = match.label
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.confirm_launch_title)
                        .setMessage(match.label)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.launch) { _, _ -> AppCatalog.launch(this, match.packageName) }
                        .show()
                }
            }
        }
    }
}

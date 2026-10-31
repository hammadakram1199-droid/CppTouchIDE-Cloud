package com.example.cpptouchide

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var codeEditor: EditText
    private lateinit var outputText: TextView
    private lateinit var symbolBar: LinearLayout
    private lateinit var btnRun: Button
    private lateinit var handwritingView: HandwritingView
    private lateinit var btnRecognize: Button
    private lateinit var btnClearCanvas: Button
    private lateinit var handwritingHint: TextView

    private lateinit var recognizer: DigitalInkRecognizer
    private lateinit var remoteModelManager: RemoteModelManager

    private val symbols = listOf(
        "#", "<", ">", "{", "}", "(", ")", "[", "]",
        ";", ":", "+", "-", "*", "/", "=", "&", "|", "!",
        "\"", "'", ",", ".", "->", "::", "\t", "\n"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        codeEditor = findViewById(R.id.codeEditor)
        outputText = findViewById(R.id.outputText)
        symbolBar = findViewById(R.id.symbolBar)
        btnRun = findViewById(R.id.btnRun)
        handwritingView = findViewById(R.id.handwritingView)
        btnRecognize = findViewById(R.id.btnRecognize)
        btnClearCanvas = findViewById(R.id.btnClearCanvas)
        handwritingHint = findViewById(R.id.handwritingHint)

        buildSymbolKeyboard()
        initDigitalInk()

        btnRecognize.setOnClickListener { recognizeHandwriting() }
        btnClearCanvas.setOnClickListener {
            handwritingView.clear()
            handwritingHint.visibility = TextView.VISIBLE
        }

        btnRun.setOnClickListener {
            val code = codeEditor.text.toString()
            if (code.isBlank()) {
                outputText.text = "Write some C++ code first."
                return@setOnClickListener
            }
            btnRun.isEnabled = false
            outputText.text = "Running online..."
            Thread {
                val result = runCppOnline(code)
                runOnUiThread {
                    outputText.text = result
                    btnRun.isEnabled = true
                }
            }.start()
        }
    }

    private fun initDigitalInk() {
        val modelIdentifier = try {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
        } catch (e: Exception) {
            Toast.makeText(this, "Language not supported.", Toast.LENGTH_LONG).show()
            return
        } ?: return

        val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )
        remoteModelManager = RemoteModelManager.getInstance()
        remoteModelManager.download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener {
                Toast.makeText(this, "Handwriting model ready (offline).", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Model download failed. Check internet.", Toast.LENGTH_LONG).show()
            }
    }

    private fun recognizeHandwriting() {
        val ink = handwritingView.getInk()
        if (ink.strokes.isEmpty()) {
            Toast.makeText(this, "Write something first.", Toast.LENGTH_SHORT).show()
            return
        }
        btnRecognize.isEnabled = false
        btnRecognize.text = "Recognizing..."
        recognizer.recognize(ink)
            .addOnSuccessListener { result ->
                btnRecognize.isEnabled = true
                btnRecognize.text = "Recognize Handwriting"
                if (result.candidates.isNotEmpty()) {
                    val recognizedText = result.candidates[0].text
                    insertAtCursor(recognizedText)
                    handwritingView.clear()
                    handwritingHint.visibility = TextView.VISIBLE
                } else {
                    Toast.makeText(this, "Could not recognize. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                btnRecognize.isEnabled = true
                btnRecognize.text = "Recognize Handwriting"
                Toast.makeText(this, "Recognition failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun runCppOnline(code: String): String {
        val endpoint = URL("https://emkc.org/api/v2/piston/execute")
        var conn: HttpURLConnection? = null

        return try {
            val files = JSONArray().put(JSONObject().put("content", code))
            val body = JSONObject()
                .put("language", "c++")
                .put("version", "10.2.0")
                .put("files", files)
                .toString()

            conn = endpoint.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val codeResp = conn.responseCode
            val stream = if (codeResp in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader().use(BufferedReader::readText)

            if (codeResp !in 200..299) return "HTTP $codeResp\n$response"

            val json = JSONObject(response)
            val sb = StringBuilder()

            json.optJSONObject("compile")?.let { compile ->
                val stderr = compile.optString("stderr", "")
                if (stderr.isNotBlank()) {
                    sb.append("COMPILE ERROR:\n").append(stderr).append("\n\n")
                }
            }

            json.optJSONObject("run")?.let { run ->
                val stdout = run.optString("stdout", "")
                val stderr = run.optString("stderr", "")
                if (stdout.isNotBlank()) sb.append("OUTPUT:\n").append(stdout).append("\n")
                if (stderr.isNotBlank()) sb.append("RUNTIME ERROR:\n").append(stderr).append("\n")
            }

            if (sb.isBlank()) sb.append("No output. Code compiled/ran with no output.")
            sb.toString()
        } catch (e: Exception) {
            "Network/API error:\n${e.message}"
        } finally {
            conn?.disconnect()
        }
    }

    private fun buildSymbolKeyboard() {
        for (sym in symbols) {
            val b = Button(this)
            val label = when (sym) {
                "\t" -> "TAB"; "\n" -> "ENTER"; else -> sym
            }
            b.text = label
            b.textSize = 14f
            b.setPadding(8, 0, 8, 0)
            b.setOnClickListener { insertAtCursor(sym) }
            symbolBar.addView(b)
        }
    }

    private fun insertAtCursor(text: String) {
        val start = codeEditor.selectionStart
        val end = codeEditor.selectionEnd
        codeEditor.text.replace(start, end, text)
        codeEditor.setSelection(start + text.length)
    }
}

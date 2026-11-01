package com.example.cpptouchide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class OutputFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var outputText: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        
        val btnRun = Button(requireContext()).apply { text = "Run Code Online" }
        outputText = TextView(requireContext()).apply { 
            setTextColor(0xFFEEEEEE.toInt())
            setBackgroundColor(0xFF050505.toInt())
            text = "Output will appear here..."
            setPadding(20, 20, 20, 20)
        }

        layout.addView(btnRun)
        layout.addView(outputText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return layout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        
        val btnRun = (view as LinearLayout).getChildAt(0) as Button
        btnRun.setOnClickListener {
            val code = viewModel.currentCode.value ?: ""
            if (code.isBlank()) {
                outputText.text = "No code to run!"
                return@setOnClickListener
            }
            outputText.text = "Compiling and running..."
            Thread {
                val result = runCppOnline(code)
                requireActivity().runOnUiThread { outputText.text = result }
            }.start()
        }
    }

    private fun runCppOnline(code: String): String {
        val endpoint = URL("https://emkc.org/api/v2/piston/execute")
        var conn: HttpURLConnection? = null
        return try {
            val files = JSONArray().put(JSONObject().put("content", code))
            val body = JSONObject().put("language", "c++").put("version", "10.2.0").put("files", files).toString()

            conn = endpoint.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            // THIS HEADER FIXES THE "NOT WHITELISTED" ERROR
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)") 
            conn.doOutput = true

            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val response = if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                return "Server Error: ${conn.responseCode}"
            }

            val json = JSONObject(response)
            val sb = StringBuilder()

            json.optJSONObject("compile")?.let { compile ->
                if (compile.optString("stderr").isNotBlank()) sb.append("COMPILE ERROR:\n${compile.optString("stderr")}\n\n")
            }
            json.optJSONObject("run")?.let { run ->
                if (run.optString("stdout").isNotBlank()) sb.append(run.optString("stdout"))
                if (run.optString("stderr").isNotBlank()) sb.append("RUNTIME ERROR:\n${run.optString("stderr")}")
            }
            if (sb.isBlank()) "Process finished with no output." else sb.toString()
        } catch (e: Exception) {
            "Network Error: ${e.message}"
        } finally { conn?.disconnect() }
    }
}

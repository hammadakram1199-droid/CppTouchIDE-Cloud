package com.example.cpptouchide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class OutputFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var tvOutput: TextView
    private lateinit var btnRun: MaterialButton
    private val client = OkHttpClient()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_output, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        
        tvOutput = view.findViewById(R.id.tvOutput)
        btnRun = view.findViewById(R.id.btnRun)

        viewModel.output.observe(viewLifecycleOwner) {
            tvOutput.text = it
        }

        btnRun.setOnClickListener {
            val code = viewModel.currentCode.value ?: return@setOnClickListener
            if (code.isBlank()) {
                Toast.makeText(context, "Code is empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runCode(code)
        }
    }

    private fun runCode(code: String) {
        btnRun.isEnabled = false
        viewModel.updateOutput("\n> Compiling and running...\n")

        val json = JSONObject().apply {
            put("language", "c++")
            put("version", "10.2.0")
            put("files", JSONArray().put(JSONObject().put("content", code)))
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://emkc.org/api/v2/piston/execute")
            .post(requestBody)
            .header("User-Agent", "CppTouchIDE/2.0") // Fixes the "Not Whitelisted" error
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    viewModel.updateOutput("Network Error: ${e.message}")
                    btnRun.isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                requireActivity().runOnUiThread {
                    btnRun.isEnabled = true
                    if (responseData != null) {
                        viewModel.updateOutput(parseResponse(responseData))
                    } else {
                        viewModel.updateOutput("Error: Empty response from server.")
                    }
                }
            }
        })
    }

    private fun parseResponse(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            val sb = StringBuilder("\n")
            
            json.optJSONObject("compile")?.let { compile ->
                val stderr = compile.optString("stderr")
                if (stderr.isNotBlank()) sb.append("COMPILE ERROR:\n$stderr\n\n")
            }
            
            json.optJSONObject("run")?.let { run ->
                val stdout = run.optString("stdout")
                val stderr = run.optString("stderr")
                if (stdout.isNotBlank()) sb.append("OUTPUT:\n$stdout\n")
                if (stderr.isNotBlank()) sb.append("RUNTIME ERROR:\n$stderr\n")
            }
            
            if (sb.length < 5) sb.append("> Process finished with no output.") else sb.toString()
        } catch (e: Exception) {
            "Error parsing response."
        }
    }
}

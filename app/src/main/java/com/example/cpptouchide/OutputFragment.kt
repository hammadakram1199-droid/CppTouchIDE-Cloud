package com.example.cpptouchide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
    private val client = OkHttpClient()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_output, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        tvOutput = view.findViewById(R.id.tvOutput)
        
        view.findViewById<MaterialButton>(R.id.btnRun).setOnClickListener {
            val code = viewModel.currentCode.value ?: ""
            if (code.isBlank()) {
                tvOutput.text = "> Error: Code is empty."
                return@setOnClickListener
            }
            runCode(code)
        }
    }

    private fun runCode(code: String) {
        tvOutput.text = "> Compiling and running...\n"
        
        val json = JSONObject().apply {
            put("language", "c++")
            put("version", "10.2.0") // Using exact version for stability
            put("files", JSONArray().put(JSONObject().put("content", code)))
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://emkc.org/api/v2/piston/execute")
            .post(requestBody)
            .header("User-Agent", "Mozilla/5.0 (Android)")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded) {
                    requireActivity().runOnUiThread { 
                        tvOutput.text = "> NETWORK ERROR:\n${e.message}\n\nPlease check your internet connection." 
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        if (responseData != null) {
                            try {
                                val jsonRes = JSONObject(responseData)
                                val sb = StringBuilder()
                                
                                jsonRes.optJSONObject("compile")?.let {
                                    val stderr = it.optString("stderr")
                                    if (stderr.isNotBlank()) sb.append("COMPILE ERROR:\n$stderr\n\n")
                                }
                                
                                jsonRes.optJSONObject("run")?.let {
                                    val stdout = it.optString("stdout")
                                    val stderr = it.optString("stderr")
                                    if (stdout.isNotBlank()) sb.append("OUTPUT:\n$stdout\n")
                                    if (stderr.isNotBlank()) sb.append("RUNTIME ERROR:\n$stderr\n")
                                }
                                
                                if (sb.isBlank()) {
                                    sb.append("> Code executed successfully, but no output was generated.")
                                }
                                tvOutput.text = sb.toString()
                            } catch (e: Exception) {
                                tvOutput.text = "> Error parsing server response:\n$responseData"
                            }
                        } else {
                            tvOutput.text = "> Error: Empty response from server."
                        }
                    }
                }
            }
        })
    }
}

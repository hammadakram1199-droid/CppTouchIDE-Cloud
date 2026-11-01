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
                tvOutput.text = "> Error: No code to run."
                return@setOnClickListener
            }
            runCode(code)
        }
    }

    private fun runCode(code: String) {
        tvOutput.text = "> Sending to compiler...\n"
        
        val json = JSONObject().apply {
            put("language", "c++")
            put("version", "10.2.0")
            put("files", JSONArray().put(JSONObject().put("content", code)))
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://emkc.org/api/v2/piston/execute")
            .post(requestBody)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded) {
                    requireActivity().runOnUiThread { 
                        tvOutput.text = "> NETWORK ERROR:\n${e.message}\n\nCheck your internet connection." 
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
                                val sb = StringBuilder("> COMPILE SUCCESS\n\n")
                                var hasOutput = false
                                
                                jsonRes.optJSONObject("compile")?.let {
                                    val stderr = it.optString("stderr")
                                    if (stderr.isNotBlank()) {
                                        sb.append("COMPILE ERROR:\n$stderr\n")
                                        hasOutput = true
                                    }
                                }
                                jsonRes.optJSONObject("run")?.let {
                                    val stdout = it.optString("stdout")
                                    val stderr = it.optString("stderr")
                                    if (stdout.isNotBlank()) {
                                        sb.append("OUTPUT:\n$stdout\n")
                                        hasOutput = true
                                    }
                                    if (stderr.isNotBlank()) {
                                        sb.append("RUNTIME ERROR:\n$stderr\n")
                                        hasOutput = true
                                    }
                                }
                                
                                if (!hasOutput) {
                                    sb.append("> Program executed, but printed nothing.\n")
                                }
                                tvOutput.text = sb.toString()
                            } catch (e: Exception) {
                                tvOutput.text = "> SERVER ERROR:\n$responseData"
                            }
                        } else {
                            tvOutput.text = "> Error: Server returned empty response."
                        }
                    }
                }
            }
        })
    }
}

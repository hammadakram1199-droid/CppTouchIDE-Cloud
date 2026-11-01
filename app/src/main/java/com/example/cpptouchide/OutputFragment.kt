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
            val code = viewModel.currentCode.value ?: return@setOnClickListener
            runCode(code)
        }
    }

    private fun runCode(code: String) {
        tvOutput.text = "> Compiling and running...\n"
        val json = JSONObject().apply {
            put("language", "c++")
            put("version", "10.2.0")
            put("files", JSONArray().put(JSONObject().put("content", code)))
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://emkc.org/api/v2/piston/execute")
            .post(requestBody)
            .header("User-Agent", "Mozilla/5.0") // FIX FOR NOT WHITELISTED
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread { tvOutput.text = "> Error: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                requireActivity().runOnUiThread {
                    if (responseData != null) {
                        try {
                            val jsonRes = JSONObject(responseData)
                            val sb = StringBuilder("> Output:\n\n")
                            jsonRes.optJSONObject("compile")?.let {
                                if (it.optString("stderr").isNotBlank()) sb.append(it.optString("stderr")).append("\n")
                            }
                            jsonRes.optJSONObject("run")?.let {
                                if (it.optString("stdout").isNotBlank()) sb.append(it.optString("stdout"))
                                if (it.optString("stderr").isNotBlank()) sb.append(it.optString("stderr"))
                            }
                            tvOutput.text = sb.toString()
                        } catch (e: Exception) {
                            tvOutput.text = "> Error parsing response."
                        }
                    }
                }
            }
        })
    }
}

package com.example.cppidepro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.cppidepro.databinding.FragmentOutputBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object PistonConfig {
    const val URL = "https://emkc.org/api/v2/piston/execute"
    const val TOKEN = ""   // <--- PUT YOUR TOKEN HERE
}

class OutputFragment : Fragment() {
    private var _binding: FragmentOutputBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
    }
    private var currentCall: Call? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOutputBinding.inflate(inflater, container, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.output.observe(viewLifecycleOwner) { text ->
            binding.tvOutput.text = text
            binding.outputScroll.post { _binding?.outputScroll?.fullScroll(View.FOCUS_UP) }
        }
        viewModel.isRunning.observe(viewLifecycleOwner) { running ->
            binding.progress.visibility = if (running) View.VISIBLE else View.GONE
            binding.btnRun.isEnabled = !running
        }
        binding.btnRun.setOnClickListener { runCode() }
    }

    private fun runCode() {
        val code = viewModel.code.value.orEmpty()
        if (code.isBlank()) { viewModel.setOutput("Nothing to run. The Code tab is empty."); return }
        val payload = JSONObject().apply {
            put("language", "c++"); put("version", "*")
            put("files", JSONArray().put(JSONObject().put("name", "main.cpp").put("content", code)))
        }
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder().url(PistonConfig.URL)
            .header("User-Agent", "Mozilla/5.0").header("Accept", "application/json").post(body)
        if (PistonConfig.TOKEN.isNotBlank()) requestBuilder.header("Authorization", PistonConfig.TOKEN)
        viewModel.setRunning(true); viewModel.setOutput("Compiling and running…")
        currentCall?.cancel()
        val call = client.newCall(requestBuilder.build())
        currentCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModel.postRunning(false)
                if (!call.isCanceled()) viewModel.postOutput("Network error: ${e.message ?: e.javaClass.simpleName}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = try { it.body?.string().orEmpty() } catch (e: IOException) { "" }
                    val message = try { formatResponse(it.code, raw) } catch (e: Exception) { "Could not parse server response:\n${raw.take(1500)}" }
                    viewModel.postOutput(message); viewModel.postRunning(false)
                }
            }
        })
    }

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

    private fun formatResponse(httpCode: Int, raw: String): String {
        if (httpCode !in 200..299) {
            val serverMessage = try { JSONObject(raw).str("message") } catch (e: Exception) { "" }
            val hint = when (httpCode) {
                401, 403 -> "\n\nThe public Piston API now requires an authorization token (since Feb 15, 2026). Set PistonConfig.TOKEN, or self-host Piston and change PistonConfig.URL."
                429 -> "\n\nRate limited. Wait a moment and try again."
                else -> ""
            }
            return "Server returned HTTP $httpCode" + (if (serverMessage.isNotBlank()) ": $serverMessage" else "") + hint
        }
        val root = JSONObject(raw); val run = root.optJSONObject("run"); val compile = root.optJSONObject("compile")
        if (run == null && root.str("message").isNotBlank()) return "API error: ${root.str("message")}"
        val topLevelCompileError = root.str("compile_error")
        val compileStderr = compile?.let { it.str("stderr").ifBlank { it.str("output") } }.orEmpty()
        val compileFailed = (compile != null && compile.optInt("code", 0) != 0) || topLevelCompileError.isNotBlank()
        if (compileFailed) return "── Compilation Error ──\n" + topLevelCompileError.ifBlank { compileStderr }.ifBlank { "Unknown compiler error." }
        val sb = StringBuilder()
        if (compileStderr.isNotBlank()) sb.append("── Compiler warnings ──\n").append(compileStderr.trimEnd()).append("\n\n")
        val stdout = run?.str("stdout").orEmpty(); val stderr = run?.str("stderr").orEmpty()
        val exitCode = run?.optInt("code", 0) ?: 0; val signal = run?.str("signal").orEmpty()
        if (stdout.isNotEmpty()) sb.append(stdout)
        if (stderr.isNotBlank()) { if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n"); sb.append("── Runtime Error (stderr) ──\n").append(stderr) }
        if (exitCode != 0 || signal.isNotBlank()) {
            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
            sb.append("\n[Process exited with code $exitCode")
            if (signal.isNotBlank()) sb.append(", signal $signal")
            sb.append("]")
        }
        return if (stdout.isEmpty() && stderr.isBlank() && compileStderr.isBlank() && exitCode == 0 && signal.isBlank()) {
            "Program executed, but printed nothing."
        } else { sb.toString() }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
    override fun onDestroy() { super.onDestroy(); currentCall?.cancel() }
}

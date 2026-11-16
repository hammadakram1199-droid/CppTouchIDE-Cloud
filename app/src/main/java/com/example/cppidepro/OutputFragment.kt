package com.example.cppidepro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.cppidepro.databinding.FragmentOutputBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OutputFragment : Fragment() {
    private var _binding: FragmentOutputBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS).build()
    private var currentCall: Call? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOutputBinding.inflate(inflater, container, false)
        return binding.root
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

        viewModel.runEvent.observe(viewLifecycleOwner) { shouldRun ->
            if (shouldRun) {
                runCode()
                viewModel.onRunHandled() 
            }
        }
    }

    private fun runCode() {
        val code = viewModel.code.value.orEmpty()
        if (code.isBlank()) { 
            viewModel.setOutput("Nothing to run. The Code tab is empty.")
            return 
        }

        // GET THE STANDARD INPUT FROM THE USER
        val stdinInput = binding.etStdin.text.toString()

        val payload = JSONObject().apply {
            put("code", code)
            put("compiler", "gcc-head")
            put("options", "warning,c++17")
            // SEND THE INPUT TO WANDBOX
            if (stdinInput.isNotEmpty()) {
                put("stdin", stdinInput)
            }
        }
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://wandbox.org/api/compile.json")
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        viewModel.setRunning(true)
        viewModel.setOutput("Compiling and running…\n\n")

        currentCall?.cancel()
        val call = client.newCall(request)
        currentCall = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModel.postRunning(false)
                if (!call.isCanceled()) viewModel.postOutput("Network error: ${e.message ?: e.javaClass.simpleName}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = try { it.body?.string().orEmpty() } catch (e: IOException) { "" }
                    val message = try { formatResponse(it.code, raw) } catch (e: Exception) { "Could not parse response:\n${raw.take(1500)}" }
                    viewModel.postOutput(message)
                    viewModel.postRunning(false)
                }
            }
        })
    }

    private fun formatResponse(httpCode: Int, raw: String): String {
        if (httpCode !in 200..299) return "Server returned HTTP $httpCode\n$raw"
        val root = JSONObject(raw)
        val sb = StringBuilder()
        
        val compilerError = root.optString("compiler_error", "")
        if (compilerError.isNotBlank()) return "── Compilation Error ──\n$compilerError"

        val programOutput = root.optString("program_output", "")
        val programError = root.optString("program_error", "")

        if (programOutput.isNotBlank()) sb.append(programOutput)
        if (programError.isNotBlank()) {
            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
            sb.append("── Runtime Error ──\n").append(programError)
        }
        if (sb.isBlank()) sb.append("Program executed, but printed nothing.")
        return sb.toString()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
    override fun onDestroy() { super.onDestroy(); currentCall?.cancel() }
}

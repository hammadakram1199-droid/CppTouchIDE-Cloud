package com.example.cppidepro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.cppidepro.databinding.FragmentHandwritingBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class HandwritingFragment : Fragment() {
    private var _binding: FragmentHandwritingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private var recognizer: DigitalInkRecognizer? = null
    private var modelReady = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS).build()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHandwritingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.btnClear.setOnClickListener { 
            binding.handwritingView.clear()
            binding.etHandwrittenCode.text.clear()
        }
        
        binding.btnRecognize.setOnClickListener { recognizeInk() }
        binding.btnRunHandwriting.setOnClickListener { runHandwrittenCode() }
        
        if (recognizer == null) setupRecognizer()
    }

    private fun setupRecognizer() {
        try {
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US") ?: return
            val model = DigitalInkRecognitionModel.builder(identifier).build()
            recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
            
            val manager = RemoteModelManager.getInstance()
            manager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
                if (downloaded) { 
                    modelReady = true
                    binding.tvStatus.text = "Ready to draw."
                } else {
                    binding.tvStatus.text = "Downloading handwriting model (needs internet)..."
                    manager.download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener { 
                            modelReady = true
                            binding.tvStatus.text = "Ready to draw."
                        }
                }
            }
        } catch (t: Throwable) {
            binding.tvStatus.text = "Handwriting unavailable."
        }
    }

    private fun recognizeInk() {
        val rec = recognizer
        if (rec == null) { showToast("Recognizer not ready."); return }
        if (!binding.handwritingView.hasInk()) { showToast("Draw something first!"); return }
        if (!modelReady) { showToast("Model is downloading. Please wait."); return }
        
        val inks = binding.handwritingView.buildInkPerLine()
        val lines = ArrayList<String>()
        binding.btnRecognize.isEnabled = false
        binding.tvStatus.text = "Recognizing..."
        recognizeLine(rec, inks, 0, lines)
    }

    private fun recognizeLine(rec: DigitalInkRecognizer, inks: List<Ink>, index: Int, out: MutableList<String>) {
        if (index >= inks.size) { 
            finishRecognition(out)
            return 
        }
        rec.recognize(inks[index]).addOnSuccessListener { result ->
            out.add(result.candidates.firstOrNull()?.text.orEmpty())
            recognizeLine(rec, inks, index + 1, out)
        }.addOnFailureListener { 
            _binding?.btnRecognize?.isEnabled = true
            _binding?.tvStatus?.text = "Recognition failed."
        }
    }

    private fun finishRecognition(lines: List<String>) {
        val text = lines.filter { it.isNotBlank() }.joinToString("\n")
        _binding?.btnRecognize?.isEnabled = true
        _binding?.tvStatus?.text = "Ready to draw."
        
        if (text.isNotBlank()) {
            val currentText = binding.etHandwrittenCode.text.toString()
            val separator = if (currentText.isEmpty() || currentText.endsWith("\n")) "" else "\n"
            binding.etHandwrittenCode.append(separator + text + "\n")
            binding.handwritingView.clear()
        }
    }

    private fun runHandwrittenCode() {
        val code = binding.etHandwrittenCode.text.toString()
        if (code.isBlank()) {
            showToast("Editor is empty.")
            return
        }

        val payload = JSONObject().apply {
            put("code", code)
            put("compiler", "gcc-head")
            put("options", "warning,c++17")
        }
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("https://wandbox.org/api/compile.json")
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        binding.btnRunHandwriting.isEnabled = false
        binding.tvStatus.text = "Running code..."
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    binding.btnRunHandwriting.isEnabled = true
                    binding.tvStatus.text = "Ready."
                    showOutputDialog("Network Error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: ""
                requireActivity().runOnUiThread {
                    binding.btnRunHandwriting.isEnabled = true
                    binding.tvStatus.text = "Ready."
                    showOutputDialog(parseWandbox(raw))
                }
            }
        })
    }

    private fun parseWandbox(raw: String): String {
        return try {
            val root = JSONObject(raw)
            val sb = StringBuilder()
            
            val compilerError = root.optString("compiler_error", "")
            if (compilerError.isNotBlank()) {
                return "── Compilation Error ──\n$compilerError"
            }

            val programOutput = root.optString("program_output", "")
            val programError = root.optString("program_error", "")

            if (programOutput.isNotBlank()) sb.append(programOutput)
            if (programError.isNotBlank()) {
                if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                sb.append("── Runtime Error ──\n").append(programError)
            }
            if (sb.isBlank()) "Program executed, but printed nothing."
            else sb.toString()
        } catch (e: Exception) {
            "Error parsing response."
        }
    }

    private fun showOutputDialog(output: String) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Handwritten Code Output")
            .setMessage(output)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
    override fun onDestroy() { super.onDestroy(); try { recognizer?.close() } catch (e: Throwable) {}; recognizer = null }
}

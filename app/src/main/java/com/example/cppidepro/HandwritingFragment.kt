package com.example.cppidepro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.cppidepro.databinding.FragmentHandwritingBinding
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink

class HandwritingFragment : Fragment() {
    private var _binding: FragmentHandwritingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private var recognizer: DigitalInkRecognizer? = null
    private var modelReady = false
    private var isPenMode = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHandwritingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set the default C++ code in the Text Editor
        binding.etHandwrittenCode.setText(MainViewModel.DEFAULT_CODE)

        binding.btnClear.setOnClickListener { 
            binding.handwritingView.clear()
            binding.etHandwrittenCode.setText(MainViewModel.DEFAULT_CODE)
        }
        
        binding.btnRecognize.setOnClickListener { recognizeInk() }
        
        binding.btnToggleMode.setOnClickListener {
            isPenMode = !isPenMode
            if (isPenMode) {
                binding.handwritingScroll.visibility = View.VISIBLE
                binding.etHandwrittenCode.visibility = View.GONE
                binding.btnToggleMode.text = "Switch to Text"
                binding.tvStatus.text = "Mode: Pen (Draw Code)"
                binding.btnRecognize.visibility = View.VISIBLE
            } else {
                binding.handwritingScroll.visibility = View.GONE
                binding.etHandwrittenCode.visibility = View.VISIBLE
                binding.btnToggleMode.text = "Switch to Pen"
                binding.tvStatus.text = "Mode: Text (Type Code)"
                binding.btnRecognize.visibility = View.GONE
            }
        }

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
                    binding.tvStatus.text = "Downloading model (needs internet)..."
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
        if (!binding.handwritingView.hasInk()) { showToast("Write something first!"); return }
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
            // Append to the text editor
            val currentText = binding.etHandwrittenCode.text.toString()
            val separator = if (currentText.isEmpty() || currentText.endsWith("\n")) "" else "\n"
            binding.etHandwrittenCode.append(separator + text + "\n")
            
            // Also update the global ViewModel so it goes to the Code tab
            viewModel.appendCode(text)
            binding.handwritingView.clear()
            
            // Switch to text mode so user can see the result immediately
            binding.btnToggleMode.performClick()
            showToast("Recognized and added to text!")
        } else {
            showToast("Could not recognize. Write clearer.")
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
    override fun onDestroy() { super.onDestroy(); try { recognizer?.close() } catch (e: Throwable) {}; recognizer = null }
}

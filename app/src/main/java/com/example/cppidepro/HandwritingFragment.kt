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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHandwritingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Toggle Scroll Mode (Free swipe)
        binding.btnToggleMode.setOnClickListener {
            binding.handwritingView.isScrollMode = !binding.handwritingView.isScrollMode
            if (binding.handwritingView.isScrollMode) {
                binding.btnToggleMode.setImageResource(android.R.drawable.ic_menu_sort_by_size)
                Toast.makeText(context, "Scroll Mode (Swipe to move)", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnToggleMode.setImageResource(android.R.drawable.ic_menu_edit)
                Toast.makeText(context, "Draw Mode", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnUndo.setOnClickListener { binding.handwritingView.undo() }
        binding.btnRedo.setOnClickListener { binding.handwritingView.redo() }
        binding.btnClear.setOnClickListener { binding.handwritingView.clear() }

        binding.btnScrollDown.setOnClickListener { binding.handwritingView.scrollByLines(12) }
        binding.btnScrollUp.setOnClickListener { binding.handwritingView.scrollByLines(-12) }

        binding.btnRecognize.setOnClickListener { recognizeInk() }

        setupRecognizer()
    }

    private fun setupRecognizer() {
        try {
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US") ?: return
            val model = DigitalInkRecognitionModel.builder(identifier).build()
            recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
            
            val manager = RemoteModelManager.getInstance()
            manager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
                if (downloaded) { modelReady = true; binding.btnRecognize.isEnabled = true }
                else {
                    binding.btnRecognize.text = "Downloading..."
                    binding.btnRecognize.isEnabled = false
                    manager.download(model, DownloadConditions.Builder().build()).addOnSuccessListener { 
                        modelReady = true
                        binding.btnRecognize.isEnabled = true
                        binding.btnRecognize.text = "Recognize Code"
                    }
                }
            }
        } catch (t: Throwable) {
            binding.btnRecognize.isEnabled = false
        }
    }

    private fun recognizeInk() {
        val rec = recognizer ?: return
        if (!binding.handwritingView.hasInk()) { 
            Toast.makeText(context, "Draw something first!", Toast.LENGTH_SHORT).show()
            return 
        }
        
        val inks = binding.handwritingView.buildInkPerLine()
        val lines = ArrayList<String>()
        binding.btnRecognize.isEnabled = false
        binding.btnRecognize.text = "Processing..."
        
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
            _binding?.btnRecognize?.text = "Recognize Code"
        }
    }

    private fun finishRecognition(lines: List<String>) {
        val text = lines.filter { it.isNotBlank() }.joinToString("\n")
        _binding?.btnRecognize?.isEnabled = true
        _binding?.btnRecognize?.text = "Recognize Code"
        
        if (text.isNotBlank()) {
            // Append to ViewModel
            viewModel.appendCode(text)
            // Clear Canvas
            binding.handwritingView.clear()
            
            // Automatically jump to Code Tab
            viewModel.navigateTo(MainViewModel.TAB_CODE)
            Toast.makeText(context, "Successfully recognized!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Could not read. Try again.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
    override fun onDestroy() { super.onDestroy(); try { recognizer?.close() } catch (e: Throwable) {}; recognizer = null }
}

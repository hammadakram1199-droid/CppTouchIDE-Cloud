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
    private var isScrollMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            isPenMode = savedInstanceState.getBoolean("isPenMode", true)
            isScrollMode = savedInstanceState.getBoolean("isScrollMode", false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHandwritingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.etHandwrittenCode.setText(MainViewModel.DEFAULT_CODE)

        // Restore UI
        updateUiForMode()

        binding.btnClear.setOnClickListener { 
            binding.handwritingView.clear()
            binding.etHandwrittenCode.setText(MainViewModel.DEFAULT_CODE)
        }
        
        binding.btnRecognize.setOnClickListener { recognizeInk() }
        
        binding.btnToggleMode.setOnClickListener {
            isPenMode = !isPenMode
            updateUiForMode()
        }

        binding.btnScrollMode.setOnClickListener {
            if (!isPenMode) return@setOnClickListener
            isScrollMode = !isScrollMode
            binding.handwritingView.isScrollMode = isScrollMode
            updateUiForMode()
        }

        // --- NEW SCROLL BUTTONS ---
        // Scrolls by exactly 12 lines (12 * 42dp line height = ~500dp)
        val scrollAmount = (12 * 42 * resources.displayMetrics.density).toInt()

        binding.btnScrollDown.setOnClickListener {
            binding.handwritingScroll.smoothScrollBy(0, scrollAmount)
        }

        binding.btnScrollUp.setOnClickListener {
            binding.handwritingScroll.smoothScrollBy(0, -scrollAmount)
        }

        if (recognizer == null) setupRecognizer()
    }

    private fun updateUiForMode() {
        if (isPenMode) {
            binding.handwritingScroll.visibility = View.VISIBLE
            binding.etHandwrittenCode.visibility = View.GONE
            binding.btnToggleMode.text = "Switch to Text"
            binding.btnRecognize.visibility = View.VISIBLE
            
            if (isScrollMode) {
                binding.btnScrollMode.text = "Draw Mode"
                binding.btnScrollMode.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00FF88"))
                binding.tvStatus.text = "Mode: Scroll (Swipe freely)"
            } else {
                binding.btnScrollMode.text = "Scroll Mode"
                binding.btnScrollMode.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFA500"))
                binding.tvStatus.text = "Mode: Pen (Draw Code)"
            }
        } else {
            binding.handwritingScroll.visibility = View.GONE
            binding.etHandwrittenCode.visibility = View.VISIBLE
            binding.btnToggleMode.text = "Switch to Pen"
            binding.btnRecognize.visibility = View.GONE
            binding.tvStatus.text = "Mode: Text Editor"
        }
        binding.handwritingView.isScrollMode = isScrollMode
    }

    private fun setupRecognizer() {
        try {
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US") ?: return
            val model = DigitalInkRecognitionModel.builder(identifier).build()
            recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
            
            val manager = RemoteModelManager.getInstance()
            manager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
                if (downloaded) { modelReady = true; updateUiForMode() }
                else {
                    manager.download(model, DownloadConditions.Builder().build()).addOnSuccessListener { modelReady = true; updateUiForMode() }
                }
            }
        } catch (t: Throwable) { binding.tvStatus.text = "Handwriting unavailable." }
    }

    private fun recognizeInk() {
        val rec = recognizer ?: return
        if (!binding.handwritingView.hasInk()) { Toast.makeText(context, "Write something first!", Toast.LENGTH_SHORT).show(); return }
        if (!modelReady) { Toast.makeText(context, "Model downloading...", Toast.LENGTH_SHORT).show(); return }
        
        val inks = binding.handwritingView.buildInkPerLine()
        val lines = ArrayList<String>()
        binding.btnRecognize.isEnabled = false
        binding.tvStatus.text = "Recognizing..."
        recognizeLine(rec, inks, 0, lines)
    }

    private fun recognizeLine(rec: DigitalInkRecognizer, inks: List<Ink>, index: Int, out: MutableList<String>) {
        if (index >= inks.size) { finishRecognition(out); return }
        rec.recognize(inks[index]).addOnSuccessListener { result ->
            out.add(result.candidates.firstOrNull()?.text.orEmpty())
            recognizeLine(rec, inks, index + 1, out)
        }.addOnFailureListener { _binding?.btnRecognize?.isEnabled = true; updateUiForMode() }
    }

    private fun finishRecognition(lines: List<String>) {
        val text = lines.filter { it.isNotBlank() }.joinToString("\n")
        _binding?.btnRecognize?.isEnabled = true
        updateUiForMode()
        
        if (text.isNotBlank()) {
            val currentText = binding.etHandwrittenCode.text.toString()
            val separator = if (currentText.isEmpty() || currentText.endsWith("\n")) "" else "\n"
            binding.etHandwrittenCode.append(separator + text + "\n")
            viewModel.appendCode(text)
            binding.handwritingView.clear()
            if (isPenMode) { isPenMode = false; updateUiForMode() }
            Toast.makeText(context, "Recognized!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isPenMode", isPenMode)
        outState.putBoolean("isScrollMode", isScrollMode)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
    override fun onDestroy() { super.onDestroy(); try { recognizer?.close() } catch (e: Throwable) {}; recognizer = null }
}

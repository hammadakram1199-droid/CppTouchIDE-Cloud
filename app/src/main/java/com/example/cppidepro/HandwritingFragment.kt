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
            updateUiForMode()
        }

        binding.btnScrollDown.setOnClickListener {
            binding.handwritingView.scrollByLines(12)
        }

        binding.btnScrollUp.setOnClickListener {
            binding.handwritingView.scrollByLines(-12)
        }

        // Auto start downloading the model on load
        setupRecognizer()
    }

    private fun updateUiForMode() {
        if (isPenMode) {
            binding.handwritingView.visibility = View.VISIBLE
            binding.etHandwrittenCode.visibility = View.GONE
            binding.btnToggleMode.text = "Switch to Text"
            
            if (isScrollMode) {
                binding.btnScrollMode.text = "Draw Mode"
                binding.btnScrollMode.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00FF88"))
                binding.tvStatus.text = "Mode: Scroll (Swipe to move)"
            } else {
                binding.btnScrollMode.text = "Scroll Mode"
                binding.btnScrollMode.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFA500"))
                binding.tvStatus.text = "Mode: Pen (Draw Code)"
            }
        } else {
            binding.handwritingView.visibility = View.GONE
            binding.etHandwrittenCode.visibility = View.VISIBLE
            binding.btnToggleMode.text = "Switch to Pen"
            binding.tvStatus.text = "Mode: Text Editor"
        }
        binding.handwritingView.isScrollMode = isScrollMode
        
        // If the model isn't ready yet, disable the Recognize button
        if (!modelReady) {
            binding.btnRecognize.isEnabled = false
        }
    }

    private fun setupRecognizer() {
        try {
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
            if (identifier == null) {
                binding.tvStatus.text = "Language not supported."
                binding.btnRecognize.isEnabled = false
                return
            }
            val model = DigitalInkRecognitionModel.builder(identifier).build()
            recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
            
            val manager = RemoteModelManager.getInstance()
            
            // 1. Check if it's already downloaded
            manager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
                if (downloaded) {
                    modelReady = true
                    binding.btnRecognize.isEnabled = true
                    binding.btnRecognize.text = "Recognize Pen"
                } else {
                    // 2. Download it automatically
                    binding.btnRecognize.text = "Downloading Model..."
                    binding.btnRecognize.isEnabled = false
                    
                    manager.download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener {
                            modelReady = true
                            binding.btnRecognize.isEnabled = true
                            binding.btnRecognize.text = "Recognize Pen"
                            Toast.makeText(context, "Handwriting model ready!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            binding.btnRecognize.text = "Download Failed"
                            binding.tvStatus.text = "Error: ${e.message}"
                        }
                }
            }
        } catch (t: Throwable) {
            binding.tvStatus.text = "Handwriting requires Google Play Services."
            binding.btnRecognize.isEnabled = false
        }
    }

    private fun recognizeInk() {
        val rec = recognizer ?: return
        if (!modelReady) { 
            Toast.makeText(context, "Please wait, model is still downloading...", Toast.LENGTH_SHORT).show()
            return 
        }
        if (!binding.handwritingView.hasInk()) { 
            Toast.makeText(context, "Write something first!", Toast.LENGTH_SHORT).show()
            return 
        }
        
        val inks = binding.handwritingView.buildInkPerLine()
        val lines = ArrayList<String>()
        binding.btnRecognize.isEnabled = false
        binding.btnRecognize.text = "Recognizing..."
        binding.tvStatus.text = "Processing..."
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
            _binding?.btnRecognize?.text = "Recognize Pen"
            updateUiForMode()
            Toast.makeText(context, "Recognition failed. Try writing larger.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishRecognition(lines: List<String>) {
        val text = lines.filter { it.isNotBlank() }.joinToString("\n")
        _binding?.btnRecognize?.isEnabled = true
        _binding?.btnRecognize?.text = "Recognize Pen"
        updateUiForMode()
        
        if (text.isNotBlank()) {
            val currentText = binding.etHandwrittenCode.text.toString()
            val separator = if (currentText.isEmpty() || currentText.endsWith("\n")) "" else "\n"
            binding.etHandwrittenCode.append(separator + text + "\n")
            viewModel.appendCode(text)
            binding.handwritingView.clear()
            
            // Switch to text mode automatically so they can see it
            if (isPenMode) { isPenMode = false; updateUiForMode() }
            Toast.makeText(context, "Recognized: $text", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Could not read that. Try again.", Toast.LENGTH_SHORT).show()
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

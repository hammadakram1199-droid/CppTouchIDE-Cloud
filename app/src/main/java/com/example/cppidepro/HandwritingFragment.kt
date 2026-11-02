package com.example.cppidepro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
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
    private var isUpdatingFromViewModel = false

    // Simple Undo/Redo tracker
    private val undoStack = ArrayList<String>()
    private val redoStack = ArrayList<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHandwritingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 1. Sync Text Editor with Shared ViewModel
        viewModel.code.observe(viewLifecycleOwner) { code ->
            if (binding.etHandwrittenCode.text.toString() != code) {
                isUpdatingFromViewModel = true
                binding.etHandwrittenCode.setText(code)
                isUpdatingFromViewModel = false
            }
        }

        binding.etHandwrittenCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!isUpdatingFromViewModel) undoStack.add(s?.toString() ?: "")
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdatingFromViewModel) {
                    viewModel.setCode(s.toString())
                }
            }
        })

        // 2. Toolbar Buttons
        binding.btnToggleKeyboard.setOnClickListener { togglePenAndKeyboard() }
        
        binding.btnBackspace.setOnClickListener {
            if (!isPenMode) {
                // Send a backspace key event to delete letter by letter
                binding.etHandwrittenCode.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                binding.etHandwrittenCode.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            } else {
                Toast.makeText(context, "Switch to Text Mode to delete letters", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("code", binding.etHandwrittenCode.text.toString()))
            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
        }

        binding.btnPaste.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            val start = binding.etHandwrittenCode.selectionStart
            binding.etHandwrittenCode.text.insert(start, text)
        }

        binding.btnUndo.setOnClickListener {
            if (undoStack.isNotEmpty()) {
                redoStack.add(binding.etHandwrittenCode.text.toString())
                val prev = undoStack.removeAt(undoStack.size - 1)
                isUpdatingFromViewModel = true
                binding.etHandwrittenCode.setText(prev)
                binding.etHandwrittenCode.setSelection(prev.length)
                isUpdatingFromViewModel = false
                viewModel.setCode(prev)
            }
        }

        binding.btnRedo.setOnClickListener {
            if (redoStack.isNotEmpty()) {
                undoStack.add(binding.etHandwrittenCode.text.toString())
                val next = redoStack.removeAt(redoStack.size - 1)
                isUpdatingFromViewModel = true
                binding.etHandwrittenCode.setText(next)
                binding.etHandwrittenCode.setSelection(next.length)
                isUpdatingFromViewModel = false
                viewModel.setCode(next)
            }
        }

        binding.btnClear.setOnClickListener {
            binding.handwritingView.clear()
            isUpdatingFromViewModel = true
            binding.etHandwrittenCode.setText("")
            isUpdatingFromViewModel = false
            viewModel.setCode("")
            Toast.makeText(context, "Cleared", Toast.LENGTH_SHORT).show()
        }

        binding.btnSave.setOnClickListener {
            Toast.makeText(context, "Project Saved!", Toast.LENGTH_SHORT).show()
            // MainActivity handles actual save logic
        }

        // 3. Scroll Buttons
        binding.btnScrollDown.setOnClickListener { binding.handwritingView.scrollByLines(12) }
        binding.btnScrollUp.setOnClickListener { binding.handwritingView.scrollByLines(-12) }

        // 4. Recognize
        binding.btnRecognize.setOnClickListener { recognizeInk() }

        setupRecognizer()
        updateUiMode()
    }

    private fun togglePenAndKeyboard() {
        isPenMode = !isPenMode
        updateUiMode()
    }

    private fun updateUiMode() {
        if (isPenMode) {
            binding.handwritingView.visibility = View.VISIBLE
            binding.etHandwrittenCode.visibility = View.GONE
            binding.btnToggleKeyboard.setImageResource(android.R.drawable.ic_menu_edit) // Pen icon
        } else {
            binding.handwritingView.visibility = View.GONE
            binding.etHandwrittenCode.visibility = View.VISIBLE
            binding.btnToggleKeyboard.setImageResource(android.R.drawable.ic_menu_view) // Keyboard icon
        }
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
                        binding.btnRecognize.text = "Recognize Pen"
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
            _binding?.btnRecognize?.text = "Recognize Pen"
        }
    }

    private fun finishRecognition(lines: List<String>) {
        val text = lines.filter { it.isNotBlank() }.joinToString("\n")
        _binding?.btnRecognize?.isEnabled = true
        _binding?.btnRecognize?.text = "Recognize Pen"
        
        if (text.isNotBlank()) {
            val currentText = binding.etHandwrittenCode.text.toString()
            val separator = if (currentText.isEmpty() || currentText.endsWith("\n")) "" else "\n"
            val newText = currentText + separator + text + "\n"
            
            isUpdatingFromViewModel = true
            binding.etHandwrittenCode.setText(newText)
            isUpdatingFromViewModel = false
            
            viewModel.setCode(newText)
            binding.handwritingView.clear()
            
            // Auto-switch to keyboard mode to see the result
            if (isPenMode) togglePenAndKeyboard()
            Toast.makeText(context, "Recognized!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Could not read. Try again.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
    override fun onDestroy() { super.onDestroy(); try { recognizer?.close() } catch (e: Throwable) {}; recognizer = null }
}

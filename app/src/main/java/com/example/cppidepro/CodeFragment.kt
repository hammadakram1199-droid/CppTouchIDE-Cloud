package com.example.cppidepro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.cppidepro.databinding.FragmentCodeBinding
import com.google.android.material.button.MaterialButton

class CodeFragment : Fragment() {
    private var _binding: FragmentCodeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private var updatingFromModel = false
    private var lastLineCount = -1
    private lateinit var soundManager: SoundManager
    private var lastTextLength = 0
    
    // Undo / Redo Logic
    private val undoStack = ArrayList<String>()
    private val redoStack = ArrayList<String>()
    private var isUndoRedoAction = false

    // Syntax Highlighting
    private val highlighter = SyntaxHighlighter()
    private val highlightHandler = Handler(Looper.getMainLooper())
    private var highlightRunnable: Runnable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        soundManager = SoundManager()
        
        binding.etCode.setHorizontallyScrolling(true)
        binding.etCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!updatingFromModel && !isUndoRedoAction && s != null) {
                    undoStack.add(s.toString())
                    redoStack.clear()
                }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                updateLineNumbers(text)
                
                if (!updatingFromModel) {
                    viewModel.setCode(text)
                    if (text.length > lastTextLength) soundManager.playTypeSound()
                    
                    if (s != null) {
                        highlightRunnable?.let { highlightHandler.removeCallbacks(it) }
                        highlightRunnable = Runnable {
                            val selStart = binding.etCode.selectionStart
                            val selEnd = binding.etCode.selectionEnd
                            highlighter.highlight(s)
                            if (selStart >= 0 && selEnd >= 0) binding.etCode.setSelection(selStart, selEnd)
                        }
                        highlightHandler.postDelayed(highlightRunnable!!, 100)
                    }
                }
                lastTextLength = text.length
                isUndoRedoAction = false
            }
        })

        viewModel.code.observe(viewLifecycleOwner) { code ->
            if (binding.etCode.text.toString() != code) {
                updatingFromModel = true
                binding.etCode.setText(code)
                binding.etCode.setSelection(binding.etCode.text.length)
                lastTextLength = code.length
                updatingFromModel = false
                binding.etCode.text?.let { highlighter.highlight(it) }
            }
        }

        // Toolbar Buttons
        binding.btnUndo.setOnClickListener { performUndo() }
        binding.btnRedo.setOnClickListener { performRedo() }
        binding.btnDeleteAll.setOnClickListener { 
            binding.etCode.setText("") 
            viewModel.setCode("")
        }
        binding.btnRun.setOnClickListener {
            viewModel.triggerRun() 
            viewModel.navigateTo(MainViewModel.TAB_OUTPUT) 
        }

        // NEW: Export to Cxxdroid Button
        binding.btnCxxdroid.setOnClickListener {
            exportToCxxdroid()
        }

        buildSymbolBar()
    }

    private fun exportToCxxdroid() {
        val code = binding.etCode.text.toString()
        if (code.isBlank()) {
            Toast.makeText(context, "Code is empty!", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Copy code to clipboard
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("C++ Code", code)
        clipboard.setPrimaryClip(clip)

        // 2. Launch Cxxdroid
        val launchIntent = requireContext().packageManager.getLaunchIntentForPackage("ru.iiec.cxxdroid")
        if (launchIntent != null) {
            startActivity(launchIntent)
            Toast.makeText(context, "Code Copied! Paste it in Cxxdroid and press Run.", Toast.LENGTH_LONG).show()
        } else {
            // Cxxdroid not installed, show Play Store
            Toast.makeText(context, "Cxxdroid is not installed. Redirecting to Play Store...", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=ru.iiec.cxxdroid")))
            } catch (e: Exception) {
                Toast.makeText(context, "Play Store not found. Code is in your clipboard.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val currentText = binding.etCode.text.toString()
            redoStack.add(currentText)
            
            val prevText = undoStack.removeAt(undoStack.size - 1)
            isUndoRedoAction = true
            updatingFromModel = true
            binding.etCode.setText(prevText)
            binding.etCode.setSelection(prevText.length)
            viewModel.setCode(prevText)
            updatingFromModel = false
        }
    }

    private fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val currentText = binding.etCode.text.toString()
            undoStack.add(currentText)
            
            val nextText = redoStack.removeAt(redoStack.size - 1)
            isUndoRedoAction = true
            updatingFromModel = true
            binding.etCode.setText(nextText)
            binding.etCode.setSelection(nextText.length)
            viewModel.setCode(nextText)
            updatingFromModel = false
        }
    }

    private fun updateLineNumbers(text: String) {
        val count = text.count { it == '\n' } + 1
        if (count == lastLineCount) return
        lastLineCount = count
        binding.tvLineNumbers.text = (1..count).joinToString("\n")
    }

    private fun buildSymbolBar() {
        val symbols = resources.getStringArray(R.array.cpp_symbols)
        binding.symbolBar.removeAllViews()
        for (symbol in symbols) {
            val button = layoutInflater.inflate(R.layout.item_symbol_button, binding.symbolBar, false) as MaterialButton
            button.text = symbol
            button.setOnClickListener { insertAtCursor(symbol) }
            binding.symbolBar.addView(button)
        }
    }

    private fun insertAtCursor(symbol: String) {
        val editor = binding.etCode
        val text = editor.text
        var start = editor.selectionStart
        var end = editor.selectionEnd
        if (start < 0 || end < 0) { start = text.length; end = text.length }
        val lo = minOf(start, end)
        val hi = maxOf(start, end)
        text.replace(lo, hi, symbol)
        editor.setSelection(lo + symbol.length)
        if (!editor.hasFocus()) editor.requestFocus()
    }

    override fun onDestroyView() { 
        super.onDestroyView()
        highlightRunnable?.let { highlightHandler.removeCallbacks(it) }
        soundManager.release()
        _binding = null
        lastLineCount = -1
        lastTextLength = 0
    }
}

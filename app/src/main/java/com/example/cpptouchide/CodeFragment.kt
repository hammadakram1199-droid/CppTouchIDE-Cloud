package com.example.cpptouchide

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

class CodeFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var editor: EditText
    private lateinit var lineNumbers: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_code, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        editor = view.findViewById(R.id.codeEditor)
        lineNumbers = view.findViewById(R.id.lineNumbers)
        val symbolBar = view.findViewById<LinearLayout>(R.id.symbolBar)

        // Setup Symbol Bar
        val symbols = listOf("TAB", "(", ")", "{", "}", ";", "#", "<", ">", "=", "\"", "+", "-", "*", "/")
        for (sym in symbols) {
            val btn = Button(requireContext()).apply {
                text = sym
                textSize = 14f
                setPadding(8, 0, 8, 0)
                setOnClickListener {
                    val start = editor.selectionStart
                    val textToInsert = if (sym == "TAB") "    " else sym
                    editor.text.insert(start, textToInsert)
                }
            }
            symbolBar.addView(btn)
        }

        editor.setText(viewModel.currentCode.value)
        updateLineNumbers()

        viewModel.currentCode.observe(viewLifecycleOwner) { newCode ->
            if (editor.text.toString() != newCode) {
                editor.setText(newCode)
                updateLineNumbers()
            }
        }

        editor.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateCode(s.toString())
                updateLineNumbers()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateLineNumbers() {
        val lines = editor.lineCount
        val displayLines = maxOf(lines, 20) // Show at least 20 numbers
        val builder = StringBuilder()
        for (i in 1..displayLines) {
            builder.append(i).append("\n")
        }
        lineNumbers.text = builder.toString()
    }
}

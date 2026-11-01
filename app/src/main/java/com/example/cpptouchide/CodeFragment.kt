package com.example.cpptouchide

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

class CodeFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var editor: EditText
    private lateinit var lineNumbers: TextView
    private var isUpdating = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_code, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        editor = view.findViewById(R.id.codeEditor)
        lineNumbers = view.findViewById(R.id.lineNumbers)

        editor.setText(viewModel.currentCode.value)
        updateLineNumbers()

        viewModel.currentCode.observe(viewLifecycleOwner) { newCode ->
            if (!isUpdating && editor.text.toString() != newCode) {
                isUpdating = true
                editor.setText(newCode)
                editor.setSelection(editor.text.length)
                updateLineNumbers()
                isUpdating = false
            }
        }

        editor.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdating) {
                    viewModel.updateCode(s.toString())
                    updateLineNumbers()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateLineNumbers() {
        val lines = editor.lineCount
        // Force it to render at least 300 lines of numbers
        val displayLines = maxOf(lines, 300) 
        val builder = StringBuilder()
        for (i in 1..displayLines) {
            builder.append(i).append("\n")
        }
        lineNumbers.text = builder.toString()
    }
}

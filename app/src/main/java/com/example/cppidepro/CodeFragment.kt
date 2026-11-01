package com.example.cppidepro

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.etCode.setHorizontallyScrolling(true)
        binding.etCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                updateLineNumbers(text)
                if (!updatingFromModel) viewModel.setCode(text)
            }
        })
        viewModel.code.observe(viewLifecycleOwner) { code ->
            if (binding.etCode.text.toString() != code) {
                updatingFromModel = true
                binding.etCode.setText(code)
                binding.etCode.setSelection(binding.etCode.text.length)
                updatingFromModel = false
            }
        }
        buildSymbolBar()
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

    override fun onDestroyView() { super.onDestroyView(); _binding = null; lastLineCount = -1 }
}

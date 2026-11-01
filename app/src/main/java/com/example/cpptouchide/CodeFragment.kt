package com.example.cpptouchide

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

class CodeFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var editor: EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        editor = EditText(requireContext()).apply {
            setBackgroundColor(0xFF111111.toInt())
            setTextColor(0xFF00FF88.toInt())
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            isVerticalScrollBarEnabled = true
            minLines = 300 // Minimum 300 lines!
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "Write or paste C++ code here..."
        }
        layout.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return layout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        editor.setText(viewModel.currentCode.value ?: "")
        
        editor.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.updateCode(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
}

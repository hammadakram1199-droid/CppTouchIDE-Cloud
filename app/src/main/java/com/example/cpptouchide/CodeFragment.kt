package com.example.cpptouchide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.github.rosemoe.sora.widget.CodeEditor

class CodeFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var editor: CodeEditor
    private var isUpdating = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_code, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        editor = view.findViewById(R.id.editor)
        
        // Font size 15
        editor.setTextSize(15f)
        editor.setText(viewModel.currentCode.value)

        viewModel.currentCode.observe(viewLifecycleOwner) { newCode ->
            if (!isUpdating && editor.text.toString() != newCode) {
                isUpdating = true
                editor.setText(newCode)
                isUpdating = false
            }
        }

        editor.subscribeEvent(io.github.rosemoe.sora.event.ContentChangeEvent::class.java) { event, _ ->
            if (!isUpdating) {
                viewModel.updateCode(editor.text.toString())
            }
        }
    }
}

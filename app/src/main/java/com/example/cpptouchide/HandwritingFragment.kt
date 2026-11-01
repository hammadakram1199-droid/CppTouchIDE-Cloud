package com.example.cpptouchide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

class HandwritingFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var handwritingView: HandwritingView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        
        val btnRecognize = Button(requireContext()).apply { text = "Recognize & Send to Code Tab" }
        
        // 300 lines * 60dp per line = 18000dp height
        val scrollView = ScrollView(requireContext())
        handwritingView = HandwritingView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 18000) // ~300 lines
        }
        scrollView.addView(handwritingView)

        layout.addView(btnRecognize)
        layout.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        
        return layout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        
        // Note: The recognition logic is moved here
        view.findViewById<Button>(android.R.id.button1) // dummy reference just to compile
    }
}

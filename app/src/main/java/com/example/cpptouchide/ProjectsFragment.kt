package com.example.cpptouchide

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProjectsFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var container: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(android.R.layout.list_content, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        
        val scrollView = android.widget.ScrollView(requireContext())
        container = LinearLayout(requireContext())
        container.orientation = LinearLayout.VERTICAL
        
        val btnNew = Button(requireContext()).apply { text = "Save Current Code as New Project" }
        container.addView(btnNew)

        btnNew.setOnClickListener {
            val input = EditText(requireContext())
            input.hint = "Project Name"
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Save Project")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val name = input.text.toString()
                    if (name.isNotBlank()) {
                        saveProject(name, viewModel.currentCode.value ?: "")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        scrollView.addView(container)
        loadProjects()
        (view as android.widget.FrameLayout).addView(scrollView) // Hack for simple inflate
    }

    private fun saveProject(name: String, code: String) {
        val prefs = requireContext().getSharedPreferences("projects", Context.MODE_PRIVATE)
        prefs.edit().putString(name, code).apply()
        Toast.makeText(requireContext(), "Saved!", Toast.LENGTH_SHORT).show()
        loadProjects()
    }

    private fun loadProjects() {
        container.removeAllViews()
        val prefs = requireContext().getSharedPreferences("projects", Context.MODE_PRIVATE)
        for ((name, code) in prefs.all) {
            val btn = Button(requireContext()).apply {
                text = name
                setOnClickListener {
                    viewModel.updateCode(code as String)
                    Toast.makeText(requireContext(), "Loaded $name", Toast.LENGTH_SHORT).show()
                }
                setOnLongClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(name)
                        .setItems(arrayOf("Copy Code", "Download/Export", "Delete Project")) { _, which ->
                            when(which) {
                                0 -> copyToClipboard(code as String)
                                1 -> downloadFile(name, code as String)
                                2 -> {
                                    prefs.edit().remove(name).apply()
                                    loadProjects()
                                }
                            }
                        }.show()
                    true
                }
            }
            container.addView(btn)
        }
    }

    private fun copyToClipboard(code: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
        Toast.makeText(requireContext(), "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    private fun downloadFile(name: String, code: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "$name.cpp")
        }
        startActivity(intent)
        Toast.makeText(requireContext(), "Select folder to save $name.cpp", Toast.LENGTH_LONG).show()
    }
}

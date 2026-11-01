package com.example.cpptouchide

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ProjectsFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ProjectsAdapter

    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/x-c++src")) { uri: Uri? ->
        uri?.let {
            val code = viewModel.currentCode.value ?: ""
            requireContext().contentResolver.openOutputStream(it)?.use { stream ->
                stream.write(code.toByteArray())
                Toast.makeText(context, "File saved successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_projects, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val fab = view.findViewById<FloatingActionButton>(R.id.fabAdd)

        adapter = ProjectsAdapter(
            onLoad = { project -> viewModel.updateCode(project.code) },
            onLongClick = { project -> showOptions(project) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadProjects()

        fab.setOnClickListener { showSaveDialog() }
    }

    private fun showSaveDialog() {
        val input = EditText(requireContext()).apply { hint = "Project Name" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Save Project")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    val prefs = requireContext().getSharedPreferences("projects", Context.MODE_PRIVATE)
                    prefs.edit().putString(name, viewModel.currentCode.value).apply()
                    loadProjects()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showOptions(project: Project) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(project.name)
            .setItems(arrayOf("Copy Code", "Download as .cpp", "Delete")) { _, which ->
                when (which) {
                    0 -> {
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("code", project.code))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        viewModel.updateCode(project.code)
                        createFileLauncher.launch("${project.name}.cpp")
                    }
                    2 -> {
                        val prefs = requireContext().getSharedPreferences("projects", Context.MODE_PRIVATE)
                        prefs.edit().remove(project.name).apply()
                        loadProjects()
                    }
                }
            }.show()
    }

    private fun loadProjects() {
        val prefs = requireContext().getSharedPreferences("projects", Context.MODE_PRIVATE)
        val projects = prefs.all.map { Project(it.key, it.value as String) }.sortedBy { it.name }
        adapter.submitList(projects)
    }
}

data class Project(val name: String, val code: String)

class ProjectsAdapter(
    private val onLoad: (Project) -> Unit,
    private val onLongClick: (Project) -> Unit
) : RecyclerView.Adapter<ProjectsAdapter.ViewHolder>() {
    private var projects = listOf<Project>()

    fun submitList(newProjects: List<Project>) {
        projects = newProjects
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val project = projects[position]
        holder.name.text = project.name
        holder.snippet.text = project.code.take(100).replace("\n", " ")
        holder.itemView.setOnClickListener { onLoad(project) }
        holder.itemView.setOnLongClickListener { onLongClick(project); true }
    }

    override fun getItemCount() = projects.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: android.widget.TextView = view.findViewById(R.id.tvProjectName)
        val snippet: android.widget.TextView = view.findViewById(R.id.tvSnippet)
    }
}

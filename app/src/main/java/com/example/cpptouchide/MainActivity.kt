package com.example.cpptouchide

import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        
        toolbar.inflateMenu(R.menu.main_menu)

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> {
                    showSaveDialog()
                    true
                }
                R.id.action_load -> {
                    showLoadDialog()
                    true
                }
                else -> false
            }
        }

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> HandwritingFragment()
                    1 -> CodeFragment()
                    2 -> OutputFragment()
                    else -> CodeFragment()
                }
            }
        }

        val tabTitles = arrayOf("Handwriting", "Code", "Output")
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    private fun showSaveDialog() {
        val input = EditText(this).apply { hint = "Project Name" }
        MaterialAlertDialogBuilder(this)
            .setTitle("Save Project")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    val prefs = getSharedPreferences("projects", Context.MODE_PRIVATE)
                    prefs.edit().putString(name, viewModel.currentCode.value).apply()
                    Toast.makeText(this, "Saved $name!", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showLoadDialog() {
        val prefs = getSharedPreferences("projects", Context.MODE_PRIVATE)
        val projects = prefs.all.keys.toTypedArray()
        
        if (projects.isEmpty()) {
            Toast.makeText(this, "No saved projects found.", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Load Project")
            .setItems(projects) { _, which ->
                val name = projects[which]
                val code = prefs.getString(name, "")
                viewModel.updateCode(code ?: "")
                Toast.makeText(this, "Loaded $name!", Toast.LENGTH_SHORT).show()
            }.show()
    }
}

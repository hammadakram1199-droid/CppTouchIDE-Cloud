package com.example.cpptouchide

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var btnHandwriting: MaterialButton
    private lateinit var btnCode: MaterialButton
    private lateinit var btnOutput: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        viewPager = findViewById(R.id.viewPager)
        btnHandwriting = findViewById(R.id.btnTabHandwriting)
        btnCode = findViewById(R.id.btnTabCode)
        btnOutput = findViewById(R.id.btnTabOutput)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        
        viewPager.isUserInputEnabled = false 

        toolbar.inflateMenu(R.menu.main_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> { showSaveDialog(); true }
                R.id.action_load -> { showLoadDialog(); true }
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

        btnHandwriting.setOnClickListener { selectTab(0) }
        btnCode.setOnClickListener { selectTab(1) }
        btnOutput.setOnClickListener { selectTab(2) }

        selectTab(0)
    }

    private fun selectTab(index: Int) {
        viewPager.setCurrentItem(index, false)
        
        btnHandwriting.setBackgroundColor(Color.parseColor("#333333"))
        btnHandwriting.setIconTint(android.content.res.ColorStateList.valueOf(Color.WHITE))
        btnCode.setBackgroundColor(Color.parseColor("#333333"))
        btnCode.setTextColor(Color.WHITE)
        btnOutput.setBackgroundColor(Color.parseColor("#333333"))
        btnOutput.setTextColor(Color.WHITE)

        when (index) {
            0 -> { 
                btnHandwriting.setBackgroundColor(Color.parseColor("#00FF88")) 
                btnHandwriting.setIconTint(android.content.res.ColorStateList.valueOf(Color.BLACK))
            }
            1 -> { 
                btnCode.setBackgroundColor(Color.parseColor("#00FF88")) 
                btnCode.setTextColor(Color.BLACK) 
            }
            2 -> { 
                btnOutput.setBackgroundColor(Color.parseColor("#00FF88")) 
                btnOutput.setTextColor(Color.BLACK) 
            }
        }
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
            Toast.makeText(this, "No saved projects.", Toast.LENGTH_SHORT).show()
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

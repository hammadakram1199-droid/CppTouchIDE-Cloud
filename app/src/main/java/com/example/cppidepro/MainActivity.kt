package com.example.cppidepro

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.activity.viewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.cppidepro.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var tabButtons: List<MaterialButton>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupPager()
        setupTabs()
        setupMenu()
        viewModel.navigateTo.observe(this) { index ->
            if (index != null) { binding.pager.setCurrentItem(index, false); viewModel.onNavigationHandled() }
        }
    }

    private fun setupPager() {
        binding.pager.adapter = PagerAdapter(this)
        binding.pager.isUserInputEnabled = false
        binding.pager.offscreenPageLimit = 2
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { highlightTab(position); hideKeyboard() }
        })
    }

    private fun setupTabs() {
        tabButtons = listOf(binding.btnTabHandwriting, binding.btnTabCode, binding.btnTabOutput)
        tabButtons.forEachIndexed { index, button ->
            button.setOnClickListener { binding.pager.setCurrentItem(index, false) }
        }
        highlightTab(binding.pager.currentItem)
    }

    private fun highlightTab(selected: Int) {
        val green = ContextCompat.getColor(this, R.color.accent)
        val gray = ContextCompat.getColor(this, R.color.tab_inactive)
        val black = ContextCompat.getColor(this, R.color.black)
        val light = ContextCompat.getColor(this, R.color.editor_text)
        tabButtons.forEachIndexed { index, button ->
            val active = index == selected
            button.backgroundTintList = ColorStateList.valueOf(if (active) green else gray)
            button.setTextColor(if (active) black else light)
        }
    }

    private fun setupMenu() {
        binding.toolbar.inflateMenu(R.menu.main_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> { saveProject(); true }
                R.id.action_load -> { confirmLoadProject(); true }
                else -> false
            }
        }
    }

    private fun saveProject() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_PROJECT, viewModel.code.value.orEmpty()).apply()
        Toast.makeText(this, "Project saved", Toast.LENGTH_SHORT).show()
    }

    private fun confirmLoadProject() {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PROJECT, null)
        if (saved == null) { Toast.makeText(this, "No saved project found", Toast.LENGTH_SHORT).show(); return }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_load).setMessage("Replace the current code with the saved project?")
            .setPositiveButton("Load") { _, _ ->
                viewModel.setCode(saved)
                binding.pager.setCurrentItem(MainViewModel.TAB_CODE, false)
                Toast.makeText(this, "Project loaded", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun hideKeyboard() {
        val view = currentFocus ?: return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private class PagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment = when (position) {
            MainViewModel.TAB_HANDWRITING -> HandwritingFragment()
            MainViewModel.TAB_CODE -> CodeFragment()
            else -> OutputFragment()
        }
    }

    companion object {
        private const val PREFS = "cpp_ide_prefs"
        private const val KEY_PROJECT = "saved_project"
    }
}

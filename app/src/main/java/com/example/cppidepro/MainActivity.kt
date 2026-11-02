package com.example.cppidepro

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.cppidepro.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton

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
        binding.pager.offscreenPageLimit = 2 // Keeps handwriting drawings alive
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
                R.id.action_export_pdf -> { exportAsPdf(); true }
                R.id.action_export_txt -> { exportAsTxt(); true }
                else -> false
            }
        }
    }

    private fun exportAsPdf() {
        val code = viewModel.code.value.orEmpty()
        if (code.isBlank()) {
            Toast.makeText(this, "No code to export!", Toast.LENGTH_SHORT).show()
            return
        }
        
        val webView = WebView(this)
        val html = "<html><body style='background-color:#1E1E1E; color:#D4D4D4; font-family:monospace; padding:20px;'><pre>$code</pre></body></html>"
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "CppIdeProject"
                printManager.print(jobName, view.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
            }
        }
    }

    private fun exportAsTxt() {
        val code = viewModel.code.value.orEmpty()
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, code)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Export Code"))
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
}

package com.example.cpptouchide

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import io.github.rosemoe.sora.langs.cpp.CppLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var editor: CodeEditor
    private lateinit var tvOutput: TextView
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        editor = findViewById(R.id.editor)
        tvOutput = findViewById(R.id.tvOutput)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        // Setup Toolbar
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_sort_by_size)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        toolbar.inflateMenu(R.menu.main_toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_run -> {
                    runCode(editor.text.toString())
                    true
                }
                else -> false
            }
        }

        // Setup Sora Code Editor
        editor.setEditorLanguage(CppLanguage())
        val scheme = EditorColorScheme()
        scheme.setColor(EditorColorScheme.BACKGROUND, 0xFF1E1E1E.toInt())
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, 0xFFD4D4D4.toInt())
        scheme.setColor(EditorColorScheme.LINE_NUMBER, 0xFF858585.toInt())
        editor.setColorScheme(scheme)
        editor.setText("#include <iostream>\n\nint main() {\n    std::cout << \"Hello World!\" << std::endl;\n    return 0;\n}")

        // Setup Navigation Drawer
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_new -> {
                    editor.setText("#include <iostream>\n\nint main() {\n    \n    return 0;\n}")
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_save -> {
                    // We will implement real file saving later
                    Toast.makeText(this, "Project Saved!", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_handwriting -> {
                    Toast.makeText(this, "Handwriting feature coming in next update!", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }
    }

    private fun runCode(code: String) {
        tvOutput.text = "> Compiling...\n"
        val json = JSONObject().apply {
            put("language", "c++")
            put("version", "10.2.0")
            put("files", JSONArray().put(JSONObject().put("content", code)))
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://emkc.org/api/v2/piston/execute")
            .post(requestBody)
            .header("User-Agent", "CppTouchIDE/3.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { tvOutput.text = "> Network Error: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                runOnUiThread {
                    if (responseData != null) {
                        val jsonRes = JSONObject(responseData)
                        val sb = StringBuilder("> Output:\n")
                        
                        jsonRes.optJSONObject("compile")?.let {
                            if (it.optString("stderr").isNotBlank()) sb.append(it.optString("stderr")).append("\n")
                        }
                        jsonRes.optJSONObject("run")?.let {
                            if (it.optString("stdout").isNotBlank()) sb.append(it.optString("stdout"))
                            if (it.optString("stderr").isNotBlank()) sb.append(it.optString("stderr"))
                        }
                        tvOutput.text = sb.toString()
                    }
                }
            }
        })
    }
}

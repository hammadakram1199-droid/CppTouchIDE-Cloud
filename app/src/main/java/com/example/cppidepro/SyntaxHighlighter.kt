package com.example.cppidepro

import android.graphics.Color
import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan

class SyntaxHighlighter {
    // VS Code Dark Theme Colors
    private val colorDefault = Color.parseColor("#D4D4D4")
    private val colorKeyword = Color.parseColor("#569CD6") // Blue
    private val colorType = Color.parseColor("#4EC9B0")    // Teal
    private val colorString = Color.parseColor("#CE9178")  // Orange
    private val colorComment = Color.parseColor("#6A9955") // Green
    private val colorNumber = Color.parseColor("#B5CEA8")  // Light Green
    private val colorPreprocessor = Color.parseColor("#C586C0") // Pink

    // Regex Patterns
    private val keywordRegex = Regex("\\b(using|namespace|int|return|void|if|else|for|while|class|struct|public|private|true|false|cout|cin|endl|std)\\b")
    private val typeRegex = Regex("\\b(string|char|float|double|bool|long|short)\\b")
    private val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"") // Handles escaped quotes
    private val commentRegex = Regex("//.*")
    private val numberRegex = Regex("\\b\\d+\\b")
    private val preprocessorRegex = Regex("^\\s*#.*", RegexOption.MULTILINE)

    fun highlight(text: Editable) {
        // 1. Remove all old spans
        val spans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
        for (span in spans) {
            text.removeSpan(span)
        }

        // 2. Apply new spans
        applyColor(text, keywordRegex, colorKeyword)
        applyColor(text, typeRegex, colorType)
        applyColor(text, stringRegex, colorString)
        applyColor(text, commentRegex, colorComment)
        applyColor(text, numberRegex, colorNumber)
        applyColor(text, preprocessorRegex, colorPreprocessor)
    }

    private fun applyColor(text: Editable, regex: Regex, color: Int) {
        val matches = regex.findAll(text)
        for (match in matches) {
            // Don't color over a comment or string if there is overlap (simple priority)
            text.setSpan(
                ForegroundColorSpan(color),
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}

package com.example.cpptouchide

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    private val _currentCode = MutableLiveData<String>("#include <iostream>\n\nint main() {\n    std::cout << \"Hello, World!\";\n    return 0;\n}")
    val currentCode: LiveData<String> get() = _currentCode

    private val _output = MutableLiveData<String>("\n> Ready to compile...")
    val output: LiveData<String> get() = _output

    fun updateCode(code: String) { _currentCode.value = code }
    fun appendCode(newCode: String) { _currentCode.value = (_currentCode.value ?: "") + newCode }
    fun updateOutput(text: String) { _output.value = text }
}

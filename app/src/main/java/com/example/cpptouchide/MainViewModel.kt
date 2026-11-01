package com.example.cpptouchide

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    private val _currentCode = MutableLiveData<String>()
    val currentCode: LiveData<String> get() = _currentCode

    private val _output = MutableLiveData<String>("Output will appear here...")
    val output: LiveData<String> get() = _output

    fun updateCode(code: String) { _currentCode.value = code }
    fun updateOutput(text: String) { _output.value = text }
}

package com.example.cppidepro

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    private val _code = MutableLiveData(DEFAULT_CODE)
    val code: LiveData<String> = _code
    private val _output = MutableLiveData(DEFAULT_OUTPUT)
    val output: LiveData<String> = _output
    private val _isRunning = MutableLiveData(false)
    val isRunning: LiveData<Boolean> = _isRunning
    private val _navigateTo = MutableLiveData<Int?>()
    val navigateTo: LiveData<Int?> = _navigateTo

    // New Event to trigger run from Code tab
    private val _runEvent = MutableLiveData<Boolean>(false)
    val runEvent: LiveData<Boolean> = _runEvent

    fun setCode(newCode: String) { if (_code.value != newCode) _code.value = newCode }
    
    fun appendCode(text: String) {
        if (text.isBlank()) return
        val current = _code.value.orEmpty()
        val separator = if (current.isEmpty() || current.endsWith("\n")) "" else "\n"
        _code.value = current + separator + text + "\n"
    }
    
    fun setOutput(text: String) { _output.value = text }
    fun setRunning(running: Boolean) { _isRunning.value = running }
    fun postOutput(text: String) = _output.postValue(text)
    fun postRunning(running: Boolean) = _isRunning.postValue(running)
    fun navigateTo(tabIndex: Int) { _navigateTo.value = tabIndex }
    fun onNavigationHandled() { _navigateTo.value = null }

    // Run Events
    fun triggerRun() { _runEvent.value = true }
    fun onRunHandled() { _runEvent.value = false }

    companion object {
        const val TAB_HANDWRITING = 0
        const val TAB_CODE = 1
        const val TAB_OUTPUT = 2
        const val DEFAULT_OUTPUT = "// Press \"Run Code\" to compile and run your program."
        const val DEFAULT_CODE = "#include <iostream>\nusing namespace std;\n\nint main() {\n    cout << \"Hello, World!\" << endl;\n    return 0;\n}\n"
    }
}

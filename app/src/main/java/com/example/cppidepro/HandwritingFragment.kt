package com.example.cppidepro

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.cppidepro.databinding.FragmentHandwritingBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink

class HandwritingFragment : Fragment() {
    private var _binding: FragmentHandwritingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private var recognizer: DigitalInkRecognizer? = null
    private lateinit var soundManager: SoundManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHandwritingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        soundManager = SoundManager()
        binding.handwritingView.soundManager = soundManager

        // Sync Sound Icon with Global State
        updateSoundIcon()

        binding.btnToggleMode.setOnClickListener {
            binding.handwritingView.isScrollMode = !binding.handwritingView.isScrollMode
            if (binding.handwritingView.isScrollMode) {
                binding.btnToggleMode.setImageResource(android.R.drawable.ic_menu_sort_by_size)
                binding.handwritingView.isEraseMode = false
                Toast.makeText(context, "Scroll Mode", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnToggleMode.setImageResource(android.R.drawable.ic_menu_edit)
                Toast.makeText(context, "Draw Mode", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSoundToggle.setOnClickListener {
            SoundManager.isMuted = !SoundManager.isMuted
            updateSoundIcon()
            if (SoundManager.isMuted) {
                Toast.makeText(context, "Sounds OFF", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Sounds ON", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPenSettings.setOnClickListener { showPenSettingsDialog() }

        binding.btnEraser.setOnClickListener {
            binding.handwritingView.isEraseMode = !binding.handwritingView.isEraseMode
            if (binding.handwritingView.isEraseMode) {
                binding.btnEraser.setBackgroundColor(Color.parseColor("#FF5252"))
                Toast.makeText(context, "Eraser ON", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnEraser.setBackgroundColor(Color.TRANSPARENT)
                Toast.makeText(context, "Eraser OFF", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnUndo.setOnClickListener { binding.handwritingView.undo() }
        binding.btnRedo.setOnClickListener { binding.handwritingView.redo() }
        binding.btnClear.setOnClickListener { binding.handwritingView.clear() }
        binding.btnSaveProject.setOnClickListener { showSaveDialog() }

        binding.btnScrollDown.setOnClickListener { binding.handwritingView.scrollByLines(12) }
        binding.btnScrollUp.setOnClickListener { binding.handwritingView.scrollByLines(-12) }

        binding.btnRecognize.setOnClickListener { recognizeInk() }

        setupRecognizer()
    }

    private fun updateSoundIcon() {
        if (SoundManager.isMuted) {
            binding.btnSoundToggle.setImageResource(android.R.drawable.ic_lock_silent_mode)
            binding.btnSoundToggle.setColorFilter(Color.parseColor("#FF5252"))
        } else {
            binding.btnSoundToggle.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            binding.btnSoundToggle.setColorFilter(Color.WHITE)
        }
    }

    private fun showPenSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pen_settings, null)
        val seekBarThickness = dialogView.findViewById<SeekBar>(R.id.seekBarThickness)
        val seekBarEraser = dialogView.findViewById<SeekBar>(R.id.seekBarEraserSize)
        
        seekBarThickness.progress = binding.handwritingView.currentPenThickness.toInt() - 1
        seekBarEraser.progress = binding.handwritingView.currentEraserSize.toInt()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pen & Eraser Settings")
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .create()

        dialogView.findViewById<View>(R.id.colorWhite).setOnClickListener { binding.handwritingView.currentPenColor = Color.WHITE }
        dialogView.findViewById<View>(R.id.colorGreen).setOnClickListener { binding.handwritingView.currentPenColor = Color.parseColor("#00FF88") }
        dialogView.findViewById<View>(R.id.colorRed).setOnClickListener { binding.handwritingView.currentPenColor = Color.parseColor("#FF5252") }
        dialogView.findViewById<View>(R.id.colorBlue).setOnClickListener { binding.handwritingView.currentPenColor = Color.parseColor("#448AFF") }
        dialogView.findViewById<View>(R.id.colorYellow).setOnClickListener { binding.handwritingView.currentPenColor = Color.parseColor("#FFEB3B") }

        seekBarThickness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.handwritingView.currentPenThickness = (progress + 1).toFloat()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarEraser.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.handwritingView.currentEraserSize = progress.toFloat().coerceAtLeast(10f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialog.show()
    }

    private fun showSaveDialog() {
        val input = EditText(requireContext()).apply { hint = "Project Name" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Save Project")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    val code = viewModel.code.value.orEmpty()
                    val prefs = requireContext().getSharedPreferences("projects", Context.MODE_PRIVATE)
                    prefs.edit().putString(name, code).apply()
                    Toast.makeText(context, "Saved as $name", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun setupRecognizer() {
        try {
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US") ?: return
            val model = DigitalInkRecognitionModel.builder(identifier).build()
            recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
            
            val manager = RemoteModelManager.getInstance()
            manager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
                if (!downloaded) {
                    binding.btnRecognize.text = "Downloading..."
                    binding.btnRecognize.isEnabled = false
                    manager.download(model, DownloadConditions.Builder().build()).addOnSuccessListener { 
                        binding.btnRecognize.isEnabled = true
                        binding.btnRecognize.text = "Recognize Code"
                    }
                }
            }
        } catch (t: Throwable) {
            binding.btnRecognize.isEnabled = false
        }
    }

    private fun recognizeInk() {
        val rec = recognizer ?: return
        if (!binding.handwritingView.hasInk()) { 
            Toast.makeText(context, "Draw something first!", Toast.LENGTH_SHORT).show()
            return 
        }
        
        val inks = binding.handwritingView.buildInkPerLine()
        val lines = ArrayList<String>()
        binding.btnRecognize.isEnabled = false
        binding.btnRecognize.text = "Processing..."
        
        recognizeLine(rec, inks, 0, lines)
    }

    private fun recognizeLine(rec: DigitalInkRecognizer, inks: List<Ink>, index: Int, out: MutableList<String>) {
        if (index >= inks.size) { 
            finishRecognition(out)
            return 
        }
        rec.recognize(inks[index]).addOnSuccessListener { result ->
            out.add(result.candidates.firstOrNull()?.text.orEmpty())
            recognizeLine(rec, inks, index + 1, out)
        }.addOnFailureListener { 
            _binding?.btnRecognize?.isEnabled = true
            _binding?.btnRecognize?.text = "Recognize Code"
        }
    }

    private fun finishRecognition(lines: List<String>) {
        val text = lines.filter { it.isNotBlank() }.joinToString("\n")
        _binding?.btnRecognize?.isEnabled = true
        _binding?.btnRecognize?.text = "Recognize Code"
        
        if (text.isNotBlank()) {
            viewModel.setCode(text)
            viewModel.navigateTo(MainViewModel.TAB_CODE)
            Toast.makeText(context, "Recognized!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Could not read. Try again.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() { 
        super.onDestroyView()
        soundManager.release()
        _binding = null 
    }
    override fun onDestroy() { super.onDestroy(); try { recognizer?.close() } catch (e: Throwable) {}; recognizer = null }
}

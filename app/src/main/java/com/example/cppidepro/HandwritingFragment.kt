package com.example.cppidepro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.cppidepro.databinding.FragmentHandwritingBinding
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
    private var modelReady = false
    private var recognizerError: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHandwritingBinding.inflate(inflater, container, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClear.setOnClickListener { binding.handwritingView.clear() }
        binding.btnRecognize.setOnClickListener { recognizeInk() }
        binding.btnMode.setOnClickListener {
            val view2 = binding.handwritingView
            view2.fingerDrawingEnabled = !view2.fingerDrawingEnabled
            binding.btnMode.setText(if (view2.fingerDrawingEnabled) R.string.mode_draw else R.string.mode_scroll)
        }
        if (recognizer == null) setupRecognizer()
    }

    private fun setupRecognizer() {
        try {
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
            if (identifier == null) { recognizerError = "Handwriting language not supported."; setStatus(recognizerError!!); return }
            val model = DigitalInkRecognitionModel.builder(identifier).build()
            recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
            ensureModelDownloaded(model)
        } catch (t: Throwable) {
            recognizer = null
            recognizerError = "Handwriting recognition unavailable (Google Play Services missing?)"
            setStatus(recognizerError!!)
        }
    }

    private fun ensureModelDownloaded(model: DigitalInkRecognitionModel) {
        try {
            val manager = RemoteModelManager.getInstance()
            manager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
                if (downloaded) { modelReady = true; setStatus(getString(R.string.handwriting_hint)) }
                else {
                    setStatus("Downloading handwriting model (one time, needs internet)…")
                    manager.download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener { modelReady = true; setStatus(getString(R.string.handwriting_hint)) }
                        .addOnFailureListener { setStatus("Model download failed. Check your connection and reopen this tab.") }
                }
            }.addOnFailureListener { setStatus("Could not check handwriting model: ${it.message}") }
        } catch (t: Throwable) {
            recognizerError = "Handwriting recognition unavailable."
            setStatus(recognizerError!!)
        }
    }

    private fun recognizeInk() {
        val rec = recognizer
        if (rec == null) { toast(recognizerError ?: "Handwriting recognizer not ready"); return }
        if (!binding.handwritingView.hasInk()) { toast("Write something first"); return }
        if (!modelReady) { toast("Handwriting model is still downloading…"); return }
        val inks = binding.handwritingView.buildInkPerLine()
        val lines = ArrayList<String>()
        binding.btnRecognize.isEnabled = false
        setStatus("Recognizing…")
        recognizeLine(rec, inks, 0, lines)
    }

    private fun recognizeLine(rec: DigitalInkRecognizer, inks: List<Ink>, index: Int, out: MutableList<String>) {
        if (index >= inks.size) { finishRecognition(out); return }
        try {
            rec.recognize(inks[index]).addOnSuccessListener { result ->
                out.add(result.candidates.firstOrNull()?.text.orEmpty())
                recognizeLine(rec, inks, index + 1, out)
            }.addOnFailureListener { e ->
                _binding?.btnRecognize?.isEnabled = true
                setStatus("Recognition failed: ${e.message}")
            }
        } catch (t: Throwable) {
            _binding?.btnRecognize?.isEnabled = true
            setStatus("Recognition failed: ${t.message}")
        }
    }

    private fun finishRecognition(lines: List<String>) {
        val text = lines.filter { it.isNotBlank() }.joinToString("\n")
        _binding?.btnRecognize?.isEnabled = true
        setStatus(getString(R.string.handwriting_hint))
        if (text.isBlank()) { toast("Nothing recognized, try writing larger or clearer"); return }
        viewModel.appendCode(text)
        _binding?.handwritingView?.clear()
        viewModel.navigateTo(MainViewModel.TAB_CODE)
    }

    private fun setStatus(message: String) { _binding?.tvStatus?.text = message }
    private fun toast(message: String) { context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() } }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
    override fun onDestroy() { super.onDestroy(); try { recognizer?.close() } catch (e: Throwable) { }; recognizer = null }
}

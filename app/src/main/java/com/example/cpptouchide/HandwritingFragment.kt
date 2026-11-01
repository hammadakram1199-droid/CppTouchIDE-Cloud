package com.example.cpptouchide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer

class HandwritingFragment : Fragment() {
    private lateinit var viewModel: MainViewModel
    private lateinit var handwritingView: HandwritingView
    private lateinit var recognizer: DigitalInkRecognizer

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_handwriting, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        handwritingView = view.findViewById(R.id.handwritingView)

        initModel()

        view.findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            handwritingView.clear()
        }

        view.findViewById<MaterialButton>(R.id.btnRecognize).setOnClickListener {
            val ink = handwritingView.getInk()
            if (ink.strokes.isEmpty()) {
                Toast.makeText(context, "Draw something first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            recognizer.recognize(ink).addOnSuccessListener { result ->
                if (result.candidates.isNotEmpty()) {
                    val text = result.candidates[0].text
                    viewModel.appendCode("\n$text\n")
                    Toast.makeText(context, "Added to Code tab!", Toast.LENGTH_SHORT).show()
                    handwritingView.clear()
                }
            }.addOnFailureListener {
                Toast.makeText(context, "Recognition failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initModel() {
        val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US") ?: return
        val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
        recognizer = DigitalInkRecognition.getClient(
            com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions.builder(model).build()
        )
        val remoteModelManager = RemoteModelManager.getInstance()
        remoteModelManager.download(model, DownloadConditions.Builder().build())
    }
}

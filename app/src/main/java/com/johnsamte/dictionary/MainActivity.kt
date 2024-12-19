package com.johnsamte.dictionary

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.johnsamte.dictionary.databinding.ActivityMainBinding
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Locale
import android.Manifest
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MeaningAdapter
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.searchInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val word = binding.searchInput.text.toString()
                getMeaning(word)
                true // Consume the event
            } else {
                false // Pass the event to the next listener
            }
        }

        binding.searchBtn.setOnClickListener {
            val word = binding.searchInput.text.toString()
            getMeaning(word)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        binding.voiceSearch.setOnClickListener {
            checkAudioPermission {
                startVoiceRecognition()
            }
        }

        adapter = MeaningAdapter(emptyList())
        binding.meaningRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.meaningRecyclerView.adapter = adapter


        // SpeechRecognizer listener
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { word ->
                    binding.searchInput.setText(word)
                    getMeaning(word)
                }
            }

            override fun onError(error: Int) {
                Toast.makeText(this@MainActivity, "Error recognizing speech: $error", Toast.LENGTH_SHORT).show()
            }

            // Implement other callbacks as needed
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }


    @OptIn(DelicateCoroutinesApi::class)
    private fun getMeaning(word: String){
        setInProgress(true)
        GlobalScope.launch {
            try {
                val response = RetrofitInstance.dictionaryApi.getMeaning(word)
                if (response.body()==null){
                    throw (Exception())
                }
                runOnUiThread {
                    setInProgress(false)
                    response.body()?.first()?.let {
                        setUI(it)
                    }
                }
            }catch (e:Exception){
                runOnUiThread {
                    setInProgress(false)
                    Toast.makeText(applicationContext, "Something went wrong.!", Toast.LENGTH_SHORT).show()
                }
            }

        }
    }

    private fun setUI(response: WordResult){
        binding.wordTextview.text = response.word
        binding.phoneticTextview.text = response.phonetic
        response.meanings.let { adapter.updateNewData(it) }
    }


    private fun setInProgress(inProgress : Boolean){
        if (inProgress){
            binding.searchBtn.visibility = View.INVISIBLE
            binding.progressBar.visibility = View.VISIBLE
        }else{
            binding.searchBtn.visibility = View.VISIBLE
            binding.progressBar.visibility = View.INVISIBLE
        }
    }

    private fun startVoiceRecognition() {
        speechRecognizer.startListening(speechRecognizerIntent)
    }

    private fun checkAudioPermission(onPermissionGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            onPermissionGranted()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition()
        } else {
            Toast.makeText(this, "Permission denied to record audio", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}
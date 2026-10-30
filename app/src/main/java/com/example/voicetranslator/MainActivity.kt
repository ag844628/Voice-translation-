package com.example.voicetranslator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech

    private lateinit var tvOutput: TextView
    private lateinit var tvTranslated: TextView
    private lateinit var tvPhonetic: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnSpeak: Button
    private lateinit var btnPlayTTS: ImageButton
    private lateinit var spinnerSourceLang: Spinner
    private lateinit var spinnerTargetLang: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var toggleVoiceGender: RadioGroup
    private lateinit var rbMale: RadioButton
    private lateinit var rbFemale: RadioButton

    private var isMaleVoice = true

    private val languages = listOf(
        Pair("मराठी (Marathi)", "mr-IN"),
        Pair("हिन्दी (Hindi)", "hi-IN"),
        Pair("English (US)", "en-US"),
        Pair("English (India)", "en-IN"),
        Pair("ગુજરાતી (Gujarati)", "gu-IN"),
        Pair("Español (Spanish)", "es-ES"),
        Pair("Français (French)", "fr-FR"),
        Pair("Deutsch (German)", "de-DE")
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var currentSpokenText = ""
    private var currentTranslatedText = ""

    companion object {
        private const val REQUEST_RECORD_AUDIO = 200
        private const val TRANSLATION_API_URL = "https://ais-dev-rqf3dqamxnqsinyjphkxqw-235454605684.asia-northeast1.run.app/api/translate"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOutput = findViewById(R.id.tvOutput)
        tvTranslated = findViewById(R.id.tvTranslated)
        tvPhonetic = findViewById(R.id.tvPhonetic)
        tvStatus = findViewById(R.id.tvStatus)
        btnSpeak = findViewById(R.id.btnSpeak)
        btnPlayTTS = findViewById(R.id.btnPlayTTS)
        spinnerSourceLang = findViewById(R.id.spinnerSourceLang)
        spinnerTargetLang = findViewById(R.id.spinnerTargetLang)
        progressBar = findViewById(R.id.progressBar)
        toggleVoiceGender = findViewById(R.id.radioGroupVoiceGender)
        rbMale = findViewById(R.id.rbMale)
        rbFemale = findViewById(R.id.rbFemale)

        setupSpinners()
        setupVoiceGenderSelection()

        tts = TextToSpeech(this, this)

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        } else {
            Toast.makeText(this, "Speech recognition is not available on this device", Toast.LENGTH_LONG).show()
        }

        checkAudioPermission()

        btnSpeak.setOnClickListener {
            val selectedSource = languages[spinnerSourceLang.selectedItemPosition].second
            startListening(selectedSource)
        }

        btnPlayTTS.setOnClickListener {
            if (currentTranslatedText.isNotEmpty()) {
                val targetCode = languages[spinnerTargetLang.selectedItemPosition].second
                speakText(currentTranslatedText, targetCode)
            }
        }
    }

    private fun setupVoiceGenderSelection() {
        toggleVoiceGender.setOnCheckedChangeListener { _, checkedId ->
            isMaleVoice = (checkedId == R.id.rbMale)
            val genderText = if (isMaleVoice) "पुरुषी आवाज (Male Voice)" else "स्त्री आवाज (Female Voice)"
            Toast.makeText(this, "आवाज सेट केला: $genderText", Toast.LENGTH_SHORT).show()

            if (currentTranslatedText.isNotEmpty()) {
                val targetCode = languages[spinnerTargetLang.selectedItemPosition].second
                speakText(currentTranslatedText, targetCode)
            }
        }
    }

    private fun setupSpinners() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages.map { it.first })
        spinnerSourceLang.adapter = adapter
        spinnerTargetLang.adapter = adapter

        spinnerSourceLang.setSelection(0)
        spinnerTargetLang.setSelection(2)
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }
    }

    private fun startListening(languageCode: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "बोला (Speak now)...")
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                tvStatus.text = "बोला (Speak now)..."
                btnSpeak.isEnabled = false
            }

            override fun onBeginningOfSpeech() {
                tvStatus.text = "ऐकत आहे (Listening)..."
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                tvStatus.text = "प्रक्रिया चालू आहे (Translating)..."
                btnSpeak.isEnabled = true
            }

            override fun onError(error: Int) {
                btnSpeak.isEnabled = true
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "आवाज ओळखता आला नाही (No match found)"
                    SpeechRecognizer.ERROR_NETWORK -> "इंटरनेट कनेक्शन तपासा (Network error)"
                    SpeechRecognizer.ERROR_AUDIO -> "मायक्रोफोन त्रुटी (Audio error)"
                    else -> "त्रुटी कोड: $error"
                }
                tvStatus.text = errorMessage
            }

            override fun onResults(results: Bundle?) {
                btnSpeak.isEnabled = true
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    currentSpokenText = spokenText
                    tvOutput.text = "तुम्ही म्हणालात: $spokenText"

                    val sourceCode = languages[spinnerSourceLang.selectedItemPosition].second
                    val targetCode = languages[spinnerTargetLang.selectedItemPosition].second
                    translateAndSpeak(spokenText, sourceCode, targetCode)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty()) {
                    tvOutput.text = "तुम्ही म्हणालात: ${partial[0]}..."
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    private fun translateAndSpeak(text: String, sourceLang: String, targetLang: String) {
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "भाषांतर होत आहे (Translating with AI)..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("text", text)
                    put("sourceLang", sourceLang)
                    put("targetLang", targetLang)
                }

                val requestBody = jsonPayload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(TRANSLATION_API_URL)
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseData = response.body?.string() ?: ""

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && responseData.isNotEmpty()) {
                        val json = JSONObject(responseData)
                        val translated = json.optString("translatedText", text)
                        val phonetic = json.optString("phoneticTarget", "")

                        currentTranslatedText = translated
                        tvTranslated.text = translated
                        tvPhonetic.text = if (phonetic.isNotEmpty()) "उच्चार: $phonetic" else ""
                        tvStatus.text = "भाषांतर पूर्ण (Translated successfully)"
                        btnPlayTTS.visibility = View.VISIBLE

                        speakText(translated, targetLang)
                    } else {
                        tvStatus.text = "भाषांतर करताना त्रुटी आली."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "त्रुटी: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun speakText(text: String, langCode: String) {
        val localeParts = langCode.split("-")
        val targetLocale = if (localeParts.size > 1) {
            Locale(localeParts[0], localeParts[1])
        } else {
            Locale(localeParts[0])
        }

        tts.language = targetLocale

        if (isMaleVoice) {
            tts.setPitch(0.80f)
        } else {
            tts.setPitch(1.22f)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val availableVoices = tts.voices
                if (!availableVoices.isNullOrEmpty()) {
                    val targetLang = targetLocale.language
                    val genderKeyword = if (isMaleVoice) "male" else "female"
                    val matchedVoice = availableVoices.find { voice ->
                        voice.locale.language == targetLang &&
                            voice.name.contains(genderKeyword, ignoreCase = true)
                    }
                    if (matchedVoice != null) {
                        tts.voice = matchedVoice
                    }
                }
            } catch (e: Exception) {
            }
        }

        val utteranceId = UUID.randomUUID().toString()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread { tvStatus.text = "आवाज चालू आहे (Speaking)..." }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread { tvStatus.text = "तयार (Ready)" }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread { tvStatus.text = "उच्चार त्रुटी" }
                }
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
    }
}

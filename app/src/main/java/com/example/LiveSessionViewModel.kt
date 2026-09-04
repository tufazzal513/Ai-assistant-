package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class AssistantState {
    IDLE, CONNECTING, LISTENING, SPEAKING, ERROR
}

class LiveSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AssistantState.IDLE)
    val state = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var client: GeminiLiveClient? = null
    private val audioRecorder = AudioRecorder()
    private val audioPlayer = AudioPlayer()
    
    private val deviceActions = DeviceActions(application)

    init {
        viewModelScope.launch {
            audioRecorder.audioFlow.collect { pcmData ->
                if (_state.value == AssistantState.LISTENING) {
                    client?.sendAudio(pcmData)
                }
            }
        }
        
        viewModelScope.launch {
            audioPlayer.isPlaying.collect { playing ->
                if (playing && _state.value == AssistantState.LISTENING) {
                    _state.value = AssistantState.SPEAKING
                } else if (!playing && _state.value == AssistantState.SPEAKING) {
                    _state.value = AssistantState.LISTENING
                }
            }
        }
    }

    fun toggleSession() {
        if (_state.value == AssistantState.IDLE || _state.value == AssistantState.ERROR) {
            startSession()
        } else {
            stopSession()
        }
    }

    private fun startSession() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "YOUR_API_KEY") {
            _errorMessage.value = "API key missing. Set it in Secrets."
            _state.value = AssistantState.ERROR
            return
        }
        
        _state.value = AssistantState.CONNECTING
        _errorMessage.value = null
        client = GeminiLiveClient(apiKey).apply {
            viewModelScope.launch {
                events.collect { event ->
                    handleEvent(event)
                }
            }
            connect()
        }
    }

    private fun handleEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.Connected -> {
                _state.value = AssistantState.LISTENING
                audioRecorder.startRecording()
            }
            is LiveEvent.Disconnected -> {
                stopSession()
            }
            is LiveEvent.Error -> {
                _errorMessage.value = event.message
                stopSession()
                _state.value = AssistantState.ERROR
            }
            is LiveEvent.AudioData -> {
                audioPlayer.play(event.pcmData)
            }
            is LiveEvent.FunctionCall -> {
                handleFunctionCall(event.name, event.args)
            }
            is LiveEvent.TurnComplete -> {
                // Server finished its turn
            }
        }
    }

    private fun handleFunctionCall(name: String, args: JSONObject?) {
        var response = JSONObject()
        try {
            when (name) {
                "openWhatsApp" -> response = deviceActions.openWhatsApp()
                "openApp" -> response = deviceActions.openApp(args?.optString("appName") ?: "")
                "makeCall" -> response = deviceActions.makeCall(args?.optString("phoneNumber") ?: "")
                "callContact" -> response = deviceActions.callContact(args?.optString("contactName") ?: "")
                else -> {
                    response.put("success", false)
                    response.put("error", "Unknown function")
                }
            }
        } catch (e: Exception) {
            response.put("success", false)
            response.put("error", e.message)
        }
        client?.sendFunctionResponse(name, response)
    }

    fun stopSession() {
        client?.disconnect()
        client = null
        audioRecorder.stopRecording()
        audioPlayer.stop()
        if (_state.value != AssistantState.ERROR) {
            _state.value = AssistantState.IDLE
        }
    }
    
    fun testSpeaker() {
        audioPlayer.playTestTone()
    }
    
    fun interrupt() {
        audioPlayer.stop()
        client?.sendClientContent("Please stop and listen.")
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
        audioPlayer.release()
    }
}

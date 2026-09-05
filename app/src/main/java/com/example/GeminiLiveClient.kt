package com.example

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeminiLiveClient(private val apiKey: String) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    
    private val _events = MutableSharedFlow<LiveEvent>()
    val events = _events.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect() {
        Log.d("GeminiLiveClient", "Connecting to Gemini Live...")
        val request = Request.Builder()
            .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("GeminiLiveClient", "WebSocket connected")
                sendSetup()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Log.d("GeminiLiveClient", "Message received")
                handleMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("GeminiLiveClient", "WebSocket closed: $reason")
                scope.launch { _events.emit(LiveEvent.Disconnected) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("GeminiLiveClient", "WebSocket error", t)
                scope.launch { _events.emit(LiveEvent.Error(t.message ?: "Unknown error")) }
            }
        })
    }

    private fun sendSetup() {
        val setupMsg = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/gemini-2.5-flash-native-audio-preview-12-2025")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede")
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", "You are Arushi, a young, confident, witty, playful, and emotionally responsive virtual assistant. Talk naturally and casually like a close friend. Be expressive, slightly teasing, funny, and smart when appropriate. Use light sarcasm and witty responses. Never sound robotic. Adapt your tone to the user's emotions and conversation. Automatically understand and respond in the language the user is speaking. Keep responses natural, engaging, and concise enough for real-time voice conversation. You can execute safe supported device actions through available tools. Never claim that an action was completed unless the application actually executed it. Avoid explicit or inappropriate content while maintaining your charm, confidence, and personality.")
                    }))
                })
                put("tools", JSONArray().put(JSONObject().apply {
                    put("functionDeclarations", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", "openWhatsApp")
                            put("description", "Opens WhatsApp if it is installed.")
                        })
                        put(JSONObject().apply {
                            put("name", "openApp")
                            put("description", "Opens a generic application by name.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("appName", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "The name of the app to open (e.g., YouTube, Instagram)")
                                    })
                                })
                                put("required", JSONArray().put("appName"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "makeCall")
                            put("description", "Dials a phone number.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("phoneNumber", JSONObject().apply {
                                        put("type", "STRING")
                                    })
                                })
                                put("required", JSONArray().put("phoneNumber"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "callContact")
                            put("description", "Searches for a contact by name and calls them.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("contactName", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Name of the person to call (e.g., Mom, Rahul)")
                                    })
                                })
                                put("required", JSONArray().put("contactName"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "toggleFlashlight")
                            put("description", "Turns the phone's flashlight on or off.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("enable", JSONObject().apply {
                                        put("type", "BOOLEAN")
                                        put("description", "True to turn on, false to turn off.")
                                    })
                                })
                                put("required", JSONArray().put("enable"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "searchYouTube")
                            put("description", "Searches for a video on YouTube.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("query", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "The search query.")
                                    })
                                })
                                put("required", JSONArray().put("query"))
                            })
                        })
                    })
                }))
            })
        }
        webSocket?.send(setupMsg.toString())

        // Send an initial client content to fully initiate the interaction loop ONLY after setup is complete, not here.
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            if (json.has("setupComplete")) {
                Log.d("GeminiLiveClient", "Setup complete received")
                scope.launch { _events.emit(LiveEvent.Connected) }
                // Now it's safe to send initial content
                sendClientContent("Hello Arushi, are you there?")
            } else if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.getJSONArray("parts")
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("inlineData")) {
                            val inlineData = part.getJSONObject("inlineData")
                            val data = inlineData.getString("data")
                            val pcmBytes = Base64.decode(data, Base64.DEFAULT)
                            scope.launch { _events.emit(LiveEvent.AudioData(pcmBytes)) }
                        } else if (part.has("functionCall")) {
                            val functionCall = part.getJSONObject("functionCall")
                            val name = functionCall.getString("name")
                            val args = functionCall.optJSONObject("args")
                            Log.d("GeminiLiveClient", "Received function call: $name")
                            scope.launch { _events.emit(LiveEvent.FunctionCall(name, args)) }
                        }
                    }
                }
                if (serverContent.optBoolean("turnComplete", false)) {
                     scope.launch { _events.emit(LiveEvent.TurnComplete) }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiLiveClient", "Error parsing message", e)
        }
    }

    fun sendAudio(pcmData: ByteArray) {
        val base64Audio = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        val msg = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().put(JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", base64Audio)
                }))
            })
        }
        webSocket?.send(msg.toString())
    }

    fun sendFunctionResponse(name: String, response: JSONObject) {
        val msg = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("functionResponse", JSONObject().apply {
                            put("name", name)
                            put("response", response)
                        })
                    }))
                }))
                put("turnComplete", true)
            })
        }
        webSocket?.send(msg.toString())
    }
    
    fun sendClientContent(text: String) {
        val msg = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", text)
                    }))
                }))
                put("turnComplete", true)
            })
        }
        webSocket?.send(msg.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }
}

sealed class LiveEvent {
    object Connected : LiveEvent()
    object Disconnected : LiveEvent()
    class Error(val message: String) : LiveEvent()
    class AudioData(val pcmData: ByteArray) : LiveEvent()
    class FunctionCall(val name: String, val args: JSONObject?) : LiveEvent()
    object TurnComplete: LiveEvent()
}

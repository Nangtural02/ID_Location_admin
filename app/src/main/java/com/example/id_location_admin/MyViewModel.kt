package com.example.id_location_admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class MyViewModel : ViewModel() {

    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()

    private val _connectionStatus = MutableStateFlow("연결되지 않음")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private val _processedData = MutableStateFlow(Point(-1f,-1f,-1f))
    val processedData: StateFlow<Point> = _processedData

    // 위험 감지 상태 변수
    private val _isDanger = MutableStateFlow(false)
    val isDanger: StateFlow<Boolean> = _isDanger

    // 위험 감지 트리거 함수
    fun triggerDanger() {
        _isDanger.value = true

        // 5초 후에 위험 상태 해제
        viewModelScope.launch {
            delay(5000)
            clearDanger()
        }
    }

    // 위험 해제 함수
    fun clearDanger() {
        _isDanger.value = false
    }


    fun connectWebSocket() {
        if (_connectionStatus.value == "연결됨") return // 이미 연결된 경우 무시

        val request = Request.Builder()
            .url("ws://202.30.29.212:5000/androidB")
            .build()
        webSocket = client.newWebSocket(request, SocketListener())
        _connectionStatus.value = "연결 중..."
    }

    fun closeWebSocket() {
        if (_connectionStatus.value != "연결됨") return // 연결되지 않은 경우 무시

        webSocket.close(1000, "사용자 요청으로 연결 종료")
        _connectionStatus.value = "연결되지 않음"
    }

    inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d("WebSocket", "웹소켓 연결됨")
            _connectionStatus.value = "연결됨"
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d("WebSocket", "수신: $text")
            viewModelScope.launch {
                try{
                    val point = Gson().fromJson(text, Point::class.java)
                    _processedData.emit(point)
                }catch(e: JsonSyntaxException){
                    Log.d("asdf","Json Passing Error: ${e.message}")
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            Log.d("WebSocket", "수신 바이트: $bytes")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d("WebSocket", "연결 종료: $code / $reason")
            _connectionStatus.value = "연결되지 않음"
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.d("WebSocket", "오류: ${t.message}")
            _connectionStatus.value = "연결 실패"
        }
    }
}

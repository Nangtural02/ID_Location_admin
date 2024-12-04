package com.example.id_location_admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.viewModels
import androidx.compose.material3.Button
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MyViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            enableEdgeToEdge()
            MyApp(viewModel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.closeWebSocket()
    }

    @Composable
    fun MyApp(viewModel: MyViewModel) {
        val connectionStatus by viewModel.connectionStatus.collectAsState()
        val nowPoint: Point by viewModel.processedData.collectAsState()
        val isDanger by viewModel.isDanger.collectAsState()

        /*Text(text = "수신 데이터:")
                   Text(text = processedData)
                   */
        Domyon(
            anchorList = listOf(Point(0f, 0f), Point(6f, 6f)),
            pointsList = listOf(nowPoint),
            backgroundImageResId = R.drawable.yulgok_background,
            isDanger = isDanger
        )
        if (connectionStatus != "연결됨") {
            Text(text = "연결 상태: $connectionStatus")
            Button(onClick = {
                if (connectionStatus == "연결됨") {
                    viewModel.closeWebSocket()
                } else {
                    viewModel.connectWebSocket()
                }
            }) {
                Text(if (connectionStatus == "연결됨") "서버 연결 해제" else "서버 연결")
            }
        }
        Text(text = nowPoint.toString())
    }
}
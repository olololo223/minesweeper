package com.example.client_mobile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.client_mobile.net.NetworkClient
import com.example.client_mobile.protocol.MessageType
import com.example.client_mobile.protocol.payload.LoginRequest
import com.example.client_mobile.protocol.payload.LoginResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoginScreen()
                }
            }
        }
    }
}

@Composable
fun LoginScreen() {
    var username by remember { mutableStateOf("android_test") }
    var password by remember { mutableStateOf("1234") }
    var server by remember { mutableStateOf("10.0.2.2") }
    var port by remember { mutableStateOf("9000") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Сапёр — вход", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(username, { username = it },
            label = { Text("Имя") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, { password = it },
            label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(server, { server = it },
            label = { Text("Сервер") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(port, { port = it },
            label = { Text("Порт") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        Button(
            enabled = !busy,
            onClick = {
                busy = true
                status = "Подключение..."
                scope.launch {
                    try {
                        val client = NetworkClient(server, port.toInt())
                        client.connect()
                        status = "Подключено, логинимся..."

                        val resp: LoginResponse = client.request(
                            MessageType.LOGIN_REQUEST,
                            LoginRequest(username, password),
                            MessageType.LOGIN_RESPONSE,
                            LoginResponse::class.java
                        )

                        status = if (resp.success)
                            "OK: userId=${resp.userId}, ${resp.message}"
                        else
                            "Ошибка: ${resp.message}"
                    } catch (e: Exception) {
                        Log.e("NetClient", "Login error", e)
                        status = "Ошибка: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Ждём..." else "Войти")
        }

        Spacer(Modifier.height(16.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}
package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.example.eventWraps.*
import com.example.plugins.configureAdministration
import com.example.plugins.configureRouting
import com.example.plugins.configureSecurity
import com.example.plugins.configureSockets
import com.example.robot.RobotGo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.*

enum class AppType {
    Server, Client
}

object ApplicationState {
    var error: String? by mutableStateOf(null)
    var serverState: Boolean? by mutableStateOf(null)
    private val scope = CoroutineScope(Dispatchers.Default)

    var app by mutableStateOf<AppType?>(null)
    var host by mutableStateOf("192.168.1.19")
    var code by mutableStateOf((10..99).random().toString())


    const val port = 8082
    val shutdownUrl = "/${UUID.randomUUID().toString().replace("-", "").take(10)}"

    val httpClient = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(WebSockets)
        expectSuccess = false
    }

    fun startServer() {
        serverState = false
        error = null
        scope.launch {
            runCatching {
                embeddedServer(CIO, port = port, host = "0.0.0.0", module = Application::module)
                    .start(wait = true)
            }.onFailure {
                error = it.toString()
                it.printStackTrace()
            }
        }
    }

    fun stopServer() {
        error = null
        scope.launch {
            runCatching {
                HttpClient(io.ktor.client.engine.cio.CIO).get {
                    url {
                        host = "localhost"
                        port = ApplicationState.port
                        path(shutdownUrl)
                    }
                }
            }.onFailure {
                error = it.toString()
                it.printStackTrace()
            }
        }
    }

    private suspend fun canStartClient(): Boolean {
        error = null
        val httpResponse = httpClient.get("http://$host:$port/ws")

        if (httpResponse.status != HttpStatusCode.OK ||
            httpResponse.body<String>() != "ServerReady"
        ) {
            error = "Cannot connect to server (${httpResponse.status})"
        }
        return error != null
    }

    fun launchClient() {
        error = null
        scope.launch {
            if (!canStartClient())
                return@launch

            app = AppType.Client
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main1() = application {

    Window(
        state = rememberWindowState(
            /*placement = WindowPlacement.Fullscreen,*/ position = WindowPosition(Alignment.TopCenter),
            isMinimized = true,
        ),
        undecorated = true,
        transparent = true,
        onCloseRequest = { exitApplication() },
        resizable = false,
        alwaysOnTop = true,
    ) {
        Dialog(
            state = rememberDialogState(
                position = WindowPosition(Alignment.TopCenter),
                size = DpSize(500.dp, 3.dp),
            ),
            onCloseRequest = { exitApplication() },
            undecorated = true,
        ) {
            Surface(
                border = BorderStroke(2.dp, Color.Green),
                color = Color.Transparent,
                modifier = Modifier.fillMaxSize()
                    .onPointerEvent(PointerEventType.Enter) {
                        RobotGo.isActive = !RobotGo.isActive
                    },
            ) {
                Text("asd")
            }
        }
    }
}

fun main() = application {
    when (ApplicationState.app) {
        AppType.Client -> ClientScreen()
        AppType.Server -> ServerScreen()
        else -> HomeScreen()
    }
}

fun Application.module() {
    configureSockets()
    configureAdministration()
    configureSecurity()
    configureRouting()
    ApplicationState.serverState = true
    ApplicationState.app = AppType.Server
}


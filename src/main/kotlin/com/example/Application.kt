package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.example.eventWraps.KeyPress
import com.example.eventWraps.MouseClick
import com.example.eventWraps.MouseMove
import com.example.eventWraps.ReleaseEvent
import com.example.plugins.configureAdministration
import com.example.plugins.configureRouting
import com.example.plugins.configureSecurity
import com.example.plugins.configureSockets
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.util.*

object ApplicationState {
    var error: String? by mutableStateOf(null)
    var serverState: Boolean? by mutableStateOf(null)
    val scope = CoroutineScope(Dispatchers.Default)

    var client by mutableStateOf(false)
    var comChannel = Channel<Any>(onBufferOverflow = BufferOverflow.DROP_OLDEST)
        private set

    @Volatile
    var comChannelCloseForMouseMove = false

    private const val port = 8082
    val shutdownUrl = "/${UUID.randomUUID().toString().replace("-", "").take(10)}"

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

    fun startClient(host: String, code: String) {
        error = null
        scope.launch {
            runCatching {
                HttpClient(io.ktor.client.engine.cio.CIO) {
                    install(WebSockets)
                }.webSocket(
                    method = HttpMethod.Get, host = host,
                    port = port, path = "/ws",
                ) {
                    comChannel = Channel {  }
                    client = true
                    send(code)
                    for (it in comChannel) {
                        send(it.toString())
                        comChannelCloseForMouseMove = true
                        val frame = incoming.receive()
                        frame as Frame.Text
                        if (frame.readText() == "ACK") {
                            comChannelCloseForMouseMove = false
                        } else
                            error("No ACK from server")
                    }
                }
            }.onFailure {
                error = it.toString()
                it.printStackTrace()
            }
        }
    }
}

fun main() = application {
    if (ApplicationState.client)
        ClientScreen()
    else
        Window(
            onCloseRequest = ::exitApplication,
            title = "Compose for Desktop",
            state = rememberWindowState(width = 300.dp, height = 300.dp, position = WindowPosition(Alignment.Center))
        ) {
            MaterialTheme {
                Column(Modifier.fillMaxSize(), Arrangement.spacedBy(5.dp)) {
                    Button(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        enabled = ApplicationState.serverState == null || ApplicationState.serverState == true,
                        onClick = {
                            when (ApplicationState.serverState) {
                                null -> ApplicationState.startServer()
                                true -> ApplicationState.stopServer()
                                else -> {}
                            }
                        },
                    ) {
                        Text(
                            when (ApplicationState.serverState) {
                                true -> "Started.. click to stop"
                                false -> "Starting server.. wait"
                                else -> "Server"
                            }
                        )
                    }

                    var host by remember { mutableStateOf("192.168.1.19") }
                    var code by remember { mutableStateOf("") }
                    TextField(host, onValueChange = { host = it })
                    TextField(code, onValueChange = { code = it })

                    Button(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = {
                            ApplicationState.startClient(host, code)
                        },
                        enabled = ApplicationState.serverState == null,
                    ) {
                        Text("Client")
                    }

                    if (ApplicationState.error != null) {
                        Text(ApplicationState.error!!)
                    }
                }
            }
        }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ApplicationScope.ClientScreen() {
    val scope = rememberCoroutineScope()
    var lastKeyEvent by remember { mutableStateOf<KeyPress?>(null) }
    val sendFn = remember {
        { event: Any ->
            if (event != lastKeyEvent)
                runBlocking {
                    delay(5)
                    scope.launch {
                        ApplicationState.comChannel.send(event)
                        if (event is KeyPress)
                            lastKeyEvent = event
                    }
                }
        }
    }

    val windowState = rememberWindowState(
        placement = WindowPlacement.Fullscreen,
        position = WindowPosition(Alignment.Center),
    )
    Window(
        state = windowState,
        onCloseRequest = ::exitApplication,
        undecorated = true,
        transparent = true,
        resizable = false,
        onKeyEvent = { keyEvent ->
            val event = keyEvent.nativeKeyEvent as? KeyEvent
            if (event != null) {
                if (event.id == KeyEvent.KEY_PRESSED)
                    sendFn(KeyPress(event.keyCode, isPressed = true))
                else if (event.id == KeyEvent.KEY_RELEASED)
                    sendFn(KeyPress(event.keyCode, isPressed = false))
            }

            if (keyEvent.isAltPressed && keyEvent.key == Key.X) {
                if (keyEvent.isCtrlPressed) {
                    ApplicationState.comChannel.close()
                } else {
                    windowState.isMinimized = true
                }
            }

            !(keyEvent.isAltPressed && keyEvent.key == Key.Tab)
        },
    ) {
        remember {
            window.addWindowFocusListener(object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) {
                    println("windowGainedFocus")
                }

                override fun windowLostFocus(e: WindowEvent?) {
                    sendFn(ReleaseEvent)
                }
            })
        }
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Transparent)
                .onPointerEvent(PointerEventType.Move) {
                    val mouseEvent = it.nativeEvent as? MouseEvent
                    mouseEvent ?: return@onPointerEvent
                    if (!ApplicationState.comChannelCloseForMouseMove)
                        sendFn(
                            MouseMove(
                                Offset(
                                    mouseEvent.xOnScreen.toFloat(),
                                    mouseEvent.yOnScreen.toFloat()
                                )
                            )
                        )
                }
                .onPointerEvent(PointerEventType.Release) {
                    sendFn(
                        MouseClick.mousePressOrRelease(it, isPressing = false)
                            ?: return@onPointerEvent
                    )
                }
                .onPointerEvent(PointerEventType.Press) {
                    sendFn(
                        MouseClick.mousePressOrRelease(it, isPressing = true)
                            ?: return@onPointerEvent
                    )
                }
                .onPointerEvent(PointerEventType.Scroll) {
                    println(it.nativeEvent)
                }
                .border(BorderStroke(3.dp, Color.Red)),
        )
    }
}

fun Application.module() {
    configureSockets()
    configureAdministration()
    configureSecurity()
    configureRouting()
    ApplicationState.serverState = true
}

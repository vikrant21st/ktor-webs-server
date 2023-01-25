package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
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
    var mouseChannel = Channel<MouseEvents>(onBufferOverflow = BufferOverflow.DROP_OLDEST)
        private set
    var keyChannel = Channel<KeyPress>(onBufferOverflow = BufferOverflow.SUSPEND)
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
                val httpClient = HttpClient(io.ktor.client.engine.cio.CIO) {
                    install(WebSockets)
                }

                val job1 = launch {
                    httpClient.webSocket(host = host, port = port, path = "/mws") {
                        mouseChannel = Channel { }
                        client = true
                        send(code)
                        for (it in mouseChannel) {
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
                }

                val job2 = launch {
                    httpClient.webSocket(host = host, port = port, path = "/kws") {
                        keyChannel = Channel { }
                        client = true
                        send(code)
                        for (it in keyChannel) {
                            send(it.toString())
                        }
                    }
                }
                job1.join()
                job2.join()
            }.onFailure {
                client = false
                error = it.toString()
                it.printStackTrace()
            }
            runCatching { mouseChannel.close() }
            runCatching { keyChannel.close() }
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
    val sendMouseFn = remember {
        { event: MouseEvents ->
            runBlocking {
                scope.launch {
                    ApplicationState.mouseChannel.send(event)
                }
            }
        }
    }
    val sendKeyFn = remember {
        { event: KeyPress ->
            if (event != lastKeyEvent)
                runBlocking {
                    scope.launch {
                        ApplicationState.keyChannel.send(event)
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
            if (keyEvent.isAltPressed && keyEvent.key == Key.X) {
                if (keyEvent.isCtrlPressed) {
                    ApplicationState.mouseChannel.close()
                    ApplicationState.keyChannel.close()
                } else {
                    windowState.isMinimized = true
                }
            } else {
                val event = keyEvent.nativeKeyEvent as? KeyEvent
                if (event != null) {
                    if (event.id == KeyEvent.KEY_PRESSED)
                        sendKeyFn(KeyPress(event.keyCode, isPressed = true))
                    else if (event.id == KeyEvent.KEY_RELEASED)
                        sendKeyFn(KeyPress(event.keyCode, isPressed = false))
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
                    sendMouseFn(ReleaseEvent)
                }
            })
        }
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Transparent)
                .onPointerEvent(PointerEventType.Move) {
                    val mouseEvent = it.nativeEvent as? MouseEvent
                    mouseEvent ?: return@onPointerEvent
                    if (!ApplicationState.comChannelCloseForMouseMove)
                        sendMouseFn(
                            MouseMove(
                                with(mouseEvent) {
                                    Offset(xOnScreen.toFloat(), yOnScreen.toFloat())
                                }
                            )
                        )
                }
                .onPointerEvent(PointerEventType.Release) {
                    sendMouseFn(
                        MouseClick.mousePressOrRelease(it, isPressing = false)
                            ?: return@onPointerEvent
                    )
                }
                .onPointerEvent(PointerEventType.Press) {
                    sendMouseFn(
                        MouseClick.mousePressOrRelease(it, isPressing = true)
                            ?: return@onPointerEvent
                    )
                }
                .onPointerEvent(PointerEventType.Scroll) {
                    val event = it.nativeEvent as? java.awt.event.MouseWheelEvent
                    event ?: return@onPointerEvent
                    sendMouseFn(MouseScroll(event.wheelRotation))
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

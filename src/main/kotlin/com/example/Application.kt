package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.example.eventWraps.MouseClick
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
import io.ktor.server.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.util.*

object ApplicationState {
    var serverState: Boolean? by mutableStateOf(null)
    val scope = CoroutineScope(Dispatchers.Default)

    var client by mutableStateOf(false)
    val comChannel = Channel<Any>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private const val port = 8082
    val shutdownUrl = "/${UUID.randomUUID().toString().replace("-", "").take(10)}"

    fun startServer() {
        serverState = false
        scope.launch {
            embeddedServer(CIO, port = port, host = "0.0.0.0", module = Application::module)
                .start(wait = true)
        }
    }

    fun stopServer() {
        scope.launch {
            HttpClient(io.ktor.client.engine.cio.CIO).get {
                url {
                    host = "localhost"
                    port = ApplicationState.port
                    path(shutdownUrl)
                }
            }
        }
    }

    fun startClient(host: String, code: String) {
        scope.launch {

            HttpClient(io.ktor.client.engine.cio.CIO) {
                install(WebSockets)
            }.webSocket(
                method = HttpMethod.Get, host = host,
                port = port, path = "/ws",
            ) {
                client = true
                send(code)
                comChannel.consumeEach {
                    send(it.toString())
                }
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
                }
            }
        }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ApplicationScope.ClientScreen() {
    val scope = rememberCoroutineScope()
    val sendFn = remember {
        { event: Any ->
            runBlocking {
                delay(5)
                scope.launch { ApplicationState.comChannel.send(event) }
            }
        }
    }

    Window(
        state = rememberWindowState(
            placement = WindowPlacement.Fullscreen,
            position = WindowPosition(Alignment.Center),
        ),
        onCloseRequest = ::exitApplication,
        undecorated = true,
        transparent = true,
        onKeyEvent = { keyEvent ->
            sendFn(keyEvent)

            if (keyEvent.isAltPressed && keyEvent.key == Key.X) {
                runBlocking {
                    ApplicationState.comChannel.close()
                    delay(500)
                    exitApplication()
                }
            }
            true
        },
    ) {
        remember {
            window.addWindowFocusListener(object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) {
                    println("windowGainedFocus")
                }

                override fun windowLostFocus(e: WindowEvent?) {
                    println("windowLostFocus")
                }
            })
        }
        Box(
            modifier = Modifier.fillMaxSize()
                .onPointerEvent(PointerEventType.Enter) {
                    sendFn(it)
                }
                .onPointerEvent(PointerEventType.Exit) {
                    sendFn(it)
                }
                .onPointerEvent(PointerEventType.Move) {
                    sendFn(it)
                }
                .onPointerEvent(PointerEventType.Press) {
                    val mouseEvent = this.currentEvent.nativeEvent as? java.awt.event.MouseEvent
                    mouseEvent ?: return@onPointerEvent
                    sendFn(
                        MouseClick(
                            position = Offset(
                                mouseEvent.xOnScreen.toFloat(),
                                mouseEvent.yOnScreen.toFloat()
                            ),
                            button = mouseEvent.button,
                            clickCount = mouseEvent.clickCount,
                            pressed = true,
                        )
                    )
                }
                .onPointerEvent(PointerEventType.Release) {
                    val mouseEvent = this.currentEvent.nativeEvent as? java.awt.event.MouseEvent
                    mouseEvent ?: return@onPointerEvent
                    sendFn(
                        MouseClick(
                            position = Offset(
                                mouseEvent.xOnScreen.toFloat(),
                                mouseEvent.yOnScreen.toFloat()
                            ),
                            button = mouseEvent.button,
                            clickCount = mouseEvent.clickCount,
                            pressed = false,
                        )
                    )
                },
        ) {
            Surface(
                color = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
                border = BorderStroke(3.dp, Color.Red),
            ) {
                LaunchedEffect("sada") {/*
                val robot = Robot()
                delay(100)
                robot.autoDelay = 1
                robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                repeat(300) {
                    robot.mouseMove(it + 400, it + 400)
                }
                robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                delay(1000)
                robot.keyPress(java.awt.event.KeyEvent.VK_ALT)
                robot.keyPress(java.awt.event.KeyEvent.VK_X)
                robot.keyRelease(java.awt.event.KeyEvent.VK_X)
                robot.keyRelease(java.awt.event.KeyEvent.VK_ALT)*/
                }
            }
        }
    }
}

fun Application.module() {
    configureSockets()
    configureAdministration()
    configureSecurity()
    configureRouting()
    ApplicationState.serverState = true
}

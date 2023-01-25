package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
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
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import java.awt.event.*

class ClientScreenState(private val scope: CoroutineScope) {
    @Volatile
    var comChannelCloseForMouseMove = false

    private var mouseChannel = Channel<MouseEvents>(onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var keyChannel = Channel<KeyPress>(onBufferOverflow = BufferOverflow.SUSPEND)

    var isClientActive by mutableStateOf(false)

    init {
        scope.launch { startClient() }
    }

    fun sendMouseFn(event: MouseEvents) {
        scope.launch { mouseChannel.trySend(event) }
    }

    fun sendKeyFn(event: KeyPress) {
        scope.launch { keyChannel.trySend(event) }
    }

    fun stopClient() {
        mouseChannel.close()
        keyChannel.close()
        ApplicationState.app = null
        scope.cancel()
    }

    private suspend fun startClient() = coroutineScope {
        runCatching {
            mouseChannel = Channel { }
            keyChannel = Channel { }
            val job1 = launch { sendMouseEvents() }
            val job2 = launch { sendKeyEvents() }
            job1.join()
            job2.join()
        }.onFailure {
            ApplicationState.app = null
            ApplicationState.error = it.toString()
            it.printStackTrace()
        }
        runCatching { mouseChannel.close() }
        runCatching { keyChannel.close() }
    }

    private suspend fun sendMouseEvents() {
        ApplicationState.httpClient.webSocket(
            host = ApplicationState.host,
            port = ApplicationState.port,
            path = "/mws"
        ) {
            send(ApplicationState.code)
            for (it in mouseChannel) {
                if (!isClientActive)
                    continue

                send(it.toString())
                comChannelCloseForMouseMove = true
                val frame = incoming.receive() as Frame.Text
                if (frame.readText() == "ACK") {
                    comChannelCloseForMouseMove = false
                } else {
                    error("No ACK from server")
                }
            }
        }
    }

    private suspend fun sendKeyEvents() {
        ApplicationState.httpClient.webSocket(
            host = ApplicationState.host,
            port = ApplicationState.port,
            path = "/kws"
        ) {
            send(ApplicationState.code)
            launch {
                while (true) {
                    while (isClientActive) {
                        val frame = incoming.receive() as Frame.Text
                        if (frame.readText() == "DeActivate")
                            isClientActive = false
                    }
                    if (!isClientActive)
                        delay(500)
                }
            }

            var lastKeyEvent: KeyPress? = null
            for (it in keyChannel) {
                if (isClientActive && it != lastKeyEvent) {
                    send(it.toString())
                    lastKeyEvent = it
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ClientScreen() {
    val scope = rememberCoroutineScope()
    val state = remember { ClientScreenState(scope) }

    val windowState = rememberWindowState(
        placement = WindowPlacement.Fullscreen,
        position = WindowPosition(Alignment.Center),
    )

    Dialog(
        visible = !state.isClientActive,
        onCloseRequest = state::stopClient,
        state = rememberDialogState(WindowPosition(Alignment.TopCenter), DpSize(500.dp, 3.dp)),
        undecorated = true,
        resizable = false,
    ) {
        Surface(
            color = Color.Green,
            modifier = Modifier.fillMaxSize()
                .onPointerEvent(PointerEventType.Enter) { state.isClientActive = true },
            shape = RoundedCornerShape(0.50f),
        ) {}
    }

    Window(
        visible = state.isClientActive,
        state = windowState,
        onCloseRequest = state::stopClient,
        undecorated = true,
        transparent = true,
        resizable = false,
        onKeyEvent = { keyEvent ->
            if (keyEvent.isAltPressed && keyEvent.key == Key.X) {
                if (keyEvent.isCtrlPressed) {
                    state.stopClient()
                } else {
                    windowState.isMinimized = true
                }
            } else {
                val event = keyEvent.nativeKeyEvent as? KeyEvent
                if (event != null) {
                    if (event.id == KeyEvent.KEY_PRESSED)
                        state.sendKeyFn(KeyPress(event.keyCode, isPressed = true))
                    else if (event.id == KeyEvent.KEY_RELEASED)
                        state.sendKeyFn(KeyPress(event.keyCode, isPressed = false))
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
                    state.sendMouseFn(ReleaseEvent)
                }
            })
        }
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Transparent)
                .onPointerEvent(PointerEventType.Move) {
                    val mouseEvent = it.nativeEvent as? MouseEvent
                    mouseEvent ?: return@onPointerEvent
                    if (!state.comChannelCloseForMouseMove)
                        state.sendMouseFn(
                            MouseMove(
                                with(mouseEvent) {
                                    Offset(xOnScreen.toFloat(), yOnScreen.toFloat())
                                }
                            )
                        )
                }
                .onPointerEvent(PointerEventType.Release) {
                    state.sendMouseFn(
                        MouseClick.mousePressOrRelease(it, isPressing = false)
                            ?: return@onPointerEvent
                    )
                }
                .onPointerEvent(PointerEventType.Press) {
                    state.sendMouseFn(
                        MouseClick.mousePressOrRelease(it, isPressing = true)
                            ?: return@onPointerEvent
                    )
                }
                .onPointerEvent(PointerEventType.Scroll) {
                    val event = it.nativeEvent as? MouseWheelEvent
                    event ?: return@onPointerEvent
                    state.sendMouseFn(MouseScroll(event.wheelRotation))
                }
                .border(BorderStroke(3.dp, Color.Red)),
        )
    }
}

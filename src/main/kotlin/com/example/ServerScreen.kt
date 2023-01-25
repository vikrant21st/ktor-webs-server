package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.example.robot.RobotGo

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ApplicationScope.ServerScreen() {
    Window(
        onCloseRequest = {
            ApplicationState.stopServer()
            ApplicationState.app = null
        },
        title = "Compose for Desktop",
        state = rememberWindowState(
            position = WindowPosition(Alignment.TopCenter),
            size = DpSize(500.dp, 3.dp),
        ),
        onKeyEvent = {
            true
        },
        undecorated = true,
        alwaysOnTop = true,
    ) {
        Surface(
            border = BorderStroke(2.dp, Color.Green),
            color = Color.Transparent,
            modifier = Modifier.fillMaxSize()
                .onPointerEvent(PointerEventType.Enter) {
                    RobotGo.isActive = true
//                }
//                .onPointerEvent(PointerEventType.Exit) {
//                    RobotGo.isActive = false
                },
        ) {}
    }
}
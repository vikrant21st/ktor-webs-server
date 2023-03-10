package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState

@Composable
fun ApplicationScope.HomeScreen() {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Compose for Desktop",
        state = rememberWindowState(width = 300.dp, height = 300.dp, position = WindowPosition(Alignment.Center))
    ) {
        MaterialTheme {
            Column(Modifier.fillMaxSize(), Arrangement.spacedBy(5.dp)) {
                TextField(ApplicationState.host, onValueChange = { ApplicationState.host = it })
                TextField(ApplicationState.code, onValueChange = { ApplicationState.code = it })

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

                Button(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = ApplicationState::launchClient,
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

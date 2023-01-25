package com.example.robot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.example.eventWraps.KeyPress
import com.example.eventWraps.MouseClick
import com.example.eventWraps.MouseScroll
import com.example.eventWraps.ReleaseEvent
import io.ktor.util.collections.*
import kotlinx.coroutines.*
import java.awt.Robot
import kotlin.math.abs

object RobotGo {
    private val robot = Robot().apply { autoDelay = (2) }
    private val mouseButtonsPressed = ConcurrentSet<Int>()
    private val keysPressed = ConcurrentSet<Int>()
    private val scope = CoroutineScope(Dispatchers.Default)
    var isActive by mutableStateOf(false)

    suspend fun processKeyPress(command: String) {
        scope.async {
            if (command.startsWith("Key"))
                KeyPress.fromString(command)?.run {
                    if (isPressed) {
                        robot.keyPress(keyCode)
                        keysPressed += keyCode
                    } else {
                        robot.keyRelease(keyCode)
                        keysPressed -= keyCode
                    }
                }
        }.await()
    }

    suspend fun process(command: String) =
        scope.async {
            if (command == ReleaseEvent.toString()) {
                mouseButtonsPressed.forEach { robot.mouseRelease(it) }
                keysPressed.forEach { robot.keyRelease(it) }
            } else if (command.startsWith("Mouse"))
                MouseClick.fromString(command)?.run {
                    moveMouseTo(position)

                    if (this is MouseClick) {
                        if (clickCount > 1)
                            repeat(clickCount) {
                                pressMouse(button)
                                delay(5)
                                releaseMouse(button)
                            }
                        else if (pressed)
                            pressMouse(button)
                        else
                            releaseMouse(button)
                    }
                } ?: println("NOT PARSED: $command")
            else if (command.startsWith("Scroll"))
                MouseScroll.fromString(command)?.run {
                    val factor = if (amount < 0) -1 else 1
                    repeat(abs(amount)) {
                        robot.mouseWheel(factor)
                    }
                }
            else
                println("NOT PARSED: $command")
        }.await()

    private fun releaseMouse(button: Int) {
        robot.mouseRelease(button)
        mouseButtonsPressed -= button
    }

    private fun pressMouse(button: Int) {
        robot.mousePress(button)
        mouseButtonsPressed += button
    }

    private fun moveMouseTo(position: Offset) {
        position.run { robot.mouseMove(x.toInt(), y.toInt()) }
    }
}

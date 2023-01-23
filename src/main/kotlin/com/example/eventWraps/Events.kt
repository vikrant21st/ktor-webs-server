package com.example.eventWraps

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import java.awt.event.InputEvent
import java.awt.event.MouseEvent

object ReleaseEvent {
    override fun toString() = "Release"
}

open class MouseMove(
    val position: Offset,
) {
    override fun toString() = "Mouse>>${position.x}-${position.y}"
}

class MouseClick(
    position: Offset,
    button: Int,
    val clickCount: Int,
    val pressed: Boolean,
) : MouseMove(position) {

    val button = when (button) {
        MouseEvent.BUTTON1,
        MouseEvent.BUTTON1_DOWN_MASK ->
            InputEvent.BUTTON1_DOWN_MASK

        MouseEvent.BUTTON3,
        MouseEvent.BUTTON3_DOWN_MASK ->
            InputEvent.BUTTON3_DOWN_MASK

        else ->
            button
    }

    override fun toString() =
        "Mouse>>${position.x}-${position.y}>>${button}>>${clickCount}>>$pressed"

    companion object {
        fun fromString(string: String): MouseMove? {
            if (!string.startsWith("Mouse"))
                return null

            val splits = string.split(">>")
            if (splits.size < 2)
                return null

            val (_, position) = splits
            val (x, y) = position.split('-')
            val mouseMove = MouseMove(position = Offset(x.toFloat(), y.toFloat()))

            if (splits.size == 5) {
                return MouseClick(
                    position = mouseMove.position,
                    button = splits[2].toInt(),
                    clickCount = splits[3].toInt(),
                    pressed = splits[4].toBoolean(),
                )
            }
            return null
        }

        fun mousePressOrRelease(
            it: PointerEvent,
            isPressing: Boolean,
        ): MouseClick? {
            val mouseEvent = it.nativeEvent as? MouseEvent
            mouseEvent ?: return null
            return MouseClick(
                position = Offset(
                    mouseEvent.xOnScreen.toFloat(),
                    mouseEvent.yOnScreen.toFloat()
                ),
                button = mouseEvent.button,
                clickCount = mouseEvent.clickCount,
                pressed = isPressing,
            )
        }
    }
}

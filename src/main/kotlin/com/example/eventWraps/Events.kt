package com.example.eventWraps

import androidx.compose.ui.geometry.Offset
import java.awt.event.InputEvent

class MouseClick(
    val position: Offset,
    button: Int,
    val clickCount: Int,
    val pressed: Boolean,
) {
    val button = when (button) {
        java.awt.event.MouseEvent.BUTTON1,
        java.awt.event.MouseEvent.BUTTON1_DOWN_MASK ->
            InputEvent.BUTTON1_DOWN_MASK

        java.awt.event.MouseEvent.BUTTON3,
        java.awt.event.MouseEvent.BUTTON3_DOWN_MASK ->
            InputEvent.BUTTON3_DOWN_MASK

        else ->
            button
    }

    override fun toString() =
        "Mouse>>${position.x}-${position.y}>>${button}>>${clickCount}>>$pressed"

    companion object {
        fun fromString(string: String): MouseClick? {
            if (!string.startsWith("Mouse"))
                return null

            val splits = string.split(">>")
            if (splits.size != 5)
                return null

            val (_, position, button, clickCount, pressed) = splits
            val (x, y) = position.split('-')
            return MouseClick(
                position = Offset(x.toFloat(), y.toFloat()),
                button = button.toInt(),
                clickCount = clickCount.toInt(),
                pressed = pressed.toBoolean(),
            )
        }
    }
}

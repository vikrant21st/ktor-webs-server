package com.example.eventWraps

import androidx.compose.ui.geometry.Offset

class MouseClick(
    val position: Offset,
    val button: Int,
    val clickCount: Int,
    val pressed: Boolean,
) {
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

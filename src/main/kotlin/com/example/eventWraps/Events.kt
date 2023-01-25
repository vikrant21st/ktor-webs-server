package com.example.eventWraps

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import java.awt.event.InputEvent
import java.awt.event.MouseEvent

sealed interface MouseEvents

object ReleaseEvent: MouseEvents {
    override fun toString() = "Release"
}

open class MouseMove(
    val position: Offset,
): MouseEvents {
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
            return mouseMove
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
                    mouseEvent.yOnScreen.toFloat(),
                ),
                button = mouseEvent.button,
                clickCount = mouseEvent.clickCount,
                pressed = isPressing,
            )
        }
    }
}

data class KeyPress(
    val keyCode: Int,
    val isPressed: Boolean,
) {
    override fun toString(): String = "Key>>$keyCode>>$isPressed"

    companion object {
        fun fromString(string: String): KeyPress? {
            val splits = string.split(">>")
            if (splits.size < 3)
                return null
            val (_, keyCode, isPressed) = splits
            return KeyPress(keyCode.toInt(), isPressed.toBoolean())
        }
    }
}

class MouseScroll(
   val amount: Int,
): MouseEvents {
    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true

        return other is MouseScroll && other.amount == amount
    }

    override fun hashCode(): Int {
        return amount.hashCode()
    }

    override fun toString() = "Scroll>>$amount"

    companion object {
        fun fromString(string: String): MouseScroll? {
            val splits = string.split(">>")
            if (splits.size == 2)
                return MouseScroll(splits[1].toInt())
            return null
        }
    }
}

package com.johnny.wxdexinspector.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 全局统一大圆角规范 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object WxRadius {
    val card = 22.dp
    val dialog = 28.dp
    val chip = 50.dp
    val button = 18.dp
    val listItem = 18.dp
}

package com.johnny.wxdexinspector.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.data.model.NavStyle

private data class NavItem(val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("首页", Icons.Filled.Home),
    NavItem("历史", Icons.Filled.History),
    NavItem("设置", Icons.Filled.Settings),
    NavItem("关于", Icons.Filled.Info)
)

/**
 * 底部导航栏：支持原生标准导航 / 液态玻璃悬浮导航两种风格。
 * @param alpha 液态玻璃透明度 0.2~0.9，实时生效
 */
@Composable
fun WxBottomBar(
    style: NavStyle,
    alpha: Float,
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    when (style) {
        NavStyle.NATIVE -> NativeBar(current, onSelect, modifier)
        NavStyle.GLASS -> GlassBar(alpha, current, onSelect, modifier)
    }
}

@Composable
private fun NativeBar(current: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        NAV_ITEMS.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == current,
                onClick = { onSelect(index) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}

@Composable
private fun GlassBar(alpha: Float, current: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(28.dp)
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(14.dp, shape, clip = false)
                .clip(shape)
                .background(scheme.surface.copy(alpha = alpha.coerceIn(0.2f, 0.9f)))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.55f), shape)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NAV_ITEMS.forEachIndexed { index, item ->
                GlassItem(
                    item = item,
                    selected = index == current,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GlassItem(item: NavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (selected) scheme.primary else scheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            item.label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .width(18.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) scheme.primary else scheme.surface.copy(alpha = 0f))
        )
    }
}

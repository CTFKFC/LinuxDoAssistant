package com.ydm.linuxdo.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** 底栏一项 */
data class GlassNavItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * 可收起的玻璃胶囊底栏。
 *
 * ## 为什么做成可收起
 *
 * 底栏是**浮在内容之上**的，而浏览器页里 Cloudflare 人机验证的复选框
 * 恰好在页面左下角，会被挡住。虽然已经用 `bottomInset` 让内容避让，
 * 但网页可视面积也因此缩水一截。
 *
 * 做成可收起后：需要看网页时点一下收成一枚小胶囊（只剩当前页图标），
 * 要切页时再点开。既不挡人机验证，也不浪费屏幕。
 *
 * 收起态仍显示当前标签的图标，用户不会迷失在哪一页。
 */
@Composable
fun GlassBottomBar(
    items: List<GlassNavItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = LocalIsDarkTheme.current
    val selectedItem = items.firstOrNull { it.key == selectedKey }

    Box(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        GlassSurface(
            shape = RoundedCornerShape(GlassTokens.RadiusPill),
            interactive = false,
            darkTheme = darkTheme,
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 收起/展开把手：收起时只剩它 + 当前页图标
                CollapseHandle(
                    expanded = expanded,
                    onClick = onToggleExpanded,
                )

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(MotionTokens.DurationMedium)) +
                        expandHorizontally(
                            tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEntrance),
                        ),
                    exit = fadeOut(tween(MotionTokens.DurationFast)) +
                        shrinkHorizontally(
                            tween(MotionTokens.DurationFast, easing = MotionTokens.EasingExit),
                        ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items.forEach { item ->
                            GlassNavBarItem(
                                item = item,
                                selected = item.key == selectedKey,
                                onClick = { onSelect(item.key) },
                            )
                        }
                    }
                }

                // 收起态：显示当前页图标，让用户知道自己在哪
                AnimatedVisibility(
                    visible = !expanded,
                    enter = fadeIn(tween(MotionTokens.DurationMedium)),
                    exit = fadeOut(tween(MotionTokens.DurationInstant)),
                ) {
                    selectedItem?.let {
                        Row(
                            modifier = Modifier.padding(end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = it.icon,
                                contentDescription = it.label,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = it.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 左侧的收起/展开把手，用一个会旋转的箭头表示方向 */
@Composable
private fun CollapseHandle(expanded: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else 180f,
        animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasized),
        label = "handle-rotation",
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .selectable(
                selected = false,
                onClick = onClick,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.ArrowForward,
            contentDescription = if (expanded) "收起导航" else "展开导航",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun GlassNavBarItem(
    item: GlassNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val lift by animateFloatAsState(
        targetValue = if (hovered) -3f else 0f,
        animationSpec = tween(MotionTokens.DurationFast, easing = MotionTokens.EasingStandard),
        label = "nav-lift",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            hovered -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(MotionTokens.DurationMedium),
        label = "nav-color",
    )
    val indicatorScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasized),
        label = "nav-indicator",
    )

    Column(
        modifier = modifier
            .graphicsLayer { translationY = lift }
            .hoverable(interactionSource)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .width(16.dp)
                .height(3.dp)
                .graphicsLayer {
                    scaleX = indicatorScale
                    alpha = indicatorScale
                },
        ) {
            drawRoundRect(
                color = contentColor,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
            )
        }
    }
}

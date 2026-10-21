package com.example.drivertracking.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeButton(
    text: String,
    onSwipeComplete: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingText: String? = null
) {
    var width by remember { mutableStateOf(0) }
    val thumbSize = 56.dp
    val thumbSizePx = with(LocalDensity.current) { thumbSize.toPx() }
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var isComplete by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            isComplete = true
            offsetX.animateTo(width - thumbSizePx, tween(200))
        } else {
            isComplete = false
            offsetX.animateTo(0f, tween(300))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbSize)
            .clip(RoundedCornerShape(thumbSize / 2))
            .background(if (enabled) containerColor.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.5f))
            .onSizeChanged { width = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        // Background text
        Text(
            text = text,
            color = if (enabled) containerColor else Color.Gray,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.Center)
        )

        // Progress bar (fills as you swipe)
        Box(
            modifier = Modifier
                .height(thumbSize)
                .width(with(LocalDensity.current) { (offsetX.value + thumbSizePx).toDp() })
                .background(if (enabled) containerColor else Color.LightGray)
        )

        if (isLoading && loadingText != null) {
            Text(
                text = loadingText,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(thumbSize)
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (enabled) contentColor else Color.White)
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = enabled && !isLoading,
                    state = rememberDraggableState { delta ->
                        if (!isComplete) {
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + delta).coerceIn(0f, width - thumbSizePx)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    },
                    onDragStopped = {
                        if (!isComplete && enabled && !isLoading) {
                            val maxOffset = width - thumbSizePx
                            if (offsetX.value > maxOffset * 0.8f) { // 80% swipe completes it
                                coroutineScope.launch {
                                    offsetX.animateTo(maxOffset, tween(200))
                                    isComplete = true
                                    onSwipeComplete()
                                }
                            } else {
                                coroutineScope.launch {
                                    offsetX.animateTo(0f, tween(300))
                                }
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = containerColor,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Swipe to start",
                    tint = if (enabled) containerColor else Color.Gray
                )
            }
        }
    }
}

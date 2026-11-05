package com.example.fileexplorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AssistiveIcon { NOTIFICATIONS, CONTROL_CENTER, SCREENSHOT, BACK, HOME, RECENTS, VOLUME_DOWN, LOCK, VOLUME_UP, POWER }

data class AssistiveAction(val label: String, val icon: AssistiveIcon, val run: () -> Unit)

@Suppress("DEPRECATION")
private fun AssistiveIcon.vector(): ImageVector = when (this) {
    AssistiveIcon.NOTIFICATIONS -> Icons.Filled.Notifications
    AssistiveIcon.CONTROL_CENTER -> Icons.Filled.Tune
    AssistiveIcon.SCREENSHOT -> Icons.Filled.Screenshot
    AssistiveIcon.BACK -> Icons.AutoMirrored.Filled.ArrowBack
    AssistiveIcon.HOME -> Icons.Outlined.Circle
    AssistiveIcon.RECENTS -> Icons.Filled.FilterNone
    AssistiveIcon.VOLUME_DOWN -> Icons.Filled.VolumeDown
    AssistiveIcon.LOCK -> Icons.Filled.Lock
    AssistiveIcon.VOLUME_UP -> Icons.Filled.VolumeUp
    AssistiveIcon.POWER -> Icons.Filled.PowerSettingsNew
}

/** The panel that pops up from the floating button: a dark rounded square of shortcuts, iOS style. */
@Composable
fun AssistiveMenu(actions: List<AssistiveAction>, onDismiss: () -> Unit) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    val noRipple = remember { MutableInteractionSource() }

    // Tapping anywhere outside the panel closes it.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x33000000))
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visibleState = appear, enter = scaleIn(initialScale = 0.6f) + fadeIn()) {
            Column(
                modifier = Modifier
                    .size(300.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color(0xE61C1C1E))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                actions.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { MenuCell(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuCell(action: AssistiveAction) {
    Column(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = action.run)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(action.icon.vector(), contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            action.label, color = Color.White, fontSize = 11.sp, lineHeight = 13.sp,
            textAlign = TextAlign.Center, maxLines = 2,
        )
    }
}

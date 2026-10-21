package com.pori.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pori.assistant.core.ChatLine
import com.pori.assistant.core.PoriController
import com.pori.assistant.core.PoriForegroundService
import com.pori.assistant.core.PoriState
import com.pori.assistant.data.Languages
import com.pori.assistant.ui.PoriColors
import com.pori.assistant.ui.PoriTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val controller = (application as PoriApplication).controller
        setContent { PoriTheme { HomeScreen(controller) } }
    }
}

@Composable
private fun HomeScreen(controller: PoriController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by controller.state.collectAsState()
    val lines by controller.lines.collectAsState()
    val lang by controller.settings.language.collectAsState(initial = "auto")

    var apiKey by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    var serviceOn by remember { mutableStateOf(false) }
    var pendingTalk by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apiKey = controller.settings.apiKeyNow()
        if (apiKey.isBlank()) showSettings = true
    }

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) {
            if (pendingTalk) controller.talkNow() else { PoriForegroundService.start(context); serviceOn = true }
        }
        pendingTalk = false
    }

    fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    fun askPermissions(forTalk: Boolean) {
        pendingTalk = forTalk
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        perms.launch(need.toTypedArray())
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PoriColors.BgTop, PoriColors.BgBottom)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            // Header: name + state + settings
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pori", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = PoriColors.Text)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(stateColor(state)))
                        Spacer(Modifier.size(8.dp))
                        Text(stateLabel(state), fontSize = 14.sp, color = PoriColors.Muted)
                    }
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PoriColors.Surface)
                        .clickable { showSettings = true },
                    contentAlignment = Alignment.Center
                ) { Text("⚙", fontSize = 20.sp, color = PoriColors.Text) }
            }

            // The orb: tap to talk
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Orb(state) {
                    if (hasMic()) controller.talkNow() else askPermissions(true)
                }
            }
            Text(
                if (state == PoriState.IDLE || state == PoriState.WAITING_WAKE) "Tap the orb and speak" else "",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = PoriColors.Muted
            )

            // Conversation
            val listState = rememberLazyListState()
            LaunchedEffect(lines.size) {
                if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lines) { line -> ChatItem(line) }
            }

            // Bottom: always-listening toggle
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Pill(
                    text = if (serviceOn) "Always listening · tap to stop" else "Always listen",
                    filled = serviceOn
                ) {
                    if (serviceOn) { PoriForegroundService.stop(context); serviceOn = false }
                    else if (hasMic()) { PoriForegroundService.start(context); serviceOn = true }
                    else askPermissions(false)
                }
            }
        }
    }

    if (showSettings) {
        var visible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } },
            title = { Text("Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; scope.launch { controller.settings.setApiKey(it) } },
                        label = { Text("Anthropic API key") },
                        supportingText = { Text("Key from console.anthropic.com (starts with sk-ant-)") },
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        trailingIcon = {
                            TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Language", fontSize = 13.sp, color = PoriColors.Muted)
                    Surface(
                        color = PoriColors.Surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showLanguages = true }
                    ) {
                        Text(Languages.nameOf(lang) + "  ▾", Modifier.padding(14.dp), color = PoriColors.Text)
                    }
                    Text(
                        "Pori replies in whatever language you speak. If speech recognition mishears you, pick your language here.",
                        fontSize = 12.sp,
                        color = PoriColors.Muted
                    )
                }
            }
        )
    }

    if (showLanguages) {
        AlertDialog(
            onDismissRequest = { showLanguages = false },
            confirmButton = { TextButton(onClick = { showLanguages = false }) { Text("Close") } },
            title = { Text("Language") },
            text = {
                LazyColumn(Modifier.height(380.dp)) {
                    items(Languages.picker) { l ->
                        val selected = l.tag == lang
                        Text(
                            l.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { controller.settings.setLanguage(l.tag) }
                                    showLanguages = false
                                }
                                .padding(vertical = 12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else PoriColors.Text,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun Orb(state: PoriState, onClick: () -> Unit) {
    val active = state == PoriState.LISTENING || state == PoriState.THINKING || state == PoriState.SPEAKING
    val color by animateColorAsState(stateColor(state), tween(500), label = "orbColor")
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(if (active) 650 else 2200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulseValue"
    )
    Box(
        Modifier
            .size(180.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), radius = r),
                radius = r * pulse
            )
            val core = r * 0.55f * pulse
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.55f)), radius = core),
                radius = core
            )
        }
        Text("🎙", fontSize = 38.sp)
    }
}

@Composable
private fun ChatItem(line: ChatLine) {
    when (line.who) {
        "you" -> Bubble(line.text, mine = true)
        "pori" -> Bubble(line.text, mine = false)
        else -> Text(
            line.text,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = PoriColors.Muted
        )
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (mine) PoriColors.UserBubble else PoriColors.Surface,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = Color.White,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun Pill(text: String, filled: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (filled) PoriColors.UserBubble else PoriColors.Surface,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

private fun stateColor(s: PoriState): Color = when (s) {
    PoriState.IDLE -> Color(0xFF6D5BD0)
    PoriState.WAITING_WAKE -> Color(0xFF7C5CFF)
    PoriState.LISTENING -> Color(0xFFFF6FAE)
    PoriState.THINKING -> Color(0xFFFFC857)
    PoriState.SPEAKING -> Color(0xFF58E6C2)
}

private fun stateLabel(s: PoriState) = when (s) {
    PoriState.IDLE -> "Ready"
    PoriState.WAITING_WAKE -> "Listening for \"Hey Pori\""
    PoriState.LISTENING -> "Listening to you"
    PoriState.THINKING -> "Thinking"
    PoriState.SPEAKING -> "Speaking"
}

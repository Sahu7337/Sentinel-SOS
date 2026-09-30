package com.example.sentinelsos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinelsos.ui.theme.SentinelSOSTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SosCountdownActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sosManager = SosManager(this)
        val isTest = intent.getBooleanExtra("is_test", false)

        setContent {
            SentinelSOSTheme {
                CountdownScreen(
                    isTest = isTest,
                    onCancel = { finish() },
                    onTimeout = {
                        if (isTest) {
                            finish()
                        } else {
                            sosManager.triggerSos {
                                finish()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CountdownScreen(isTest: Boolean, onCancel: () -> Unit, onTimeout: () -> Unit) {
    var timeLeft by remember { mutableIntStateOf(10) }
    var isExecuting by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isTest) Color(0xFF000C1A) // Deep dark blue
                      else if (timeLeft > 3) Color(0xFF1A0000) // Deep dark red
                      else Color(0xFF2D0000), // Slightly warmer dark red
        animationSpec = tween(500),
        label = "bgColor"
    )

    LaunchedEffect(Unit) {
        while (timeLeft > 0 && !isExecuting) {
            delay(1000)
            if (!isExecuting) timeLeft--
        }
        if (!isExecuting) {
            isExecuting = true
            onTimeout()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(), // Ensure column fills width for centering
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isExecuting) "GO" else "$timeLeft",
                        fontSize = 110.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = if (isExecuting) "EXECUTING" else "SECONDS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = if (isTest) "SYSTEM TEST" else "SOS EMERGENCY",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = if (isTest) "Simulating alert sequence..." else "Alerting 112 & Contacts soon",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(56.dp))

            SwipeButton(
                text = "SLIDE TO TRIGGER NOW",
                icon = Icons.Default.FlashOn,
                onSwipeComplete = {
                    if (!isExecuting) {
                        isExecuting = true
                        onTimeout()
                    }
                },
                modifier = Modifier.fillMaxWidth(0.85f),
                backgroundColor = Color.White.copy(alpha = 0.1f),
                thumbColor = Color.White,
                iconColor = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            SwipeButton(
                text = "SLIDE TO CANCEL",
                icon = Icons.Default.Close,
                onSwipeComplete = onCancel,
                modifier = Modifier.fillMaxWidth(0.85f),
                backgroundColor = Color.Black.copy(alpha = 0.15f),
                thumbColor = Color.White,
                iconColor = Color.Red
            )
        }
        
        // Background Warning Icon
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier
                .size(300.dp)
                .offset(y = (-150).dp)
                .alpha(0.05f),
            tint = Color.White
        )
    }
}

@Composable
fun SwipeButton(
    text: String,
    icon: ImageVector,
    onSwipeComplete: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White.copy(alpha = 0.2f),
    thumbColor: Color = Color.White,
    iconColor: Color = Color.Black,
    textColor: Color = Color.White
) {
    val thumbSize = 56.dp
    val trackHeight = 64.dp
    var dragAmount by remember { mutableFloatStateOf(0f) }
    var trackWidth by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val thumbOffset by animateFloatAsState(
        targetValue = dragAmount,
        label = "thumbOffset"
    )

    Box(
        modifier = modifier
            .height(trackHeight)
            .clip(CircleShape)
            .background(backgroundColor)
            .onGloballyPositioned { trackWidth = it.size.width.toFloat() }
    ) {
        Text(
            text = text,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = textColor.copy(alpha = 0.7f)
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.toInt(), 0) }
                .size(thumbSize)
                .padding(4.dp)
                .clip(CircleShape)
                .background(thumbColor)
                .pointerInput(trackWidth) {
                    if (trackWidth <= 0f) return@pointerInput
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragPx ->
                            change.consume()
                            val maxDrag = trackWidth - with(density) { thumbSize.toPx() }
                            dragAmount = (dragAmount + dragPx).coerceIn(0f, maxDrag)
                        },
                        onDragEnd = {
                            val maxDrag = trackWidth - with(density) { thumbSize.toPx() }
                            if (dragAmount >= maxDrag * 0.85f) {
                                dragAmount = maxDrag
                                onSwipeComplete()
                            } else {
                                scope.launch {
                                    Animatable(dragAmount).animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(300)
                                    ) {
                                        dragAmount = value
                                    }
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

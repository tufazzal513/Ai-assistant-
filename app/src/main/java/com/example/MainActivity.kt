package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA // Added for flashlight mapping directly via CameraManager without intent if needed on older APIs
        )
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(notGranted.toTypedArray())
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ArushiAssistantScreen()
                }
            }
        }
    }
}

@Composable
fun ArushiAssistantScreen(
    modifier: Modifier = Modifier,
    viewModel: LiveSessionViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val primaryGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(primaryGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Arushi",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "AI Voice Assistant",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Center Visualizer Section
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AssistantVisualizer(state = state)
            }

            // Suggestions & Status Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (state) {
                        AssistantState.IDLE -> "Tap to start speaking"
                        AssistantState.CONNECTING -> "Connecting..."
                        AssistantState.LISTENING -> "Listening..."
                        AssistantState.SPEAKING -> "Speaking..."
                        AssistantState.ERROR -> "Connection Error"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = if (state == AssistantState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Suggestion Chips
                if (state == AssistantState.IDLE || state == AssistantState.LISTENING) {
                    val suggestions = listOf(
                        "Call Mom",
                        "Open WhatsApp",
                        "Turn on flashlight",
                        "Search YouTube"
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(suggestions) { suggestion ->
                            SuggestionChip(
                                onClick = { /* Visual purely to guide the user */ },
                                label = { Text(suggestion) },
                                shape = RoundedCornerShape(16.dp),
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Main Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Test Speaker Button
                    FilledTonalIconButton(
                        onClick = { viewModel.testSpeaker() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Test Speaker")
                    }

                    // Main Action Button
                    val isActionActive = state == AssistantState.LISTENING || state == AssistantState.SPEAKING || state == AssistantState.CONNECTING
                    FloatingActionButton(
                        onClick = { viewModel.toggleSession() },
                        shape = CircleShape,
                        containerColor = if (isActionActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (isActionActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            imageVector = if (isActionActive) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Toggle Session",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Interrupt Button
                    FilledTonalIconButton(
                        onClick = { viewModel.interrupt() },
                        modifier = Modifier.size(56.dp),
                        enabled = isActionActive
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = "Interrupt")
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantVisualizer(state: AssistantState) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val waveScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale"
    )

    val color = when (state) {
        AssistantState.IDLE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        AssistantState.CONNECTING -> Color(0xFFF6A500).copy(alpha = 0.5f)
        AssistantState.LISTENING -> Color(0xFF4CAF50).copy(alpha = 0.6f)
        AssistantState.SPEAKING -> Color(0xFF2196F3).copy(alpha = 0.6f)
        AssistantState.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
    }

    val currentScale = when (state) {
        AssistantState.LISTENING -> pulseScale
        AssistantState.SPEAKING -> waveScale
        else -> 1.0f
    }

    Box(
        modifier = Modifier
            .size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(currentScale)
                .clip(CircleShape)
                .background(color)
        )
        
        if (state == AssistantState.SPEAKING) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(waveScale * 0.8f)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.3f))
            )
        }
        
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (state == AssistantState.IDLE) MaterialTheme.colorScheme.surfaceVariant else color.copy(alpha = 0.9f))
        )
    }
}

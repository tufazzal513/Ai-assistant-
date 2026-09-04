package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Handle permission results
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(notGranted.toTypedArray())
        }

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ArushiAssistantScreen(modifier = Modifier.padding(innerPadding))
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Arushi AI Assistant",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Status: \${state.name}",
            style = MaterialTheme.typography.titleMedium
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(64.dp))

        val indicatorColor = when (state) {
            AssistantState.IDLE -> Color.Gray
            AssistantState.CONNECTING -> Color(0xFFF6A500) // Amber
            AssistantState.LISTENING -> Color(0xFF4CAF50) // Green
            AssistantState.SPEAKING -> Color(0xFF2196F3) // Blue
            AssistantState.ERROR -> Color.Red
        }

        Box(
            modifier = Modifier
                .size(160.dp)
                .background(indicatorColor.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(
                onClick = { viewModel.toggleSession() },
                shape = CircleShape,
                containerColor = indicatorColor,
                contentColor = Color.White,
                modifier = Modifier.size(90.dp)
            ) {
                Icon(
                    imageVector = if (state == AssistantState.IDLE || state == AssistantState.ERROR) Icons.Default.Mic else Icons.Default.Stop,
                    contentDescription = "Toggle Session",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = { viewModel.testSpeaker() }) {
            Icon(Icons.Default.VolumeUp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Test Speaker")
        }
        
        if (state == AssistantState.SPEAKING || state == AssistantState.LISTENING) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.interrupt() }) {
                Text("Interrupt")
            }
        }
    }
}

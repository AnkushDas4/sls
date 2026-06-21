package com.sednium.localspaces.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ErrorBoundary(
    content: @Composable () -> Unit
) {
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // While we cannot wrap `@Composable` content in a try-catch block due to Compose Compiler
    // enforcement, we can intercept global uncaught exceptions and switch the UI state.
    DisposableEffect(Unit) {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            hasError = true
            errorMessage = throwable.localizedMessage ?: "Unknown error occurred"
            // We do not call originalHandler here so the app doesn't crash to home screen,
            // though keeping a crashed thread alive may have undefined behavior.
        }
        onDispose {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }

    if (hasError) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = "Something went wrong.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
                Button(onClick = { 
                    hasError = false 
                    errorMessage = null
                }) {
                    Text("Try Again")
                }
            }
        }
    } else {
        content()
    }
}

package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var allergiesText by remember { mutableStateOf("") }
    var dislikesText by remember { mutableStateOf("") }
    var goalsText by remember { mutableStateOf("") }

    LaunchedEffect(preferences) {
        allergiesText = preferences.allergies.joinToString(", ")
        dislikesText = preferences.dislikes.joinToString(", ")
        goalsText = preferences.healthGoals.joinToString(", ")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Preferences") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Use comma-separated values.")
            OutlinedTextField(
                value = allergiesText,
                onValueChange = { allergiesText = it },
                label = { Text(text = "Allergies") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = dislikesText,
                onValueChange = { dislikesText = it },
                label = { Text(text = "Dislikes") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = goalsText,
                onValueChange = { goalsText = it },
                label = { Text(text = "Health Goals (low_sugar, low_salt)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    viewModel.savePreferences(allergiesText, dislikesText, goalsText)
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(12.dp)
            ) {
                Text(text = "Save Preferences")
            }
        }
    }
}

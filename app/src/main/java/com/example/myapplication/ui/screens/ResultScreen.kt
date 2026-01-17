package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.data.model.MenuItem
import com.example.myapplication.data.model.RiskHit
import com.example.myapplication.viewmodel.ResultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    historyId: Long,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(historyId) {
        viewModel.load(historyId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Analysis Result") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = uiState.errorMessage ?: "Unknown error")
                }
            }
            else -> {
                val response = uiState.response
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = "Risk Level: ${response?.riskLevel ?: "UNKNOWN"}")
                            }
                        }
                    }
                    item {
                        SectionCard(
                            title = "Suggestions",
                            lines = response?.suggestions ?: emptyList()
                        )
                    }
                    item {
                        HitsSection(hits = response?.hits ?: emptyList())
                    }
                    item {
                        MenuItemsSection(items = response?.menuItems ?: emptyList())
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title)
            if (lines.isEmpty()) {
                Text(text = "None")
            } else {
                lines.forEach { line ->
                    Text(text = line)
                }
            }
        }
    }
}

@Composable
private fun HitsSection(hits: List<RiskHit>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Hits")
            if (hits.isEmpty()) {
                Text(text = "None")
            } else {
                hits.forEach { hit ->
                    Text(text = "${hit.term} - ${hit.level} (${hit.reason})")
                }
            }
        }
    }
}

@Composable
private fun MenuItemsSection(items: List<MenuItem>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Menu Items")
            if (items.isEmpty()) {
                Text(text = "None")
            } else {
                items.forEach { item ->
                    Text(text = item.name)
                    if (item.ingredients.isNotEmpty()) {
                        Text(text = "Ingredients: ${item.ingredients.joinToString(", ")}")
                    }
                }
            }
        }
    }
}

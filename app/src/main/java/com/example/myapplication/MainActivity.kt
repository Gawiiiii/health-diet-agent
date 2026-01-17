package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.ui.AppNavHost
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.AppViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MyApplication
        val viewModelFactory = AppViewModelFactory(
            application = application,
            analysisRepository = app.container.analysisRepository,
            preferencesRepository = app.container.preferencesRepository
        )
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavHost(viewModelFactory = viewModelFactory)
            }
        }
    }
}

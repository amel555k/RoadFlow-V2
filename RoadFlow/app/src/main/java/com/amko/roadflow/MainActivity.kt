package com.amko.roadflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.amko.roadflow.presentation.screens.MainScreen
import com.amko.roadflow.ui.theme.RoadFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoadFlowTheme {
                MainScreen()
            }
        }
    }
}
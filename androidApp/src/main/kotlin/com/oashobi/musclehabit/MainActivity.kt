package com.oashobi.musclehabit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.oashobi.musclehabit.ui.MainScreen
import com.oashobi.musclehabit.ui.theme.MuscleHabitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuscleHabitTheme {
                MainScreen()
            }
        }
    }
}

package com.example.radioplayer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.radioplayer.ui.screens.PlayerScreen
import com.example.radioplayer.ui.theme.RadioPlayerTheme
import com.example.radioplayer.viewmodel.RadioViewModel

class MainActivity : ComponentActivity() {

    private val viewModel : RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.initController(this)

        setContent {
            RadioPlayerTheme {
                PlayerScreen(viewModel)
            }
        }
    }

}
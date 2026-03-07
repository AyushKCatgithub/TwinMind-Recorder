package com.example.twinmind

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.twinmind.service.workers.TerminationRecoveryWorker
import com.example.twinmind.ui.navigation.TwinMindNavGraph
import com.example.twinmind.ui.theme.TwinMindTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        enqueueRecoveryWorker()

        setContent {
            TwinMindTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    TwinMindNavGraph(navController = navController)
                }
            }
        }
    }

    private fun enqueueRecoveryWorker() {
        val workRequest = OneTimeWorkRequestBuilder<TerminationRecoveryWorker>()
            .addTag("termination_recovery")
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueue(workRequest)
    }
}

package com.paperscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.paperscan.ui.PaperScanApp
import com.paperscan.viewmodel.ScanSessionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as PaperScanApplication
        val viewModel = ViewModelProvider(
            this,
            ScanSessionViewModel.Factory(application, application.container)
        )[ScanSessionViewModel::class.java]
        setContent { PaperScanApp(viewModel) }
    }
}

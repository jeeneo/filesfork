package me.zhanghai.android.files.sync

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.theme.AppTheme

class SyncActivity : AppActivity() {
    private val viewModel: SyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            return
        }
        enableEdgeToEdge()
        setContent {
            AppTheme {
                SyncScreen(viewModel)
            }
        }
    }
}

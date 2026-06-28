/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.viewer.image

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import java8.nio.file.Path
import me.zhanghai.android.filesfork.app.AppActivity
import me.zhanghai.android.filesfork.util.extraPathList

class ImageViewerActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val intent = intent
        val paths = intent.extraPathList
        val position = intent.getIntExtra(EXTRA_POSITION, 0)
        setContent {
            ImageViewerScreen(
                paths = paths,
                initialPosition = position,
                onNavigateUp = { finish() }
            )
        }
    }

    companion object {
        private val EXTRA_POSITION = "${ImageViewerActivity::class.java.name}.extra.POSITION"

        fun putExtras(intent: Intent, paths: List<Path>, position: Int) {
            intent.extraPathList = paths
            intent.putExtra(EXTRA_POSITION, position)
        }
    }
}

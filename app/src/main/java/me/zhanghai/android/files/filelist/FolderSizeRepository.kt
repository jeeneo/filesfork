/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.filesfork.filelist

import java8.nio.file.FileVisitResult
import java8.nio.file.FileVisitor
import java8.nio.file.Files
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Collections

enum class CalcSizesOption {
    NEVER, LOCAL_ONLY, ALWAYS;
}

object FolderSizeRepository {
    private const val CACHE_MAX_SIZE = 256
    private const val PROGRESS_INTERVAL_MS = 150L

    private val cache: MutableMap<Path, Long> = Collections.synchronizedMap(object :
        LinkedHashMap<Path, Long>(CACHE_MAX_SIZE + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Path, Long>): Boolean =
            size > CACHE_MAX_SIZE
    })
    private val jobsLock = Any()
    private val inFlightJobs = mutableMapOf<Path, Job>()

    fun invalidateAll() {
        cache.clear()
    }

    fun computeAsync(
        scope: CoroutineScope,
        path: Path,
        onProgress: (sizeBytes: Long, isComplete: Boolean) -> Unit
    ) {
        val cached = cache[path]
        if (cached != null) {
            onProgress(cached, true)
            return
        }
        synchronized(jobsLock) {
            if (inFlightJobs.containsKey(path)) return
        }
        val job = scope.launch {
            runWalk(path, onProgress)
        }
        synchronized(jobsLock) {
            inFlightJobs[path] = job
        }
        job.invokeOnCompletion {
            synchronized(jobsLock) { inFlightJobs.remove(path) }
        }
    }

    fun cancelAll() {
        val jobs: List<Job>
        synchronized(jobsLock) {
            jobs = inFlightJobs.values.toList()
            inFlightJobs.clear()
        }
        jobs.forEach { it.cancel() }
    }

    private suspend fun runWalk(
        directory: Path, onProgress: (Long, Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        var totalSize = 0L
        suspend fun notify(isComplete: Boolean) {
            val size = totalSize
            if (!isActive) return
            withContext(Dispatchers.Main.immediate) {
                if (isActive) {
                    onProgress(size, isComplete)
                }
            }
        }
        try {
            Files.walkFileTree(directory, object : FileVisitor<Path> {
                private var lastNotifyMs = System.currentTimeMillis()
                override fun preVisitDirectory(
                    dir: Path, attrs: BasicFileAttributes
                ): FileVisitResult {
                    if (!isActive) return FileVisitResult.TERMINATE
                    // Count directory entry itself (usually 4 KiB on ext4, but size() == 0
                    // for most virtual FS – we add it unconditionally and let the kernel decide).
                    if (dir != directory) {
                        totalSize += attrs.size()
                        maybeNotify()
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path, attrs: BasicFileAttributes
                ): FileVisitResult {
                    if (!isActive) return FileVisitResult.TERMINATE
                    totalSize += attrs.size()
                    maybeNotify()
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    file: Path, exc: IOException
                ): FileVisitResult {
                    exc.printStackTrace()
                    return if (isActive) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
                }

                override fun postVisitDirectory(
                    dir: Path, exc: IOException?
                ): FileVisitResult {
                    exc?.printStackTrace()
                    return if (isActive) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
                }

                private fun maybeNotify() {
                    val now = System.currentTimeMillis()
                    if (now >= lastNotifyMs + PROGRESS_INTERVAL_MS) {
                        lastNotifyMs = now
                        kotlinx.coroutines.runBlocking(Dispatchers.Main.immediate) {
                            if (this@withContext.isActive) {
                                onProgress(totalSize, false)
                            }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (isActive) {
            cache[directory] = totalSize
        }
        notify(isComplete = true)
    }
}

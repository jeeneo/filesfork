package me.zhanghai.android.files.sync

import android.content.SharedPreferences
import java8.nio.file.Path
import java8.nio.file.Paths
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val GROUP_KEY_PREFIX = "group"
private const val KEY_GROUP_COUNT = "group.count"
private const val SOURCE_KEY = "source"
private const val KEY_ID = "id"
private const val KEY_DISPLAY_NAME = "displayName"
private const val KEY_TARGET = "target"
private const val KEY_DIRECTION = "direction"
private const val KEY_CONFLICT_POLICY = "conflictPolicy"
private const val KEY_DELETE_EXTRANEOUS = "deleteExtraneous"
private const val KEY_PRESERVE_TIMESTAMPS = "preserveTimestamps"
private const val KEY_SOURCE_COUNT = "sourceCount"
private const val KEY_PATH = "path"
private const val KEY_IS_DIRECTORY = "isDirectory"

private fun groupKey(index: Int, key: String): String = "$GROUP_KEY_PREFIX.$index.$key"

private fun sourceKey(groupIndex: Int, sourceIndex: Int, key: String): String =
    "$GROUP_KEY_PREFIX.$groupIndex.$SOURCE_KEY.$sourceIndex.$key"

class SyncGroupStore(private val preferences: SharedPreferences) {
    private val writeMutex = Mutex()

    fun getGroups(): List<SyncGroup> {
        val count = preferences.getInt(KEY_GROUP_COUNT, 0)
        return buildList {
            for (index in 0 until count) {
                group(index)?.let { add(it) }
            }
        }
    }

    suspend fun update(
        transform: (List<SyncGroup>) -> List<SyncGroup>,
    ): List<SyncGroup> = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val groups = transform(getGroups())
            val entries = groups.toEntries()
            val staleKeys = preferences.all.keys.filterNot { it in entries }
            val editor = preferences.edit()
            for ((key, value) in entries) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    else -> throw IllegalArgumentException("Unsupported preference value: $key")
                }
            }
            for (key in staleKeys) {
                editor.remove(key)
            }
            if (!editor.commit()) {
                throw IOException("Failed to save sync groups")
            }
            groups
        }
    }

    private fun group(index: Int): SyncGroup? {
        val id = preferences.getString(groupKey(index, KEY_ID), null)
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val direction = preferences.getString(groupKey(index, KEY_DIRECTION), null)
            ?.let { name -> SyncDirection.entries.firstOrNull { it.name == name } }
            ?: SyncDirection.Push
        val conflictPolicy = preferences.getString(groupKey(index, KEY_CONFLICT_POLICY), null)
            ?.let { name -> ConflictPolicy.entries.firstOrNull { it.name == name } }
            ?.takeIf { direction.supports(it) }
            ?: direction.defaultPolicy
        val displayName = preferences.getString(groupKey(index, KEY_DISPLAY_NAME), null)
            ?.takeIf { it.isNotEmpty() }
        val targetDirectory = preferences.getString(groupKey(index, KEY_TARGET), null)
            ?.takeIf { it.isNotEmpty() }
            ?.toPathOrNull()
        val sourceCount = preferences.getInt(groupKey(index, KEY_SOURCE_COUNT), 0)
        val sourceItems = buildList {
            for (sourceIndex in 0 until sourceCount) {
                source(index, sourceIndex)?.let { add(it) }
            }
        }
        return SyncGroup(
            id = id,
            displayName = displayName,
            sourceItems = sourceItems,
            targetDirectory = targetDirectory,
            options = SyncOptions(
                direction = direction,
                conflictPolicy = conflictPolicy,
                deleteExtraneous = preferences.getBoolean(
                    groupKey(index, KEY_DELETE_EXTRANEOUS), false,
                ),
                preserveTimestamps = preferences.getBoolean(
                    groupKey(index, KEY_PRESERVE_TIMESTAMPS), true,
                ),
            ),
        )
    }

    private fun source(groupIndex: Int, sourceIndex: Int): SyncSourceItem? {
        val path = preferences.getString(sourceKey(groupIndex, sourceIndex, KEY_PATH), null)
            ?.takeIf { it.isNotEmpty() }
            ?.toPathOrNull()
            ?: return null
        return SyncSourceItem(
            path = path,
            isDirectory = preferences.getBoolean(
                sourceKey(groupIndex, sourceIndex, KEY_IS_DIRECTORY), false,
            ),
        )
    }
}

private fun List<SyncGroup>.toEntries(): Map<String, Any> {
    val count = size
    return buildMap<String, Any> {
        put(KEY_GROUP_COUNT, count)
        forEachIndexed { index, group -> putAll(group.toEntries(index)) }
    }
}

private fun SyncGroup.toEntries(index: Int): Map<String, Any> = buildMap<String, Any> {
    put(groupKey(index, KEY_ID), id)
    displayName?.let { put(groupKey(index, KEY_DISPLAY_NAME), it) }
    targetDirectory?.let { put(groupKey(index, KEY_TARGET), it.toUri().toString()) }
    put(groupKey(index, KEY_DIRECTION), options.direction.name)
    put(groupKey(index, KEY_CONFLICT_POLICY), options.conflictPolicy.name)
    put(groupKey(index, KEY_DELETE_EXTRANEOUS), options.deleteExtraneous)
    put(groupKey(index, KEY_PRESERVE_TIMESTAMPS), options.preserveTimestamps)
    put(groupKey(index, KEY_SOURCE_COUNT), sourceItems.size)
    sourceItems.forEachIndexed { sourceIndex, item ->
        put(sourceKey(index, sourceIndex, KEY_PATH), item.path.toUri().toString())
        put(sourceKey(index, sourceIndex, KEY_IS_DIRECTORY), item.isDirectory)
    }
}

private fun String.toPathOrNull(): Path? =
    try {
        Paths.get(URI.create(this))
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

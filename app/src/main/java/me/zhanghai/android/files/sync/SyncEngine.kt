package me.zhanghai.android.files.sync

import java8.nio.file.CopyOption
import java8.nio.file.LinkOption
import java8.nio.file.NoSuchFileException
import java8.nio.file.Path
import java8.nio.file.StandardCopyOption
import java8.nio.file.attribute.BasicFileAttributes
import me.zhanghai.android.files.provider.common.copyTo
import me.zhanghai.android.files.provider.common.createDirectories
import me.zhanghai.android.files.provider.common.deleteIfExists
import me.zhanghai.android.files.provider.common.isDirectory
import me.zhanghai.android.files.provider.common.newDirectoryStream
import me.zhanghai.android.files.provider.common.readAttributes
import me.zhanghai.android.files.provider.common.resolveForeign

internal object SyncEngine {
    private enum class Side(
        val operation: SyncOperation,
        val deleteOperation: SyncOperation,
    ) {
        Source(SyncOperation.Push, SyncOperation.DeleteSource), Target(
            SyncOperation.Pull, SyncOperation.DeleteTarget
        ),
    }

    fun plan(
        sourceItems: List<SyncSourceItem>,
        targetDirectory: Path,
        options: SyncOptions,
    ): SyncPlan {
        val actions = mutableListOf<SyncAction>()
        val missingOnTarget = mutableListOf<SyncMissingPair>()
        val missingOnSource = mutableListOf<SyncMissingPair>()
        for ((path) in sourceItems) {
            val target =
                path.fileName?.let { targetDirectory.resolveForeign(it) } ?: targetDirectory
            if (path == target || target.startsWith(path)) {
                // don't sync into self
                continue
            }
            when (options.direction) {
                SyncDirection.Push -> planOneWay(
                    path, target, Side.Source, actions, options
                )

                SyncDirection.Pull -> planOneWay(
                    target, path, Side.Target, actions, options
                )

                SyncDirection.TwoWay -> planTwoWay(
                    path, target, options, actions, missingOnTarget, missingOnSource
                )
            }
        }
        return SyncPlan(actions, missingOnTarget, missingOnSource)
    }

    fun execute(action: SyncAction, options: SyncOptions) {
        when (action) {
            is SyncAction.CreateDirectory -> action.path.createDirectories()

            is SyncAction.CopyFile -> {
                action.path.parent?.createDirectories()
                action.source.copyTo(action.path, *copyOptions(options))
            }

            is SyncAction.Delete -> action.path.deleteRecursively()
        }
    }

    internal fun resolveMissing(
        plan: SyncPlan,
        resolution: SyncMissingResolution,
    ): SyncPlan {
        val actions = plan.actions.toMutableList()
        for ((source, target) in plan.missingOnTarget) {
            when (resolution.missingOnTarget) {
                SyncMissingAction.Copy -> planCopy(
                    source, target, SyncOperation.Push, actions
                )

                SyncMissingAction.Delete -> actions += SyncAction.Delete(
                    source, SyncOperation.DeleteSource
                )

                SyncMissingAction.Skip -> Unit
            }
        }
        for ((source, target) in plan.missingOnSource) {
            when (resolution.missingOnSource) {
                SyncMissingAction.Copy -> planCopy(
                    target, source, SyncOperation.Pull, actions
                )

                SyncMissingAction.Delete -> actions += SyncAction.Delete(
                    target, SyncOperation.DeleteTarget
                )

                SyncMissingAction.Skip -> Unit
            }
        }
        return SyncPlan(actions)
    }

    private fun planOneWay(
        origin: Path,
        destination: Path,
        side: Side,
        actions: MutableList<SyncAction>,
        options: SyncOptions,
    ) {
        checkInterrupted()
        val originAttributes = origin.attributesOrNull()
        val destinationAttributes = destination.attributesOrNull()
        when {
            originAttributes == null -> if (destinationAttributes != null && options.deleteExtraneous) {
                actions += SyncAction.Delete(destination, side.other.deleteOperation)
            }

            destinationAttributes == null -> planCopy(origin, destination, side.operation, actions)

            originAttributes.isDirectory != destinationAttributes.isDirectory -> if (conflictWinner(
                    originAttributes, destinationAttributes, options
                ) == side
            ) {
                actions += SyncAction.Delete(destination, side.other.deleteOperation)
                planCopy(origin, destination, side.operation, actions)
            }

            originAttributes.isDirectory -> {
                val originChildren = listChildren(origin)
                val destinationChildren = listChildren(destination)
                for ((name, originChild) in originChildren) {
                    val destinationChild = destinationChildren[name] ?: destination.resolveForeign(
                        originChild.fileName!!
                    )
                    planOneWay(originChild, destinationChild, side, actions, options)
                }
                if (options.deleteExtraneous) {
                    for ((name, destinationChild) in destinationChildren) {
                        if (name !in originChildren) {
                            actions += SyncAction.Delete(
                                destinationChild, side.other.deleteOperation
                            )
                        }
                    }
                }
            }

            conflictWinner(originAttributes, destinationAttributes, options) == side && isDifferent(
                originAttributes, destinationAttributes
            ) -> actions += SyncAction.CopyFile(origin, destination, side.operation)
        }
    }

    private fun planTwoWay(
        source: Path,
        target: Path,
        options: SyncOptions,
        actions: MutableList<SyncAction>,
        missingOnTarget: MutableList<SyncMissingPair>,
        missingOnSource: MutableList<SyncMissingPair>,
    ) {
        checkInterrupted()
        val sourceAttributes = source.attributesOrNull()
        val targetAttributes = target.attributesOrNull()
        if (sourceAttributes == null) {
            if (targetAttributes != null) {
                missingOnSource += SyncMissingPair(source, target)
            }
            return
        }
        if (targetAttributes == null) {
            missingOnTarget += SyncMissingPair(source, target)
            return
        }
        if (sourceAttributes.isDirectory != targetAttributes.isDirectory) {
            when (conflictWinner(sourceAttributes, targetAttributes, options)) {
                Side.Source -> {
                    actions += SyncAction.Delete(target, SyncOperation.DeleteTarget)
                    planCopy(source, target, SyncOperation.Push, actions)
                }

                Side.Target -> {
                    actions += SyncAction.Delete(source, SyncOperation.DeleteSource)
                    planCopy(target, source, SyncOperation.Pull, actions)
                }

                null -> Unit
            }
            return
        }
        if (sourceAttributes.isDirectory) {
            val sourceChildren = listChildren(source)
            val targetChildren = listChildren(target)
            for ((name, sourceChild) in sourceChildren) {
                val targetChild =
                    targetChildren[name] ?: target.resolveForeign(sourceChild.fileName!!)
                planTwoWay(
                    sourceChild, targetChild, options, actions, missingOnTarget, missingOnSource
                )
            }
            for ((name, targetChild) in targetChildren) {
                if (name !in sourceChildren) {
                    planTwoWay(
                        source.resolveForeign(targetChild.fileName!!),
                        targetChild,
                        options,
                        actions,
                        missingOnTarget,
                        missingOnSource,
                    )
                }
            }
            return
        }
        when (conflictWinner(sourceAttributes, targetAttributes, options)) {
            Side.Source -> if (isDifferent(sourceAttributes, targetAttributes)) {
                actions += SyncAction.CopyFile(source, target, SyncOperation.Push)
            }

            Side.Target -> if (isDifferent(targetAttributes, sourceAttributes)) {
                actions += SyncAction.CopyFile(target, source, SyncOperation.Pull)
            }

            null -> Unit
        }
    }

    private fun planCopy(
        source: Path,
        target: Path,
        operation: SyncOperation,
        actions: MutableList<SyncAction>,
    ) {
        checkInterrupted()
        val sourceAttributes = source.attributesOrNull() ?: return
        if (sourceAttributes.isDirectory) {
            actions += SyncAction.CreateDirectory(target, operation)
            for (child in listChildren(source).values) {
                planCopy(child, target.resolveForeign(child.fileName!!), operation, actions)
            }
        } else {
            actions += SyncAction.CopyFile(source, target, operation)
        }
    }

    private fun Path.attributesOrNull(): BasicFileAttributes? = try {
        readAttributes(BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (_: NoSuchFileException) {
        null
    }

    private fun listChildren(directory: Path): Map<String, Path> =
        directory.newDirectoryStream().use { directoryStream ->
            buildMap {
                for (child in directoryStream) {
                    checkInterrupted()
                    val name = child.fileName?.toString() ?: continue
                    put(name, child)
                }
            }
        }

    private fun conflictWinner(
        originAttributes: BasicFileAttributes,
        destinationAttributes: BasicFileAttributes,
        options: SyncOptions,
    ): Side? = when (options.conflictPolicy) {
        ConflictPolicy.OverwriteTarget -> Side.Source
        ConflictPolicy.OverwriteSource -> Side.Target
        ConflictPolicy.KeepNewer -> {
            val originTime = originAttributes.lastModifiedTime()
            val destinationTime = destinationAttributes.lastModifiedTime()
            when {
                originTime > destinationTime -> Side.Source
                destinationTime > originTime -> Side.Target
                else -> null
            }
        }

        ConflictPolicy.Skip -> null
    }

    private val Side.other: Side
        get() = when (this) {
            Side.Source -> Side.Target
            Side.Target -> Side.Source
        }

    private fun isDifferent(
        sourceAttributes: BasicFileAttributes,
        targetAttributes: BasicFileAttributes,
    ): Boolean =
        sourceAttributes.size() != targetAttributes.size() || sourceAttributes.lastModifiedTime() != targetAttributes.lastModifiedTime()

    private fun copyOptions(options: SyncOptions): Array<CopyOption> =
        mutableListOf<CopyOption>().apply {
            this += LinkOption.NOFOLLOW_LINKS
            this += StandardCopyOption.REPLACE_EXISTING
            if (options.preserveTimestamps) {
                this += StandardCopyOption.COPY_ATTRIBUTES
            }
        }.toTypedArray()

    private fun Path.deleteRecursively() {
        checkInterrupted()
        if (isDirectory(LinkOption.NOFOLLOW_LINKS)) {
            newDirectoryStream().use { directoryStream ->
                for (child in directoryStream) {
                    child.deleteRecursively()
                }
            }
        }
        deleteIfExists()
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException()
        }
    }
}

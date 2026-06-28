/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

@file:Suppress("SpellCheckingInspection")

package me.zhanghai.android.filesfork.filelist

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.filesfork.R
import me.zhanghai.android.filesfork.databinding.CreateArchiveDialogBinding
import me.zhanghai.android.filesfork.databinding.NameDialogNameIncludeBinding
import me.zhanghai.android.filesfork.settings.Settings
import me.zhanghai.android.filesfork.util.ParcelableArgs
import me.zhanghai.android.filesfork.util.args
import me.zhanghai.android.filesfork.util.putArgs
import me.zhanghai.android.filesfork.util.setTextWithSelection
import me.zhanghai.android.filesfork.util.show
import me.zhanghai.android.filesfork.util.takeIfNotEmpty
import me.zhanghai.android.filesfork.util.valueCompat
import me.zhanghai.android.libarchive.Archive
import kotlin.math.roundToInt

open class CreateArchiveDialogFragment : FileNameDialogFragment() {
    private val args by args<Args>()

    override val binding: Binding
        get() = super.binding as Binding

    override val listener: Listener
        get() = super.listener as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)

        if (savedInstanceState == null) {
            val files = args.files
            var name: String? = null
            if (files.size == 1) {
                name = files.single().path.fileName.toString()
            } else {
                val parent = files.mapTo(mutableSetOf()) { it.path.parent }.singleOrNull()
                if (parent != null && parent.nameCount > 0) {
                    name = parent.fileName.toString()
                }
            }
            name?.let { binding.nameEdit.setTextWithSelection(it) }
        }
        binding.typeDropdown.setAdapter(
            ArrayAdapter(binding.typeDropdown.context, R.layout.dropdown_item, TYPE_OPTIONS)
        )
        if (savedInstanceState == null) {
            binding.typeDropdown.setText(Settings.CREATE_ARCHIVE_TYPE.valueCompat.label, false)
            binding.compressionSlider.value =
                Settings.CREATE_ARCHIVE_COMPRESSION_LEVEL.valueCompat.toFloat()
        }
        binding.typeDropdown.doAfterTextChanged {
            Settings.CREATE_ARCHIVE_TYPE.putValue(selectedType)
            updatePasswordLayoutVisibility()
            updateCompressionLayoutVisibility()
            updateCompressionSliderRange()
        }
        binding.compressionSlider.addOnChangeListener { _, value, _ ->
            if (isCompressionSupported) {
                Settings.CREATE_ARCHIVE_COMPRESSION_LEVEL.putValue(value.roundToInt())
            }
        }
        updateArchiveOptionsVisibility()
        updateCompressionSliderRange()
        return dialog
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        updateArchiveOptionsVisibility()
        updateCompressionSliderRange()
    }

    @StringRes
    override val titleRes: Int = R.string.file_create_archive_title

    override fun onInflateBinding(inflater: LayoutInflater): NameDialogFragment.Binding =
        Binding.inflate(inflater)

    override val name: String
        get() = "${super.name}.${selectedType.extension}"

    private val selectedType: ArchiveType
        get() = ArchiveType.fromLabel(binding.typeDropdown.text?.toString()?.trim())
            ?: ArchiveType.DEFAULT

    private val isPasswordSupported: Boolean
        get() = selectedType.supportsPassword

    private val isCompressionSupported: Boolean
        get() = selectedType.supportsCompression

    private fun updateArchiveOptionsVisibility() {
        updatePasswordLayoutVisibility()
        updateCompressionLayoutVisibility()
    }

    private fun updatePasswordLayoutVisibility() {
        binding.passwordLayout.isGone = !isPasswordSupported
    }

    private fun updateCompressionLayoutVisibility() {
        binding.compressionLayout.isGone = !isCompressionSupported
    }

    private fun updateCompressionSliderRange() {
        val type = selectedType
        val minCompressionLevel = type.minCompressionLevel.toFloat()
        val maxCompressionLevel = type.maxCompressionLevel.toFloat()
        binding.compressionSlider.valueFrom = minCompressionLevel
        binding.compressionSlider.valueTo = maxCompressionLevel
        val value =
            binding.compressionSlider.value.coerceIn(minCompressionLevel, maxCompressionLevel)
        if (binding.compressionSlider.value != value) {
            binding.compressionSlider.value = value
        }
    }

    override fun onOk(name: String) {
        val type = selectedType
        val password = if (isPasswordSupported) {
            binding.passwordEdit.text!!.toString().takeIfNotEmpty()
        } else {
            null
        }
        val compressionLevel = if (isCompressionSupported) {
            binding.compressionSlider.value.roundToInt()
        } else {
            null
        }
        listener.archive(
            args.files,
            name,
            type.format,
            type.filter,
            type.compressionTarget,
            password,
            compressionLevel
        )
    }

    companion object {
        private val TYPE_OPTIONS = ArchiveType.entries.map { it.label }

        fun show(files: FileItemSet, fragment: Fragment) {
            CreateArchiveDialogFragment().putArgs(Args(files)).show(fragment)
        }
    }

    @Parcelize
    class Args(val files: FileItemSet) : ParcelableArgs

    protected class Binding private constructor(
        root: View,
        nameLayout: TextInputLayout,
        nameEdit: EditText,
        val typeDropdown: AutoCompleteTextView,
        val compressionLayout: LinearLayout,
        val compressionSlider: Slider,
        val passwordLayout: TextInputLayout,
        val passwordEdit: TextInputEditText
    ) : NameDialogFragment.Binding(root, nameLayout, nameEdit) {
        companion object {
            fun inflate(inflater: LayoutInflater): Binding {
                val binding = CreateArchiveDialogBinding.inflate(inflater)
                val bindingRoot = binding.root
                val nameBinding = NameDialogNameIncludeBinding.bind(bindingRoot)
                return Binding(
                    bindingRoot,
                    nameBinding.nameLayout,
                    nameBinding.nameEdit,
                    binding.typeDropdown,
                    binding.compressionLayout,
                    binding.compressionSlider,
                    binding.passwordLayout,
                    binding.passwordEdit
                )
            }
        }
    }

    interface Listener : FileNameDialogFragment.Listener {
        fun archive(
            files: FileItemSet,
            name: String,
            format: Int,
            filter: Int,
            compressionTarget: CompressionTarget,
            password: String?,
            compressionLevel: Int?
        )
    }
}

enum class ArchiveType(
    val label: String,
    val extension: String,
    val format: Int,
    val filter: Int,
    val compressionTarget: CompressionTarget,
    val minCompressionLevel: Int = 0,
    val maxCompressionLevel: Int = 9,
    val supportsPassword: Boolean,
    val supportsCompression: Boolean
) {
    ZIP(
        "zip",
        "zip",
        Archive.FORMAT_ZIP,
        Archive.FILTER_NONE,
        CompressionTarget.FORMAT,
        0,
        9,
        true,
        true
    ),
    TAR(
        "tar",
        "tar",
        Archive.FORMAT_TAR,
        Archive.FILTER_NONE,
        CompressionTarget.FORMAT,
        0,
        9,
        false,
        false
    ),
    TAR_XZ(
        "tar.xz",
        "tar.xz",
        Archive.FORMAT_TAR,
        Archive.FILTER_XZ,
        CompressionTarget.FILTER,
        0,
        9,
        false,
        false
    ),
    TAR_GZ(
        "tar.gz",
        "tar.gz",
        Archive.FORMAT_TAR,
        Archive.FILTER_GZIP,
        CompressionTarget.FILTER,
        minCompressionLevel = 1,
        maxCompressionLevel = 9,
        supportsPassword = false,
        supportsCompression = true
    ),
    TAR_ZSTD(
        "zstd",
        "tar.zst",
        Archive.FORMAT_TAR,
        Archive.FILTER_ZSTD,
        CompressionTarget.FILTER,
        1,
        19,
        false,
        true
    ),
    SEVEN_Z(
        "7z",
        "7z",
        Archive.FORMAT_7ZIP,
        Archive.FILTER_NONE,
        CompressionTarget.FORMAT, // 7zip is FORMAT, not TARGET
        0,
        6,
        false,
        true
    );

    companion object {
        val DEFAULT: ArchiveType = ZIP
        fun fromLabel(label: String?): ArchiveType? = entries.firstOrNull { it.label == label }
        fun fromStoredValue(storedValue: String?): ArchiveType? = fromLabel(storedValue)
    }
}

enum class CompressionTarget {
    FORMAT, FILTER, BOTH
}

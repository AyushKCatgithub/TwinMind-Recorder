package com.example.twinmind.utils

import android.content.Context
import android.os.StatFs
import java.io.File

object StorageUtils {
    fun getAvailableStorageMB(context: Context): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes / (1024 * 1024)
    }

    fun getAudioDir(context: Context): File {
        val dir = File(context.filesDir, "audio_chunks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}

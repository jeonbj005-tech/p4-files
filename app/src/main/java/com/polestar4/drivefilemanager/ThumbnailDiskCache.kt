package com.polestar4.drivefilemanager

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ThumbnailDiskCache(private val directory: File) {
    fun read(key: String): ByteArray? = file(key).takeIf(File::isFile)?.readBytes()

    fun write(key: String, bytes: ByteArray) {
        require(bytes.isNotEmpty())
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("썸네일 캐시 폴더를 만들 수 없습니다")
        val target = file(key)
        val temp = File.createTempFile("${target.name}.", ".tmp", directory)
        try {
            FileOutputStream(temp).use { output -> output.write(bytes); output.fd.sync() }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun delete(key: String) { file(key).delete() }

    private fun file(key: String) = File(directory, "${key.hashCode().toUInt().toString(16)}.jpg")
}

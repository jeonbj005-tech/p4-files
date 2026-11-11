package com.polestar4.drivefilemanager

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

class UpdateDocumentsProvider : DocumentsProvider() {
    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = cursor(projection, ROOT_COLUMNS, listOf(
        mapOf(
            DocumentsContract.Root.COLUMN_ROOT_ID to ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS to DocumentsContract.Root.FLAG_LOCAL_ONLY,
            DocumentsContract.Root.COLUMN_ICON to R.drawable.p4_files_icon,
            DocumentsContract.Root.COLUMN_TITLE to "P4 Files 업데이트 저장소",
            DocumentsContract.Root.COLUMN_SUMMARY to "검증된 APK · 시스템 승인 후 설치",
            DocumentsContract.Root.COLUMN_DOCUMENT_ID to ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES to APK_MIME
        )
    ))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val values = if (documentId == ROOT_ID) directoryValues() else fileValues(requireUpdateFile(documentId))
        return cursor(projection, DOCUMENT_COLUMNS, listOf(values))
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        if (parentDocumentId != ROOT_ID) throw FileNotFoundException("Unknown update folder")
        val rows = updateDirectory(providerContext()).listFiles().orEmpty()
            .filter { APK_FILE.matches(it.name) && it.isFile && it.length() > 0L }
            .sortedByDescending(File::lastModified)
            .map(::fileValues)
        return cursor(projection, DOCUMENT_COLUMNS, rows)
    }

    override fun getDocumentType(documentId: String): String =
        if (documentId == ROOT_ID) DocumentsContract.Document.MIME_TYPE_DIR else {
            requireUpdateFile(documentId)
            APK_MIME
        }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Update repository is read-only")
        return ParcelFileDescriptor.open(requireUpdateFile(documentId), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId == ROOT_ID && runCatching { requireUpdateFile(documentId) }.isSuccess

    private fun requireUpdateFile(documentId: String): File {
        if (!APK_FILE.matches(documentId)) throw FileNotFoundException("Unknown update APK")
        val directory = updateDirectory(providerContext()).canonicalFile
        val file = File(directory, documentId).canonicalFile
        if (file.parentFile != directory || !file.isFile || file.length() <= 0L) {
            throw FileNotFoundException("Update APK is no longer available")
        }
        return file
    }

    private fun providerContext(): Context = context ?: throw FileNotFoundException("Update repository unavailable")

    private fun directoryValues() = mapOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID to ROOT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME to "P4 Files 업데이트 저장소",
        DocumentsContract.Document.COLUMN_MIME_TYPE to DocumentsContract.Document.MIME_TYPE_DIR,
        DocumentsContract.Document.COLUMN_FLAGS to 0,
        DocumentsContract.Document.COLUMN_SIZE to 0L,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED to 0L
    )

    private fun fileValues(file: File) = mapOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID to file.name,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME to file.name,
        DocumentsContract.Document.COLUMN_MIME_TYPE to APK_MIME,
        DocumentsContract.Document.COLUMN_FLAGS to 0,
        DocumentsContract.Document.COLUMN_SIZE to file.length(),
        DocumentsContract.Document.COLUMN_LAST_MODIFIED to file.lastModified()
    )

    private fun cursor(projection: Array<out String>?, defaults: Array<String>, rows: List<Map<String, Any?>>): MatrixCursor {
        val columns = projection?.let { Array(it.size) { index -> it[index] } } ?: defaults
        return MatrixCursor(columns).apply {
            rows.forEach { values -> newRow().apply { columns.forEach { add(values[it]) } } }
        }
    }

    companion object {
        const val AUTHORITY = "com.polestar4.drivefilemanager.update-store"
        const val ROOT_ID = "p4_files_updates"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private val APK_FILE = Regex("P4-Files-v[A-Za-z0-9][A-Za-z0-9._-]{0,99}\\.apk")
        private val ROOT_COLUMNS = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES
        )
        private val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        fun updateDirectory(context: Context) = File(context.filesDir, "Updates")

        fun notifyRepositoryChanged(context: Context) {
            context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(AUTHORITY), null)
            context.contentResolver.notifyChange(DocumentsContract.buildChildDocumentsUri(AUTHORITY, ROOT_ID), null)
        }
    }
}

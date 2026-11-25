package com.gaiding.ditchshortvideo.share

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the one generated chart image from the app cache. The provider is not
 * exported; the share intent grants a temporary read permission to the chosen
 * app.
 */
class ShareProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri.lastPathSegment != CHART_NAME) throw FileNotFoundException(uri.toString())
        val file = chartFile() ?: throw FileNotFoundException(uri.toString())
        if (!file.exists()) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns, 1)
        val file = chartFile()
        val values = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> CHART_NAME
                OpenableColumns.SIZE -> file?.length() ?: 0L
                else -> null
            }
        }
        cursor.addRow(values)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun chartFile(): File? {
        val ctx = context ?: return null
        return File(File(ctx.cacheDir, "share"), CHART_NAME)
    }

    companion object {
        const val CHART_NAME = "ditch-short-video-chart.png"
        fun uri(context: android.content.Context): Uri =
            Uri.parse("content://${context.packageName}.share/$CHART_NAME")
    }
}

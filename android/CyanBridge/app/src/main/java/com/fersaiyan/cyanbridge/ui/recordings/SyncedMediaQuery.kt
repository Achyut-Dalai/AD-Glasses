package com.fersaiyan.cyanbridge.ui.recordings

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.fersaiyan.cyanbridge.media.SyncedMediaFolder

object SyncedMediaQuery {
    fun query(
        context: Context,
        imagesOnly: Boolean = false,
        limit: Int? = null,
    ): List<SyncedMediaItem> {
        val items = mutableListOf<SyncedMediaItem>()
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )

        val allowedTypes = if (imagesOnly) {
            listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
        } else {
            listOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE,
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
            )
        }
        val mediaTypeSelection = allowedTypes.joinToString(
            separator = " OR ",
            prefix = "(",
            postfix = ")",
        ) { "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?" }

        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.MediaColumns.RELATIVE_PATH
            selection = buildString {
                append(mediaTypeSelection)
                append(" AND (")
                append(MediaStore.MediaColumns.RELATIVE_PATH)
                append("=? OR ")
                append(MediaStore.MediaColumns.RELATIVE_PATH)
                append("=? OR ")
                append(MediaStore.MediaColumns.RELATIVE_PATH)
                append(" LIKE ?)")
            }
            selectionArgs = (
                allowedTypes.map(Int::toString) + listOf(
                    SyncedMediaFolder.relativePath,
                    SyncedMediaFolder.relativePathWithTrailingSlash,
                    SyncedMediaFolder.relativePathLikePattern(),
                )
            ).toTypedArray()
        } else {
            @Suppress("DEPRECATION")
            run {
                projection += MediaStore.MediaColumns.DATA
            }
            selection = "$mediaTypeSelection AND ${MediaStore.MediaColumns.DATA} LIKE ?"
            selectionArgs = (
                allowedTypes.map(Int::toString) + SyncedMediaFolder.legacyAbsolutePathLikePattern()
            ).toTypedArray()
        }

        val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC"
        val uri = MediaStore.Files.getContentUri("external")
        context.contentResolver.query(uri, projection.toTypedArray(), selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val dateTakenIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val dateAddedIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val typeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)

                while (cursor.moveToNext() && (limit == null || items.size < limit)) {
                    if (idIdx < 0 || typeIdx < 0) continue
                    val mediaType = cursor.getInt(typeIdx)
                    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val isImage = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    if (!isVideo && !isImage) continue

                    val id = cursor.getLong(idIdx)
                    val contentUri = if (isVideo) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }
                    val name = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        cursor.getString(nameIdx)
                    } else {
                        "media_$id"
                    }
                    val mime = if (mimeIdx >= 0 && !cursor.isNull(mimeIdx)) {
                        cursor.getString(mimeIdx)
                    } else if (isVideo) {
                        "video/mp4"
                    } else {
                        "image/jpeg"
                    }
                    val dateTakenMs = if (dateTakenIdx >= 0 && !cursor.isNull(dateTakenIdx)) {
                        cursor.getLong(dateTakenIdx)
                    } else {
                        0L
                    }
                    val dateAddedMs = if (dateAddedIdx >= 0 && !cursor.isNull(dateAddedIdx)) {
                        cursor.getLong(dateAddedIdx) * 1000L
                    } else {
                        0L
                    }

                    items += SyncedMediaItem(
                        id = id,
                        contentUri = contentUri,
                        displayName = name,
                        mimeType = mime,
                        isVideo = isVideo,
                        takenAtMs = if (dateTakenMs > 0L) dateTakenMs else dateAddedMs,
                    )
                }
            }

        return items
    }
}

package com.revenge.music.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.revenge.core.db.entities.Song

private const val LOCAL_MEDIA_SCHEME_PREFIX = "content://"
private const val LOCAL_FILE_SCHEME_PREFIX = "file://"

/** Local library songs are keyed by their MediaStore/file URI. */
fun String.isLocalMediaId(): Boolean =
    startsWith(LOCAL_MEDIA_SCHEME_PREFIX) || startsWith(LOCAL_FILE_SCHEME_PREFIX)

/** Shares the local audio file behind [song] through the system share sheet. */
fun shareLocalAudio(context: Context, song: Song) {
    if (!song.id.isLocalMediaId()) return
    val uri = Uri.parse(song.id)
    val title = song.title
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(uri) ?: "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, title)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

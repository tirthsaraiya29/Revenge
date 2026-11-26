package com.revenge.music.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Exported, read-only provider for signed automotive artwork URIs. The signature check in
 * [AutomotiveArtwork.originalUrl] is the security boundary: callers cannot make the provider fetch
 * arbitrary URLs.
 */
class AutomotiveArtworkProvider : ContentProvider() {

  override fun onCreate(): Boolean {
    context?.applicationContext?.let { AutomotiveArtwork.applicationContext = it }
    return true
  }

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    if (mode != "r") throw SecurityException("Artwork is read-only")
    val context = context ?: throw FileNotFoundException("No provider context")
    val url = AutomotiveArtwork.originalUrl(context, uri)
      ?: throw FileNotFoundException("Unknown artwork URI")
    val file = AutomotiveArtwork.artworkFile(context, url)
      ?: throw FileNotFoundException("Artwork unavailable")
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
  }

  override fun getType(uri: Uri): String = "image/jpeg"

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ): Cursor? = null

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?,
  ): Int = 0
}

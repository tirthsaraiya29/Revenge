package com.revenge.music.utils

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AutomotiveArtworkTest {

  private val context = RuntimeEnvironment.getApplication()

  @Test
  fun remoteUrlMapsToThreeSegmentSignedContentUri() {
    AutomotiveArtwork.fetchBitmap = { null }
    val original = Uri.parse("https://example.test/cover.jpg")

    val mapped = AutomotiveArtwork.contentUriFor(context, original)!!

    assertEquals("content", mapped.scheme)
    assertEquals("${context.packageName}.artwork", mapped.authority)
    assertEquals(3, mapped.pathSegments.size)
    assertEquals("art", mapped.pathSegments[0])
    assertEquals(original.toString(), AutomotiveArtwork.originalUrl(context, mapped))
  }

  @Test
  fun mappingIsCachedAndNonHttpUrisPassThrough() {
    AutomotiveArtwork.fetchBitmap = { null }
    val original = Uri.parse("https://example.test/other.jpg")
    val first = AutomotiveArtwork.contentUriFor(context, original)
    val second = AutomotiveArtwork.contentUriFor(context, original)
    assertEquals(first, second)

    val resource = Uri.parse("android.resource://com.example/audio/1")
    assertSame(resource, AutomotiveArtwork.contentUriFor(context, resource))
    assertNull(AutomotiveArtwork.contentUriFor(context, null))
  }

  @Test
  fun originalUrlRejectsTamperedSignatureAndWrongShape() {
    AutomotiveArtwork.fetchBitmap = { null }
    val mapped = AutomotiveArtwork.contentUriFor(context, Uri.parse("https://example.test/a.jpg"))!!
    val text = mapped.toString()
    val flipped = text.dropLast(1) + if (text.last() == '0') "1" else "0"
    assertNull(AutomotiveArtwork.originalUrl(context, Uri.parse(flipped)))

    val wrongAuthority = mapped.buildUpon().authority("other.authority").build()
    assertNull(AutomotiveArtwork.originalUrl(context, wrongAuthority))

    val wrongSegments = Uri.parse("content://${context.packageName}.artwork/art/only-two")
    assertNull(AutomotiveArtwork.originalUrl(context, wrongSegments))

    assertNotEquals(text, flipped)
  }

  @Test
  fun providerRejectsWriteMode() {
    val provider = AutomotiveArtworkProvider()
    val uri = Uri.parse("content://${context.packageName}.artwork/art/a/b")
    val error = assertThrows(SecurityException::class.java) { provider.openFile(uri, "w") }
    assertEquals("Artwork is read-only", error.message)
  }

  @Test
  fun extensionsLeaveMissingArtworkUntouched() {
    val metadata =
      com.revenge.core.models.MediaMetadata(
        id = "id",
        title = "title",
        artists = emptyList(),
        duration = 0,
        thumbnailUrl = null,
      )
    assertSame(metadata, metadata.withAutomotiveArtworkUri(context))

    val remote =
      metadata.copy(thumbnailUrl = "https://example.test/cover.jpg")
    val mapped = remote.withAutomotiveArtworkUri(context)
    assertTrue(mapped.thumbnailUrl!!.startsWith("content://"))
  }
}

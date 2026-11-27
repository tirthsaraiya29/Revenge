package com.revenge.music.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.revenge.music.R
import com.revenge.music.ui.component.LyricsBackgroundStyle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ComposeToImage {
    private const val OUTPUT_SIZE = 2160
    private const val UNIT = OUTPUT_SIZE / 340f

    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        width: Int,
        height: Int,
        backgroundColor: Int? = null,
        backgroundStyle: LyricsBackgroundStyle = LyricsBackgroundStyle.SOLID,
        textColor: Int? = null,
        secondaryTextColor: Int? = null,
        lyricsAlignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
    ): Bitmap = withContext(Dispatchers.Default) {
        val cover = loadCover(context, coverArtUrl)
        val bitmap = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val primary = textColor ?: 0xFFFFFFFF.toInt()
        val secondary = secondaryTextColor ?: 0xB3FFFFFF.toInt()

        drawBackground(canvas, cover, backgroundColor, backgroundStyle)
        drawFrame(canvas, primary)
        drawHeader(context, canvas, cover, songTitle, artistName, primary, secondary)
        drawFooter(context, canvas, secondary)
        drawLyrics(canvas, lyrics, primary, lyricsAlignment)
        bitmap
    }

    fun saveBitmapAsFile(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val displayName = "$fileName.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/revenge")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to insert image into MediaStore")
            resolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } ?: throw IllegalStateException("Failed to open image output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }

        val directory = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(directory, displayName)
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    }

    private suspend fun loadCover(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(1024)
                .allowHardware(false)
                .build()
            SingletonImageLoader.get(context).execute(request).image?.toBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun drawBackground(
        canvas: Canvas,
        cover: Bitmap?,
        backgroundColor: Int?,
        style: LyricsBackgroundStyle,
    ) {
        val solid = backgroundColor ?: 0xFF121212.toInt()
        when (style) {
            LyricsBackgroundStyle.SOLID -> canvas.drawColor(solid)

            LyricsBackgroundStyle.BLUR -> {
                canvas.drawColor(0xFF000000.toInt())
                if (cover == null) {
                    canvas.drawColor(solid)
                    return
                }
                val blurred = fastBlur(cover)
                if (blurred == null) {
                    canvas.drawColor(solid)
                } else {
                    canvas.drawBitmap(blurred, null, RectF(0f, 0f, OUTPUT_SIZE.toFloat(), OUTPUT_SIZE.toFloat()), null)
                    canvas.drawColor(0x4D000000)
                }
            }

            LyricsBackgroundStyle.GRADIENT -> {
                if (cover == null) {
                    canvas.drawColor(solid)
                    return
                }
                val palette = try {
                    Palette.from(cover).generate()
                } catch (_: Exception) {
                    null
                }
                if (palette == null) {
                    canvas.drawColor(solid)
                    return
                }
                val vibrant = palette.getVibrantColor(solid)
                val darkVibrant = palette.getDarkVibrantColor(solid)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f,
                        0f,
                        0f,
                        OUTPUT_SIZE.toFloat(),
                        vibrant,
                        darkVibrant,
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(0f, 0f, OUTPUT_SIZE.toFloat(), OUTPUT_SIZE.toFloat(), paint)
            }
        }
    }

    private fun fastBlur(cover: Bitmap): Bitmap? = try {
        val small = Bitmap.createScaledBitmap(cover, 48, 48, true)
        Bitmap.createScaledBitmap(small, OUTPUT_SIZE, OUTPUT_SIZE, true)
    } catch (_: Exception) {
        null
    }

    private fun drawFrame(canvas: Canvas, textColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 20 * UNIT
            color = withAlpha(textColor, 0.09f)
        }
        val inset = paint.strokeWidth / 2f
        val radius = 20 * UNIT
        canvas.drawRoundRect(inset, inset, OUTPUT_SIZE - inset, OUTPUT_SIZE - inset, radius, radius, paint)
    }

    private fun drawHeader(
        context: Context,
        canvas: Canvas,
        cover: Bitmap?,
        songTitle: String,
        artistName: String,
        primary: Int,
        secondary: Int,
    ) {
        val padding = 28 * UNIT
        val coverSize = 64 * UNIT
        val textLeft = padding + coverSize + 12 * UNIT
        val textWidth = OUTPUT_SIZE - padding - textLeft

        if (cover != null) {
            val destination = RectF(padding, padding, padding + coverSize, padding + coverSize)
            val radius = 3 * UNIT
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = 0xFF000000.toInt()
            canvas.drawRoundRect(destination, radius, radius, paint)
            val clip = canvas.save()
            val path = android.graphics.Path().apply {
                addRoundRect(destination, radius, radius, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(path)
            canvas.drawBitmap(cover, null, destination, null)
            canvas.restoreToCount(clip)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * UNIT
            paint.color = withAlpha(primary, 0.16f)
            canvas.drawRoundRect(destination, radius, radius, paint)
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primary
            textSize = 20 * UNIT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondary
            textSize = 16 * UNIT
        }

        val title = TextUtils.ellipsize(songTitle, titlePaint, textWidth, TextUtils.TruncateAt.END)
        val artist = TextUtils.ellipsize(artistName, artistPaint, textWidth, TextUtils.TruncateAt.END)
        val baseline = padding + 26 * UNIT
        canvas.drawText(title.toString(), textLeft, baseline, titlePaint)
        canvas.drawText(artist.toString(), textLeft, baseline + 24 * UNIT, artistPaint)
    }

    private fun drawFooter(context: Context, canvas: Canvas, secondary: Int) {
        val padding = 28 * UNIT
        val footerCenterY = OUTPUT_SIZE - padding - 16 * UNIT
        val badgeRadius = 12 * UNIT
        val badgeLeft = padding + badgeRadius

        val logo = try {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }
        if (logo != null) {
            val destination = RectF(
                badgeLeft - badgeRadius,
                footerCenterY - badgeRadius,
                badgeLeft + badgeRadius,
                footerCenterY + badgeRadius,
            )
            val clip = canvas.save()
            val path = android.graphics.Path().apply {
                addCircle(badgeLeft, footerCenterY, badgeRadius, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(path)
            canvas.drawBitmap(logo, null, destination, null)
            canvas.restoreToCount(clip)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = secondary }
            canvas.drawCircle(badgeLeft, footerCenterY, badgeRadius, paint)
        }

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondary
            textSize = 14 * UNIT
        }
        val appName = context.getString(R.string.app_name)
        canvas.drawText(appName, badgeLeft + badgeRadius + 8 * UNIT, footerCenterY + 5 * UNIT, namePaint)

        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(secondary, 0.2f)
            strokeWidth = 1 * UNIT
        }
        val dividerY = footerCenterY - badgeRadius - 10 * UNIT
        canvas.drawLine(padding, dividerY, OUTPUT_SIZE - padding, dividerY, divider)
    }

    private fun drawLyrics(
        canvas: Canvas,
        lyrics: String,
        textColor: Int,
        alignment: Layout.Alignment,
    ) {
        if (lyrics.isBlank()) return
        val padding = 28 * UNIT
        val width = (OUTPUT_SIZE - 2 * padding).toInt()
        val top = padding + 64 * UNIT + 20 * UNIT
        val bottom = OUTPUT_SIZE - padding - 52 * UNIT
        val available = (bottom - top).toInt()
        if (width <= 0 || available <= 0) return

        val startSize = 50 * UNIT
        val floorSize = 13 * UNIT
        var textSize = startSize
        var layout = buildLayout(lyrics, textColor, textSize, width, alignment)
        while (layout.height > available && textSize > floorSize) {
            textSize -= 1 * UNIT
            layout = buildLayout(lyrics, textColor, textSize, width, alignment)
        }

        val y = if (layout.height <= available) {
            top + (available - layout.height) / 2f
        } else {
            top
        }
        val save = canvas.save()
        canvas.translate(padding, y)
        layout.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun buildLayout(
        lyrics: String,
        textColor: Int,
        textSize: Float,
        width: Int,
        alignment: Layout.Alignment,
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        return StaticLayout.Builder.obtain(lyrics, 0, lyrics.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(false)
            .build()
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or (((alpha * 255).toInt().coerceIn(0, 255)) shl 24)
}

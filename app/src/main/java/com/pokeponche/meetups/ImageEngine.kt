package com.pokeponche.meetups

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.provider.MediaStore
import java.io.File

object ImageEngine {
    fun render(context: Context, background: String, sprites: List<Monster>): Bitmap {
        require(sprites.size <= 3) { "Máximo tres Pokémon" }
        val size=1000
        val result=Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas=Canvas(result)
        // Prioridad: imagen elegida en Ajustes; después, imágenes incluidas en la app.
        // Si el usuario aún no cambió nada, temporada.jpg o social.jpg vienen integradas.
        val input = if (background.isNotBlank()) {
            BitmapFactory.decodeFile(background) ?: error("No se pudo abrir el fondo personalizado")
        } else {
            val asset = if (sprites.isEmpty()) "fondos/social.jpg" else "fondos/temporada.jpg"
            context.assets.open(asset).use { stream ->
                BitmapFactory.decodeStream(stream) ?: error("No se pudo abrir $asset")
            }
        }
        canvas.drawBitmap(input, null, RectF(0f,0f,size.toFloat(),size.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        input.recycle()
        val rects = when(sprites.size) {
            0 -> emptyList()
            1 -> listOf(RectF(75f, 75f, 925f, 925f))
            2 -> listOf(RectF(4f, 160f, 610f, 790f),RectF(390f, 210f, 996f, 840f))
            else -> listOf(RectF(220f, 18f, 780f, 575f),RectF(25f, 410f, 545f, 940f),RectF(455f, 410f, 975f, 940f))
        }
        sprites.forEachIndexed { idx, p ->
            val sprite = runCatching { context.assets.open("sprites/${p.sprite}").use { BitmapFactory.decodeStream(it) } }.getOrNull()
                ?: error("Sprite no disponible: ${p.sprite}. Copia los PNG a app/src/main/assets/sprites/")
            canvas.drawBitmap(sprite, null, rects[idx], Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            sprite.recycle()
        }
        return result
    }
    fun save(context: Context, bitmap: Bitmap): String {
        val file=File(context.filesDir, "event_images/${System.currentTimeMillis()}.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        val values=ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "quedada_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/PokeponcheMeetups")
            put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
            ?: error("No se pudo guardar en galería")
        try {
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: error("No se pudo escribir la imagen")
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING,0)
            context.contentResolver.update(uri,values,null,null)
        } catch (ex: Exception) { context.contentResolver.delete(uri,null,null); throw ex }
        return file.absolutePath
    }
}

package com.example.facecamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.GREEN
    }

    private var faces: List<Rect> = emptyList()
    private var imageWidth = 1
    private var imageHeight = 1

    fun setFaces(newFaces: List<Rect>, width: Int, height: Int) {
        faces = newFaces
        imageWidth = width.coerceAtLeast(1)
        imageHeight = height.coerceAtLeast(1)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faces.isEmpty()) return

        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight

        for (face in faces) {
            canvas.drawRect(
                face.left * scaleX,
                face.top * scaleY,
                face.right * scaleX,
                face.bottom * scaleY,
                paint
            )
        }
    }
}

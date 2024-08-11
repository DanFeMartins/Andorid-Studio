package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class CubeView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Definindo as coordenadas do cubo
        val path = Path()
        path.moveTo(300f, 300f) // Frente superior esquerda
        path.lineTo(500f, 300f) // Frente superior direita
        path.lineTo(500f, 500f) // Frente inferior direita
        path.lineTo(300f, 500f) // Frente inferior esquerda
        path.close()

        // Desenhando a frente do cubo
        canvas.drawPath(path, paint)

        // Desenhando as laterais do cubo
        path.reset()
        path.moveTo(300f, 300f)
        path.lineTo(400f, 200f) // Topo superior esquerda
        path.lineTo(600f, 200f) // Topo superior direita
        path.lineTo(500f, 300f)
        path.close()

        canvas.drawPath(path, paint)

        // Desenhando as linhas que conectam as frentes
        canvas.drawLine(500f, 300f, 600f, 200f, paint)
        canvas.drawLine(500f, 500f, 600f, 400f, paint)
        canvas.drawLine(300f, 500f, 400f, 400f, paint)
        canvas.drawLine(300f, 300f, 400f, 200f, paint)
    }
}

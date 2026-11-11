package com.example.recordatoriomedicamentos

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.ContextCompat

/** Fondo estático de marca compartido por el inicio de sesión y el panel principal. */
class MedAlarmRoot
@JvmOverloads
constructor(contexto: Context, atributos: AttributeSet? = null, atributoEstilo: Int = 0) :
    FrameLayout(contexto, atributos, atributoEstilo) {
    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val densidad = resources.displayMetrics.density
    private val desplazamientoOnda: Float =
        contexto.obtainStyledAttributes(atributos, R.styleable.MedAlarmRoot).use {
            it.getDimension(R.styleable.MedAlarmRoot_dashboardWaveOffset, 0f)
        }
    private val desplazamientoSuperiorOnda: Float =
        contexto.obtainStyledAttributes(atributos, R.styleable.MedAlarmRoot).use {
            it.getDimension(R.styleable.MedAlarmRoot_dashboardWaveTopOffset, 0f)
        }
    private val altoEncabezadoOnda: Float =
        contexto.obtainStyledAttributes(atributos, R.styleable.MedAlarmRoot).use {
            it.getDimension(R.styleable.MedAlarmRoot_waveHeaderHeight, 104f * densidad)
        }
    private val colorInicioOndaIntensa: Int? =
        contexto.obtainStyledAttributes(atributos, R.styleable.MedAlarmRoot).use {
            if (it.hasValue(R.styleable.MedAlarmRoot_waveStrongStartColor))
                it.getColor(R.styleable.MedAlarmRoot_waveStrongStartColor, 0)
            else null
        }
    private val colorFinOndaIntensa: Int? =
        contexto.obtainStyledAttributes(atributos, R.styleable.MedAlarmRoot).use {
            if (it.hasValue(R.styleable.MedAlarmRoot_waveStrongEndColor))
                it.getColor(R.styleable.MedAlarmRoot_waveStrongEndColor, 0)
            else null
        }
    private val colorOndaClara: Int? =
        contexto.obtainStyledAttributes(atributos, R.styleable.MedAlarmRoot).use {
            if (it.hasValue(R.styleable.MedAlarmRoot_waveLightColor))
                it.getColor(R.styleable.MedAlarmRoot_waveLightColor, 0)
            else null
        }

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(lienzo: Canvas) {
        super.onDraw(lienzo)
        val ancho = width.toFloat()
        val alto = height.toFloat()
        val encabezado = altoEncabezadoOnda
        if (colorInicioOndaIntensa != null && colorFinOndaIntensa != null) {
            pincel.shader =
                LinearGradient(
                    0f,
                    0f,
                    ancho,
                    0f,
                    colorInicioOndaIntensa!!,
                    colorFinOndaIntensa!!,
                    Shader.TileMode.CLAMP,
                )
        } else {
            pincel.shader =
                LinearGradient(
                    0f,
                    0f,
                    ancho,
                    0f,
                    ContextCompat.getColor(context, R.color.brand_purple_light),
                    ContextCompat.getColor(context, R.color.brand_purple_dark),
                    Shader.TileMode.CLAMP,
                )
        }
        lienzo.drawRect(0f, 0f, ancho, encabezado, pincel)

        pincel.shader = null
        pincel.color = colorOndaClara ?: ContextCompat.getColor(context, R.color.wave_lavender)
        val caminoOndaClara =
            Path().apply {
                moveTo(0f, encabezado * .70f - desplazamientoSuperiorOnda)
                cubicTo(
                    ancho * .20f,
                    encabezado * .48f - desplazamientoSuperiorOnda,
                    ancho * .34f,
                    encabezado * .94f - desplazamientoSuperiorOnda,
                    ancho * .55f,
                    encabezado * .77f - desplazamientoSuperiorOnda,
                )
                cubicTo(
                    ancho * .77f,
                    encabezado * .59f - desplazamientoSuperiorOnda,
                    ancho * .87f,
                    encabezado * .58f - desplazamientoSuperiorOnda,
                    ancho,
                    encabezado * .68f - desplazamientoSuperiorOnda,
                )
                lineTo(ancho, alto)
                lineTo(0f, alto)
                close()
            }
        lienzo.drawPath(caminoOndaClara, pincel)
        pincel.shader = null

        pincel.color = ContextCompat.getColor(context, R.color.page_background)
        val caminoFondoInferior =
            Path().apply {
                moveTo(0f, encabezado * 1.08f + desplazamientoOnda)
                cubicTo(
                    ancho * .24f,
                    encabezado * 1.28f + desplazamientoOnda,
                    ancho * .49f,
                    encabezado * .87f + desplazamientoOnda,
                    ancho * .72f,
                    encabezado * 1.01f + desplazamientoOnda,
                )
                cubicTo(
                    ancho * .88f,
                    encabezado * 1.11f + desplazamientoOnda,
                    ancho * .95f,
                    encabezado * 1.17f + desplazamientoOnda,
                    ancho,
                    encabezado * 1.06f + desplazamientoOnda,
                )
                lineTo(ancho, alto)
                lineTo(0f, alto)
                close()
            }
        lienzo.drawPath(caminoFondoInferior, pincel)
        pincel.color = ContextCompat.getColor(context, R.color.page_background)
        lienzo.drawRect(0f, encabezado * 1.28f + desplazamientoOnda, ancho, alto, pincel)
    }
}

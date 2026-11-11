package com.example.recordatoriomedicamentos

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * Limita el ancho de lectura en ventanas grandes y respeta la densidad y escala de texto actuales.
 */
class AdaptiveContentLayout
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    LinearLayout(context, attrs, defStyleAttr) {
    private val anchoMaximoContenidoPx = (720f * resources.displayMetrics.density).toInt()

    override fun onMeasure(especificacionAncho: Int, especificacionAlto: Int) {
        val modoAncho = MeasureSpec.getMode(especificacionAncho)
        val anchoDisponible = MeasureSpec.getSize(especificacionAncho)
        val especificacionAnchoLimitada =
            if (modoAncho != MeasureSpec.UNSPECIFIED && anchoDisponible > anchoMaximoContenidoPx) {
                MeasureSpec.makeMeasureSpec(anchoMaximoContenidoPx, MeasureSpec.EXACTLY)
            } else especificacionAncho
        super.onMeasure(especificacionAnchoLimitada, especificacionAlto)
    }
}

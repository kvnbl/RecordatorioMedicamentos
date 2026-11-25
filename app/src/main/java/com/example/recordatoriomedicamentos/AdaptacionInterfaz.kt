package com.example.recordatoriomedicamentos

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Ajustes de distribución para la escala de texto y el ancho de ventana actuales de Android. */
object UiAdaptation {
    fun adaptarEncabezadoDeSeccion(actividad: Activity) {
        val encabezado = actividad.findViewById<LinearLayout>(R.id.sectionHeader) ?: return
        val anchoDp = actividad.resources.configuration.screenWidthDp
        val escalaFuente = actividad.resources.configuration.fontScale
        val apilado = escalaFuente >= 1.3f || anchoDp < 360
        encabezado.orientation = if (apilado) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val volver = encabezado.findViewById<View>(R.id.btnVolver)
        val titulo = encabezado.findViewById<TextView>(R.id.tvSectionTitle)
        val parametrosTitulo = titulo.layoutParams as LinearLayout.LayoutParams
        if (apilado) {
            parametrosTitulo.width = ViewGroup.LayoutParams.MATCH_PARENT
            parametrosTitulo.weight = 0f
            parametrosTitulo.marginStart = 0
            parametrosTitulo.topMargin = actividad.resources.getDimensionPixelSize(R.dimen.ui_space_8)
            titulo.gravity = Gravity.START
            volver.layoutParams =
                (volver.layoutParams as LinearLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                }
        } else {
            parametrosTitulo.width = 0
            parametrosTitulo.weight = 1f
            parametrosTitulo.marginStart = actividad.resources.getDimensionPixelSize(R.dimen.ui_space_12)
            parametrosTitulo.topMargin = 0
            titulo.gravity = Gravity.END
        }
        titulo.layoutParams = parametrosTitulo
    }
}

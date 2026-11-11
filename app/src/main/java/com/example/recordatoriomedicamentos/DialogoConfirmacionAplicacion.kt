package com.example.recordatoriomedicamentos

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** Modal de confirmación con estilo propio, compartido entre acciones de guardado y eliminación. */
object AppConfirmationDialog {
    fun mostrar(
        contexto: Context,
        titulo: CharSequence,
        mensaje: CharSequence,
        textoCancelar: CharSequence,
        textoAceptar: CharSequence,
        alAceptar: () -> Unit,
    ) {
        val dialogo = Dialog(contexto)
        dialogo.setContentView(R.layout.dialog_app_confirmation)
        dialogo.setCancelable(true)
        dialogo.setCanceledOnTouchOutside(true)
        dialogo.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogo.window?.setTitle(titulo)
        dialogo.findViewById<TextView>(R.id.confirmationTitle).text = titulo
        dialogo.findViewById<TextView>(R.id.confirmationMessage).text = mensaje
        dialogo.findViewById<MaterialButton>(R.id.btnConfirmationCancel).text = textoCancelar
        dialogo.findViewById<MaterialButton>(R.id.btnConfirmationAccept).apply {
            text = textoAceptar
            setOnClickListener {
                dialogo.dismiss()
                alAceptar()
            }
        }
        dialogo.findViewById<MaterialButton>(R.id.btnConfirmationCancel).setOnClickListener {
            dialogo.dismiss()
        }
        dialogo.show()
        dialogo.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val densidad = contexto.resources.displayMetrics.density
            val anchoPantalla = contexto.resources.displayMetrics.widthPixels
            setLayout(
                minOf(anchoPantalla - (32 * densidad).toInt(), (400 * densidad).toInt()),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.55f }
        }
    }
}

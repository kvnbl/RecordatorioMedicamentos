package com.example.recordatoriomedicamentos

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

/** Presenta la alarma recibida por Bluetooth en la pantalla visible. */
class MedAlarmApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var actividadVisible: Activity? = null
    private var dialogo: Dialog? = null
    private var eventoMostrado: String? = null

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    fun alarmaActualizada() {
        val preferencias = getSharedPreferences("active_alarm", MODE_PRIVATE)
        if (preferencias.getString("event", null) == null) {
            dialogo?.dismiss()
            dialogo = null
            return
        }
        mostrarAlarma()
    }

    fun mostrarAlarma(forzar: Boolean = false) {
        val actividad = actividadVisible ?: return
        if (actividad.isFinishing || actividad.isDestroyed || FirebaseAuth.getInstance().currentUser == null) return
        val preferencias = getSharedPreferences("active_alarm", MODE_PRIVATE)
        val evento = preferencias.getString("event", null) ?: return
        if (dialogo?.isShowing == true && eventoMostrado != evento) dialogo?.dismiss()
        if (!forzar && eventoMostrado == evento && dialogo?.isShowing == true) return
        if (dialogo?.isShowing == true) return
        val detalles = preferencias.getString("medicines", "").orEmpty()
        val titulo = preferencias.getString("title", getString(R.string.active_alarm_title)).orEmpty()
        val nuevoDialogo = Dialog(actividad)
        nuevoDialogo.setContentView(R.layout.dialog_active_alarm)
        nuevoDialogo.setCancelable(false)
        nuevoDialogo.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        nuevoDialogo.findViewById<TextView>(R.id.activeAlarmTitle).text = titulo
        val lista = nuevoDialogo.findViewById<LinearLayout>(R.id.activeAlarmList)
        val filas = detalles.lines().filter(String::isNotBlank)
        if (filas.isEmpty()) {
            lista.addView(TextView(actividad).apply {
                text = getString(R.string.active_alarm_empty)
                setTextAppearance(R.style.MedAlarmTextBody)
                setTextColor(getColor(R.color.text_secondary))
            })
        } else filas.forEach { detalle ->
            lista.addView(TextView(actividad).apply {
                text = "• $detalle"
                setTextAppearance(R.style.MedAlarmTextBody)
                setTextColor(getColor(R.color.text_primary))
                setPadding(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
            })
        }
        nuevoDialogo.findViewById<MaterialButton>(R.id.btnActiveAlarmClose).setOnClickListener {
            preferencias.edit().clear().apply()
            eventoMostrado = null
            nuevoDialogo.dismiss()
        }
        nuevoDialogo.setOnDismissListener { if (dialogo === nuevoDialogo) dialogo = null }
        nuevoDialogo.show()
        nuevoDialogo.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val densidad = resources.displayMetrics.density
            setLayout(
                minOf(resources.displayMetrics.widthPixels - (32 * densidad).toInt(), (400 * densidad).toInt()),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.55f }
        }
        dialogo = nuevoDialogo
        eventoMostrado = evento
    }

    override fun onActivityResumed(activity: Activity) {
        actividadVisible = activity
        mostrarAlarma()
    }
    override fun onActivityPaused(activity: Activity) {
        if (actividadVisible === activity) actividadVisible = null
        dialogo?.dismiss()
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

class AlarmListScrollView(context: Context, attrs: AttributeSet) : ScrollView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maximo = minOf((resources.displayMetrics.heightPixels * 0.45f).toInt(), (320 * resources.displayMetrics.density).toInt())
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maximo, View.MeasureSpec.AT_MOST))
    }
}

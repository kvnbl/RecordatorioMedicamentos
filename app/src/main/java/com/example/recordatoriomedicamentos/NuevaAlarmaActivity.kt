package com.example.recordatoriomedicamentos

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.Locale
import java.util.TimeZone

/** Formulario independiente para crear o editar alarmas. */
class NuevaAlarmaActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ID_ALARMA_EDICION = "alarm_id_to_edit"
        private const val URL_BASE_DATOS =
            "https://recordatoriodemedicament-90e4f-default-rtdb.firebaseio.com/"
        private const val CLAVE_DIAS_ALARMA = "alarm_days"
        private const val CLAVE_HORA_ALARMA = "alarm_hour"
        private const val CLAVE_MINUTO_ALARMA = "alarm_minute"
        private const val CLAVE_ID_PENDIENTE = "alarm_pending_id"
        private const val CLAVE_ID_EN_EDICION = "alarm_editing_id"
        private const val INDICE_ANTES_MEDIODIA = 0
        private const val INDICE_DESPUES_MEDIODIA = 1
    }

    private lateinit var autenticacion: FirebaseAuth
    private lateinit var referencia: DatabaseReference
    private lateinit var idUsuario: String
    private lateinit var medicamento: EditText
    private lateinit var dosis: EditText
    private lateinit var botonGuardar: Button
    private lateinit var desplazamientoAlarma: android.widget.ScrollView
    private lateinit var selectorHora: NumberPicker
    private lateinit var selectorMinutos: NumberPicker
    private lateinit var selectorMeridiano: NumberPicker
    private lateinit var desplazamientoDiasSemana: HorizontalScrollView
    private lateinit var filaSelectorDias: LinearLayout
    private lateinit var detalleRepeticion: TextView
    private val controlesDias = mutableListOf<FrameLayout>()
    private val etiquetasDias = mutableListOf<TextView>()
    private lateinit var estado: TextView
    private var diasSeleccionados = BooleanArray(7)
    private var hora = 8
    private var minuto = 0
    private var actualizandoSelectorHora = false
    private var idPendiente: String? = null
    private var idEnEdicion: String? = null
    private var guardando = false
    private val diasSemana by lazy { resources.getStringArray(R.array.weekday_names) }
    private val nombresResumenDias by lazy {
        resources.getStringArray(R.array.weekday_summary_names)
    }
    private val diasVisibles =
        listOf(
            0 to R.string.weekday_short_monday,
            1 to R.string.weekday_short_tuesday,
            2 to R.string.weekday_short_wednesday,
            3 to R.string.weekday_short_thursday,
            4 to R.string.weekday_short_friday,
            5 to R.string.weekday_short_saturday,
            6 to R.string.weekday_short_sunday,
        )
    private val escuchaAutenticacion =
        FirebaseAuth.AuthStateListener {
            if (it.currentUser?.uid != idUsuario && !isFinishing) finish()
        }

    override fun onCreate(estadoGuardado: Bundle?) {
        super.onCreate(estadoGuardado)
        enableEdgeToEdge()
        autenticacion = FirebaseAuth.getInstance()
        idUsuario =
            autenticacion.currentUser?.uid
                ?: run {
                    finish()
                    return
                }
        referencia =
            FirebaseDatabase.getInstance(URL_BASE_DATOS)
                .getReference("usuarios")
                .child(idUsuario)
                .child("recordatorios")
        setContentView(R.layout.activity_nueva_alarma)
        val raiz = findViewById<View>(R.id.main)
        desplazamientoAlarma = findViewById(R.id.alarmScrollView)
        desplazamientoAlarma.setVerticalFadingEdgeEnabled(true)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, margenesSistema ->
            val barras = margenesSistema.getInsets(WindowInsetsCompat.Type.systemBars())
            val teclado = margenesSistema.getInsets(WindowInsetsCompat.Type.ime())
            vista.setPadding(barras.left, barras.top, barras.right, maxOf(barras.bottom, teclado.bottom))
            if (teclado.bottom > 0)
                desplazamientoAlarma.postDelayed({ asegurarCampoEnPantalla() }, 120)
            margenesSistema
        }
        ViewCompat.requestApplyInsets(raiz)

        medicamento = findViewById(R.id.etMedicamento)
        dosis = findViewById(R.id.etDosis)
        botonGuardar = findViewById(R.id.btnGuardarRecordatorio)
        selectorHora = findViewById(R.id.pickerHour)
        selectorMinutos = findViewById(R.id.pickerMinute)
        selectorMeridiano = findViewById(R.id.pickerMeridiem)
        desplazamientoDiasSemana = findViewById(R.id.weeklyDaysScroll)
        filaSelectorDias = findViewById(R.id.weekdaySelectorRow)
        detalleRepeticion = findViewById(R.id.tvRepetitionDetails)
        estado = findViewById(R.id.tvEstadoGuardado)
        idEnEdicion =
            estadoGuardado?.getString(CLAVE_ID_EN_EDICION)
                ?: intent.getStringExtra(EXTRA_ID_ALARMA_EDICION)
        idPendiente = estadoGuardado?.getString(CLAVE_ID_PENDIENTE)
        diasSeleccionados =
            estadoGuardado?.getBooleanArray(CLAVE_DIAS_ALARMA)?.takeIf { it.size == 7 }
                ?: BooleanArray(7)
        hora = estadoGuardado?.getInt(CLAVE_HORA_ALARMA, 8) ?: 8
        minuto = estadoGuardado?.getInt(CLAVE_MINUTO_ALARMA, 0) ?: 0
        crearControlesDias()
        configurarSelectoresHora()

        findViewById<TextView>(R.id.tvSectionTitle)
            .setText(
                if (idEnEdicion == null) R.string.action_new_alarm else R.string.alarm_edit_title
            )
        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvZonaHoraria).text = TimeZone.getDefault().id
        desplazamientoDiasSemana.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            actualizarAnchoFilaDias()
        }
        dosis.setOnFocusChangeListener { _, tieneEnfoque ->
            if (tieneEnfoque) desplazamientoAlarma.postDelayed({ asegurarCampoEnPantalla() }, 300)
        }
        botonGuardar.setOnClickListener { mostrarConfirmacion() }
        actualizarBotones()
        if (idEnEdicion != null && estadoGuardado == null) cargarAlarma(idEnEdicion!!)
    }

    override fun onStart() {
        super.onStart()
        if (autenticacion.currentUser?.uid != idUsuario) {
            finish()
            return
        }
        autenticacion.addAuthStateListener(escuchaAutenticacion)
    }

    override fun onStop() {
        autenticacion.removeAuthStateListener(escuchaAutenticacion)
        super.onStop()
    }

    override fun onSaveInstanceState(estadoSalida: Bundle) {
        estadoSalida.putBooleanArray(CLAVE_DIAS_ALARMA, diasSeleccionados)
        estadoSalida.putInt(CLAVE_HORA_ALARMA, hora)
        estadoSalida.putInt(CLAVE_MINUTO_ALARMA, minuto)
        estadoSalida.putString(CLAVE_ID_PENDIENTE, idPendiente)
        estadoSalida.putString(CLAVE_ID_EN_EDICION, idEnEdicion)
        super.onSaveInstanceState(estadoSalida)
    }

    private fun cargarAlarma(identificador: String) {
        botonGuardar.isEnabled = false
        estado.text = getString(R.string.alarm_loading)
        referencia
            .child(identificador)
            .get()
            .addOnSuccessListener { fila ->
                if (isFinishing || isDestroyed || autenticacion.currentUser?.uid != idUsuario)
                    return@addOnSuccessListener
                if (!fila.exists()) {
                    finish()
                    return@addOnSuccessListener
                }
                medicamento.setText(fila.child("medicamento").getValue(String::class.java).orEmpty())
                dosis.setText(fila.child("dosis").getValue(String::class.java).orEmpty())
                diasSeleccionados = BooleanArray(7)
                fila.child("diasSemana").children.forEach { elemento ->
                    (elemento.value as? Number)?.toInt()?.let { dia ->
                        if (dia in 1..7) diasSeleccionados[dia - 1] = true
                    }
                }
                val partesHora =
                    fila.child("hora").getValue(String::class.java).orEmpty().split(":")
                hora = partesHora.getOrNull(0)?.toIntOrNull() ?: 8
                minuto = partesHora.getOrNull(1)?.toIntOrNull() ?: 0
                actualizarControlesDias()
                sincronizarSeleccionHora()
                actualizarBotones()
                botonGuardar.isEnabled = true
                estado.text = getString(R.string.alarm_editing)
            }
            .addOnFailureListener {
                if (!isFinishing && !isDestroyed) {
                    estado.text = getString(R.string.alarm_list_error)
                    botonGuardar.isEnabled = true
                }
            }
    }

    private fun configurarSelectoresHora() {
        selectorHora.minValue = 1
        selectorHora.maxValue = 12
        selectorHora.wrapSelectorWheel = true
        estilizarSelectorHora(selectorHora, R.dimen.ui_text_time_wheel, negrita = false)
        selectorMinutos.minValue = 0
        selectorMinutos.maxValue = 59
        selectorMinutos.wrapSelectorWheel = true
        selectorMinutos.setFormatter { valor -> String.format(Locale.ROOT, "%02d", valor) }
        estilizarSelectorHora(selectorMinutos, R.dimen.ui_text_time_wheel, negrita = false)
        selectorMeridiano.minValue = 0
        selectorMeridiano.maxValue = 1
        selectorMeridiano.displayedValues =
            arrayOf(getString(R.string.time_am), getString(R.string.time_pm))
        selectorMeridiano.wrapSelectorWheel = false
        estilizarSelectorHora(selectorMeridiano, R.dimen.ui_text_time_period, negrita = false)

        selectorHora.setOnValueChangedListener { _, _, valor ->
            if (actualizandoSelectorHora) return@setOnValueChangedListener
            val esDespuesMediodia = selectorMeridiano.value == INDICE_DESPUES_MEDIODIA
            hora =
                when {
                    esDespuesMediodia && valor == 12 -> 12
                    esDespuesMediodia -> valor + 12
                    valor == 12 -> 0
                    else -> valor
                }
            actualizarBotones()
            reaplicarTipografiaSelectorHora(
                selectorHora,
                R.dimen.ui_text_time_wheel,
                negrita = false,
            )
        }
        selectorMinutos.setOnValueChangedListener { _, _, valor ->
            if (actualizandoSelectorHora) return@setOnValueChangedListener
            minuto = valor
            actualizarBotones()
            reaplicarTipografiaSelectorHora(
                selectorMinutos,
                R.dimen.ui_text_time_wheel,
                negrita = false,
            )
        }
        selectorMeridiano.setOnValueChangedListener { _, _, valor ->
            if (actualizandoSelectorHora) return@setOnValueChangedListener
            val hora12 = ((hora + 11) % 12) + 1
            hora =
                when {
                    valor == INDICE_DESPUES_MEDIODIA && hora12 == 12 -> 12
                    valor == INDICE_DESPUES_MEDIODIA -> hora12 + 12
                    hora12 == 12 -> 0
                    else -> hora12
                }
            actualizarBotones()
            reaplicarTipografiaSelectorHora(
                selectorMeridiano,
                R.dimen.ui_text_time_period,
                negrita = false,
            )
        }
        sincronizarSeleccionHora()
    }

    private fun estilizarSelectorHora(
        selector: NumberPicker,
        dimensionTamanoTexto: Int,
        negrita: Boolean,
    ) {
        aplicarTipografiaSelectorHora(selector, dimensionTamanoTexto, negrita)
        selector.setOnScrollListener { _, estadoDesplazamiento ->
            // NumberPicker actualiza su EditText seleccionado al terminar el desplazamiento.
            // Reaplica la tipografía en el siguiente fotograma para conservar el estilo.
            reaplicarTipografiaSelectorHora(selector, dimensionTamanoTexto, negrita)
            if (estadoDesplazamiento == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) {
                selector.postDelayed(
                    { aplicarTipografiaSelectorHora(selector, dimensionTamanoTexto, negrita) },
                    48,
                )
            }
        }
    }

    private fun reaplicarTipografiaSelectorHora(
        selector: NumberPicker,
        dimensionTamanoTexto: Int,
        negrita: Boolean,
    ) {
        aplicarTipografiaSelectorHora(selector, dimensionTamanoTexto, negrita)
        selector.post { aplicarTipografiaSelectorHora(selector, dimensionTamanoTexto, negrita) }
    }

    private fun aplicarTipografiaSelectorHora(
        selector: NumberPicker,
        dimensionTamanoTexto: Int,
        negrita: Boolean,
    ) {
        val tamanoTextoPx = resources.getDimension(dimensionTamanoTexto)
        runCatching {
            NumberPicker::class
                .java
                .getDeclaredField("mSelectionDivider")
                .apply { isAccessible = true }
                .set(selector, null)
        }
        runCatching {
            NumberPicker::class
                .java
                .getMethod("setSelectionDividerHeight", Int::class.javaPrimitiveType)
                .invoke(selector, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selector.setTextSize(tamanoTextoPx)
        }
        runCatching {
            val pincel =
                NumberPicker::class
                    .java
                    .getDeclaredField("mSelectorWheelPaint")
                    .apply { isAccessible = true }
                    .get(selector) as? Paint
            pincel?.apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) textSize = tamanoTextoPx
                typeface = Typeface.create(typeface, if (negrita) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        for (indice in 0 until selector.childCount) {
            (selector.getChildAt(indice) as? EditText)?.apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, tamanoTextoPx)
                }
                includeFontPadding = false
                typeface = Typeface.create(typeface, if (negrita) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        selector.invalidate()
    }

    private fun crearControlesDias() {
        controlesDias.clear()
        etiquetasDias.clear()
        filaSelectorDias.removeAllViews()
        diasVisibles.forEach { (indiceDia, idEtiqueta) ->
            val controlDia =
                FrameLayout(this).apply {
                    minimumWidth = 44.enDp()
                    minimumHeight = 48.enDp()
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(0, 48.enDp(), 1f)
                    contentDescription =
                        getString(
                            if (diasSeleccionados[indiceDia]) R.string.weekday_selected_accessibility
                            else R.string.weekday_unselected_accessibility,
                            diasSemana[indiceDia],
                        )
                    setOnClickListener {
                        diasSeleccionados[indiceDia] = !diasSeleccionados[indiceDia]
                        actualizarControlesDias()
                        actualizarBotones()
                    }
                }
            val etiqueta =
                TextView(this).apply {
                    text = getString(idEtiqueta)
                    gravity = Gravity.CENTER
                    setTextAppearance(R.style.MedAlarmTextBodyStrong)
                    layoutParams = FrameLayout.LayoutParams(40.enDp(), 40.enDp(), Gravity.CENTER)
                }
            controlDia.addView(etiqueta)
            filaSelectorDias.addView(controlDia)
            controlesDias += controlDia
            etiquetasDias += etiqueta
        }
        actualizarControlesDias()
        desplazamientoDiasSemana.post { actualizarAnchoFilaDias() }
    }

    private fun actualizarControlesDias() {
        diasVisibles.forEachIndexed { indiceControl, (indiceDia, _) ->
            val seleccionado = diasSeleccionados[indiceDia]
            controlesDias.getOrNull(indiceControl)?.apply {
                isSelected = seleccionado
                contentDescription =
                    getString(
                        if (seleccionado) R.string.weekday_selected_accessibility
                        else R.string.weekday_unselected_accessibility,
                        diasSemana[indiceDia],
                    )
            }
            etiquetasDias.getOrNull(indiceControl)?.apply {
                setTextColor(getColor(if (seleccionado) R.color.white else R.color.text_primary))
                background = if (seleccionado) circuloDia(getColor(R.color.brand_primary)) else null
            }
        }
    }

    private fun actualizarAnchoFilaDias() {
        val anchoVisible =
            desplazamientoDiasSemana.width -
                desplazamientoDiasSemana.paddingLeft -
                desplazamientoDiasSemana.paddingRight
        if (anchoVisible <= 0) return
        val anchoObjetivo = maxOf(anchoVisible, 7 * 44.enDp())
        val parametros = filaSelectorDias.layoutParams
        if (parametros.width != anchoObjetivo) {
            parametros.width = anchoObjetivo
            filaSelectorDias.layoutParams = parametros
        }
    }

    private fun circuloDia(color: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun sincronizarSeleccionHora() {
        if (!::selectorHora.isInitialized) return
        actualizandoSelectorHora = true
        selectorHora.value = ((hora + 11) % 12) + 1
        selectorMinutos.value = minuto.coerceIn(0, 59)
        selectorMeridiano.value =
            if (hora >= 12) INDICE_DESPUES_MEDIODIA else INDICE_ANTES_MEDIODIA
        actualizandoSelectorHora = false
    }

    private fun actualizarBotones() {
        desplazamientoDiasSemana.visibility = View.VISIBLE
        desplazamientoDiasSemana.post { actualizarAnchoFilaDias() }
        detalleRepeticion.text =
            if (diasSeleccionados.any { it }) {
                val resumenDias =
                    if (diasSeleccionados.all { it }) getString(R.string.repeat_all_days_label)
                    else
                        diasSeleccionados.indices
                            .filter { diasSeleccionados[it] }
                            .joinToString(", ") { nombresResumenDias[it] }
                getString(R.string.repeat_days_selected, resumenDias)
            } else {
                getString(R.string.alarm_select_day_hint)
            }
        botonGuardar.isEnabled = diasSeleccionados.any { it } && !guardando
        botonGuardar.setText(
            if (idEnEdicion == null) R.string.action_save_alarm else R.string.action_update_alarm
        )
    }

    private fun mostrarConfirmacion() {
        if (guardando || autenticacion.currentUser?.uid != idUsuario) return
        medicamento.error = null
        dosis.error = null
        val nombre = medicamento.text.toString().trim()
        val dosisIndicada = dosis.text.toString().trim()
        if (nombre.isEmpty() || nombre.length > 80) {
            medicamento.error = getString(R.string.alarm_medicine_validation)
            medicamento.requestFocus()
            return
        }
        if (dosisIndicada.isEmpty() || dosisIndicada.length > 80) {
            dosis.error = getString(R.string.alarm_dose_validation)
            dosis.requestFocus()
            return
        }
        val dias = diasSeleccionados.indices.filter { diasSeleccionados[it] }.map { it + 1 }
        if (dias.isEmpty()) {
            estado.text = getString(R.string.alarm_select_day_hint)
            return
        }
        val textoDias =
            if (dias.size == 7) getString(R.string.repeat_all_days_label)
            else dias.joinToString(", ") { diasSemana[it - 1] }
        val textoHorario =
            getString(
                R.string.alarm_repeat_weekly_summary,
                "$textoDias · ${formatearHoraVisible()}",
            )
        AppConfirmationDialog.mostrar(
            contexto = this,
            titulo =
                getString(
                    if (idEnEdicion == null) R.string.alarm_confirm_create_title
                    else R.string.alarm_confirm_edit_title
                ),
            mensaje =
                getString(
                    R.string.alarm_confirm_summary,
                    nombre,
                    dosisIndicada,
                    textoHorario,
                    TimeZone.getDefault().id,
                ),
            textoCancelar = getString(R.string.alarm_confirm_edit),
            textoAceptar = getString(R.string.alarm_confirm_save),
            alAceptar = { guardarAlarma(nombre, dosisIndicada, dias) },
        )
    }

    private fun guardarAlarma(nombre: String, dosisIndicada: String, dias: List<Int>) {
        if (guardando || autenticacion.currentUser?.uid != idUsuario) return

        val identificador = idEnEdicion ?: idPendiente ?: referencia.push().key
        if (identificador == null) {
            estado.text = getString(R.string.alarm_prepare_error)
            return
        }
        idPendiente = identificador
        val valores =
            mutableMapOf<String, Any?>(
                "id" to identificador,
                "medicamento" to nombre,
                "dosis" to dosisIndicada,
                "diasSemana" to dias,
                "tipoRepeticion" to null,
                "fechaUnica" to null,
                "compartimiento" to null,
                "finalizada" to false,
                "finalizadaEn" to null,
                "hora" to horaFormateada(),
                "zonaHoraria" to TimeZone.getDefault().id,
                "activo" to true,
                "actualizadoEn" to ServerValue.TIMESTAMP,
            )
        if (idEnEdicion == null) valores["creadoEn"] = ServerValue.TIMESTAMP
        guardando = true
        establecerFormularioHabilitado(false)
        estado.text = getString(R.string.alarm_saving)
        referencia.child(identificador).updateChildren(valores).addOnCompleteListener { resultado ->
            if (isFinishing || isDestroyed || autenticacion.currentUser?.uid != idUsuario)
                return@addOnCompleteListener
            guardando = false
            establecerFormularioHabilitado(true)
            actualizarBotones()
            if (!resultado.isSuccessful) {
                val errorDeGuardado = resultado.exception
                android.util.Log.e(
                    "NuevaAlarma",
                    "Firebase rejected alarm ${if (idEnEdicion == null) "create" else "edit"}: ${errorDeGuardado?.javaClass?.simpleName}: ${errorDeGuardado?.message}",
                )
                val permisoDenegado =
                    errorDeGuardado?.message?.contains("permission denied", ignoreCase = true) == true
                estado.text =
                    getString(
                        if (permisoDenegado) R.string.alarm_save_permission_error
                        else R.string.alarm_save_error
                    )
                return@addOnCompleteListener
            }
            DoseOccurrenceTracker.encolarReconciliacion(applicationContext, idUsuario)
            if (idEnEdicion != null) {
                finish()
            } else {
                limpiarFormulario()
                estado.text = getString(R.string.alarm_saved)
            }
        }
    }

    private fun limpiarFormulario() {
        medicamento.text.clear()
        dosis.text.clear()
        diasSeleccionados = BooleanArray(7)
        hora = 8
        minuto = 0
        idPendiente = null
        actualizarControlesDias()
        sincronizarSeleccionHora()
        actualizarBotones()
    }

    private fun establecerFormularioHabilitado(habilitado: Boolean) {
        medicamento.isEnabled = habilitado
        dosis.isEnabled = habilitado
        selectorHora.isEnabled = habilitado
        selectorMinutos.isEnabled = habilitado
        selectorMeridiano.isEnabled = habilitado
        botonGuardar.isEnabled = habilitado
        controlesDias.forEach { it.isEnabled = habilitado }
    }

    private fun horaFormateada() = String.format(Locale.ROOT, "%02d:%02d", hora, minuto)

    private fun formatearHoraVisible(): String {
        val hora12 = ((hora + 11) % 12) + 1
        return String.format(
            Locale.ROOT,
            "%02d:%02d %s",
            hora12,
            minuto,
            getString(if (hora < 12) R.string.time_am else R.string.time_pm),
        )
    }

    private fun Int.indiceDeLunes(): Int = (this + 5) % 7

    private fun Int.enDp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun asegurarCampoEnPantalla() {
        if (!::desplazamientoAlarma.isInitialized) return
        val campoEnfocado = currentFocus ?: return
        val rectangulo = Rect(0, 0, campoEnfocado.width, campoEnfocado.height)
        desplazamientoAlarma.offsetDescendantRectToMyCoords(campoEnfocado, rectangulo)
        val limiteSuperiorVisible = desplazamientoAlarma.scrollY + desplazamientoAlarma.paddingTop
        val limiteInferiorVisible =
            desplazamientoAlarma.scrollY +
                desplazamientoAlarma.height -
                desplazamientoAlarma.paddingBottom
        when {
            rectangulo.bottom > limiteInferiorVisible ->
                desplazamientoAlarma.smoothScrollBy(
                    0,
                    rectangulo.bottom - limiteInferiorVisible + 16.enDp(),
                )
            rectangulo.top < limiteSuperiorVisible ->
                desplazamientoAlarma.smoothScrollBy(
                    0,
                    rectangulo.top - limiteSuperiorVisible - 16.enDp(),
                )
        }
    }
}

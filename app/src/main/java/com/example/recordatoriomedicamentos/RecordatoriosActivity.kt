package com.example.recordatoriomedicamentos

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/** Pantalla para consultar, editar y eliminar alarmas existentes. */
class RecordatoriosActivity : AppCompatActivity() {
    private lateinit var autenticacion: FirebaseAuth
    private lateinit var referencia: DatabaseReference
    private lateinit var idUsuario: String
    private lateinit var listado: LinearLayout
    private lateinit var estadoLista: TextView
    private lateinit var estadoVacio: View
    private lateinit var cuadriculaIndicadores: GridLayout
    private var escucha: ValueEventListener? = null
    private val escucharSesion =
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
        setContentView(R.layout.activity_recordatorios)
        val raiz = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, margenesSistema ->
            val barras = margenesSistema.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            margenesSistema
        }
        ViewCompat.requestApplyInsets(raiz)
        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnAddAlarm).setOnClickListener { abrirFormulario() }
        listado = findViewById(R.id.listRecordatorios)
        estadoLista = findViewById(R.id.tvRecordatorios)
        estadoVacio = findViewById(R.id.alarmEmptyState)
        cuadriculaIndicadores = findViewById(R.id.alarmKpiGrid)
        adaptarCabeceraEIndicadores()
    }

    override fun onStart() {
        super.onStart()
        if (autenticacion.currentUser?.uid != idUsuario) {
            finish()
            return
        }
        autenticacion.addAuthStateListener(escucharSesion)
        escucharRecordatorios()
    }

    override fun onStop() {
        autenticacion.removeAuthStateListener(escucharSesion)
        detenerEscucha()
        super.onStop()
    }

    private fun abrirFormulario(identificador: String? = null) {
        startActivity(
            Intent(this, NuevaAlarmaActivity::class.java).apply {
                identificador?.let { putExtra(NuevaAlarmaActivity.EXTRA_ID_ALARMA_EDICION, it) }
            }
        )
    }

    private fun escucharRecordatorios() {
        detenerEscucha()
        escucha =
            object : ValueEventListener {
                    override fun onDataChange(instantanea: DataSnapshot) {
                        if (autenticacion.currentUser?.uid != idUsuario || isFinishing || isDestroyed)
                            return
                        AlarmSchedule.migrarEsquemaAnterior(referencia, instantanea)
                        renderizarAlarmas(instantanea)
                    }

                    override fun onCancelled(errorDeBaseDatos: DatabaseError) {
                        if (autenticacion.currentUser?.uid == idUsuario && !isFinishing && !isDestroyed) {
                            listado.removeAllViews()
                            estadoVacio.visibility = View.GONE
                            listado.visibility = View.GONE
                            cuadriculaIndicadores.visibility = View.GONE
                            estadoLista.visibility = View.VISIBLE
                            estadoLista.text = getString(R.string.alarm_list_error)
                        }
                    }
                }
                .also(referencia::addValueEventListener)
    }

    private fun renderizarAlarmas(instantanea: DataSnapshot) {
        listado.removeAllViews()
        val filas =
            instantanea.children.filter { fila ->
                fila.child("medicamento").getValue(String::class.java) != null &&
                    fila.child("tipoRepeticion").getValue(String::class.java) != "una_vez" &&
                    fila.child("diasSemana").children.any { (it.value as? Number)?.toInt() in 1..7 }
            }
        cuadriculaIndicadores.visibility = View.VISIBLE
        configurarIndicadoresAlarma(
            programadas = filas.size,
            activas =
                filas.count {
                    it.child("activo").getValue(Boolean::class.java) == true &&
                        it.child("finalizada").getValue(Boolean::class.java) != true
                },
            finalizadas = filas.count { it.child("finalizada").getValue(Boolean::class.java) == true },
        )
        if (filas.isEmpty()) {
            estadoLista.visibility = View.GONE
            estadoVacio.visibility = View.VISIBLE
            listado.visibility = View.GONE
            return
        }
        estadoLista.visibility = View.GONE
        estadoVacio.visibility = View.GONE
        listado.visibility = View.VISIBLE
        filas.forEach { fila -> listado.addView(crearFilaAlarma(fila)) }
    }

    private fun adaptarCabeceraEIndicadores() {
        val anchoDp =
            resources.configuration.screenWidthDp.takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val compacta = anchoDp < 390 || resources.configuration.fontScale >= 1.25f
        val encabezado = findViewById<LinearLayout>(R.id.alarmHero)
        val textoEncabezado = findViewById<LinearLayout>(R.id.alarmHeroCopy)
        val ilustracion = findViewById<ImageView>(R.id.alarmHeroIllustration)
        textoEncabezado.translationY = -resources.getDimension(R.dimen.ui_space_8)
        encabezado.orientation =
            if (compacta) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        textoEncabezado.layoutParams =
            (textoEncabezado.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (compacta) ViewGroup.LayoutParams.MATCH_PARENT else 0
                weight = if (compacta) 0f else 1f
            }
        ilustracion.layoutParams =
            (ilustracion.layoutParams as LinearLayout.LayoutParams).apply {
                val tamano =
                    resources.getDimensionPixelSize(
                        if (compacta) R.dimen.ui_space_96 else R.dimen.device_hero_art_size
                    )
                width = tamano
                height = tamano
                marginStart =
                    if (compacta) 0 else resources.getDimensionPixelSize(R.dimen.ui_space_8)
                gravity = if (compacta) Gravity.END else Gravity.CENTER_VERTICAL
            }
        ilustracion.translationY = -resources.getDimension(R.dimen.ui_space_8)
        cuadriculaIndicadores.columnCount = if (compacta) 1 else 3
        val configuraciones =
            listOf(
                Triple(R.drawable.ic_alarm, R.drawable.bg_feature_lavender, R.color.brand_primary),
                Triple(
                    R.drawable.ic_check_circle,
                    R.drawable.bg_feature_mint,
                    R.color.feature_green,
                ),
                Triple(R.drawable.ic_schedule, R.drawable.bg_feature_peach, R.color.feature_orange),
            )
        for (indice in 0 until cuadriculaIndicadores.childCount) {
            val mosaico = cuadriculaIndicadores.getChildAt(indice) as LinearLayout
            val (idIcono, idFondo, idColor) = configuraciones[indice]
            mosaico.background = ContextCompat.getDrawable(this, idFondo)
            mosaico.minimumHeight =
                resources.getDimensionPixelSize(
                    if (compacta) R.dimen.history_kpi_compact_height else R.dimen.history_kpi_height
                )
            mosaico.orientation = if (compacta) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            mosaico.gravity = if (compacta) Gravity.CENTER_VERTICAL else Gravity.CENTER
            val vistaIcono = mosaico.findViewById<ImageView>(R.id.alarmKpiIcon)
            vistaIcono.setImageResource(idIcono)
            vistaIcono.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, idColor))
            val etiqueta = mosaico.findViewById<TextView>(R.id.alarmKpiLabel)
            val cantidad = mosaico.findViewById<TextView>(R.id.alarmKpiCount)
            etiqueta.setTextColor(ContextCompat.getColor(this, idColor))
            cantidad.setTextColor(ContextCompat.getColor(this, idColor))
            etiqueta.gravity =
                if (compacta) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            etiqueta.layoutParams =
                if (compacta)
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = resources.getDimensionPixelSize(R.dimen.ui_space_8)
                        topMargin = 0
                    }
                else
                    LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        .apply { topMargin = resources.getDimensionPixelSize(R.dimen.ui_space_4) }
            cantidad.gravity = if (compacta) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.CENTER
            cantidad.layoutParams =
                if (compacta)
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                else
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            val parametros = mosaico.layoutParams as GridLayout.LayoutParams
            parametros.width = 0
            parametros.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            parametros.setMargins(3.enDp(), 3.enDp(), 3.enDp(), 3.enDp())
            mosaico.layoutParams = parametros
        }
        configurarIndicadoresAlarma(0, 0, 0)
    }

    private fun configurarIndicadoresAlarma(programadas: Int, activas: Int, finalizadas: Int) {
        val etiquetas =
            listOf(
                R.string.alarm_kpi_scheduled,
                R.string.alarm_kpi_active,
                R.string.alarm_kpi_finalized,
            )
        val valores = listOf(programadas, activas, finalizadas)
        for (indice in 0 until cuadriculaIndicadores.childCount) {
            val mosaico = cuadriculaIndicadores.getChildAt(indice)
            mosaico.findViewById<TextView>(R.id.alarmKpiLabel).setText(etiquetas[indice])
            mosaico.findViewById<TextView>(R.id.alarmKpiCount).text = valores[indice].toString()
        }
    }

    private fun crearFilaAlarma(fila: DataSnapshot): View {
        val compacta =
            resources.configuration.screenWidthDp < 390 ||
                resources.configuration.fontScale >= 1.25f
        val tarjeta =
            MaterialCardView(this).apply {
                radius = resources.getDimension(R.dimen.ui_radius_card)
                cardElevation = 0f
                setCardBackgroundColor(
                    ContextCompat.getColor(this@RecordatoriosActivity, R.color.alarm_item_surface)
                )
                strokeWidth = 0
                layoutParams =
                    LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = resources.getDimensionPixelSize(R.dimen.ui_space_8)
                    }
            }
        val contenido =
            LinearLayout(this).apply {
                orientation = if (compacta) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
        val margenInterior = resources.getDimensionPixelSize(R.dimen.ui_container_padding)
        setPadding(margenInterior, margenInterior, margenInterior, margenInterior)
            }
        val nombre = fila.child("medicamento").getValue(String::class.java).orEmpty()
        val dosisIndicada = fila.child("dosis").getValue(String::class.java).orEmpty()
        val horaProgramada = fila.child("hora").getValue(String::class.java).orEmpty()
        val activa = fila.child("activo").getValue(Boolean::class.java) ?: false
        val finalizada = fila.child("finalizada").getValue(Boolean::class.java) == true
        val dias =
            fila.child("diasSemana")
                .children
                .mapNotNull { (it.value as? Number)?.toInt() }
                .mapNotNull { DIAS_SEMANA.getOrNull(it - 1)?.take(3) }
        val textoRepeticion =
            if (dias.size == 7) getString(R.string.repeat_all_days_label)
            else dias.joinToString(", ")
        val textoEstado =
            when {
                finalizada -> getString(R.string.alarm_finalized)
                activa -> getString(R.string.alarm_status_active)
                else -> getString(R.string.alarm_status_inactive)
            }
        val colorEstado =
            ContextCompat.getColor(
                this,
                when {
                    finalizada -> R.color.text_secondary
                    activa -> R.color.feature_green
                    else -> R.color.feature_orange
                },
            )
        val indicadorEstado =
            TextView(this).apply {
                text = textoEstado
                setTextAppearance(R.style.MedAlarmAlarmItemStatus)
                setTextColor(colorEstado)
                background =
                    dibujableRedondeado(
                        Color.argb(
                            30,
                            Color.red(colorEstado),
                            Color.green(colorEstado),
                            Color.blue(colorEstado),
                        ),
                        28.enDp(),
                    )
                setPadding(10.enDp(), 5.enDp(), 10.enDp(), 5.enDp())
                gravity = Gravity.CENTER
                maxLines = 1
            }

        val mosaicoIcono =
            FrameLayout(this).apply {
                background =
                    ContextCompat.getDrawable(
                        this@RecordatoriosActivity,
                        R.drawable.bg_feature_icon_lavender,
                    )
                layoutParams = LinearLayout.LayoutParams(48.enDp(), 48.enDp())
            }
        mosaicoIcono.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.history_pill)
                contentDescription = null
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(36.enDp(), 36.enDp(), Gravity.CENTER)
            }
        )

        val detalles =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
        detalles.addView(
            crearVistaTexto(nombre, R.style.MedAlarmAlarmItemTitle, R.color.text_primary).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        )
        detalles.addView(
            crearVistaTexto(dosisIndicada, R.style.MedAlarmAlarmItemSecondary, R.color.text_secondary).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 2.enDp() }
            }
        )

        val (horaVisible, periodo) = separarHoraMostrada(horaProgramada)
        val grupoHora =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val vistaHora =
            crearVistaTexto(horaVisible, R.style.MedAlarmAlarmListTime, R.color.brand_primary).apply {
                maxLines = 1
            }
        grupoHora.addView(vistaHora)
        grupoHora.addView(
            crearVistaTexto(periodo, R.style.MedAlarmAlarmItemPeriod, R.color.brand_primary).apply {
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = 3.enDp() }
                if (periodo.isBlank()) visibility = View.GONE
            }
        )
        val filaHorario =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 5.enDp() }
            }
        filaHorario.addView(grupoHora)
        filaHorario.addView(
            View(this).apply {
                setBackgroundColor(
                    ContextCompat.getColor(this@RecordatoriosActivity, R.color.divider_lavender)
                )
                layoutParams =
                    LinearLayout.LayoutParams(1.enDp(), 24.enDp()).apply {
                        marginStart = 8.enDp()
                        marginEnd = 8.enDp()
                    }
            }
        )
        filaHorario.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_calendar_filled)
                imageTintList =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(this@RecordatoriosActivity, R.color.text_secondary)
                    )
                contentDescription = null
                layoutParams =
                    LinearLayout.LayoutParams(18.enDp(), 18.enDp()).apply { marginEnd = 5.enDp() }
            }
        )
        filaHorario.addView(
            crearVistaTexto(textoRepeticion, R.style.MedAlarmAlarmItemSecondary, R.color.text_secondary).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
        )
        detalles.addView(filaHorario)

        val acciones =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
        val botonesAccion =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.enDp() }
            }
        botonesAccion.addView(
            botonConIconoDeAccion(
                R.drawable.ic_edit_filled,
                R.string.action_edit,
                R.color.brand_primary,
                R.color.surface_lavender,
            ) {
                fila.key?.let(::abrirFormulario)
            }
        )
        botonesAccion.addView(
            botonConIconoDeAccion(
                    R.drawable.ic_delete_filled,
                    R.string.action_delete,
                    R.color.error_red,
                    R.color.error_surface,
                ) {
                    confirmarEliminacion(fila)
                }
                .apply {
                    layoutParams =
                        LinearLayout.LayoutParams(40.enDp(), 40.enDp()).apply { marginStart = 6.enDp() }
                }
        )
        if (compacta) {
            val filaMedicamento =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
            filaMedicamento.addView(mosaicoIcono)
            detalles.removeView(filaHorario)
            detalles.layoutParams =
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 10.enDp() }
            filaMedicamento.addView(detalles)
            indicadorEstado.layoutParams =
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = 6.enDp() }
            filaMedicamento.addView(indicadorEstado)
            contenido.addView(filaMedicamento)
            filaHorario.layoutParams =
                LinearLayout.LayoutParams(-1, -2).apply {
                    marginStart = 48.enDp()
                    topMargin = 6.enDp()
                }
            contenido.addView(filaHorario)
            botonesAccion.layoutParams =
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6.enDp() }
            contenido.addView(botonesAccion)
        } else {
            contenido.addView(mosaicoIcono)
            contenido.addView(
                View(this).apply {
                    setBackgroundColor(
                        ContextCompat.getColor(this@RecordatoriosActivity, R.color.divider_lavender)
                    )
                    layoutParams =
                        LinearLayout.LayoutParams(1.enDp(), 62.enDp()).apply {
                            marginStart = 10.enDp()
                            marginEnd = 10.enDp()
                        }
                }
            )
            detalles.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            contenido.addView(detalles)
            acciones.addView(indicadorEstado)
            acciones.addView(botonesAccion)
            acciones.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = 8.enDp() }
            contenido.addView(acciones)
        }

        tarjeta.addView(contenido)
        tarjeta.contentDescription =
            getString(
                R.string.alarm_item_accessibility,
                nombre,
                dosisIndicada,
                "$textoRepeticion, $horaVisible $periodo",
                textoRepeticion,
            )
        return tarjeta
    }

    private fun botonConIconoDeAccion(
        idIcono: Int,
        idDescripcion: Int,
        idTinte: Int,
        idColorFondo: Int,
        accion: () -> Unit,
    ) =
        android.widget.ImageButton(this).apply {
            setImageResource(idIcono)
            imageTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this@RecordatoriosActivity, idTinte))
            background =
                dibujableRedondeado(
                    ContextCompat.getColor(this@RecordatoriosActivity, idColorFondo),
                    16.enDp(),
                )
            contentDescription = getString(idDescripcion)
            isFocusable = true
            isClickable = true
            setPadding(9.enDp(), 9.enDp(), 9.enDp(), 9.enDp())
            layoutParams = LinearLayout.LayoutParams(40.enDp(), 40.enDp())
            elevation = 0f
            setOnClickListener { accion() }
        }

    private fun dibujableRedondeado(color: Int, radioPixeles: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radioPixeles.toFloat()
        }

    private fun confirmarEliminacion(fila: DataSnapshot) {
        val identificador = fila.key ?: return
        val nombre = fila.child("medicamento").getValue(String::class.java).orEmpty()
        AppConfirmationDialog.mostrar(
            contexto = this,
            titulo = getString(R.string.alarm_delete_title),
            mensaje = "$nombre\n\n${getString(R.string.alarm_delete_message)}",
            textoCancelar = getString(R.string.action_cancel),
            textoAceptar = getString(R.string.action_delete),
            alAceptar = {
                referencia.child(identificador).removeValue().addOnCompleteListener { resultado ->
                    if (isFinishing || isDestroyed) return@addOnCompleteListener
                    estadoLista.text =
                        getString(
                            if (resultado.isSuccessful) R.string.alarm_deleted
                            else R.string.alarm_delete_error
                        )
                    if (resultado.isSuccessful)
                        DoseOccurrenceTracker.encolarReconciliacion(applicationContext, idUsuario)
                }
            },
        )
    }

    private fun crearVistaTexto(texto: String, estilo: Int, color: Int) =
        TextView(this).apply {
            text = texto
            setTextAppearance(estilo)
            setTextColor(getColor(color))
        }

    private fun separarHoraMostrada(valor: String): Pair<String, String> =
        runCatching {
                val partes = valor.split(":")
                val hora = partes[0].toInt()
                val minuto = partes[1].toInt()
                require(hora in 0..23 && minuto in 0..59)
                val hora12 = if (hora % 12 == 0) 12 else hora % 12
                String.format(java.util.Locale.ROOT, "%02d:%02d", hora12, minuto) to
                    if (hora < 12) "AM" else "PM"
            }
            .getOrElse { valor to "" }

    private fun mostrarFechaAlarma(valor: String): String =
        runCatching {
                val analizador =
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                        isLenient = false
                        timeZone = java.util.TimeZone.getDefault()
                    }
                val fecha = analizador.parse(valor) ?: return valor
                java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(fecha)
            }
            .getOrDefault(valor)

    private fun Int.enDp() = (this * resources.displayMetrics.density).toInt()

    private fun detenerEscucha() {
        if (::referencia.isInitialized) escucha?.let(referencia::removeEventListener)
        escucha = null
    }

    companion object {
        private const val URL_BASE_DATOS =
            "https://recordatoriodemedicament-90e4f-default-rtdb.firebaseio.com/"
        private val DIAS_SEMANA =
            listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    }
}

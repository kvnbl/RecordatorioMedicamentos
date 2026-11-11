package com.example.recordatoriomedicamentos

import android.content.res.ColorStateList
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HistorialActivity : AppCompatActivity() {
    private var idUsuario = ""
    private lateinit var autenticacion: FirebaseAuth
    private lateinit var referenciaHistorial: DatabaseReference
    private lateinit var estadoVista: TextView
    private lateinit var listaHistorial: LinearLayout
    private lateinit var botonReintentar: MaterialButton
    private var escuchaHistorial: ValueEventListener? = null

    private val escuchaAutenticacion =
        FirebaseAuth.AuthStateListener { autenticacionActual ->
            if (autenticacionActual.currentUser?.uid != idUsuario && !isFinishing) finish()
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
        referenciaHistorial = DoseOccurrenceTracker.referenciaHistorial(idUsuario)
        setContentView(R.layout.activity_historial)
        aplicarMargenesSistema()
        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        estadoVista = findViewById(R.id.tvHistoryState)
        listaHistorial = findViewById(R.id.historyList)
        botonReintentar = findViewById(R.id.btnHistoryRetry)
        botonReintentar.setOnClickListener { escucharHistorial() }
        adaptarEncabezadoYResumen()
        configurarIndicadores(0, 0, 0)
    }

    override fun onStart() {
        super.onStart()
        autenticacion.addAuthStateListener(escuchaAutenticacion)
        escucharHistorial()
    }

    override fun onStop() {
        autenticacion.removeAuthStateListener(escuchaAutenticacion)
        escuchaHistorial?.let(referenciaHistorial::removeEventListener)
        escuchaHistorial = null
        super.onStop()
    }

    private fun aplicarMargenesSistema() {
        val raiz = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, margenesSistema ->
            val barras = margenesSistema.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            margenesSistema
        }
        ViewCompat.requestApplyInsets(raiz)
    }

    private fun adaptarEncabezadoYResumen() {
        val anchoDp =
            resources.configuration.screenWidthDp.takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val compacto = anchoDp < 390 || resources.configuration.fontScale >= 1.25f
        val encabezado = findViewById<LinearLayout>(R.id.historyHero)
        val textoEncabezado = findViewById<LinearLayout>(R.id.historyHeroCopy)
        val ilustracion = findViewById<ImageView>(R.id.historyHeroIllustration)
        encabezado.orientation = if (compacto) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        textoEncabezado.layoutParams =
            (textoEncabezado.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (compacto) ViewGroup.LayoutParams.MATCH_PARENT else 0
                weight = if (compacto) 0f else 1f
            }
        textoEncabezado.translationY = -resources.getDimension(R.dimen.ui_space_8)
        ilustracion.layoutParams =
            (ilustracion.layoutParams as LinearLayout.LayoutParams).apply {
                val tamano =
                    resources.getDimensionPixelSize(
                        if (compacto) R.dimen.ui_space_96 else R.dimen.device_hero_art_size
                    )
                width = tamano
                height = tamano
                marginStart =
                    if (compacto) 0 else resources.getDimensionPixelSize(R.dimen.ui_space_8)
                gravity = if (compacto) Gravity.END else Gravity.CENTER_VERTICAL
            }
        ilustracion.translationY = -resources.getDimension(R.dimen.ui_space_12)

        val cuadricula = findViewById<GridLayout>(R.id.historySummaryGrid)
        cuadricula.columnCount = if (compacto) 1 else 3
        for (indice in 0 until cuadricula.childCount) {
            val mosaico = cuadricula.getChildAt(indice) as LinearLayout
            mosaico.background =
                ContextCompat.getDrawable(
                    this,
                    when (indice) {
                        0 -> R.drawable.bg_history_kpi_strong
                        1 -> R.drawable.bg_history_kpi_blue
                        else -> R.drawable.bg_history_kpi_light
                    },
                )
            mosaico.minimumHeight =
                resources.getDimensionPixelSize(
                    if (compacto) R.dimen.history_kpi_compact_height else R.dimen.history_kpi_height
                )
            val etiqueta = mosaico.findViewById<TextView>(R.id.historyKpiLabel)
            val cantidad = mosaico.findViewById<TextView>(R.id.historyKpiCount)
            mosaico.orientation = if (compacto) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            mosaico.gravity = if (compacto) Gravity.CENTER_VERTICAL else Gravity.CENTER
            etiqueta.layoutParams =
                if (compacto) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                else
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            etiqueta.gravity =
                if (compacto) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            cantidad.layoutParams =
                if (compacto)
                    LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        .apply {
                            marginStart = resources.getDimensionPixelSize(R.dimen.ui_space_8)
                            topMargin = 0
                        }
                else
                    LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        .apply { topMargin = resources.getDimensionPixelSize(R.dimen.ui_space_4) }
            cantidad.gravity = if (compacto) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.CENTER
            val colorTexto = if (indice == 0) R.color.white else R.color.history_blue_strong
            etiqueta.setTextColor(ContextCompat.getColor(this, colorTexto))
            cantidad.setTextColor(ContextCompat.getColor(this, colorTexto))
            val parametros = mosaico.layoutParams as GridLayout.LayoutParams
            parametros.width = 0
            parametros.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            parametros.setMargins(3.enDp(), 3.enDp(), 3.enDp(), 3.enDp())
            mosaico.layoutParams = parametros
        }
    }

    private fun escucharHistorial() {
        escuchaHistorial?.let(referenciaHistorial::removeEventListener)
        estadoVista.visibility = View.VISIBLE
        estadoVista.gravity = Gravity.START
        estadoVista.setText(R.string.history_loading)
        botonReintentar.visibility = View.GONE
        listaHistorial.removeAllViews()
        escuchaHistorial =
            object : ValueEventListener {
                    override fun onDataChange(instantanea: DataSnapshot) {
                        if (isFinishing || isDestroyed || autenticacion.currentUser?.uid != idUsuario) return
                        val ahora = System.currentTimeMillis()
                        val filas =
                            instantanea.children
                                .mapNotNull { registro ->
                                    val estado =
                                        registro.child("estado").getValue(String::class.java)
                                            ?: return@mapNotNull null
                                    if (estado !in ESTADOS_HISTORIAL) return@mapNotNull null
                                    val programadaEn =
                                        (registro.child("scheduledAt").value as? Number)?.toLong()
                                    val confirmadaEn =
                                        (registro.child("confirmadoEn").value as? Number)
                                            ?.toLong()
                                            ?.takeIf { it > 0L }
                                    val respondidaEn =
                                        (registro.child("respuestaEn").value as? Number)
                                            ?.toLong()
                                            ?.takeIf { it > 0L }
                                    val momentoEvento =
                                        programadaEn
                                            ?: confirmadaEn
                                            ?: respondidaEn
                                            ?: return@mapNotNull null
                                    if (
                                        momentoEvento > ahora ||
                                            estado == DoseOccurrenceTracker.ESTADO_PROGRAMADA
                                    )
                                        return@mapNotNull null
                                    val idZona =
                                        registro.child("zonaHoraria").getValue(String::class.java)
                                            ?: TimeZone.getDefault().id
                                    FilaHistorial(
                                        instantanea = registro,
                                        momentoEvento = momentoEvento,
                                        programadaEn = programadaEn ?: momentoEvento,
                                        confirmadaEn = confirmadaEn ?: respondidaEn,
                                        estado = estado,
                                        idZona = idZona,
                                        medicamento =
                                            registro
                                                .child("medicamento")
                                                .getValue(String::class.java)
                                                .orEmpty()
                                                .ifBlank { getString(R.string.medicine_label) },
                                        dosis =
                                            registro
                                                .child("dosis")
                                                .getValue(String::class.java)
                                                .orEmpty(),
                                    )
                                }
                                .sortedByDescending { it.momentoEvento }

                        val filasDeHoy = filas.filter { esHoy(it.programadaEn, it.idZona) }
                        var tomadas = 0
                        var omitidas = 0
                        var tardias = 0
                        filasDeHoy.forEach { fila ->
                            when (fila.estado) {
                                DoseOccurrenceTracker.ESTADO_CONFIRMADA -> {
                                    if ((fila.confirmadaEn ?: fila.programadaEn) > fila.programadaEn)
                                        tardias++
                                    else tomadas++
                                }
                                DoseOccurrenceTracker.ESTADO_NO_TOMADA,
                                DoseOccurrenceTracker.ESTADO_SIN_CONFIRMAR -> omitidas++
                            }
                        }
                        configurarIndicadores(tomadas, omitidas, tardias)
                        listaHistorial.removeAllViews()
                        estadoVista.visibility = View.VISIBLE
                        if (filas.isEmpty()) {
                            estadoVista.setText(R.string.history_empty)
                            estadoVista.gravity = Gravity.CENTER
                        } else {
                            estadoVista.visibility = View.GONE
                        }
                        crearGruposPorFecha(filas).forEach { grupo ->
                            listaHistorial.addView(crearGrupo(grupo))
                        }
                    }

                    override fun onCancelled(errorBaseDatos: DatabaseError) {
                        if (isFinishing || isDestroyed || autenticacion.currentUser?.uid != idUsuario) return
                        listaHistorial.removeAllViews()
                        estadoVista.visibility = View.VISIBLE
                        estadoVista.gravity = Gravity.START
                        estadoVista.setText(R.string.history_error)
                        botonReintentar.visibility = View.VISIBLE
                    }
                }
                .also { referenciaHistorial.addValueEventListener(it) }
    }

    private fun configurarIndicadores(tomadas: Int, omitidas: Int, tardias: Int) {
        configurarIndicador(R.id.historyTakenKpi, R.string.history_taken, tomadas)
        configurarIndicador(R.id.historyMissedKpi, R.string.history_missed, omitidas)
        configurarIndicador(R.id.historyLateKpi, R.string.history_late, tardias)
    }

    private fun configurarIndicador(idMosaico: Int, idTitulo: Int, valor: Int) {
        val mosaico = findViewById<View>(idMosaico)
        mosaico.findViewById<TextView>(R.id.historyKpiLabel).setText(idTitulo)
        mosaico.findViewById<TextView>(R.id.historyKpiCount).text = valor.toString()
    }

    private fun crearGruposPorFecha(filas: List<FilaHistorial>): List<GrupoHistorial> =
        filas
            .groupBy { fila -> "${fila.idZona}|${claveFecha(fila.programadaEn, fila.idZona)}" }
            .map { (clave, eventos) ->
                val separador = clave.indexOf('|')
                GrupoHistorial(
                    clave.substring(0, separador),
                    clave.substring(separador + 1),
                    eventos.sortedByDescending { it.momentoEvento },
                )
            }
            .sortedByDescending { it.filas.maxOf { fila -> fila.momentoEvento } }

    private fun crearGrupo(grupo: GrupoHistorial): View {
        val contenedor =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background =
                    ContextCompat.getDrawable(this@HistorialActivity, R.drawable.bg_history_surface)
                val margenInterior = resources.getDimensionPixelSize(R.dimen.ui_space_12)
                setPadding(margenInterior, margenInterior, margenInterior, margenInterior)
                layoutParams =
                    LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        .apply { topMargin = resources.getDimensionPixelSize(R.dimen.ui_space_12) }
            }
        contenedor.addView(
            crearEtiqueta(
                tituloGrupoFecha(grupo),
                R.style.MedAlarmTextSectionTitle,
                R.color.history_blue_strong,
            )
        )
        grupo.filas.forEach { fila -> contenedor.addView(crearTarjetaEvento(fila)) }
        return contenedor
    }

    private fun tituloGrupoFecha(grupo: GrupoHistorial): String {
        val zona = TimeZone.getTimeZone(grupo.idZona)
        val claveHoy = claveFecha(System.currentTimeMillis(), grupo.idZona)
        if (grupo.claveFecha == claveHoy) return getString(R.string.history_today)
        val ayer =
            Calendar.getInstance(zona).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        if (grupo.claveFecha == claveFecha(ayer, grupo.idZona))
            return getString(R.string.history_yesterday)
        val anoActual =
            SimpleDateFormat("yyyy", Locale.US).apply { timeZone = zona }.format(Date())
        val patron =
            if (grupo.claveFecha.take(4) == anoActual) {
                "EEEE d 'de' MMMM"
            } else "EEEE d 'de' MMMM yyyy"
        return SimpleDateFormat(patron, Locale.getDefault())
            .apply { timeZone = zona }
            .format(Date(grupo.filas.first().programadaEn))
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    private fun crearTarjetaEvento(fila: FilaHistorial): View {
        val compacta =
            resources.configuration.screenWidthDp < 390 ||
                resources.configuration.fontScale >= 1.25f
        val tarjeta =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background =
                    ContextCompat.getDrawable(this@HistorialActivity, R.drawable.bg_history_card)
                elevation = 0f
                val inset = resources.getDimensionPixelSize(R.dimen.ui_container_padding)
                setPadding(inset, inset, inset, inset)
                layoutParams =
                    LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        .apply { topMargin = resources.getDimensionPixelSize(R.dimen.ui_space_8) }
            }
        val nombreMedicamento = fila.medicamento
        val dosis = fila.dosis
        val filaPrincipal =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val tamanoPastilla = resources.getDimensionPixelSize(R.dimen.history_record_icon_size)
        val mosaicoPastilla =
            FrameLayout(this).apply {
                background = null
                layoutParams = LinearLayout.LayoutParams(tamanoPastilla, tamanoPastilla)
            }
        mosaicoPastilla.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.history_pill)
                contentDescription = null
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(tamanoPastilla, tamanoPastilla, Gravity.CENTER)
            }
        )
        filaPrincipal.addView(mosaicoPastilla)

        val detallesMedicamento =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 12.enDp()
                    }
            }
        detallesMedicamento.addView(
            crearEtiqueta(nombreMedicamento, R.style.MedAlarmHistoryRecordTitle, R.color.text_primary).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        )
        if (dosis.isNotBlank()) {
            detallesMedicamento.addView(
                crearEtiqueta(dosis, R.style.MedAlarmTextSecondary, R.color.text_secondary).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams =
                        LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            .apply { topMargin = 4.enDp() }
                }
            )
        }
        filaPrincipal.addView(detallesMedicamento)

        val horaProgramada = AlarmTimeFormatter.formatear(fila.programadaEn, TimeZone.getTimeZone(fila.idZona))
        val (textoEstado, colorEstado, fondoEstado) = presentacionEstado(fila)
        val indicadorEstado = crearIndicadorEstado(textoEstado, colorEstado, fondoEstado)
        val vistaHora =
            crearEtiqueta(horaProgramada, R.style.MedAlarmHistoryRecordTime, R.color.history_blue_strong).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

        if (compacta) {
            tarjeta.addView(filaPrincipal)
            val filaInformacion =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.START
                    layoutParams =
                        LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            .apply {
                                marginStart = tamanoPastilla + 12.enDp()
                                topMargin = 8.enDp()
                            }
                }
            filaInformacion.addView(vistaHora)
            indicadorEstado.layoutParams =
                LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    .apply { topMargin = 4.enDp() }
            filaInformacion.addView(indicadorEstado)
            tarjeta.addView(filaInformacion)
        } else {
            filaPrincipal.addView(
                View(this).apply {
                    setBackgroundColor(
                        ContextCompat.getColor(this@HistorialActivity, R.color.history_divider)
                    )
                    layoutParams =
                        LinearLayout.LayoutParams(1.enDp(), tamanoPastilla).apply {
                            marginStart = 12.enDp()
                            marginEnd = 12.enDp()
                        }
                }
            )
            val horaYEstado =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams =
                        LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            .apply { marginStart = 12.enDp() }
                }
            horaYEstado.addView(vistaHora)
            indicadorEstado.layoutParams =
                LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    .apply { topMargin = 4.enDp() }
            horaYEstado.addView(indicadorEstado)
            filaPrincipal.addView(horaYEstado)
            tarjeta.addView(filaPrincipal)
        }

        if (fila.estado == DoseOccurrenceTracker.ESTADO_NO_TOMADA) {
            tarjeta.addView(
                MaterialButton(this)
                    .apply {
                        text = getString(R.string.history_correct_taken)
                        setTextColor(
                            ContextCompat.getColor(
                                this@HistorialActivity,
                                R.color.history_blue_strong,
                            )
                        )
                        setStrokeColor(
                            ColorStateList.valueOf(
                                ContextCompat.getColor(
                                    this@HistorialActivity,
                                    R.color.history_blue_strong,
                                )
                            )
                        )
                        minHeight = 48.enDp()
                        elevation = 0f
                        setOnClickListener {
                            androidx.appcompat.app.AlertDialog.Builder(this@HistorialActivity)
                                .setMessage(R.string.history_correct_question)
                                .setNegativeButton(R.string.action_cancel, null)
                                .setPositiveButton(R.string.history_confirmed) { _, _ ->
                                    fila.instantanea?.key?.let { clave ->
                                        referenciaHistorial
                                            .child(clave)
                                            .updateChildren(
                                                mapOf(
                                                    "estado" to
                                                        DoseOccurrenceTracker.ESTADO_CONFIRMADA,
                                                    "confirmadoEn" to ServerValue.TIMESTAMP,
                                                )
                                            )
                                    }
                                }
                                .show()
                        }
                    }
                    .apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                )
                                .apply {
                                    topMargin = resources.getDimensionPixelSize(R.dimen.ui_space_8)
                                }
                    }
            )
        }
        return tarjeta
    }

    private fun crearIndicadorEstado(texto: String, color: Int, idFondo: Int) =
        TextView(this).apply {
            this.text = texto
            setTextAppearance(R.style.MedAlarmHistoryRecordStatus)
            setTextColor(ContextCompat.getColor(this@HistorialActivity, color))
            background = ContextCompat.getDrawable(this@HistorialActivity, idFondo)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(10.enDp(), 6.enDp(), 10.enDp(), 6.enDp())
        }

    private fun presentacionEstado(fila: FilaHistorial): Triple<String, Int, Int> =
        when (fila.estado) {
            DoseOccurrenceTracker.ESTADO_CONFIRMADA -> {
                val tardia = (fila.confirmadaEn ?: fila.programadaEn) > fila.programadaEn
                if (tardia)
                    Triple(
                        getString(R.string.history_taken_late),
                        R.color.feature_orange,
                        R.drawable.bg_history_status_late,
                    )
                else
                    Triple(
                        getString(R.string.history_taken_on_time),
                        R.color.feature_green,
                        R.drawable.bg_history_status_taken,
                    )
            }
            DoseOccurrenceTracker.ESTADO_NO_TOMADA ->
                Triple(
                    getString(R.string.history_omitted),
                    R.color.error_red,
                    R.drawable.bg_history_status_missed,
                )
            DoseOccurrenceTracker.ESTADO_SIN_CONFIRMAR ->
                Triple(
                    getString(R.string.history_unconfirmed_short),
                    R.color.error_red,
                    R.drawable.bg_history_status_missed,
                )
            else ->
                Triple(
                    getString(R.string.history_pending_short),
                    R.color.history_blue_strong,
                    R.drawable.bg_history_status_pending,
                )
        }

    private fun esHoy(marcaTemporal: Long, idZona: String): Boolean =
        claveFecha(marcaTemporal, idZona) == claveFecha(System.currentTimeMillis(), idZona)

    private fun claveFecha(marcaTemporal: Long, idZona: String): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone(idZona) }
            .format(Date(marcaTemporal))

    private fun crearEtiqueta(texto: CharSequence, estilo: Int, color: Int) =
        TextView(this).apply {
            this.text = texto
            setTextAppearance(estilo)
            setTextColor(ContextCompat.getColor(this@HistorialActivity, color))
        }

    private fun Int.enDp() = (this * resources.displayMetrics.density).toInt()

    private data class FilaHistorial(
        val instantanea: DataSnapshot?,
        val momentoEvento: Long,
        val programadaEn: Long,
        val confirmadaEn: Long?,
        val estado: String,
        val idZona: String,
        val medicamento: String,
        val dosis: String,
    )

    private data class GrupoHistorial(
        val idZona: String,
        val claveFecha: String,
        val filas: List<FilaHistorial>,
    )

    companion object {
        private val ESTADOS_HISTORIAL =
            setOf(
                DoseOccurrenceTracker.ESTADO_CONFIRMADA,
                DoseOccurrenceTracker.ESTADO_NO_TOMADA,
                DoseOccurrenceTracker.ESTADO_PENDIENTE,
                DoseOccurrenceTracker.ESTADO_SIN_CONFIRMAR,
            )
    }
}

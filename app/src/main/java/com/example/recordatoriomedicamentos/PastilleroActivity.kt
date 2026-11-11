package com.example.recordatoriomedicamentos

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth

class PastilleroActivity : AppCompatActivity() {
    private lateinit var estado: TextView
    private lateinit var botonConexion: Button
    private lateinit var cuerpoConexion: LinearLayout
    private lateinit var indicadorEstado: LinearLayout
    private lateinit var textoIndicadorEstado: TextView
    private lateinit var puntoEstado: View
    private lateinit var valorBluetooth: TextView
    private lateinit var valorBateria: TextView
    private lateinit var valorSincronizacion: TextView
    private lateinit var valorCompartimentos: TextView
    private lateinit var valorProximaAlarma: TextView
    private lateinit var valorTiempoFuncionamiento: TextView
    private lateinit var valorReloj: TextView
    private var claveConfirmacionMostrada: String? = null
    private var volviendoDeAjustes = false
    private var conectarTrasPermisoBluetooth = false
    private var iniciada = false
    private var ultimaSincronizacion = 0L
    private var proximaAlarmaVerificada = false
    private var horaProximaAlarma = ""
    private var compartimentoProximaAlarma = 0
    private var tiempoFuncionamientoMs = 0L
    private var tiempoFuncionamientoObservadoEn = 0L
    private var relojArduino = ""

    private val actualizarMetricas =
        object : Runnable {
            override fun run() {
                if (!iniciada) return
                mostrarMetricasEnVivo()
                window.decorView.postDelayed(this, 30_000L)
            }
        }

    private val solicitudPermiso =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val conectar = conectarTrasPermisoBluetooth
            conectarTrasPermisoBluetooth = false
            if (granted && conectar) conectarPastilleroVinculadoEnAjustes()
            else if (!granted) mostrarEstado(getString(R.string.device_disconnected))
        }

    private val eventosAplicacion =
        object : BroadcastReceiver() {
            override fun onReceive(contexto: Context, intencion: Intent) {
                when (intencion.action) {
                    DeviceConnectionService.ACCION_ESTADO ->
                        mostrarEstado(
                            intencion.getStringExtra(DeviceConnectionService.DATO_ESTADO).orEmpty()
                        )
                    DeviceConnectionService.ACCION_TOMA_PENDIENTE -> consumirAlarmaPendiente()
                    DeviceConnectionService.ACCION_TELEMETRIA -> mostrarTelemetria(intencion)
                }
            }
        }

    override fun onCreate(estadoGuardado: Bundle?) {
        super.onCreate(estadoGuardado)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_pastillero)
        val raiz = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, margenesSistema ->
            val barras = margenesSistema.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            margenesSistema
        }
        ViewCompat.requestApplyInsets(raiz)

        if (FirebaseAuth.getInstance().currentUser == null) {
            finish()
            return
        }
        estado = findViewById(R.id.tvEstadoPastillero)
        botonConexion = findViewById(R.id.btnBluetoothPastillero)
        cuerpoConexion = findViewById(R.id.deviceConnectionBody)
        indicadorEstado = findViewById(R.id.deviceStatusBadge)
        textoIndicadorEstado = findViewById(R.id.deviceStatusBadgeText)
        puntoEstado = findViewById(R.id.deviceStatusDot)
        valorBluetooth =
            configurarMetrica(
                R.id.metricBluetooth,
                R.string.device_metric_bluetooth,
                R.drawable.ic_bluetooth,
                R.color.device_accent,
            )
        valorBateria =
            configurarMetrica(
                R.id.metricBattery,
                R.string.device_metric_battery,
                R.drawable.ic_battery_full,
                R.color.device_accent,
            )
        valorSincronizacion =
            configurarMetrica(
                R.id.metricSync,
                R.string.device_metric_sync,
                R.drawable.ic_sync,
                R.color.device_accent,
            )
        valorCompartimentos =
            configurarMetrica(
                R.id.metricCompartments,
                R.string.device_metric_compartments,
                R.drawable.ic_grid_view,
                R.color.device_accent,
            )
        configurarFilaEstado(R.id.deviceUptimeRow, R.string.device_uptime_label, R.drawable.ic_schedule)
        configurarFilaEstado(R.id.deviceClockRow, R.string.device_clock_label, R.drawable.ic_schedule)
        configurarFilaEstado(
            R.id.deviceNextAlarmRow,
            R.string.device_row_next_alarm,
            R.drawable.ic_schedule,
        )
        valorTiempoFuncionamiento = obtenerValorFila(R.id.deviceUptimeRow)
        valorReloj = obtenerValorFila(R.id.deviceClockRow)
        valorProximaAlarma = obtenerValorFila(R.id.deviceNextAlarmRow)
        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        adaptarDistribucion()
        botonConexion.setOnClickListener {
            if (DeviceConnectionService.estaConectado()) {
                DeviceConnectionService.desconectar(this)
                mostrarEstado(getString(R.string.device_disconnected))
            } else abrirAjustesBluetooth()
        }
        mostrarEstado(DeviceConnectionService.obtenerEstadoActual())
        mostrarTelemetriaDesdePreferencias()
        consumirAlarmaPendiente()
    }

    private fun configurarMetrica(
        idContenedor: Int,
        idTitulo: Int,
        idIcono: Int,
        idColorIcono: Int,
    ): TextView {
        val contenedor = findViewById<View>(idContenedor)
        contenedor.findViewById<TextView>(R.id.metricTitle).setText(idTitulo)
        contenedor.findViewById<ImageView>(R.id.metricIcon).apply {
            setImageResource(idIcono)
            imageTintList = ContextCompat.getColorStateList(this@PastilleroActivity, idColorIcono)
        }
        return contenedor.findViewById(R.id.metricValue)
    }

    private fun configurarFilaEstado(idFila: Int, idEtiqueta: Int, idIcono: Int) {
        val fila = findViewById<View>(idFila)
        fila.findViewById<TextView>(R.id.deviceRowLabel).setText(idEtiqueta)
        fila.findViewById<ImageView>(R.id.deviceRowIcon).setImageResource(idIcono)
    }

    private fun obtenerValorFila(idFila: Int) =
        findViewById<View>(idFila).findViewById<TextView>(R.id.deviceRowValue)

    private fun adaptarDistribucion() {
        val anchoDp =
            resources.configuration.screenWidthDp.takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val escalaFuente = resources.configuration.fontScale.coerceAtLeast(1f)
        val anchoDisponible = (minOf(anchoDp, 720) - 42).coerceAtLeast(1)
        val cuadricula = findViewById<GridLayout>(R.id.deviceMetricsGrid)
        cuadricula.columnCount = (anchoDisponible / (168f * escalaFuente)).toInt().coerceIn(1, 2)
        val margen = (5 * resources.displayMetrics.density).toInt()
        for (indice in 0 until cuadricula.childCount) {
            val mosaico = cuadricula.getChildAt(indice)
            mosaico.background =
                ContextCompat.getDrawable(
                    this,
                    if (indice == 0 || indice == 3) R.drawable.bg_device_metric
                    else R.drawable.bg_device_metric_warm,
                )
            val parametros =
                (mosaico.layoutParams as? GridLayout.LayoutParams) ?: GridLayout.LayoutParams()
            parametros.width = 0
            parametros.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            parametros.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            parametros.setMargins(margen, margen, margen, margen)
            mosaico.layoutParams = parametros
        }

        val compacto = anchoDp < 390 || escalaFuente >= 1.25f
        val encabezado = findViewById<LinearLayout>(R.id.deviceHero)
        val textoEncabezado = findViewById<LinearLayout>(R.id.deviceHeroCopy)
        encabezado.orientation = if (compacto) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        textoEncabezado.layoutParams =
            (textoEncabezado.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (compacto) android.view.ViewGroup.LayoutParams.MATCH_PARENT else 0
                weight = if (compacto) 0f else 1f
            }
        findViewById<ImageView>(R.id.deviceHeroIllustration).apply {
            val tamano =
                resources.getDimensionPixelSize(
                    if (compacto) R.dimen.ui_space_96 else R.dimen.device_hero_art_size
                )
            layoutParams =
                (layoutParams as LinearLayout.LayoutParams).apply {
                    width = tamano
                    height = tamano
                    marginStart =
                        if (compacto) 0 else resources.getDimensionPixelSize(R.dimen.ui_space_8)
                    gravity =
                        if (compacto) android.view.Gravity.END
                        else android.view.Gravity.CENTER_VERTICAL
                }
            translationY = -resources.getDimension(R.dimen.ui_space_12)
        }

        cuerpoConexion.layoutParams =
            (cuerpoConexion.layoutParams as LinearLayout.LayoutParams).apply {
                width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                weight = 0f
            }
        botonConexion.layoutParams =
            (botonConexion.layoutParams as LinearLayout.LayoutParams).apply {
                width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                marginStart = 0
                topMargin = resources.getDimensionPixelSize(R.dimen.ui_space_12)
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
    }

    override fun onStart() {
        super.onStart()
        iniciada = true
        ContextCompat.registerReceiver(
            this,
            eventosAplicacion,
            IntentFilter().apply {
                addAction(DeviceConnectionService.ACCION_ESTADO)
                addAction(DeviceConnectionService.ACCION_TOMA_PENDIENTE)
                addAction(DeviceConnectionService.ACCION_TELEMETRIA)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        mostrarEstado(DeviceConnectionService.obtenerEstadoActual())
        mostrarMetricasEnVivo()
        window.decorView.removeCallbacks(actualizarMetricas)
        window.decorView.postDelayed(actualizarMetricas, 30_000L)
        consumirAlarmaPendiente()
    }

    override fun onStop() {
        iniciada = false
        window.decorView.removeCallbacks(actualizarMetricas)
        runCatching { unregisterReceiver(eventosAplicacion) }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (volviendoDeAjustes) {
            volviendoDeAjustes = false
            if (
                Build.VERSION.SDK_INT >= 31 &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    ) != PackageManager.PERMISSION_GRANTED
            ) {
                conectarTrasPermisoBluetooth = true
                solicitudPermiso.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else conectarPastilleroVinculadoEnAjustes()
        }
    }

    private fun mostrarEstado(valor: String) {
        val conectado = DeviceConnectionService.estaConectado() || valor.startsWith("Conectado")
        val conectando = valor.startsWith("Conectando")
        val estadoVisible =
            when {
                conectado -> getString(R.string.device_hc05_connected)
                conectando || valor.startsWith("Abriendo") ->
                    getString(R.string.device_hc05_connecting)
                else -> getString(R.string.device_hc05_disconnected)
            }
        estado.text = estadoVisible
        textoIndicadorEstado.setText(
            when {
                conectado -> R.string.device_active
                conectando -> R.string.device_status_connecting
                else -> R.string.device_disconnected
            }
        )
        indicadorEstado.background =
            ContextCompat.getDrawable(
                this,
                if (conectado) R.drawable.bg_device_status_active
                else R.drawable.bg_device_status_inactive,
            )
        textoIndicadorEstado.setTextColor(ContextCompat.getColor(this, R.color.device_accent))
        puntoEstado.backgroundTintList = ContextCompat.getColorStateList(this, R.color.device_accent)
        botonConexion.setText(
            if (conectado) R.string.device_disconnect else R.string.device_connect_short
        )
        botonConexion.isEnabled = !conectando
        mostrarMetricasEnVivo()
    }

    private fun mostrarTelemetriaDesdePreferencias() {
        val preferencias = getSharedPreferences("device_telemetry", 0)
        ultimaSincronizacion = preferencias.getLong("last_sync", 0L)
        proximaAlarmaVerificada = preferencias.getBoolean("next_alarm_verified", false)
        horaProximaAlarma = preferencias.getString("next_alarm_time", "").orEmpty()
        compartimentoProximaAlarma = preferencias.getInt("next_alarm_compartment", 0)
        tiempoFuncionamientoMs = preferencias.getLong("uptime_ms", 0L)
        tiempoFuncionamientoObservadoEn = SystemClock.elapsedRealtime()
        relojArduino = preferencias.getString("arduino_clock", "").orEmpty()
        mostrarMetricasEnVivo()
    }

    private fun mostrarTelemetria(intencion: Intent) {
        if (intencion.hasExtra("last_sync")) ultimaSincronizacion = intencion.getLongExtra("last_sync", ultimaSincronizacion)
        if (intencion.hasExtra("next_alarm_verified")) {
            proximaAlarmaVerificada = intencion.getBooleanExtra("next_alarm_verified", false)
            horaProximaAlarma = intencion.getStringExtra("next_alarm_time").orEmpty()
            compartimentoProximaAlarma = intencion.getIntExtra("next_alarm_compartment", 0)
        }
        if (intencion.hasExtra("uptime_ms")) {
            tiempoFuncionamientoMs = intencion.getLongExtra("uptime_ms", tiempoFuncionamientoMs)
            tiempoFuncionamientoObservadoEn = SystemClock.elapsedRealtime()
        }
        if (intencion.hasExtra("arduino_clock"))
            relojArduino = intencion.getStringExtra("arduino_clock").orEmpty()
        mostrarMetricasEnVivo()
    }

    private fun mostrarMetricasEnVivo() {
        val conectado = DeviceConnectionService.estaConectado()
        valorBluetooth.setText(
            if (conectado) R.string.device_metric_signal_stable
            else R.string.device_metric_no_connection
        )
        valorBateria.setText(R.string.device_metric_no_sensor)
        valorCompartimentos.setText(R.string.device_metric_compartments_value)
        valorSincronizacion.text = formatearUltimaSincronizacion(ultimaSincronizacion)
        val tiempoFuncionamientoActual =
            if (tiempoFuncionamientoMs > 0L) tiempoFuncionamientoMs + (SystemClock.elapsedRealtime() - tiempoFuncionamientoObservadoEn) else 0L
        valorTiempoFuncionamiento.text =
            if (tiempoFuncionamientoMs > 0L) formatearTiempoFuncionamiento(tiempoFuncionamientoActual)
            else getString(R.string.device_no_telemetry)
        valorReloj.text = relojArduino.ifBlank { getString(R.string.device_no_telemetry) }
        valorProximaAlarma.text =
            when {
                !proximaAlarmaVerificada -> getString(R.string.device_next_alarm_unverified)
                compartimentoProximaAlarma !in 1..7 || horaProximaAlarma.isBlank() ->
                    getString(R.string.device_no_next_alarm)
                else ->
                    getString(
                        R.string.device_next_alarm_value,
                        horaProximaAlarma,
                        resources.getStringArray(R.array.weekday_names)[compartimentoProximaAlarma - 1],
                    )
            }
    }

    private fun formatearTiempoFuncionamiento(duracionMs: Long): String {
        val segundos = duracionMs / 1_000L
        return getString(
            R.string.device_uptime_value,
            segundos / 86_400L,
            ((segundos % 86_400L) / 3_600L).toInt(),
            ((segundos % 3_600L) / 60L).toInt(),
            (segundos % 60L).toInt(),
        )
    }

    private fun formatearUltimaSincronizacion(marcaTemporal: Long): String {
        if (marcaTemporal <= 0L) return getString(R.string.device_sync_unavailable)
        val segundos = ((System.currentTimeMillis() - marcaTemporal).coerceAtLeast(0L) / 1_000L)
        return when {
            segundos < 5 -> getString(R.string.device_sync_now)
            segundos < 60 -> getString(R.string.device_sync_seconds, segundos.toInt())
            segundos < 3_600 -> getString(R.string.device_sync_minutes, (segundos / 60).toInt())
            segundos < 86_400 -> getString(R.string.device_sync_hours, (segundos / 3_600).toInt())
            else -> getString(R.string.device_sync_days, (segundos / 86_400).toInt())
        }
    }

    private fun abrirAjustesBluetooth() {
        volviendoDeAjustes = true
        estado.setText(R.string.device_opening_bluetooth)
        runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            .onFailure {
                volviendoDeAjustes = false
                mostrarEstado("Error")
            }
    }

    @Suppress("MissingPermission")
    private fun conectarPastilleroVinculadoEnAjustes() {
        volviendoDeAjustes = false
        val adaptador = BluetoothAdapter.getDefaultAdapter()
        if (adaptador == null || !adaptador.isEnabled) {
            mostrarEstado("Desconectado")
            return
        }
        val pastilleros =
            adaptador.bondedDevices.filter {
                it.name?.contains("HC-05", ignoreCase = true) == true ||
                    it.name?.contains("HC05", ignoreCase = true) == true
            }
        when (pastilleros.size) {
            0 -> mostrarEstado("Desconectado")
            1 -> {
                mostrarEstado("Conectando")
                DeviceConnectionService.conectar(this, pastilleros.single().address)
            }
            else ->
                AlertDialog.Builder(this)
                    .setTitle("Selecciona tu pastillero")
                    .setItems(pastilleros.map { it.name ?: "Pastillero" }.toTypedArray()) { _, indice ->
                        mostrarEstado("Conectando")
                        DeviceConnectionService.conectar(this, pastilleros[indice].address)
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
        }
    }

    private fun consumirAlarmaPendiente() {
        val preferencias = getSharedPreferences("pending_intake", 0)
        val idGrupo =
            intent.getStringExtra("pending_group")
                ?: preferencias.getStringSet("groups", emptySet())?.firstOrNull()
        if (idGrupo != null) {
            if (claveConfirmacionMostrada == idGrupo || isFinishing || isDestroyed) return
            claveConfirmacionMostrada = idGrupo
            val titulo = preferencias.getString("group_title_$idGrupo", "").orEmpty()
            AlertDialog.Builder(this)
                .setTitle(R.string.device_confirm_group_title)
                .setMessage(titulo)
                .setPositiveButton(R.string.device_confirm_yes) { _, _ ->
                    responderGrupo(idGrupo, true)
                }
                .setNegativeButton(R.string.device_confirm_no) { _, _ ->
                    responderGrupo(idGrupo, false)
                }
                .setOnCancelListener { claveConfirmacionMostrada = null }
                .show()
            intent.removeExtra("pending_group")
            return
        }
        val clave =
            intent.getStringExtra("pending_intake")
                ?: preferencias.getStringSet("keys", emptySet())?.firstOrNull()
                ?: return
        if (claveConfirmacionMostrada == clave || isFinishing || isDestroyed) return
        claveConfirmacionMostrada = clave
        val medicamento = preferencias.getString("medicine_$clave", "Medicamento") ?: "Medicamento"
        val dosis = preferencias.getString("dose_$clave", "").orEmpty()
        AlertDialog.Builder(this)
            .setTitle("¿Tomaste $medicamento?")
            .setMessage(dosis)
            .setPositiveButton("Sí, la tomé") { _, _ ->
                responderToma(clave, true)
                claveConfirmacionMostrada = null
            }
            .setNegativeButton("No la tomé") { _, _ ->
                responderToma(clave, false)
                claveConfirmacionMostrada = null
            }
            .setOnCancelListener { claveConfirmacionMostrada = null }
            .show()
        intent.removeExtra("pending_intake")
    }

    private fun responderToma(clave: String, tomada: Boolean) {
        DoseAnswerReceiver.guardarRespuesta(this, clave, tomada) {
            claveConfirmacionMostrada = null
            consumirAlarmaPendiente()
        }
    }

    private fun responderGrupo(idGrupo: String, tomada: Boolean) {
        DoseAnswerReceiver.guardarRespuestaDeGrupo(this, idGrupo, tomada) {
            claveConfirmacionMostrada = null
            consumirAlarmaPendiente()
        }
    }
}

package com.example.recordatoriomedicamentos

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Mantiene la conexión HC-05 de la sesión iniciada, aunque no haya una pantalla visible. */
class DeviceConnectionService : Service() {
    companion object {
        const val ACCION_CONECTAR = "com.example.recordatoriomedicamentos.device.CONNECT"
        const val ACCION_DESCONECTAR = "com.example.recordatoriomedicamentos.device.DISCONNECT"
        const val ACCION_ESTADO = "com.example.recordatoriomedicamentos.device.STATE"
        const val ACCION_TOMA_PENDIENTE =
            "com.example.recordatoriomedicamentos.device.INTAKE_PENDING"
        const val ACCION_TELEMETRIA = "com.example.recordatoriomedicamentos.device.TELEMETRY"
        const val DATO_DIRECCION = "address"
        const val DATO_ESTADO = "state"
        private const val CANAL_NOTIFICACIONES = "device_connection"
        private const val ID_NOTIFICACION = 4101
        @Volatile private var estadoPantalla = "Desconectado"
        @Volatile private var socketConectado = false

        fun obtenerEstadoActual() = estadoPantalla

        fun estaConectado() = socketConectado

        fun conectar(contexto: Context, direccion: String? = null) {
            val intencion =
                Intent(contexto, DeviceConnectionService::class.java)
                    .setAction(ACCION_CONECTAR)
                    .putExtra(DATO_DIRECCION, direccion)
            if (Build.VERSION.SDK_INT >= 26) contexto.startForegroundService(intencion)
            else contexto.startService(intencion)
        }

        fun desconectar(contexto: Context) {
            socketConectado = false
            estadoPantalla = "Desconectado"
            contexto.sendBroadcast(
                Intent(ACCION_ESTADO)
                    .setPackage(contexto.packageName)
                    .putExtra(DATO_ESTADO, estadoPantalla)
            )
            contexto.stopService(Intent(contexto, DeviceConnectionService::class.java))
        }

        fun obtenerDireccionGuardada(contexto: Context) =
            contexto.getSharedPreferences("device", 0).getString("address", null)
    }

    private var conexion: ConexionPastillero? = null
    private var referenciaAlarmas: DatabaseReference? = null
    private var escuchaAlarmas: ValueEventListener? = null
    private var instantaneaAlarmas: DataSnapshot? = null
    private var conectado = false
    private var conectando = false
    private var sincronizacionEnCurso = false
    private var protocoloListo = false
    private var estadoActual = "Desconectado"
    private var ultimaSincronizacion = 0L
    private val manejador = android.os.Handler(android.os.Looper.getMainLooper())
    private val consultarEstadoPeriodicamente =
        object : Runnable {
            override fun run() {
                if (conectado && protocoloListo) {
                    if (sincronizacionEnCurso && System.currentTimeMillis() - ultimaSincronizacion > 5000)
                        sincronizacionEnCurso = false
                    if (!sincronizacionEnCurso) conexion?.enviar("SYNC_STATUS")
                    conexion?.enviar("TELEMETRY")
                }
                manejador.postDelayed(this, 5000)
            }
        }

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
        startForeground(ID_NOTIFICACION, crearNotificacionServicio("Preparando conexión Bluetooth…"))
    }

    override fun onBind(intencion: Intent?): IBinder? = null

    override fun onStartCommand(intencion: Intent?, indicadores: Int, idInicio: Int): Int {
        when (intencion?.action) {
            ACCION_DESCONECTAR -> {
                desconectarAhora()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            "ACK_EVENT" -> {
                intencion.getStringExtra("event")?.let { idEvento -> conexion?.enviar("EVENT_ACK;$idEvento") }
                return START_NOT_STICKY
            }
            ACCION_CONECTAR -> {
                val direccion = intencion.getStringExtra(DATO_DIRECCION) ?: obtenerDireccionGuardada(this)
                if (direccion != null) abrirConexion(direccion)
            }
        }
        manejador.removeCallbacks(consultarEstadoPeriodicamente)
        manejador.postDelayed(consultarEstadoPeriodicamente, 5000)
        return START_NOT_STICKY
    }

    @Suppress("MissingPermission")
    private fun abrirConexion(direccion: String) {
        if (conectado || conectando) return actualizarEstado(estadoActual)
        if (!tienePermisoConexion())
            return fallarConexion("Permiso Bluetooth necesario para conectar el HC-05.")
        val adaptador =
            BluetoothAdapter.getDefaultAdapter()
                ?: return fallarConexion("Este teléfono no tiene Bluetooth.")
        if (!adaptador.isEnabled) return fallarConexion("Bluetooth está apagado.")
        val dispositivo =
            try {
                adaptador.getRemoteDevice(direccion)
            } catch (_: Exception) {
                return fallarConexion("No se reconoce el HC-05 vinculado.")
            }
        try {
            if (adaptador.isDiscovering) adaptador.cancelDiscovery()
        } catch (_: SecurityException) {}
        conectando = true
        getSharedPreferences("device", 0).edit().putString("address", direccion).apply()
        actualizarEstado("Conectando a ${dispositivo.name ?: "HC-05"}…")
        conexion?.desconectar()
        conexion =
            ConexionPastillero(
                    alCambiarEstado = { estadoConexion ->
                        manejador.post {
                            conectado = estadoConexion.startsWith("Conectado")
                            socketConectado = conectado
                            conectando = estadoConexion.startsWith("Conectando")
                            actualizarEstado(estadoConexion)
                            if (conectado) {
                                protocoloListo = false
                                conexion?.enviar("CAPS")
                                manejador.postDelayed(
                                    {
                                        if (conectado && !protocoloListo)
                                            actualizarEstado(
                                                "Firmware incompatible. Actualiza el firmware del pastillero."
                                            )
                                    },
                                    3000,
                                )
                            } else {
                                dejarEscuchaAlarmas()
                                if (!conectando) {
                                    manejador.post {
                                        if (!conectado && !conectando) {
                                            stopForeground(STOP_FOREGROUND_REMOVE)
                                            stopSelf()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    alRecibirLinea = { linea -> manejador.post { procesarLinea(linea) } },
                )
                .also { it.conectar(dispositivo) }
    }

    private fun tienePermisoConexion() =
        Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED

    private fun fallarConexion(mensaje: String) {
        actualizarEstado(mensaje)
        manejador.post {
            if (!conectado && !conectando) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun escucharAlarmas() {
        val idUsuario =
            FirebaseAuth.getInstance().currentUser?.uid
                ?: return actualizarEstado("Inicia sesión para sincronizar.")
        val referencia =
            FirebaseDatabase.getInstance(URL_BASE_DATOS)
                .getReference("usuarios")
                .child(idUsuario)
                .child("recordatorios")
        referenciaAlarmas = referencia
        escuchaAlarmas?.let(referencia::removeEventListener)
        escuchaAlarmas =
            object : ValueEventListener {
                    override fun onDataChange(instantanea: DataSnapshot) {
                        instantaneaAlarmas = instantanea
                        AlarmSchedule.migrarEsquemaAnterior(referencia, instantanea)
                        enviarSincronizacionCompleta()
                    }

                    override fun onCancelled(errorBaseDatos: DatabaseError) {
                        actualizarEstado("No se pudo leer la programación.")
                    }
                }
                .also(referencia::addValueEventListener)
    }

    private fun dejarEscuchaAlarmas() {
        escuchaAlarmas?.let { escucha -> referenciaAlarmas?.removeEventListener(escucha) }
        escuchaAlarmas = null
        referenciaAlarmas = null
    }

    private fun enviarSincronizacionCompleta() {
        if (!protocoloListo) return
        val instantanea = instantaneaAlarmas ?: return
        val alarmasActivas =
            instantanea.children.filter {
                it.child("activo").getValue(Boolean::class.java) == true &&
                    it.child("tipoRepeticion").getValue(String::class.java) != "una_vez" &&
                    AlarmSchedule.dias(it).isNotEmpty()
            }
        if (
            alarmasActivas.any {
                it.child("zonaHoraria").getValue(String::class.java) != TimeZone.getDefault().id
            }
        ) {
            actualizarEstado("No sincronizado: las alarmas deben usar la zona horaria del teléfono.")
            return
        }
        val horariosPorDia = sortedSetOf(compareBy<Pair<Int, String>>({ it.first }, { it.second }))
        for (alarma in alarmasActivas) {
            val hora = alarma.child("hora").getValue(String::class.java).orEmpty()
            if (!hora.matches(Regex("\\d{2}:\\d{2}"))) {
                actualizarEstado("Revisa la hora de una alarma.")
                return
            }
            val dias = AlarmSchedule.dias(alarma)
            if (dias.isEmpty()) continue
            dias.forEach { dia -> horariosPorDia += dia to hora }
        }
        if (horariosPorDia.size > AlarmSchedule.MAXIMO_HORARIOS_DIARIOS) {
            actualizarEstado("No sincronizado: el pastillero admite hasta 24 horarios distintos por día.")
            return
        }
        val lineas = mutableListOf("SYNC_BEGIN;${horariosPorDia.size}")
        publicarProximaAlarma(verificada = false)
        val reloj = SimpleDateFormat("yyyy-MM-dd;HH:mm:ss", Locale.US).format(Date())
        lineas += "TIME;$reloj"
        horariosPorDia.forEach { (dia, hora) -> lineas += "DAY_TIME;$dia;$hora" }
        lineas += "SYNC_END"
        ultimaSincronizacion = System.currentTimeMillis()
        sincronizacionEnCurso = true
        lineas.forEach { linea ->
            if (conexion?.enviar(linea) != true) {
                sincronizacionEnCurso = false
                actualizarEstado("No se pudo enviar la programación.")
                return
            }
        }
        actualizarEstado("Sincronizando programación…")
    }

    private fun procesarLinea(linea: String) {
        when {
            linea == "CAPS;WEEKDAYS7;168" -> {
                if (protocoloListo) return
                protocoloListo = true
                actualizarEstado("Conectado · firmware compatible")
                escucharAlarmas()
            }
            linea == "SYNC_OK" -> {
                sincronizacionEnCurso = false
                registrarSincronizacionExitosa()
                publicarProximaAlarma(verificada = true)
                actualizarEstado("Conectado · programación al día")
            }
            linea.startsWith("ERR;") -> {
                sincronizacionEnCurso = false
                actualizarEstado("Arduino rechazó la programación: ${linea.substringAfter(';')}")
            }
            linea.startsWith("DAY_TIME;") -> lineasConfiguracion += linea
            linea == "CONFIG_END" -> {
                val configuracionActual =
                    lineasConfiguracion
                        .toList()
                        .sortedWith(compareBy({ elemento -> elemento.split(';')[1].toInt() }, { elemento -> elemento.split(';')[2] }))
                lineasConfiguracion.clear()
                val configuracionEsperada = obtenerConfiguracionEsperada()
                if (configuracionActual != configuracionEsperada) enviarSincronizacionCompleta()
                else {
                    publicarProximaAlarma(verificada = true)
                    actualizarEstado("Conectado · programación al día")
                }
            }
            linea.startsWith("ALARM_STOPPED;") -> procesarAlarmaDetenida(linea)
            linea.startsWith("ALARM_ON;") -> {
                val campos = linea.split(';')
                val dia = campos.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..7 } ?: return
                val nombreDia = resources.getStringArray(R.array.weekday_names)[dia - 1]
                actualizarEstado("Alarma activa · $nombreDia")
            }
            linea.startsWith("TELEMETRY;") -> procesarTelemetria(linea)
        }
    }

    private fun registrarSincronizacionExitosa() {
        val marcaTemporal = System.currentTimeMillis()
        getSharedPreferences("device_telemetry", 0).edit().putLong("last_sync", marcaTemporal).apply()
        sendBroadcast(
            Intent(ACCION_TELEMETRIA).setPackage(packageName).putExtra("last_sync", marcaTemporal)
        )
    }

    private fun publicarProximaAlarma(verificada: Boolean) {
        val siguiente = if (verificada) obtenerProximaAlarmaActiva() else null
        val medicamento = siguiente?.second?.child("medicamento")?.getValue(String::class.java).orEmpty()
        val hora =
            siguiente
                ?.first
                ?.let { marcaTemporal ->
                    SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault())
                        .format(Date(marcaTemporal))
                }
                .orEmpty()
        val dia =
            siguiente?.first?.let { marcaTemporal ->
                AlarmSchedule.diaSemanaDesdeLunes(
                    Calendar.getInstance().apply { timeInMillis = marcaTemporal }
                )
            } ?: 0
        val cantidadActivas =
            if (verificada)
                instantaneaAlarmas?.children?.count {
                    it.child("activo").getValue(Boolean::class.java) == true
                } ?: 0
            else -1
        getSharedPreferences("device_telemetry", 0)
            .edit()
            .putBoolean("next_alarm_verified", verificada)
            .putString("next_alarm_medicine", medicamento)
            .putString("next_alarm_time", hora)
            .putInt("next_alarm_compartment", dia)
            .putInt("active_alarm_count", cantidadActivas)
            .apply()
        sendBroadcast(
            Intent(ACCION_TELEMETRIA)
                .setPackage(packageName)
                .putExtra("next_alarm_verified", verificada)
                .putExtra("next_alarm_medicine", medicamento)
                .putExtra("next_alarm_time", hora)
                .putExtra("next_alarm_compartment", dia)
                .putExtra("active_alarm_count", cantidadActivas)
        )
    }

    private fun obtenerProximaAlarmaActiva(): Pair<Long, DataSnapshot>? {
        val ahora = System.currentTimeMillis()
        var proxima: Pair<Long, DataSnapshot>? = null
        val zona = TimeZone.getDefault()
        for (alarma in instantaneaAlarmas?.children?.toList() ?: emptyList()) {
            if (alarma.child("activo").getValue(Boolean::class.java) != true) continue
            val partesHora = alarma.child("hora").getValue(String::class.java)?.split(":") ?: continue
            val hora = partesHora.getOrNull(0)?.toIntOrNull() ?: continue
            val minuto = partesHora.getOrNull(1)?.toIntOrNull() ?: continue
            if (hora !in 0..23 || minuto !in 0..59) continue
            val dias = AlarmSchedule.dias(alarma)
            if (dias.isEmpty()) continue
            val calendarioBase = Calendar.getInstance(zona).apply { timeInMillis = ahora }
            for (desfase in 0..7) {
                val calendarioCandidato =
                    (calendarioBase.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_YEAR, desfase)
                        set(Calendar.HOUR_OF_DAY, hora)
                        set(Calendar.MINUTE, minuto)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                val diaSemana = AlarmSchedule.diaSemanaDesdeLunes(calendarioCandidato)
                if (
                    diaSemana in dias &&
                        calendarioCandidato.timeInMillis > ahora &&
                        (proxima == null || calendarioCandidato.timeInMillis < proxima!!.first)
                )
                    proxima = calendarioCandidato.timeInMillis to alarma
            }
        }
        return proxima
    }

    private fun procesarTelemetria(linea: String) {
        val partes = linea.split(';')
        if (partes.size < 6) return
        val tiempoFuncionamiento = partes[1].toLongOrNull() ?: return
        val alarmaSonando = partes[4] == "SONANDO"
        val compartimento = partes[5].toIntOrNull() ?: 0
        val telemetriaEn = System.currentTimeMillis()
        val preferencias = getSharedPreferences("device_telemetry", 0)
        preferencias
            .edit()
            .putLong("last_telemetry_at", telemetriaEn)
            .putLong("uptime_ms", tiempoFuncionamiento)
            .putString("arduino_clock", "${partes[2]} ${partes[3]}")
            .putBoolean("alarm_on", alarmaSonando)
            .putInt("alarm_slot", compartimento)
            .apply()
        sendBroadcast(
            Intent(ACCION_TELEMETRIA)
                .setPackage(packageName)
                .putExtra("uptime_ms", tiempoFuncionamiento)
                .putExtra("arduino_clock", "${partes[2]} ${partes[3]}")
                .putExtra("alarm_on", alarmaSonando)
                .putExtra("alarm_slot", compartimento)
                .putExtra("last_sync", preferencias.getLong("last_sync", 0L))
                .putExtra("telemetry_at", telemetriaEn)
        )
    }

    private val lineasConfiguracion = mutableListOf<String>()

    private fun obtenerConfiguracionEsperada(): List<String> =
        instantaneaAlarmas
            ?.children
            ?.filter {
                it.child("activo").getValue(Boolean::class.java) == true &&
                    it.child("tipoRepeticion").getValue(String::class.java) != "una_vez"
            }
            ?.flatMap { alarma ->
                val hora =
                    alarma.child("hora").getValue(String::class.java) ?: return@flatMap emptyList()
                AlarmSchedule.dias(alarma).map { dia -> "DAY_TIME;$dia;$hora" }
            }
            ?.distinct()
            ?.sortedWith(compareBy({ it.split(';')[1].toInt() }, { it.split(';')[2] }))
            ?: emptyList()

    private fun procesarAlarmaDetenida(linea: String) {
        val campos = linea.split(';')
        if (campos.size < 5) return
        val idEvento = campos[1]
        val fecha = campos[3]
        val hora = campos[4]
        val fechaConvertida =
            runCatching {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .apply {
                            isLenient = false
                            timeZone = TimeZone.getDefault()
                        }
                        .parse(fecha)
                }
                .getOrNull() ?: return
        val calendario =
            Calendar.getInstance(TimeZone.getDefault()).apply {
                setTime(fechaConvertida)
                val partesHora = hora.split(":")
                set(Calendar.HOUR_OF_DAY, partesHora.getOrNull(0)?.toIntOrNull() ?: return)
                set(Calendar.MINUTE, partesHora.getOrNull(1)?.toIntOrNull() ?: return)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val dia = AlarmSchedule.diaSemanaDesdeLunes(calendario)
        val coincidencias =
            instantaneaAlarmas
                ?.children
                ?.filter { alarma ->
                    alarma.child("activo").getValue(Boolean::class.java) == true &&
                        AlarmSchedule.dias(alarma).contains(dia) &&
                        alarma.child("hora").getValue(String::class.java) == hora
                }
                .orEmpty()
        if (coincidencias.isEmpty()) {
            conexion?.enviar("EVENT_ACK;$idEvento")
            return
        }
        val idUsuario = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val idGrupo = "${dia}_${fecha.replace("-", "")}_${hora.replace(":", "")}"
        val registros =
            coincidencias.mapNotNull { alarma ->
                val idAlarma = alarma.key ?: return@mapNotNull null
                val clave = DoseOccurrenceTracker.claveEvento(idAlarma, calendario)
                val datos =
                    DoseOccurrenceTracker.crearOcurrencia(
                            idAlarma,
                            alarma,
                            calendario,
                            System.currentTimeMillis(),
                        )
                        .toMutableMap()
                datos["diaCompartimento"] = dia
                datos["eventoArduino"] = idEvento
                datos["alarmaDetenidaEn"] = ServerValue.TIMESTAMP
                Triple(clave, datos, alarma)
            }
        val tareas =
            registros.map { (clave, datos, _) ->
                DoseOccurrenceTracker.referenciaHistorial(idUsuario).child(clave).updateChildren(datos)
            }
        com.google.android.gms.tasks.Tasks.whenAllComplete(tareas).addOnCompleteListener {
            if (tareas.any { tarea -> !tarea.isSuccessful }) return@addOnCompleteListener
            val preferencias = getSharedPreferences("pending_intake", 0)
            val claves = registros.map { it.first }.toSet()
            val todasLasClaves =
                preferencias.getStringSet("keys", emptySet()).orEmpty().toMutableSet().apply {
                    addAll(claves)
                }
            val grupos =
                preferencias.getStringSet("groups", emptySet()).orEmpty().toMutableSet().apply {
                    add(idGrupo)
                }
            val medicamentos =
                registros.map { it.third.child("medicamento").getValue(String::class.java).orEmpty() }
            val dosis =
                registros.map { it.third.child("dosis").getValue(String::class.java).orEmpty() }
            preferencias
                .edit()
                .putStringSet("keys", todasLasClaves)
                .putStringSet("groups", grupos)
                .putStringSet("group_$idGrupo", claves)
                .putString(
                    "group_title_$idGrupo",
                    medicamentos.zip(dosis).joinToString("\n") { (nombre, dosisIndicada) ->
                        if (dosisIndicada.isBlank()) nombre else "$nombre · $dosisIndicada"
                    },
                )
                .apply()
            registros.forEach { (clave, _, alarma) ->
                preferencias
                    .edit()
                    .putString(
                        "medicine_$clave",
                        alarma.child("medicamento").getValue(String::class.java).orEmpty(),
                    )
                    .putString(
                        "dose_$clave",
                        alarma.child("dosis").getValue(String::class.java).orEmpty(),
                    )
                    .putString("group_for_$clave", idGrupo)
                    .apply()
            }
            notificarGrupoTomas(idGrupo, medicamentos.size)
            sendBroadcast(
                Intent(ACCION_TOMA_PENDIENTE).setPackage(packageName).putExtra("group_id", idGrupo)
            )
            conexion?.enviar("EVENT_ACK;$idEvento")
            actualizarEstado("Alarma detenida · confirma la toma en la app")
        }
    }

    private fun notificarGrupoTomas(idGrupo: String, cantidad: Int) {
        val respuestaSi = crearIntencionPendienteGrupo(idGrupo, "yes")
        val respuestaNo = crearIntencionPendienteGrupo(idGrupo, "no")
        val abrirAplicacion =
            PendingIntent.getActivity(
                this,
                idGrupo.hashCode(),
                Intent(this, PastilleroActivity::class.java)
                    .putExtra("pending_group", idGrupo)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notificacion =
            NotificationCompat.Builder(this, "intake")
                .setSmallIcon(R.drawable.ic_medication)
                .setContentTitle(getString(R.string.device_confirm_group_title))
                .setContentText(getString(R.string.device_confirm_group_count, cantidad))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            getSharedPreferences("pending_intake", 0)
                                .getString("group_title_$idGrupo", "")
                        )
                )
                .setContentIntent(abrirAplicacion)
                .setAutoCancel(false)
                .addAction(R.drawable.ic_medication, getString(R.string.device_confirm_yes), respuestaSi)
                .addAction(R.drawable.ic_medication, getString(R.string.device_confirm_no), respuestaNo)
                .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
            idGrupo.hashCode(),
            notificacion,
        )
    }

    private fun crearIntencionPendienteGrupo(idGrupo: String, respuesta: String) =
        PendingIntent.getBroadcast(
            this,
            (idGrupo + respuesta).hashCode(),
            Intent(this, DoseAnswerReceiver::class.java)
                .putExtra("group_id", idGrupo)
                .putExtra("answer", respuesta),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun actualizarEstado(valor: String) {
        estadoActual = valor
        estadoPantalla = valor
        emitirEstado(valor)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
            ID_NOTIFICACION,
            crearNotificacionServicio(valor),
        )
    }

    private fun emitirEstado(valor: String) {
        sendBroadcast(Intent(ACCION_ESTADO).setPackage(packageName).putExtra(DATO_ESTADO, valor))
    }

    private fun crearNotificacionServicio(valor: String): Notification {
        val estadoVisible =
            when {
                conectado -> getString(R.string.device_connected)
                valor.startsWith("Preparando") -> getString(R.string.device_connecting)
                valor.startsWith("Conectado") -> getString(R.string.device_connected)
                valor.startsWith("Conectando") -> getString(R.string.device_connecting)
                valor == "Desconectado" -> getString(R.string.device_disconnected)
                else -> getString(R.string.device_connection_lost)
            }
        return NotificationCompat.Builder(this, CANAL_NOTIFICACIONES)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentTitle(getString(R.string.nav_device))
            .setContentText(estadoVisible)
            .setOngoing(conectado || conectando)
            .build()
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= 26) {
            val gestorNotificaciones = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            gestorNotificaciones.createNotificationChannel(
                NotificationChannel(
                    CANAL_NOTIFICACIONES,
                    "Conexión del pastillero",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
            gestorNotificaciones.createNotificationChannel(
                NotificationChannel(
                    "intake",
                    "Confirmación de tomas",
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
        }
    }

    private fun desconectarAhora() {
        val estabaActivo = conectado || conectando || estadoPantalla != "Desconectado"
        conectado = false
        conectando = false
        protocoloListo = false
        socketConectado = false
        dejarEscuchaAlarmas()
        manejador.removeCallbacks(consultarEstadoPeriodicamente)
        conexion?.desconectar()
        conexion = null
        estadoActual = "Desconectado"
        estadoPantalla = estadoActual
        if (estabaActivo) emitirEstado(estadoActual)
    }

    override fun onDestroy() {
        desconectarAhora()
        super.onDestroy()
    }

    private val URL_BASE_DATOS = "https://recordatoriodemedicament-90e4f-default-rtdb.firebaseio.com/"
}

class DoseAnswerReceiver : BroadcastReceiver() {
    companion object {
        fun guardarRespuestaDeGrupo(
            contexto: Context,
            idGrupo: String,
            tomada: Boolean,
            alGuardar: (() -> Unit)? = null,
        ) {
            val preferencias = contexto.getSharedPreferences("pending_intake", 0)
            val claves = preferencias.getStringSet("group_$idGrupo", emptySet()).orEmpty().toList()
            if (claves.isEmpty()) {
                alGuardar?.invoke()
                return
            }
            var restantes = claves.size
            claves.forEach { clave ->
                guardarRespuesta(contexto, clave, tomada) {
                    restantes--
                    if (restantes == 0) {
                        val grupos =
                            preferencias
                                .getStringSet("groups", emptySet())
                                .orEmpty()
                                .toMutableSet()
                                .apply { remove(idGrupo) }
                        preferencias
                            .edit()
                            .putStringSet("groups", grupos)
                            .remove("group_$idGrupo")
                            .remove("group_title_$idGrupo")
                            .apply()
                        (contexto.getSystemService(Context.NOTIFICATION_SERVICE)
                                as NotificationManager)
                            .cancel(idGrupo.hashCode())
                        alGuardar?.invoke()
                    }
                }
            }
        }

        fun guardarRespuesta(contexto: Context, clave: String, tomada: Boolean, alGuardar: (() -> Unit)? = null) {
            val idUsuario = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val estado =
                if (tomada) DoseOccurrenceTracker.ESTADO_CONFIRMADA
                else DoseOccurrenceTracker.ESTADO_NO_TOMADA
            val actualizaciones =
                mutableMapOf<String, Any>(
                    "estado" to estado,
                    "respuestaEn" to com.google.firebase.database.ServerValue.TIMESTAMP,
                )
            if (tomada) actualizaciones["confirmadoEn"] = com.google.firebase.database.ServerValue.TIMESTAMP
            else actualizaciones["confirmadoEn"] = 0L
            DoseOccurrenceTracker.referenciaHistorial(idUsuario)
                .child(clave)
                .updateChildren(actualizaciones)
                .addOnSuccessListener {
                    val preferencias = contexto.getSharedPreferences("pending_intake", 0)
                    val claves =
                        preferencias.getStringSet("keys", emptySet()).orEmpty().toMutableSet().apply {
                            remove(clave)
                        }
                    val idGrupo = preferencias.getString("group_for_$clave", null)
                    preferencias
                        .edit()
                        .putStringSet("keys", claves)
                        .remove("medicine_$clave")
                        .remove("dose_$clave")
                        .remove("group_for_$clave")
                        .apply()
                    if (
                        idGrupo != null &&
                            preferencias.getStringSet("group_$idGrupo", emptySet()).orEmpty().none { otraClave ->
                                otraClave != clave && otraClave in claves
                            }
                    ) {
                        val grupos =
                            preferencias
                                .getStringSet("groups", emptySet())
                                .orEmpty()
                                .toMutableSet()
                                .apply { remove(idGrupo) }
                        preferencias
                            .edit()
                            .putStringSet("groups", grupos)
                            .remove("group_$idGrupo")
                            .remove("group_title_$idGrupo")
                            .apply()
                        (contexto.getSystemService(Context.NOTIFICATION_SERVICE)
                                as NotificationManager)
                            .cancel(idGrupo.hashCode())
                    }
                    (contexto.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .cancel(clave.hashCode())
                    alGuardar?.invoke()
                }
        }
    }

    override fun onReceive(contexto: Context, intencion: Intent) {
        val idGrupo = intencion.getStringExtra("group_id")
        if (idGrupo != null) {
            guardarRespuestaDeGrupo(contexto, idGrupo, intencion.getStringExtra("answer") == "yes")
            return
        }
        val clave = intencion.getStringExtra("key") ?: return
        val tomada = intencion.getStringExtra("answer") == "yes"
        guardarRespuesta(contexto, clave, tomada)
    }
}

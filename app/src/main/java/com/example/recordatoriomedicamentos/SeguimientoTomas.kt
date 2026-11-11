package com.example.recordatoriomedicamentos

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Mantiene el registro de tomas y reconcilia confirmaciones tardías de la cuenta activa. */
object DoseOccurrenceTracker {
    const val ESTADO_PROGRAMADA = "programada"
    const val ESTADO_PENDIENTE = "pendiente"
    const val ESTADO_CONFIRMADA = "confirmada"
    const val ESTADO_NO_TOMADA = "no_tomada"
    const val ESTADO_SIN_CONFIRMAR = "sin_confirmacion"
    const val VERSION_SEGUIMIENTO = 1L
    private const val URL_BASE_DATOS =
        "https://recordatoriodemedicament-90e4f-default-rtdb.firebaseio.com/"
    private const val DATO_ID_USUARIO = "uid"
    private const val DIAS_VENTANA = 30
    private const val OMITIDA_DESPUES_MS = 24L * 60L * 60L * 1000L

    fun iniciar(contexto: Context, idUsuario: String) {
        val restricciones =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val trabajoPeriodico =
            PeriodicWorkRequestBuilder<DoseOccurrenceWorker>(15, TimeUnit.MINUTES)
                .setConstraints(restricciones)
                .setInputData(Data.Builder().putString(DATO_ID_USUARIO, idUsuario).build())
                .build()
        val gestor = WorkManager.getInstance(contexto)
        gestor.enqueueUniquePeriodicWork(
            nombreTrabajoPeriodico(idUsuario),
            ExistingPeriodicWorkPolicy.KEEP,
            trabajoPeriodico,
        )
        encolarReconciliacion(contexto, idUsuario)
    }

    fun encolarReconciliacion(contexto: Context, idUsuario: String) {
        val solicitud =
            OneTimeWorkRequestBuilder<DoseOccurrenceWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setInputData(Data.Builder().putString(DATO_ID_USUARIO, idUsuario).build())
                .build()
        WorkManager.getInstance(contexto)
            .enqueueUniqueWork(nombreTrabajoInmediato(idUsuario), ExistingWorkPolicy.REPLACE, solicitud)
    }

    fun detener(contexto: Context, idUsuario: String) {
        WorkManager.getInstance(contexto).cancelUniqueWork(nombreTrabajoPeriodico(idUsuario))
        WorkManager.getInstance(contexto).cancelUniqueWork(nombreTrabajoInmediato(idUsuario))
    }

    private fun nombreTrabajoPeriodico(idUsuario: String) = "dose-occurrences-periodic-$idUsuario"

    private fun nombreTrabajoInmediato(idUsuario: String) = "dose-occurrences-now-$idUsuario"

    internal fun raiz() = FirebaseDatabase.getInstance(URL_BASE_DATOS).getReference("usuarios")

    internal fun referenciaHistorial(idUsuario: String): DatabaseReference =
        raiz().child(idUsuario).child("historial")

    internal fun asegurarInicioSeguimiento(referencia: DatabaseReference): Long {
        val pestillo = CountDownLatch(1)
        var valor = System.currentTimeMillis()
        referencia.runTransaction(
            object : Transaction.Handler {
                override fun doTransaction(datosActuales: MutableData): Transaction.Result {
                    val valorExistente = (datosActuales.value as? Number)?.toLong()
                    if (valorExistente != null) {
                        valor = valorExistente
                        return Transaction.success(datosActuales)
                    }
                    datosActuales.value = valor
                    return Transaction.success(datosActuales)
                }

                override fun onComplete(
                    errorDeBaseDatos: DatabaseError?,
                    confirmada: Boolean,
                    instantaneaActual: DataSnapshot?,
                ) {
                    if (confirmada)
                        valor = (instantaneaActual?.value as? Number)?.toLong() ?: valor
                    pestillo.countDown()
                }
            },
            false,
        )
        if (!pestillo.await(20, TimeUnit.SECONDS))
            throw FirebaseNetworkException("Tracking start update timed out")
        return valor
    }

    internal fun versionDeSeguimientoValida(instantanea: DataSnapshot): Boolean =
        (instantanea.child("trackingVersion").value as? Number)?.toLong() == VERSION_SEGUIMIENTO

    internal fun claveEvento(idAlarma: String, calendario: Calendar): String {
        val fecha =
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .apply { timeZone = calendario.timeZone }
                .format(Date(calendario.timeInMillis))
        val hora =
            SimpleDateFormat("HHmm", Locale.US)
                .apply { timeZone = calendario.timeZone }
                .format(Date(calendario.timeInMillis))
        return "${idAlarma}_${fecha}_$hora"
    }

    internal fun crearOcurrencia(
        idAlarma: String,
        instantaneaAlarma: DataSnapshot,
        calendario: Calendar,
        ahora: Long,
    ): Map<String, Any> {
        val programadaEn = calendario.timeInMillis
        val estadoInicial =
            when {
                programadaEn > ahora -> ESTADO_PROGRAMADA
                ahora >= programadaEn + OMITIDA_DESPUES_MS -> ESTADO_SIN_CONFIRMAR
                else -> ESTADO_PENDIENTE
            }
        val zonaHoraria = calendario.timeZone
        return mapOf(
            "id" to claveEvento(idAlarma, calendario),
            "idRecordatorio" to idAlarma,
            "medicamento" to (instantaneaAlarma.child("medicamento").getValue(String::class.java) ?: ""),
            "dosis" to (instantaneaAlarma.child("dosis").getValue(String::class.java) ?: ""),
            "diaCompartimento" to AlarmSchedule.diaSemanaDesdeLunes(calendario),
            "fechaProgramada" to
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .apply { timeZone = zonaHoraria }
                    .format(Date(programadaEn)),
            "horaProgramada" to
                SimpleDateFormat("HH:mm", Locale.US)
                    .apply { timeZone = zonaHoraria }
                    .format(Date(programadaEn)),
            "zonaHoraria" to zonaHoraria.id,
            "scheduledAt" to programadaEn,
            "estado" to estadoInicial,
            "trackingVersion" to VERSION_SEGUIMIENTO,
        )
    }

    internal fun calcularOcurrencias(
        instantaneaAlarma: DataSnapshot,
        ahora: Long,
        inicioSeguimiento: Long,
    ): Sequence<Pair<String, Map<String, Any>>> {
        val idAlarma = instantaneaAlarma.key ?: return emptySequence()
        val idZonaHoraria =
            instantaneaAlarma.child("zonaHoraria").getValue(String::class.java)
                ?: return emptySequence()
        val zonaHoraria = TimeZone.getTimeZone(idZonaHoraria)
        val partesHora =
            instantaneaAlarma.child("hora").getValue(String::class.java)?.split(":")
                ?: return emptySequence()
        if (partesHora.size != 2) return emptySequence()
        val hora = partesHora[0].toIntOrNull() ?: return emptySequence()
        val minuto = partesHora[1].toIntOrNull() ?: return emptySequence()
        if (hora !in 0..23 || minuto !in 0..59) return emptySequence()
        val dias = AlarmSchedule.dias(instantaneaAlarma)
        if (dias.isEmpty()) return emptySequence()

        val editadaEn =
            (instantaneaAlarma.child("actualizadoEn").value as? Number)?.toLong()
                ?: inicioSeguimiento
        val fechaMasTemprana =
            maxOf(inicioSeguimiento, editadaEn, ahora - DIAS_VENTANA * 24L * 60L * 60L * 1000L)
        val limiteTemporal = ahora + DIAS_VENTANA * 24L * 60L * 60L * 1000L
        val primerDia =
            Calendar.getInstance(zonaHoraria).apply {
                timeInMillis = fechaMasTemprana
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val ocurrencias = mutableListOf<Pair<String, Map<String, Any>>>()
        for (desplazamiento in 0..DIAS_VENTANA * 2) {
            val candidata =
                (primerDia.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, desplazamiento)
                    set(Calendar.HOUR_OF_DAY, hora)
                    set(Calendar.MINUTE, minuto)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            val diaSemanaDesdeLunes = AlarmSchedule.diaSemanaDesdeLunes(candidata)
            if (
                diaSemanaDesdeLunes !in dias ||
                    candidata.timeInMillis < fechaMasTemprana ||
                    candidata.timeInMillis > limiteTemporal
            )
                continue
            val datos = crearOcurrencia(idAlarma, instantaneaAlarma, candidata, ahora)
            ocurrencias += claveEvento(idAlarma, candidata) to datos
        }
        return ocurrencias.asSequence()
    }

    internal fun reconciliar(idUsuario: String) {
        val usuario = raiz().child(idUsuario)
        val referenciaAlarmas = usuario.child("recordatorios")
        val instantaneaAnterior = Tasks.await(referenciaAlarmas.get(), 25, TimeUnit.SECONDS)
        AlarmSchedule.migrarEsquemaAnteriorBloqueante(referenciaAlarmas, instantaneaAnterior)
        val instantaneaAlarmas = Tasks.await(referenciaAlarmas.get(), 25, TimeUnit.SECONDS)
        val ahora = System.currentTimeMillis()
        val inicioSeguimiento =
            asegurarInicioSeguimiento(usuario.child("metadata").child("seguimientoDesde"))
        val candidatas = linkedMapOf<String, Map<String, Any>>()
        for (alarma in instantaneaAlarmas.children) {
            val estaActiva = alarma.child("activo").getValue(Boolean::class.java) == true
            if (!estaActiva) continue
            val idAlarma = alarma.key ?: continue
            val ocurrenciasAlarma = calcularOcurrencias(alarma, ahora, inicioSeguimiento).toList()
            ocurrenciasAlarma.forEach { (clave, evento) -> candidatas[clave] = evento }
        }

        val historial = usuario.child("historial")
        val instantaneaHistorial = Tasks.await(historial.get(), 25, TimeUnit.SECONDS)
        val clavesExistentes = instantaneaHistorial.children.mapNotNull { it.key }.toSet()
        for ((clave, evento) in candidatas) {
            if (clave !in clavesExistentes) {
                crearSiNoExiste(historial.child(clave), evento)
            }
        }

        for (registro in instantaneaHistorial.children) {
            if (!versionDeSeguimientoValida(registro)) continue
            val clave = registro.key ?: continue
            val programadaEn =
                (registro.child("scheduledAt").value as? Number)?.toLong() ?: continue
            val estadoActual = registro.child("estado").value as? String ?: ESTADO_PROGRAMADA
            val confirmadaEn = registro.child("confirmadoEn").value
            val esFutura = programadaEn > ahora
            if (esFutura && clave !in candidatas && estadoActual != ESTADO_CONFIRMADA) {
                Tasks.await(historial.child(clave).removeValue(), 25, TimeUnit.SECONDS)
                continue
            }
            val estadoDestino =
                when {
                    estadoActual == ESTADO_NO_TOMADA -> ESTADO_NO_TOMADA
                    confirmadaEn != null || estadoActual == ESTADO_CONFIRMADA -> ESTADO_CONFIRMADA
                    programadaEn > ahora -> ESTADO_PROGRAMADA
                    ahora >= programadaEn + OMITIDA_DESPUES_MS -> ESTADO_SIN_CONFIRMAR
                    else -> ESTADO_PENDIENTE
                }
            if (estadoDestino != estadoActual) {
                actualizarEstadoSinRetroceso(historial.child(clave), estadoDestino)
            }
        }
    }

    private fun actualizarEstadoSinRetroceso(referencia: DatabaseReference, estado: String) {
        val pestillo = CountDownLatch(1)
        referencia.runTransaction(
            object : Transaction.Handler {
                override fun doTransaction(datosActuales: MutableData): Transaction.Result {
                    val mapa = datosActuales.value as? Map<*, *> ?: return Transaction.abort()
                    val estadoExistente = mapa["estado"] as? String
                    if (
                        estadoExistente == ESTADO_CONFIRMADA ||
                            estadoExistente == ESTADO_NO_TOMADA ||
                            mapa["confirmadoEn"] != null
                    )
                        return Transaction.abort()
                    datosActuales.child("estado").value = estado
                    return Transaction.success(datosActuales)
                }

                override fun onComplete(
                    errorDeBaseDatos: DatabaseError?,
                    confirmada: Boolean,
                    instantaneaActual: DataSnapshot?,
                ) {
                    pestillo.countDown()
                }
            },
            false,
        )
        if (!pestillo.await(20, TimeUnit.SECONDS))
            throw FirebaseNetworkException("Dose status update timed out")
    }

    private fun crearSiNoExiste(referencia: DatabaseReference, valor: Map<String, Any>) {
        val pestillo = CountDownLatch(1)
        referencia.runTransaction(
            object : Transaction.Handler {
                override fun doTransaction(datosActuales: MutableData): Transaction.Result {
                    if (datosActuales.value != null) return Transaction.abort()
                    datosActuales.value = valor
                    return Transaction.success(datosActuales)
                }

                override fun onComplete(
                    errorDeBaseDatos: DatabaseError?,
                    confirmada: Boolean,
                    instantaneaActual: DataSnapshot?,
                ) {
                    pestillo.countDown()
                }
            },
            false,
        )
        if (!pestillo.await(20, TimeUnit.SECONDS))
            throw FirebaseNetworkException("Dose event creation timed out")
    }
}

class DoseOccurrenceWorker(contexto: Context, parametros: WorkerParameters) :
    Worker(contexto, parametros) {
    override fun doWork(): Result {
        val idUsuario = inputData.getString("uid") ?: return Result.failure()
        if (FirebaseAuth.getInstance().currentUser?.uid != idUsuario) return Result.success()
        return try {
            DoseOccurrenceTracker.reconciliar(idUsuario)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

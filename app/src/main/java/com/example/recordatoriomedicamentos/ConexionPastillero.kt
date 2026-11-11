package com.example.recordatoriomedicamentos

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Conexión serie SPP para HC-05 (9600 baudios configurados en el módulo/sketch). */
class ConexionPastillero(
    private val alCambiarEstado: (String) -> Unit,
    private val alRecibirLinea: (String) -> Unit,
) {
    companion object {
        private val UUID_SERVICIO_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TIEMPO_ESPERA_CONEXION_MS = 15_000L
    }

    @Volatile private var socketBluetooth: BluetoothSocket? = null
    @Volatile private var salida: OutputStream? = null
    private val activa = AtomicBoolean(false)
    private val generacion = AtomicLong(0)
    private val manejadorPrincipal = Handler(Looper.getMainLooper())

    @Suppress("MissingPermission")
    fun conectar(dispositivo: BluetoothDevice) {
        desconectar()
        val intento = generacion.incrementAndGet()
        Thread {
                var socketLocal: BluetoothSocket? = null
                val tiempoAgotado = AtomicBoolean(false)
                try {
                    alCambiarEstado("Conectando a ${dispositivo.name ?: "HC-05"}…")
                    socketLocal = dispositivo.createRfcommSocketToServiceRecord(UUID_SERVICIO_SPP)
                    synchronized(this) {
                        if (generacion.get() != intento) {
                            socketLocal.close()
                            return@Thread
                        }
                        socketBluetooth = socketLocal
                    }
                    val tareaTiempoEspera = Runnable {
                        if (generacion.get() == intento && !activa.get()) {
                            tiempoAgotado.set(true)
                            try {
                                socketLocal.close()
                            } catch (_: Exception) {}
                        }
                    }
                    manejadorPrincipal.postDelayed(tareaTiempoEspera, TIEMPO_ESPERA_CONEXION_MS)
                    socketLocal.connect()
                    manejadorPrincipal.removeCallbacks(tareaTiempoEspera)
                    if (generacion.get() != intento) {
                        socketLocal.close()
                        return@Thread
                    }
                    salida = socketLocal.outputStream
                    activa.set(true)
                    alCambiarEstado("Conectado a ${dispositivo.name ?: "HC-05"}")
                    val entrada =
                        BufferedReader(InputStreamReader(socketLocal.inputStream, Charsets.UTF_8))
                    while (activa.get() && generacion.get() == intento) {
                        val linea = entrada.readLine() ?: break
                        alRecibirLinea(linea.trim())
                    }
                    if (generacion.get() == intento && activa.get()) {
                        alCambiarEstado("Bluetooth desconectado: el HC-05 cerró la conexión.")
                    }
                } catch (fallo: Exception) {
                    manejadorPrincipal.removeCallbacksAndMessages(null)
                    if (generacion.get() == intento) {
                        val detalle =
                            if (tiempoAgotado.get()) {
                                "No se pudo conectar en 15 segundos. Comprueba que el HC-05 esté encendido y libre."
                            } else {
                                "No se pudo conectar al HC-05: ${fallo.message ?: fallo.javaClass.simpleName}."
                            }
                        alCambiarEstado(detalle)
                    }
                } finally {
                    if (generacion.get() == intento) {
                        activa.set(false)
                        try {
                            socketLocal?.close()
                        } catch (_: Exception) {}
                        synchronized(this) {
                            if (socketBluetooth === socketLocal) socketBluetooth = null
                            salida = null
                        }
                    } else {
                        try {
                            socketLocal?.close()
                        } catch (_: Exception) {}
                    }
                }
            }
            .start()
    }

    @Synchronized
    fun enviar(linea: String): Boolean {
        return try {
            val flujo = salida ?: return false
            flujo.write((linea + "\n").toByteArray(Charsets.UTF_8))
            flujo.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun estaConectado(): Boolean = activa.get() && salida != null

    @Synchronized
    fun desconectar() {
        generacion.incrementAndGet()
        activa.set(false)
        try {
            socketBluetooth?.close()
        } catch (_: Exception) {}
        socketBluetooth = null
        salida = null
    }
}

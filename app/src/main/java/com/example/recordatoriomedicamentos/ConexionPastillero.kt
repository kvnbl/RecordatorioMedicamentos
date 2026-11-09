package com.example.recordatoriomedicamentos

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Conexión serie SPP para HC-05 (9600 baudios configurados en el módulo/sketch). */
class ConexionPastillero(
    private val alCambiarEstado: (String) -> Unit,
    private val alRecibirLinea: (String) -> Unit
) {
    companion object {
        private val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var salida: OutputStream? = null
    private val activa = AtomicBoolean(false)

    fun conectar(dispositivo: BluetoothDevice) {
        desconectar()
        Thread {
            try {
                alCambiarEstado("Conectando a ${dispositivo.name ?: "HC-05"}…")
                val nuevoSocket = dispositivo.createRfcommSocketToServiceRecord(UUID_SPP)
                nuevoSocket.connect()
                socket = nuevoSocket
                salida = nuevoSocket.outputStream
                activa.set(true)
                alCambiarEstado("Conectado a ${dispositivo.name ?: "HC-05"}")
                val entrada = BufferedReader(InputStreamReader(nuevoSocket.inputStream, Charsets.UTF_8))
                while (activa.get()) {
                    val linea = entrada.readLine() ?: break
                    alRecibirLinea(linea.trim())
                }
            } catch (error: Exception) {
                if (activa.get()) alCambiarEstado("Bluetooth desconectado: ${error.message ?: "sin conexión"}")
            } finally {
                activa.set(false)
                try { socket?.close() } catch (_: Exception) { }
                socket = null
                salida = null
            }
        }.start()
    }

    @Synchronized fun enviar(linea: String): Boolean {
        return try {
            val stream = salida ?: return false
            stream.write((linea + "\n").toByteArray(Charsets.UTF_8))
            stream.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun estaConectado(): Boolean = activa.get() && salida != null

    fun desconectar() {
        activa.set(false)
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        salida = null
    }
}

package com.example.recordatoriomedicamentos

import android.os.Bundle
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.os.Handler
import android.os.Looper
import java.util.Calendar
import java.util.TimeZone
import com.google.firebase.database.ServerValue
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.text.ParseException

class PastilleroActivity : AppCompatActivity() {


    private lateinit var textoEstado: TextView
    private lateinit var textoProgramacion: TextView
    private var referencia: DatabaseReference? = null
    private var escucha: ValueEventListener? = null
    private var uid = ""
    // Conserva los últimos recordatorios recibidos de Firebase.
    private var datosRecordatorios: DataSnapshot? = null
    // Avisos que están esperando que el usuario confirme la toma.
    private val tomasPendientes = linkedMapOf<String, Map<String, Any>>()
    // Identificadores de las tomas que ya se confirmaron.
    private val tomasConfirmadas = mutableSetOf<String>()
    // Evita guardar varias veces por presionar repetidamente el botón.
    private var guardandoToma = false
    // Primero debemos consultar qué tomas ya están confirmadas.
    private var historialListo = false
    // Permite programar tareas en el hilo que actualiza la pantalla.
    private val manejador = Handler(Looper.getMainLooper())
    private lateinit var conexionBluetooth: ConexionPastillero
    private val permisoBluetooth = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) elegirDispositivoEmparejado() else textoEstado.text = "Permiso Bluetooth necesario para conectar el HC-05."
    }

    // Esta tarea revisará los horarios y volverá a ejecutarse cada segundo.
    private val revisarCadaSegundo = object : Runnable {
        override fun run() {
            revisarHorarios()

            manejador.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_pastillero)
        val pantalla = findViewById<android.view.View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(pantalla) { vista, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            vista.setPadding(
                barras.left,
                barras.top,
                barras.right,
                barras.bottom
            )
            insets
        }

        ViewCompat.requestApplyInsets(pantalla)


        textoEstado = findViewById(R.id.tvEstadoPastillero)
        textoProgramacion = findViewById(R.id.tvProgramacionPastillero)
        conexionBluetooth = ConexionPastillero(
            alCambiarEstado = { estado -> runOnUiThread {
                textoEstado.text = estado
                if (estado.startsWith("Conectado")) datosRecordatorios?.let(::sincronizarConArduino)
            } },
            alRecibirLinea = { linea -> runOnUiThread { procesarRespuestaArduino(linea) } }
        )
        findViewById<Button>(R.id.btnBluetoothPastillero).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                permisoBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else elegirDispositivoEmparejado()
        }
        val usuario = FirebaseAuth.getInstance().currentUser

        if (usuario == null) {
            finish()
            return
        }

        uid = usuario.uid

        referencia = FirebaseDatabase.getInstance(
            "https://recordatoriodemedicament-90e4f-default-rtdb.firebaseio.com/"
        ).getReference("usuarios")
            .child(uid)
            .child("recordatorios")
        findViewById<Button>(R.id.btnConfirmarToma)
            .setOnClickListener {
                confirmarToma()
            }

        findViewById<Button>(R.id.btnVolverPastillero)
            .setOnClickListener {
                finish()
            }
    }

    override fun onStart() {
        super.onStart()

        if (FirebaseAuth.getInstance().currentUser?.uid != uid) {
            finish()
            return
        }

        val ubicacion = referencia ?: return

        textoEstado.text = "Cargando programación…"
        cargarConfirmaciones()

        escucha = object : ValueEventListener {

            override fun onDataChange(datos: DataSnapshot) {
                // Guardamos los datos para poder revisar sus horarios.
                datosRecordatorios = datos

                // Mostramos la lista de recordatorios.
                mostrarProgramacion(datos)
                if (conexionBluetooth.estaConectado()) sincronizarConArduino(datos)

                // Revisamos inmediatamente si alguno corresponde a este minuto.
                revisarHorarios()
            }

            override fun onCancelled(error: DatabaseError) {
                textoEstado.text = "No se pudo leer la programación."
                textoProgramacion.text = error.message
            }
        }

        escucha?.let { ubicacion.addValueEventListener(it) }
        manejador.post(revisarCadaSegundo)
    }

    @Suppress("MissingPermission")
    private fun elegirDispositivoEmparejado() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            textoEstado.text = "Este teléfono no tiene Bluetooth."
            return
        }
        if (!adapter.isEnabled) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            textoEstado.text = "Activa Bluetooth y vuelve a conectar el HC-05."
            return
        }
        val dispositivos = adapter.bondedDevices.toList()
        if (dispositivos.isEmpty()) {
            textoEstado.text = "Empareja primero el HC-05 desde los ajustes Bluetooth del teléfono."
            startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            return
        }
        val nombres = dispositivos.map { "${it.name ?: "Dispositivo Bluetooth"} (${it.address})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Selecciona el HC-05 emparejado")
            .setItems(nombres) { _, indice -> conexionBluetooth.conectar(dispositivos[indice]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun sincronizarConArduino(datos: DataSnapshot) {
        val activos = datos.children.filter { it.child("activo").value as? Boolean == true }
        if (activos.size > 2 || activos.any { ((it.child("compartimiento").value as? Number)?.toInt() ?: 0) !in 1..2 } ||
            activos.mapNotNull { (it.child("compartimiento").value as? Number)?.toInt() }.distinct().size != activos.size
        ) {
            textoEstado.text = "No sincronizado: el pastillero admite un recordatorio activo por compartimiento (1 y 2)."
            return
        }
        if (activos.any {
                (it.child("zonaHoraria").value as? String) != TimeZone.getDefault().id
            }
        ) {
            textoEstado.text = "No sincronizado: conecta el teléfono con la misma zona horaria usada al crear los recordatorios."
            return
        }
        val ahora = java.text.SimpleDateFormat("yyyy-MM-dd;HH:mm:ss", Locale.US).format(Date())
        val lineas = mutableListOf("SYNC_BEGIN;${activos.size}", "TIME;$ahora")
        for (registro in activos) {
            val id = registro.key ?: continue
            val slot = (registro.child("compartimiento").value as? Number)?.toInt() ?: continue
            val hora = registro.child("hora").value as? String ?: continue
            val dias = registro.child("diasSemana").children.mapNotNull { (it.value as? Number)?.toInt() }
            if (dias.any { it !in 1..7 } || !hora.matches(Regex("\\d{2}:\\d{2}"))) {
                textoEstado.text = "No sincronizado: revisa días y hora del recordatorio."
                return
            }
            val mascaraDias = dias.fold(0) { mascara, dia -> mascara or (1 shl (dia - 1)) }
            lineas.add("SLOT;$slot;$mascaraDias;$hora;$id")
        }
        lineas.add("SYNC_END")
        for (linea in lineas) {
            if (!conexionBluetooth.enviar(linea)) {
                textoEstado.text = "No se pudo enviar la programación al Arduino."
                return
            }
        }
        textoEstado.text = "Programación enviada; esperando confirmación del Arduino…"
    }

    private fun procesarRespuestaArduino(linea: String) {
        when {
            linea == "SYNC_OK" -> textoEstado.text = "HC-05 conectado; programación sincronizada."
            linea.startsWith("ERR;") -> textoEstado.text = "Arduino rechazó la sincronización: ${linea.substringAfter(';')}"
            linea.startsWith("TAKEN;") -> registrarTomaFisica(linea)
            linea.startsWith("ALARM_ON") -> textoEstado.text = "El pastillero está avisando una toma."
        }
    }

    private fun registrarTomaFisica(linea: String) {
        val campos = linea.split(';')
        if (campos.size != 4) return
        val slot = campos[1].toIntOrNull() ?: return
        val fecha = campos[2]
        val hora = campos[3]
        val datos = datosRecordatorios ?: return
        val fechaHora = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$fecha $hora")
        } catch (_: ParseException) { null } ?: return
        val calendario = Calendar.getInstance().apply { time = fechaHora }
        val dia = (calendario.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val registro = datos.children.firstOrNull {
            (it.child("activo").value as? Boolean == true) &&
                (it.child("compartimiento").value as? Number)?.toInt() == slot &&
                it.child("hora").value as? String == hora &&
                it.child("diasSemana").children.any { d -> (d.value as? Number)?.toInt() == dia }
        } ?: return
        val idRecordatorio = registro.key ?: return
        val idToma = "${idRecordatorio}_${fecha}_${hora.replace(":", "") }"
        val historial = referencia?.parent?.child("historial") ?: return
        if (idToma in tomasConfirmadas) return
        val toma = mapOf<String, Any>(
            "idRecordatorio" to idRecordatorio,
            "medicamento" to (registro.child("medicamento").value as? String ?: "Sin nombre"),
            "dosis" to (registro.child("dosis").value as? String ?: ""),
            "compartimiento" to slot,
            "fechaProgramada" to fecha,
            "horaProgramada" to hora,
            "zonaHoraria" to (registro.child("zonaHoraria").value as? String ?: TimeZone.getDefault().id),
            "estado" to "confirmada",
            "confirmadoEn" to ServerValue.TIMESTAMP
        )
        historial.child(idToma).setValue(toma).addOnSuccessListener {
            tomasConfirmadas.add(idToma)
            tomasPendientes.remove(idToma)
            textoEstado.text = "Toma del compartimiento $slot confirmada desde el pastillero."
            mostrarSiguienteAviso()
        }
    }

    private fun mostrarProgramacion(datos: DataSnapshot) {
        val nombresDias = listOf(
            "Lunes", "Martes", "Miércoles", "Jueves",
            "Viernes", "Sábado", "Domingo"
        )

        val recordatorios = datos.children.map { registro ->
            val medicamento =
                registro.child("medicamento").value as? String ?: "Sin nombre"

            val dosis =
                registro.child("dosis").value as? String ?: ""

            val hora =
                registro.child("hora").value as? String ?: ""

            val zona =
                registro.child("zonaHoraria").value as? String ?: ""

            val compartimiento =
                (registro.child("compartimiento").value as? Number)?.toInt()

            val activo =
                registro.child("activo").value as? Boolean ?: false

            val dias = registro.child("diasSemana").children.mapNotNull { dia ->
                val numero = (dia.value as? Number)?.toInt()

                numero?.let { nombresDias.getOrNull(it - 1) }
            }.joinToString(", ")

            """
                Medicamento: $medicamento
                Dosis: $dosis
                Días: $dias
                Hora: $hora
                Zona horaria: $zona
                Compartimiento: ${compartimiento ?: "Sin asignar"}
                Estado: ${if (activo) "Activo" else "Inactivo"}
            """.trimIndent()
        }

        if (recordatorios.isEmpty()) {
            textoEstado.text = "Todavía no hay programación."
            textoProgramacion.text = "Crea un recordatorio desde el celular."
        } else {
            textoEstado.text =
                "Programación cargada: ${recordatorios.size} recordatorio(s)."

            textoProgramacion.text = recordatorios.joinToString("\n\n")
        }
    }

    private fun revisarHorarios() {
        // Esperamos a conocer las confirmaciones anteriores.
        if (!historialListo) return

        val datos = datosRecordatorios ?: return

        for (registro in datos.children) {
            val activo = registro.child("activo").value as? Boolean ?: false
            if (!activo) continue

            val idRecordatorio = registro.key ?: continue

            val zona = registro.child("zonaHoraria").value as? String
                ?: continue

            val zonaHoraria = TimeZone.getTimeZone(zona)
            val ahora = Calendar.getInstance(zonaHoraria)

            // Lunes = 1, martes = 2, ..., domingo = 7.
            val diaActual = (ahora.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1

            val diasProgramados = registro.child("diasSemana").children
                .mapNotNull { dia ->
                    (dia.value as? Number)?.toInt()
                }

            val horaProgramada = registro.child("hora").value as? String
                ?: continue

            val formatoHora = SimpleDateFormat("HH:mm", Locale.US)
            formatoHora.timeZone = zonaHoraria

            val horaActual = formatoHora.format(ahora.time)

            // Pasamos al siguiente registro si no corresponde a este minuto.
            if (diaActual !in diasProgramados || horaActual != horaProgramada) {
                continue
            }

            val formatoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            formatoFecha.timeZone = zonaHoraria

            val fechaProgramada = formatoFecha.format(ahora.time)

            // Cada toma tiene una identificación distinta según fecha y hora.
            val idToma =
                "${idRecordatorio}_${fechaProgramada}_${horaProgramada.replace(":", "")}"

            // No agregamos una toma que ya está confirmada o pendiente.
            if (idToma in tomasConfirmadas || idToma in tomasPendientes) {
                continue
            }

            val medicamento =
                registro.child("medicamento").value as? String ?: "Sin nombre"

            val dosis =
                registro.child("dosis").value as? String ?: ""

            val compartimiento =
                (registro.child("compartimiento").value as? Number)?.toInt()
                    ?: continue

            tomasPendientes[idToma] = mapOf(
                "idRecordatorio" to idRecordatorio,
                "medicamento" to medicamento,
                "dosis" to dosis,
                "compartimiento" to compartimiento,
                "fechaProgramada" to fechaProgramada,
                "horaProgramada" to horaProgramada,
                "zonaHoraria" to zona
            )
        }

        mostrarSiguienteAviso()
    }
    private fun cargarConfirmaciones() {
        historialListo = false

        val historial = referencia?.parent?.child("historial") ?: return

        historial.get().addOnCompleteListener { tarea ->
            if (
                isFinishing ||
                isDestroyed ||
                FirebaseAuth.getInstance().currentUser?.uid != uid
            ) {
                return@addOnCompleteListener
            }

            if (tarea.isSuccessful) {
                tomasConfirmadas.clear()

                for (registro in tarea.result.children) {
                    if (registro.child("estado").value == "confirmada") {
                        registro.key?.let { tomasConfirmadas.add(it) }
                    }
                }

                // Retiramos cualquier pendiente que ya figure confirmado.
                for (id in tomasConfirmadas) {
                    tomasPendientes.remove(id)
                }

                historialListo = true
                revisarHorarios()
            } else {
                textoEstado.text =
                    "No se pudo consultar el historial. Vuelve a abrir esta pantalla."
            }
        }
    }

    private fun mostrarSiguienteAviso() {
        val textoAlarma = findViewById<TextView>(R.id.tvAlarmaPastillero)
        val botonConfirmar = findViewById<Button>(R.id.btnConfirmarToma)

        val toma = tomasPendientes.entries.firstOrNull()

        if (toma == null) {
            textoAlarma.text = "Sin tomas pendientes"
            botonConfirmar.isEnabled = false
            return
        }

        val datos = toma.value

        textoAlarma.text = """
        Toma pendiente

        Medicamento: ${datos["medicamento"]}
        Dosis: ${datos["dosis"]}
        Compartimiento: ${datos["compartimiento"]}
        Fecha: ${datos["fechaProgramada"]}
        Hora: ${datos["horaProgramada"]}

        Avisos pendientes: ${tomasPendientes.size}
    """.trimIndent()

        botonConfirmar.isEnabled = historialListo && !guardandoToma
    }

    private fun confirmarToma() {
        if (guardandoToma || !historialListo) return

        if (FirebaseAuth.getInstance().currentUser?.uid != uid) {
            finish()
            return
        }

        val toma = tomasPendientes.entries.firstOrNull() ?: return
        val idToma = toma.key

        val historial = referencia?.parent?.child("historial") ?: return

        val confirmacion = toma.value + mapOf(
            "estado" to "confirmada",
            "confirmadoEn" to ServerValue.TIMESTAMP
        )

        guardandoToma = true
        textoEstado.text = "Guardando confirmación…"
        mostrarSiguienteAviso()

        historial.child(idToma).setValue(confirmacion)
            .addOnCompleteListener { tarea ->
                if (
                    isFinishing ||
                    isDestroyed ||
                    FirebaseAuth.getInstance().currentUser?.uid != uid
                ) {
                    return@addOnCompleteListener
                }

                guardandoToma = false

                if (tarea.isSuccessful) {
                    tomasConfirmadas.add(idToma)
                    tomasPendientes.remove(idToma)

                    textoEstado.text = "Confirmación guardada."
                } else {
                    textoEstado.text =
                        "No se pudo guardar. Puedes volver a intentarlo."
                }

                mostrarSiguienteAviso()
            }
    }

    override fun onStop() {
        // Detenemos la revisión del reloj al salir de esta pantalla.
        manejador.removeCallbacks(revisarCadaSegundo)

        // Dejamos de escuchar los cambios de Firebase.
        escucha?.let { referencia?.removeEventListener(it) }
        escucha = null
        conexionBluetooth.desconectar()

        super.onStop()
    }
}

package com.example.recordatoriomedicamentos

import android.os.Bundle
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

        super.onStop()
    }
}
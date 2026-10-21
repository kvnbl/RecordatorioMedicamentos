package com.example.recordatoriomedicamentos

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.util.Locale
import java.util.TimeZone
import java.text.SimpleDateFormat
import java.util.Date

class RecordatoriosActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var referencia: DatabaseReference
    private var escucha: ValueEventListener? = null
    private var escuchaHistorial: ValueEventListener? = null
    private var uid = ""

    private lateinit var medicamento: EditText
    private lateinit var dosis: EditText
    private lateinit var botonDias: Button
    private lateinit var botonHora: Button
    private lateinit var botonGuardar: Button
    private lateinit var compartimientos: RadioGroup
    private lateinit var estado: TextView
    private lateinit var listado: TextView

    private val nombresDias = arrayOf(
        "Lunes", "Martes", "Miércoles", "Jueves",
        "Viernes", "Sábado", "Domingo"
    )

    private var diasElegidos = BooleanArray(7)
    private var hora = 8
    private var minuto = 0
    private var idPendiente: String? = null
    private var guardando = false

    private val escucharSesion = FirebaseAuth.AuthStateListener {
        if (it.currentUser?.uid != uid) {
            detenerEscucha()
            if (!isFinishing) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()

        val usuario = auth.currentUser
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

        setContentView(R.layout.activity_recordatorios)

        val pantalla = findViewById<View>(R.id.main)

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

        medicamento = findViewById(R.id.etMedicamento)
        dosis = findViewById(R.id.etDosis)
        botonDias = findViewById(R.id.btnDias)
        botonHora = findViewById(R.id.btnHora)
        botonGuardar = findViewById(R.id.btnGuardarRecordatorio)
        compartimientos = findViewById(R.id.rgCompartimientos)
        estado = findViewById(R.id.tvEstadoGuardado)
        listado = findViewById(R.id.tvRecordatorios)

        savedInstanceState?.getBooleanArray("dias")?.let {
            if (it.size == 7) diasElegidos = it
        }

        hora = savedInstanceState?.getInt("hora", 8) ?: 8
        minuto = savedInstanceState?.getInt("minuto", 0) ?: 0
        idPendiente = savedInstanceState?.getString("idPendiente")

        actualizarBotones()

        findViewById<TextView>(R.id.tvZonaHoraria).text =
            "Zona horaria: ${TimeZone.getDefault().id}"

        botonDias.setOnClickListener {
            seleccionarDias()
        }

        botonHora.setOnClickListener {
            TimePickerDialog(
                this,
                { _, nuevaHora, nuevoMinuto ->
                    hora = nuevaHora
                    minuto = nuevoMinuto
                    actualizarBotones()
                },
                hora,
                minuto,
                true
            ).show()
        }

        botonGuardar.setOnClickListener {
            guardarRecordatorio()
        }

        findViewById<Button>(R.id.btnVolver).setOnClickListener {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()

        if (!::referencia.isInitialized || auth.currentUser?.uid != uid) {
            finish()
            return
        }

        auth.addAuthStateListener(escucharSesion)
        escucharRecordatorios()
        escucharHistorial()
    }

    override fun onStop() {
        if (::auth.isInitialized) {
            auth.removeAuthStateListener(escucharSesion)
        }

        detenerEscucha()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBooleanArray("dias", diasElegidos)
        outState.putInt("hora", hora)
        outState.putInt("minuto", minuto)
        outState.putString("idPendiente", idPendiente)
        super.onSaveInstanceState(outState)
    }

    private fun seleccionarDias() {
        val seleccionTemporal = diasElegidos.copyOf()

        AlertDialog.Builder(this)
            .setTitle("Días del recordatorio")
            .setMultiChoiceItems(
                nombresDias,
                seleccionTemporal
            ) { _, indice, marcado ->
                seleccionTemporal[indice] = marcado
            }
            .setPositiveButton("Aceptar") { _, _ ->
                diasElegidos = seleccionTemporal
                actualizarBotones()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun actualizarBotones() {
        val dias = diasElegidos.indices.filter {
            diasElegidos[it]
        }

        botonDias.text = if (dias.isEmpty()) {
            "Seleccionar días"
        } else {
            dias.joinToString(", ") {
                nombresDias[it].take(3)
            }
        }

        botonHora.text = "Hora: ${horaFormateada()}"
    }

    private fun horaFormateada(): String {
        return String.format(
            Locale.ROOT,
            "%02d:%02d",
            hora,
            minuto
        )
    }

    private fun guardarRecordatorio() {
        if (guardando || auth.currentUser?.uid != uid) return

        medicamento.error = null
        dosis.error = null

        val nombre = medicamento.text.toString().trim()
        val cantidadIndicada = dosis.text.toString().trim()

        val dias = diasElegidos.indices
            .filter { diasElegidos[it] }
            .map { it + 1 }

        if (nombre.isEmpty() || nombre.length > 80) {
            medicamento.error =
                "Escribe el medicamento, hasta 80 caracteres"
            medicamento.requestFocus()
            return
        }

        if (cantidadIndicada.isEmpty() || cantidadIndicada.length > 80) {
            dosis.error =
                "Escribe la dosis indicada, hasta 80 caracteres"
            dosis.requestFocus()
            return
        }

        if (dias.isEmpty()) {
            estado.text = "Selecciona al menos un día."
            return
        }

        val id = idPendiente ?: referencia.push().key

        if (id == null) {
            estado.text =
                "No se pudo preparar el recordatorio. Intenta nuevamente."
            return
        }

        idPendiente = id

        val compartimiento = if (
            compartimientos.checkedRadioButtonId == R.id.rbCompartimiento2
        ) {
            2
        } else {
            1
        }

        val datos = mapOf<String, Any>(
            "id" to id,
            "medicamento" to nombre,
            "dosis" to cantidadIndicada,
            "diasSemana" to dias,
            "hora" to horaFormateada(),
            "zonaHoraria" to TimeZone.getDefault().id,
            "compartimiento" to compartimiento,
            "activo" to true,
            "creadoEn" to ServerValue.TIMESTAMP
        )

        guardando = true
        habilitarFormulario(false)
        estado.text = "Guardando recordatorio…"

        referencia.child(id)
            .setValue(datos)
            .addOnCompleteListener { resultado ->

                if (
                    isFinishing ||
                    isDestroyed ||
                    auth.currentUser?.uid != uid
                ) {
                    return@addOnCompleteListener
                }

                guardando = false
                habilitarFormulario(true)

                if (resultado.isSuccessful) {
                    idPendiente = null
                    medicamento.text.clear()
                    dosis.text.clear()
                    estado.text = "Recordatorio guardado en Firebase."
                } else {
                    estado.text =
                        "No se pudo guardar. Revisa tu conexión y los permisos de la base."
                }
            }
    }

    private fun escucharRecordatorios() {
        detenerEscucha()

        escucha = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                if (
                    auth.currentUser?.uid != uid ||
                    isFinishing ||
                    isDestroyed
                ) {
                    return
                }

                val registros = snapshot.children.mapNotNull { registro ->

                    val nombre =
                        registro.child("medicamento").value as? String
                            ?: return@mapNotNull null

                    val dosisTexto =
                        registro.child("dosis").value as? String ?: ""

                    val horaTexto =
                        registro.child("hora").value as? String ?: ""

                    val zona =
                        registro.child("zonaHoraria").value as? String ?: ""

                    val compartimiento =
                        registro.child("compartimiento").value as? Number

                    val dias = registro.child("diasSemana")
                        .children
                        .mapNotNull dia@ {
                            val numero = (it.value as? Number)?.toInt()
                                ?: return@dia null

                            nombresDias.getOrNull(numero - 1)?.take(3)
                        }
                        .joinToString(", ")

                    "$nombre · $dosisTexto\n" +
                            "$dias a las $horaTexto\n" +
                            "Compartimiento ${compartimiento?.toInt() ?: ""} · $zona"
                }

                listado.text = if (registros.isEmpty()) {
                    "Todavía no tienes recordatorios."
                } else {
                    registros.joinToString("\n\n")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (
                    auth.currentUser?.uid == uid &&
                    !isFinishing &&
                    !isDestroyed
                ) {
                    listado.text =
                        "No se pudieron consultar los recordatorios."
                }
            }
        }

        referencia.addValueEventListener(escucha!!)
    }

    private fun escucharHistorial() {
        val textoHistorial = findViewById<TextView>(R.id.tvHistorial)

        // Subimos desde "recordatorios" hasta el usuario
        // y entramos en su carpeta "historial".
        val historial = referencia.parent?.child("historial") ?: return

        escuchaHistorial = object : ValueEventListener {

            override fun onDataChange(datos: DataSnapshot) {
                if (
                    auth.currentUser?.uid != uid ||
                    isFinishing ||
                    isDestroyed
                ) {
                    return
                }

                // Mostramos primero las confirmaciones más recientes.
                val registros = datos.children
                    .filter { it.child("estado").value == "confirmada" }
                    .sortedByDescending {
                        (it.child("confirmadoEn").value as? Number)?.toLong() ?: 0L
                    }
                    .map { registro ->

                        val medicamento =
                            registro.child("medicamento").value as? String ?: ""

                        val dosis =
                            registro.child("dosis").value as? String ?: ""

                        val fechaProgramada =
                            registro.child("fechaProgramada").value as? String ?: ""

                        val horaProgramada =
                            registro.child("horaProgramada").value as? String ?: ""

                        val zona =
                            registro.child("zonaHoraria").value as? String
                                ?: TimeZone.getDefault().id

                        val instante =
                            (registro.child("confirmadoEn").value as? Number)?.toLong()

                        val formato = SimpleDateFormat(
                            "dd/MM/yyyy HH:mm",
                            Locale.getDefault()
                        )
                        formato.timeZone = TimeZone.getTimeZone(zona)

                        val fechaConfirmacion = if (instante != null) {
                            formato.format(Date(instante))
                        } else {
                            "Fecha pendiente"
                        }

                        """
                        Medicamento: $medicamento
                        Dosis: $dosis
                        Programada: $fechaProgramada a las $horaProgramada
                        Confirmada: $fechaConfirmacion
                        Zona horaria: $zona
                    """.trimIndent()
                    }

                textoHistorial.text = if (registros.isEmpty()) {
                    "Todavía no hay tomas confirmadas."
                } else {
                    registros.joinToString("\n\n")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (
                    auth.currentUser?.uid == uid &&
                    !isFinishing &&
                    !isDestroyed
                ) {
                    textoHistorial.text = "No se pudo consultar el historial."
                }
            }
        }

        historial.addValueEventListener(escuchaHistorial!!)
    }

    private fun detenerEscucha() {
        if (::referencia.isInitialized) {
            escucha?.let {
                referencia.removeEventListener(it)
            }

            escuchaHistorial?.let {
                referencia.parent
                    ?.child("historial")
                    ?.removeEventListener(it)
            }
        }

        escucha = null
        escuchaHistorial = null
    }

    private fun habilitarFormulario(habilitado: Boolean) {
        medicamento.isEnabled = habilitado
        dosis.isEnabled = habilitado
        botonDias.isEnabled = habilitado
        botonHora.isEnabled = habilitado
        botonGuardar.isEnabled = habilitado

        for (indice in 0 until compartimientos.childCount) {
            compartimientos.getChildAt(indice).isEnabled = habilitado
        }
    }
}
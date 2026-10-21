package com.example.recordatoriomedicamentos

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var procesando = false

    private val escucharSesion = FirebaseAuth.AuthStateListener {
        if (it.currentUser != null) {
            mostrarInicio()
        } else {
            mostrarLogin()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        mostrarLogin()
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(escucharSesion)
    }

    override fun onStop() {
        auth.removeAuthStateListener(escucharSesion)
        super.onStop()
    }

    private fun mostrarLogin() {
        setContentView(R.layout.activity_main)
        aplicarMargenesSistema()

        val correo = findViewById<EditText>(R.id.etCorreo)
        val clave = findViewById<EditText>(R.id.etContrasena)
        val ingresar = findViewById<Button>(R.id.btnIngresar)
        val crear = findViewById<Button>(R.id.btnRegistrarse)

        ingresar.setOnClickListener {
            autenticar(false, correo, clave, ingresar, crear)
        }

        crear.setOnClickListener {
            autenticar(true, correo, clave, ingresar, crear)
        }
    }

    private fun mostrarInicio() {
        setContentView(R.layout.activity_inicio)
        findViewById<Button>(R.id.btnPastillero).setOnClickListener {
            startActivity(
                android.content.Intent(this, PastilleroActivity::class.java)
            )
        }
        aplicarMargenesSistema()

        findViewById<TextView>(R.id.tvCorreo).text =
            auth.currentUser?.email.orEmpty()

        findViewById<Button>(R.id.btnRecordatorios).setOnClickListener {
            startActivity(
                android.content.Intent(
                    this,
                    RecordatoriosActivity::class.java
                )
            )
        }

        findViewById<Button>(R.id.btnCerrarSesion).setOnClickListener {
            auth.signOut()
        }
    }

    private fun autenticar(
        registrar: Boolean,
        campoCorreo: EditText,
        campoClave: EditText,
        botonIngresar: Button,
        botonCrear: Button
    ) {
        if (procesando) return

        val correo = campoCorreo.text.toString().trim()
        val clave = campoClave.text.toString()

        campoCorreo.error = null
        campoClave.error = null

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            campoCorreo.error = "Ingresa un correo válido"
            campoCorreo.requestFocus()
            return
        }

        if (clave.isEmpty()) {
            campoClave.error = "Ingresa tu contraseña"
            campoClave.requestFocus()
            return
        }

        if (registrar && clave.length < 12) {
            campoClave.error = "Usa al menos 12 caracteres"
            campoClave.requestFocus()
            return
        }

        procesando = true
        botonIngresar.isEnabled = false
        botonCrear.isEnabled = false
        campoCorreo.isEnabled = false
        campoClave.isEnabled = false

        val tarea = if (registrar) {
            auth.createUserWithEmailAndPassword(correo, clave)
        } else {
            auth.signInWithEmailAndPassword(correo, clave)
        }

        tarea.addOnCompleteListener { resultado ->
            if (isFinishing || isDestroyed) {
                return@addOnCompleteListener
            }

            procesando = false
            botonIngresar.isEnabled = true
            botonCrear.isEnabled = true
            campoCorreo.isEnabled = true
            campoClave.isEnabled = true
            campoClave.text.clear()

            val mensaje = if (resultado.isSuccessful) {
                if (registrar) {
                    "Cuenta creada correctamente"
                } else {
                    "Inicio de sesión correcto"
                }
            } else {
                when (resultado.exception) {
                    is FirebaseNetworkException ->
                        "No se pudo conectar. Revisa tu internet."

                    is FirebaseAuthUserCollisionException ->
                        "No se pudo crear la cuenta. Intenta iniciar sesión."

                    is FirebaseAuthWeakPasswordException ->
                        "La contraseña no cumple la política del proyecto."

                    else ->
                        if (registrar) {
                            "No se pudo crear la cuenta. Revisa los datos."
                        } else {
                            "No se pudo ingresar. Revisa correo y contraseña."
                        }
                }
            }

            Toast.makeText(
                this,
                mensaje,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun aplicarMargenesSistema() {
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
    }
}
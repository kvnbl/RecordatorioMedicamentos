package com.example.recordatoriomedicamentos

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Vista informativa temporal para la función de informes, aún no implementada. */
class InformesActivity : AppCompatActivity() {
    override fun onCreate(estadoGuardado: Bundle?) {
        super.onCreate(estadoGuardado)
        enableEdgeToEdge()
        setContentView(R.layout.activity_informes)

        val raiz = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, margenesSistema ->
            val barras = margenesSistema.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            margenesSistema
        }
        ViewCompat.requestApplyInsets(raiz)

        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvReportsTitle).setText(R.string.reports_title)
    }
}

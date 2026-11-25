package com.example.recordatoriomedicamentos

import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

/** Usa la URL de Realtime Database configurada para el proyecto Firebase activo. */
internal object BaseDatosFirebase {
    private val instancia: FirebaseDatabase by lazy {
        val url =
            FirebaseApp.getInstance().options.databaseUrl
                ?.takeIf(String::isNotBlank)
                ?: error(
                    "Falta la URL de Realtime Database en la configuración Firebase. " +
                        "Descarga google-services.json después de crear la base de datos."
                )
        FirebaseDatabase.getInstance(url)
    }

    fun getInstance(): FirebaseDatabase = instancia
}

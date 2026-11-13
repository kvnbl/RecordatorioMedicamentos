package com.example.recordatoriomedicamentos

import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Reglas compartidas de horarios semanales y limpieza idempotente del esquema anterior. */
object AlarmSchedule {
    const val MAXIMO_HORARIOS_SEMANALES = 21 // Tres franjas por día durante siete días.

    fun dias(instantanea: DataSnapshot): Set<Int> =
        instantanea
            .child("diasSemana")
            .children
            .mapNotNull { (it.value as? Number)?.toInt() }
            .filter { it in 1..7 }
            .toSortedSet()

    fun diaSemanaDesdeLunes(calendario: Calendar): Int =
        (calendario.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1

    fun migrarEsquemaAnterior(referencia: DatabaseReference, instantanea: DataSnapshot) {
        val actualizaciones = obtenerActualizacionesDeMigracion(instantanea)
        if (actualizaciones.isNotEmpty()) referencia.updateChildren(actualizaciones)
    }

    fun migrarEsquemaAnteriorBloqueante(referencia: DatabaseReference, instantanea: DataSnapshot) {
        val actualizaciones = obtenerActualizacionesDeMigracion(instantanea)
        if (actualizaciones.isNotEmpty())
            Tasks.await(referencia.updateChildren(actualizaciones), 25, TimeUnit.SECONDS)
    }

    private fun obtenerActualizacionesDeMigracion(instantanea: DataSnapshot): Map<String, Any?> {
        val actualizaciones = linkedMapOf<String, Any?>()
        for (fila in instantanea.children) {
            val identificador = fila.key ?: continue
            if (fila.child("medicamento").getValue(String::class.java) == null) continue
            val dias = dias(fila)
            val esDeUnaVez = fila.child("tipoRepeticion").getValue(String::class.java) == "una_vez"
            if (esDeUnaVez || dias.isEmpty()) {
                actualizaciones[identificador] = null
                continue
            }
            val diasGuardados =
                fila.child("diasSemana").children.mapNotNull { (it.value as? Number)?.toInt() }
            if (diasGuardados != dias.toList())
                actualizaciones["$identificador/diasSemana"] = dias.toList()
            listOf("tipoRepeticion", "fechaUnica", "compartimiento").forEach { campo ->
                if (fila.hasChild(campo)) actualizaciones["$identificador/$campo"] = null
            }
        }
        return actualizaciones
    }
}

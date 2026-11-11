package com.example.recordatoriomedicamentos

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Formatea las horas en ciclo de 12 horas y mantiene a. m./p. m. en un nivel visual secundario. */
object AlarmTimeFormatter {
    fun formatear(marcaTemporal: Long, zonaHoraria: TimeZone): CharSequence {
        val calendario = Calendar.getInstance(zonaHoraria).apply { timeInMillis = marcaTemporal }
        val hora24 = calendario.get(Calendar.HOUR_OF_DAY)
        val hora12 = if (hora24 % 12 == 0) 12 else hora24 % 12
        val horaVisible = String.format(Locale.ROOT, "%02d:%02d", hora12, calendario.get(Calendar.MINUTE))
        val textoHora = "$horaVisible ${if (hora24 < 12) "AM" else "PM"}"
        return SpannableString(textoHora).apply {
            setSpan(
                RelativeSizeSpan(0.6f),
                horaVisible.length + 1,
                textoHora.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
}

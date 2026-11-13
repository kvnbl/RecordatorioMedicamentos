package com.example.recordatoriomedicamentos

import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.Locale

/** Configuración semanal: siete días, tres franjas y medicamentos asociados a cada toma. */
class RecordatoriosActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var userRef: DatabaseReference
    private lateinit var alarmsRef: DatabaseReference
    private lateinit var schedulesRef: DatabaseReference
    private lateinit var userId: String
    private lateinit var listado: LinearLayout
    private lateinit var estado: TextView
    private var alarmSnapshot: DataSnapshot? = null
    private var scheduleSnapshot: DataSnapshot? = null
    private var alarmListener: ValueEventListener? = null
    private var scheduleListener: ValueEventListener? = null
    private val weekdays = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    private val periods = listOf("mañana", "tarde", "noche")
    private val defaults = listOf("08:00", "13:00", "20:00")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = FirebaseAuth.getInstance()
        userId = auth.currentUser?.uid ?: run { finish(); return }
        userRef = FirebaseDatabase.getInstance(URL_BASE_DATOS).getReference("usuarios").child(userId)
        alarmsRef = userRef.child("recordatorios")
        schedulesRef = userRef.child("agendaSemanal")
        setContentView(R.layout.activity_recordatorios)
        val root = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnGlobalReminder).setOnClickListener { showGlobalDialog() }
        listado = findViewById(R.id.listRecordatorios)
        estado = findViewById(R.id.tvRecordatorios)
    }

    override fun onStart() {
        super.onStart()
        auth.currentUser?.uid ?: run { finish(); return }
        auth.addAuthStateListener(authListener)
        alarmListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { alarmSnapshot = snapshot; render() }
            override fun onCancelled(error: DatabaseError) { estado.visibility = View.VISIBLE; estado.text = "No se pudieron cargar los recordatorios." }
        }.also(alarmsRef::addValueEventListener)
        scheduleListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { scheduleSnapshot = snapshot; render() }
            override fun onCancelled(error: DatabaseError) { scheduleSnapshot = null; render() }
        }.also(schedulesRef::addValueEventListener)
    }

    private val authListener = FirebaseAuth.AuthStateListener { if (it.currentUser?.uid != userId && !isFinishing) finish() }

    override fun onStop() {
        auth.removeAuthStateListener(authListener)
        alarmListener?.let(alarmsRef::removeEventListener)
        scheduleListener?.let(schedulesRef::removeEventListener)
        alarmListener = null; scheduleListener = null
        super.onStop()
    }

    private fun render() {
        val alarms = alarmSnapshot ?: return
        listado.removeAllViews()
        listado.visibility = View.VISIBLE
        estado.visibility = View.GONE
        for (day in 1..7) {
            val dayCard = MaterialCardView(this).apply {
                radius = dp(20).toFloat(); cardElevation = 0f
                setCardBackgroundColor(getColor(R.color.surface_primary))
                strokeWidth = dp(1); strokeColor = getColor(R.color.divider_lavender)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            }
            val dayContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
            dayContent.addView(TextView(this).apply { text = weekdays[day - 1]; setTextAppearance(R.style.MedAlarmTextSectionTitle) })
            periods.forEachIndexed { periodIndex, period ->
                val periodName = periodIndex.toString()
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(12), 0, dp(8))
                    if (periodIndex > 0) setBackgroundColor(Color.TRANSPARENT)
                }
                val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
                val hour = storedHour(day, periodIndex)
                header.addView(TextView(this).apply {
                    text = "${period.replaceFirstChar { it.uppercase() }} · ${displayHour(hour)}"
                    setTextAppearance(R.style.MedAlarmTextBodyStrong); setTextColor(getColor(R.color.brand_primary))
                    contentDescription = "Editar hora de ${weekdays[day - 1]}, $period"
                    setOnClickListener { editHour(day, periodIndex, hour) }
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
                header.addView(ImageButton(this).apply {
                    setImageResource(R.drawable.ic_edit_filled)
                    imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_secondary))
                    background = selectableItemBackgroundBorderless
                    contentDescription = "Editar hora de ${weekdays[day - 1]} en la ${period}"
                    setOnClickListener { editHour(day, periodIndex, hour) }
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                })
                header.addView(ImageButton(this).apply {
                    setImageResource(R.drawable.ic_add); imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.brand_primary))
                    background = selectableItemBackgroundBorderless; contentDescription = "Agregar medicamento a ${weekdays[day - 1]} en la ${period}"
                    setOnClickListener { showMedicineDialog(day, periodIndex) }
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                })
                row.addView(header)
                val entries = matchingEntries(alarms, day, periodIndex)
                if (entries.isEmpty()) row.addView(TextView(this).apply {
                    text = "Sin medicamentos"; setTextAppearance(R.style.MedAlarmTextSecondary); setTextColor(getColor(R.color.text_secondary))
                    setPadding(0, dp(4), 0, 0)
                }) else entries.forEach { entry -> row.addView(medicineRow(entry)) }
                dayContent.addView(row)
                if (periodIndex < 2) dayContent.addView(View(this).apply { setBackgroundColor(getColor(R.color.divider_lavender)); layoutParams = LinearLayout.LayoutParams(-1, dp(1)) })
            }
            dayCard.addView(dayContent)
            listado.addView(dayCard)
        }
    }

    private data class MedicineEntry(val id: String, val name: String, val dose: String, val day: Int, val period: Int)

    private fun matchingEntries(snapshot: DataSnapshot, day: Int, period: Int): List<MedicineEntry> = snapshot.children.mapNotNull { item ->
        val name = item.child("medicamento").getValue(String::class.java) ?: return@mapNotNull null
        val time = item.child("hora").getValue(String::class.java).orEmpty()
        val oldDays = AlarmSchedule.dias(item)
        val itemDay = item.child("diaSemana").getValue(Int::class.java)
        val itemPeriod = item.child("franja").getValue(Int::class.java) ?: periodFor(time)
        if (itemPeriod != period || if (itemDay != null) itemDay != day else day !in oldDays) return@mapNotNull null
        MedicineEntry(item.key ?: return@mapNotNull null, name, item.child("dosis").getValue(String::class.java).orEmpty(), day, period)
    }.sortedBy { it.name.lowercase(Locale.getDefault()) }

    private fun medicineRow(entry: MedicineEntry): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(5), 0, dp(5))
        val text = TextView(this@RecordatoriosActivity).apply {
            this.text = if (entry.dose.isBlank()) entry.name else "${entry.name} · ${entry.dose}"
            setTextAppearance(R.style.MedAlarmTextBody); setTextColor(getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        addView(text)
        addView(ImageButton(this@RecordatoriosActivity).apply {
            setImageResource(R.drawable.ic_delete_outline); imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.error_red))
            background = selectableItemBackgroundBorderless; contentDescription = "Eliminar ${entry.name}"
            setOnClickListener { alarmsRef.child(entry.id).removeValue().addOnSuccessListener { DoseOccurrenceTracker.encolarReconciliacion(applicationContext, auth.currentUser?.uid.orEmpty()) } }
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
    }

    private fun showMedicineDialog(day: Int, period: Int) {
        val fields = medicineFields()
        showScrollableDialog("Agregar medicamento · ${weekdays[day - 1]} · ${periodTitle(period)}", fields, "Agregar") {
            saveMedicine(fields.first.text.toString(), fields.second.text.toString(), listOf(day), period)
        }
    }

    private fun showGlobalDialog() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), 0, dp(4), 0) }
        val name = EditText(this).apply { hint = "Medicamento"; maxLines = 1 }
        val dose = EditText(this).apply { hint = "Dosis"; maxLines = 1 }
        content.addView(name); content.addView(dose)
        content.addView(TextView(this).apply { text = "Franja"; setPadding(0, dp(12), 0, dp(4)) })
        val spinner = Spinner(this).apply { adapter = ArrayAdapter(this@RecordatoriosActivity, android.R.layout.simple_spinner_dropdown_item, periods.map { it.replaceFirstChar(Char::uppercase) }) }
        content.addView(spinner)
        content.addView(TextView(this).apply { text = "Repetir los días"; setPadding(0, dp(12), 0, dp(4)) })
        val checks = weekdays.map { day -> CheckBox(this).apply { text = day; isChecked = true }.also(content::addView) }
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this).setTitle("Recordatorio Global").setView(scroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Agregar", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val days = checks.mapIndexedNotNull { index, check -> if (check.isChecked) index + 1 else null }
                        if (name.text.toString().trim().isEmpty()) { name.error = "Ingresa el medicamento"; return@setOnClickListener }
                        if (dose.text.toString().trim().isEmpty()) { dose.error = "Ingresa la dosis"; return@setOnClickListener }
                        if (days.isEmpty()) { Toast.makeText(this, "Selecciona al menos un día", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                        saveMedicine(name.text.toString(), dose.text.toString(), days, spinner.selectedItemPosition)
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }

    private fun medicineFields() = EditText(this).let { name ->
        name.hint = "Medicamento"; name.maxLines = 1
        EditText(this).let { dose -> dose.hint = "Dosis"; dose.maxLines = 1; name to dose }
    }

    private fun showScrollableDialog(title: String, fields: Pair<EditText, EditText>, accept: String, action: () -> Unit) {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), 0, dp(4), 0); addView(fields.first); addView(fields.second) }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(content) }).setNegativeButton("Cancelar", null).setPositiveButton(accept, null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (fields.first.text.toString().trim().isEmpty()) { fields.first.error = "Ingresa el medicamento"; return@setOnClickListener }
            if (fields.second.text.toString().trim().isEmpty()) { fields.second.error = "Ingresa la dosis"; return@setOnClickListener }
            action(); dialog.dismiss()
        } }
        dialog.show()
    }

    private fun saveMedicine(name: String, dose: String, days: List<Int>, period: Int) {
        val updates = mutableMapOf<String, Any?>()
        days.forEach { day ->
            val slotHour = storedHour(day, period)
            val id = alarmsRef.push().key ?: return
            updates["$id"] = mapOf(
                "id" to id, "medicamento" to name.trim(), "dosis" to dose.trim(), "diaSemana" to day,
                "franja" to period, "slotId" to "${day}_$period", "diasSemana" to listOf(day),
                "hora" to slotHour, "zonaHoraria" to java.util.TimeZone.getDefault().id,
                "activo" to true, "actualizadoEn" to ServerValue.TIMESTAMP, "creadoEn" to ServerValue.TIMESTAMP,
            )
        }
        alarmsRef.updateChildren(updates).addOnSuccessListener {
            DoseOccurrenceTracker.encolarReconciliacion(applicationContext, auth.currentUser?.uid.orEmpty())
        }.addOnFailureListener { estado.visibility = View.VISIBLE; estado.text = "No se pudo guardar. Revisa tu conexión." }
    }

    private fun editHour(day: Int, period: Int, current: String) {
        val pieces = current.split(":")
        TimePickerDialog(this, { _, hour, minute ->
            val time = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
            val range = when (period) { 0 -> hour in 0..11; 1 -> hour in 12..17; else -> hour in 18..23 }
            if (!range) { Toast.makeText(this, "La hora debe estar dentro de ${periodTitle(period).lowercase()}.", Toast.LENGTH_LONG).show(); return@TimePickerDialog }
            schedulesRef.child(day.toString()).child(period.toString()).child("hora").setValue(time)
            val updates = mutableMapOf<String, Any?>()
            alarmSnapshot?.children?.forEach { item ->
                val oldDays = AlarmSchedule.dias(item)
                val itemDay = item.child("diaSemana").getValue(Int::class.java)
                val itemPeriod = item.child("franja").getValue(Int::class.java) ?: periodFor(item.child("hora").getValue(String::class.java).orEmpty())
                if ((itemDay == day || itemDay == null && day in oldDays) && itemPeriod == period)
                    item.key?.let { updates["$it/hora"] = time; updates["$it/actualizadoEn"] = ServerValue.TIMESTAMP }
            }
            if (updates.isNotEmpty()) alarmsRef.updateChildren(updates)
                .addOnSuccessListener { DoseOccurrenceTracker.encolarReconciliacion(applicationContext, auth.currentUser?.uid.orEmpty()) }
        }, pieces.getOrNull(0)?.toIntOrNull() ?: 8, pieces.getOrNull(1)?.toIntOrNull() ?: 0, false).show()
    }

    private fun storedHour(day: Int, period: Int): String =
        scheduleSnapshot?.child(day.toString())?.child(period.toString())?.child("hora")?.getValue(String::class.java)
            ?: alarmSnapshot?.children?.firstOrNull { item ->
                val days = AlarmSchedule.dias(item)
                val itemDay = item.child("diaSemana").getValue(Int::class.java)
                val time = item.child("hora").getValue(String::class.java).orEmpty()
                val itemPeriod = item.child("franja").getValue(Int::class.java) ?: periodFor(time)
                (itemDay == day || itemDay == null && day in days) && itemPeriod == period
            }?.child("hora")?.getValue(String::class.java)
            ?: defaults[period]

    private fun periodFor(time: String): Int {
        val hour = time.substringBefore(":").toIntOrNull() ?: 8
        return when { hour < 12 -> 0; hour < 18 -> 1; else -> 2 }
    }

    private fun periodTitle(period: Int) = periods[period].replaceFirstChar { it.uppercase() }
    private fun displayHour(value: String): String {
        val p = value.split(":"); val h = p.getOrNull(0)?.toIntOrNull() ?: return value
        return String.format(Locale.ROOT, "%02d:%02d %s", if (h % 12 == 0) 12 else h % 12, p.getOrNull(1)?.toIntOrNull() ?: 0, if (h < 12) "AM" else "PM")
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val selectableItemBackgroundBorderless: android.graphics.drawable.Drawable? get() = android.util.TypedValue().let { value -> theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true); getDrawable(value.resourceId) }

    companion object {
        private const val URL_BASE_DATOS = "https://recordatoriodemedicament-90e4f-default-rtdb.firebaseio.com/"
    }
}

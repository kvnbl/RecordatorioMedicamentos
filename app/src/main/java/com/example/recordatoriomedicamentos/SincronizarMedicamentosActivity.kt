package com.example.recordatoriomedicamentos

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import java.util.Locale
import java.util.TimeZone

/** Selecciona medicamentos de una franja y los copia a otros días de esa misma franja. */
class SincronizarMedicamentosActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SOURCE_DAY = "sync_source_day"
        const val EXTRA_PERIOD = "sync_period"
    }

    private data class Medicine(val id: String, val name: String, val dose: String)

    private lateinit var auth: FirebaseAuth
    private lateinit var userId: String
    private lateinit var alarmsRef: DatabaseReference
    private lateinit var schedulesRef: DatabaseReference
    private lateinit var medicineList: LinearLayout
    private lateinit var weekdayRow: LinearLayout
    private lateinit var status: TextView
    private lateinit var retryButton: com.google.android.material.button.MaterialButton
    private lateinit var applyButton: View
    private val weekdays by lazy { resources.getStringArray(R.array.weekday_names).toList() }
    private val weekdayShorts by lazy {
        listOf(
            R.string.weekday_short_monday, R.string.weekday_short_tuesday,
            R.string.weekday_short_wednesday, R.string.weekday_short_thursday,
            R.string.weekday_short_friday, R.string.weekday_short_saturday,
            R.string.weekday_short_sunday,
        )
    }
    private val periodNames = listOf("mañana", "tarde", "noche")
    private val defaultHours = listOf("08:00", "13:00", "20:00")
    private val selectedMedicineIds = mutableSetOf<String>()
    private val selectedDays = mutableSetOf<Int>()
    private val medicineChecks = mutableMapOf<String, MaterialCheckBox>()
    private val dayLabels = mutableMapOf<Int, TextView>()
    private var alarmSnapshot: DataSnapshot? = null
    private var scheduleSnapshot: DataSnapshot? = null
    private var sourceMedicines: List<Medicine> = emptyList()
    private var sourceDay = 0
    private var period = 0
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = FirebaseAuth.getInstance()
        userId = auth.currentUser?.uid ?: run { finish(); return }
        sourceDay = intent.getIntExtra(EXTRA_SOURCE_DAY, 0)
        period = intent.getIntExtra(EXTRA_PERIOD, -1)
        if (sourceDay !in 1..7 || period !in 0..2) { finish(); return }

        val userRef = BaseDatosFirebase.getInstance().getReference("usuarios").child(userId)
        alarmsRef = userRef.child("recordatorios")
        schedulesRef = userRef.child("agendaSemanal")
        setContentView(R.layout.activity_sync_medicines)
        val root = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        medicineList = findViewById(R.id.syncMedicineList)
        weekdayRow = findViewById(R.id.syncWeekdayRow)
        status = findViewById(R.id.tvSyncStatus)
        retryButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "Reintentar"
            visibility = View.GONE
            setOnClickListener { visibility = View.GONE; status.setText(R.string.alarm_sync_loading); loadData() }
        }
        (status.parent as android.view.ViewGroup).addView(retryButton)
        applyButton = findViewById(R.id.btnSyncApply)
        findViewById<View>(R.id.btnSyncBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvSyncSubtitle).text = "${weekdays[sourceDay - 1]} · ${periodNames[period].replaceFirstChar { it.uppercase() }}"
        applyButton.setOnClickListener { synchronize() }
        createDaySelector()
        status.setText(R.string.alarm_sync_loading)
        loadData()
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authListener)
    }

    override fun onStop() {
        auth.removeAuthStateListener(authListener)
        super.onStop()
    }

    private val authListener = FirebaseAuth.AuthStateListener {
        if (it.currentUser?.uid != userId && !isFinishing) finish()
    }

    private fun loadData() {
        alarmsRef.get().addOnSuccessListener { alarms ->
            alarmSnapshot = alarms
            schedulesRef.get().addOnSuccessListener { schedules ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                scheduleSnapshot = schedules
                sourceMedicines = matchingMedicines(alarms, sourceDay, period)
                selectedMedicineIds.retainAll(sourceMedicines.map { it.id }.toSet())
                retryButton.visibility = View.GONE
                renderMedicineChoices()
                status.text = if (sourceMedicines.isEmpty()) getString(R.string.alarm_sync_empty_medicines) else ""
                updateActionState()
            }.addOnFailureListener { showLoadError() }
        }.addOnFailureListener { showLoadError() }
    }

    private fun showLoadError() {
        if (isFinishing || isDestroyed) return
        status.setText(R.string.alarm_sync_error)
        retryButton.visibility = View.VISIBLE
        applyButton.isEnabled = false
    }

    private fun matchingMedicines(snapshot: DataSnapshot, day: Int, slot: Int): List<Medicine> =
        snapshot.children.mapNotNull { item ->
            val name = item.child("medicamento").getValue(String::class.java) ?: return@mapNotNull null
            val time = item.child("hora").getValue(String::class.java).orEmpty()
            val itemDay = item.child("diaSemana").getValue(Int::class.java)
            val days = AlarmSchedule.dias(item)
            val itemPeriod = item.child("franja").getValue(Int::class.java) ?: periodFor(time)
            if (itemPeriod != slot || if (itemDay != null) itemDay != day else day !in days) return@mapNotNull null
            Medicine(item.key ?: return@mapNotNull null, name, item.child("dosis").getValue(String::class.java).orEmpty())
        }.sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) }, { it.dose.lowercase(Locale.ROOT) }))

    private fun renderMedicineChoices() {
        medicineList.removeAllViews()
        medicineChecks.clear()
        sourceMedicines.forEachIndexed { index, medicine ->
            val check = MaterialCheckBox(this).apply {
                text = if (medicine.dose.isBlank()) medicine.name else "${medicine.name}\n${medicine.dose}"
                setTextAppearance(R.style.MedAlarmTextBody)
                setTextColor(getColor(R.color.text_primary))
                minHeight = dp(56)
                isChecked = medicine.id in selectedMedicineIds
                contentDescription = getString(R.string.alarm_sync_select_medicine_accessibility, medicine.name, medicine.dose)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedMedicineIds.add(medicine.id) else selectedMedicineIds.remove(medicine.id)
                    updateActionState()
                }
            }
            medicineChecks[medicine.id] = check
            medicineList.addView(check, LinearLayout.LayoutParams(-1, -2))
            if (index < sourceMedicines.lastIndex) medicineList.addView(View(this).apply {
                setBackgroundColor(getColor(R.color.divider_lavender))
                layoutParams = LinearLayout.LayoutParams(-1, dp(1))
            })
        }
    }

    private fun createDaySelector() {
        weekdayRow.removeAllViews()
        weekdayRow.minimumWidth = dp(7 * 44)
        weekdayShorts.forEachIndexed { index, labelId ->
            val day = index + 1
            val container = FrameLayout(this).apply {
                minimumWidth = dp(44)
                minimumHeight = dp(48)
                isClickable = day != sourceDay
                isFocusable = day != sourceDay
                alpha = if (day == sourceDay) 0.42f else 1f
                contentDescription = getString(
                    if (day in selectedDays) R.string.alarm_sync_day_selected_accessibility else R.string.alarm_sync_day_unselected_accessibility,
                    weekdays[index],
                )
                setOnClickListener {
                    if (day == sourceDay) return@setOnClickListener
                    if (!selectedDays.add(day)) selectedDays.remove(day)
                    renderDaySelector()
                    updateActionState()
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            }
            val label = TextView(this).apply {
                text = getString(labelId)
                gravity = Gravity.CENTER
                setTextAppearance(R.style.MedAlarmTextBodyStrong)
                layoutParams = FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER)
            }
            dayLabels[day] = label
            container.addView(label)
            weekdayRow.addView(container)
        }
        weekdayRow.post {
            val available = (weekdayRow.parent as? View)?.width ?: 0
            val desired = maxOf(available, dp(7 * 44))
            weekdayRow.layoutParams = weekdayRow.layoutParams.apply { width = desired }
            renderDaySelector()
        }
    }

    private fun renderDaySelector() {
        weekdayShorts.indices.forEach { index ->
            val day = index + 1
            val selected = day in selectedDays
            val label = dayLabels[day] ?: return@forEach
            label.setTextColor(getColor(if (selected) R.color.white else R.color.text_primary))
            label.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (selected) setColor(getColor(R.color.brand_primary))
                else {
                    setColor(getColor(R.color.surface_primary))
                    setStroke(dp(1), getColor(R.color.divider_lavender))
                }
            }
            (label.parent as? View)?.contentDescription = getString(
                if (selected) R.string.alarm_sync_day_selected_accessibility else R.string.alarm_sync_day_unselected_accessibility,
                weekdays[index],
            )
        }
    }

    private fun updateActionState() {
        applyButton.isEnabled = !loading && sourceMedicines.any { it.id in selectedMedicineIds } && selectedDays.isNotEmpty()
    }

    private fun synchronize() {
        if (loading) return
        val selectedMedicines = sourceMedicines.filter { it.id in selectedMedicineIds }
        if (selectedMedicines.isEmpty() || selectedDays.isEmpty()) {
            status.setText(R.string.alarm_sync_select_medicines_days)
            return
        }
        val snapshot = alarmSnapshot ?: return
        val updates = linkedMapOf<String, Any?>()
        selectedDays.sorted().forEach { targetDay ->
            val existing = matchingMedicines(snapshot, targetDay, period)
            val existingKeys = existing.mapTo(mutableSetOf()) { dedupeKey(it.name, it.dose) }
            selectedMedicines.forEach medicineLoop@{ medicine ->
                val dedupeKey = dedupeKey(medicine.name, medicine.dose)
                if (!existingKeys.add(dedupeKey)) return@medicineLoop
                val id = alarmsRef.push().key ?: return
                val hour = storedHour(targetDay, period)
                updates[id] = mapOf(
                    "id" to id,
                    "medicamento" to medicine.name.trim(),
                    "dosis" to medicine.dose.trim(),
                    "diaSemana" to targetDay,
                    "franja" to period,
                    "slotId" to "${targetDay}_$period",
                    "diasSemana" to listOf(targetDay),
                    "hora" to hour,
                    "zonaHoraria" to TimeZone.getDefault().id,
                    "activo" to true,
                    "actualizadoEn" to ServerValue.TIMESTAMP,
                    "creadoEn" to ServerValue.TIMESTAMP,
                )
            }
        }
        if (updates.isEmpty()) {
            Toast.makeText(this, R.string.alarm_sync_no_changes, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        loading = true
        status.text = ""
        updateActionState()
        alarmsRef.updateChildren(updates).addOnCompleteListener { task ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            loading = false
            if (task.isSuccessful) {
                DoseOccurrenceTracker.encolarReconciliacion(applicationContext, userId)
                Toast.makeText(this, R.string.alarm_sync_success, Toast.LENGTH_LONG).show()
                finish()
            } else {
                status.setText(R.string.alarm_sync_error)
                updateActionState()
            }
        }
    }

    private fun storedHour(day: Int, slot: Int): String =
        scheduleSnapshot?.child(day.toString())?.child(slot.toString())?.child("hora")?.getValue(String::class.java)
            ?: alarmSnapshot?.children?.firstOrNull { item ->
                val itemDay = item.child("diaSemana").getValue(Int::class.java)
                val days = AlarmSchedule.dias(item)
                val time = item.child("hora").getValue(String::class.java).orEmpty()
                val itemPeriod = item.child("franja").getValue(Int::class.java) ?: periodFor(time)
                (itemDay == day || itemDay == null && day in days) && itemPeriod == slot
            }?.child("hora")?.getValue(String::class.java)
            ?: defaultHours[slot]

    private fun periodFor(time: String): Int {
        val hour = time.substringBefore(":").toIntOrNull() ?: 8
        return when { hour < 12 -> 0; hour < 18 -> 1; else -> 2 }
    }

    private fun dedupeKey(name: String, dose: String) =
        "${name.trim().lowercase(Locale.ROOT)}\u0000${dose.trim().lowercase(Locale.ROOT)}"

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

package com.example.recordatoriomedicamentos

import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.Locale
import java.util.Calendar

/** Configuración semanal: siete días, tres franjas y medicamentos asociados a cada toma. */
class RecordatoriosActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var userRef: DatabaseReference
    private lateinit var alarmsRef: DatabaseReference
    private lateinit var schedulesRef: DatabaseReference
    private lateinit var userId: String
    private lateinit var listado: LinearLayout
    private lateinit var estado: TextView
    private lateinit var botonReintentar: com.google.android.material.button.MaterialButton
    private lateinit var scrollContainer: ScrollView
    private var alarmSnapshot: DataSnapshot? = null
    private var scheduleSnapshot: DataSnapshot? = null
    private var alarmListener: ValueEventListener? = null
    private var scheduleListener: ValueEventListener? = null
    private val weekdays = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    private val periods = listOf("mañana", "tarde", "noche")
    private val defaults = listOf("08:00", "13:00", "20:00")
    private val expandedDays = mutableSetOf<Int>()
    private val expandedSlots = mutableSetOf<String>()
    private var initialExpansionSet = false
    private val sectionPrefs by lazy { getSharedPreferences("reminder_sections_$userId", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = FirebaseAuth.getInstance()
        userId = auth.currentUser?.uid ?: run { finish(); return }
        userRef = BaseDatosFirebase.getInstance().getReference("usuarios").child(userId)
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
        scrollContainer = findViewById(R.id.scrollRecordatorios)
        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        listado = findViewById(R.id.listRecordatorios)
        estado = findViewById(R.id.tvRecordatorios)
        botonReintentar = com.google.android.material.button.MaterialButton(this).apply {
            text = "Reintentar"
            visibility = View.GONE
            setOnClickListener {
                alarmListener?.let(alarmsRef::removeEventListener)
                alarmSnapshot = null
                visibility = View.GONE
                estado.text = getString(R.string.loading_alarms)
                escucharAlarmas()
            }
        }
        (estado.parent as android.view.ViewGroup).addView(botonReintentar)
        expandedDays.addAll(sectionPrefs.getStringSet("days", emptySet()).orEmpty().mapNotNull(String::toIntOrNull))
        expandedSlots.addAll(sectionPrefs.getStringSet("slots", emptySet()).orEmpty())
        initialExpansionSet = expandedDays.isNotEmpty() || expandedSlots.isNotEmpty()
    }

    override fun onStart() {
        super.onStart()
        auth.currentUser?.uid ?: run { finish(); return }
        auth.addAuthStateListener(authListener)
        escucharAlarmas()
        scheduleListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { scheduleSnapshot = snapshot; render() }
            override fun onCancelled(error: DatabaseError) { scheduleSnapshot = null; render() }
        }.also(schedulesRef::addValueEventListener)
    }

    private fun escucharAlarmas() {
        alarmListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { alarmSnapshot = snapshot; botonReintentar.visibility = View.GONE; render() }
            override fun onCancelled(error: DatabaseError) { estado.visibility = View.VISIBLE; estado.text = "No se pudieron cargar los recordatorios."; botonReintentar.visibility = View.VISIBLE }
        }.also(alarmsRef::addValueEventListener)
    }

    private val authListener = FirebaseAuth.AuthStateListener { if (it.currentUser?.uid != userId && !isFinishing) finish() }

    override fun onStop() {
        sectionPrefs.edit().putStringSet("days", expandedDays.map(Int::toString).toSet()).putStringSet("slots", expandedSlots.toSet()).apply()
        auth.removeAuthStateListener(authListener)
        alarmListener?.let(alarmsRef::removeEventListener)
        scheduleListener?.let(schedulesRef::removeEventListener)
        alarmListener = null; scheduleListener = null
        super.onStop()
    }

    private fun render() {
        val alarms = alarmSnapshot ?: return
        val scrollPosition = scrollContainer.scrollY
        if (!initialExpansionSet) {
            val calendar = Calendar.getInstance()
            val day = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1 // Lunes = 1, domingo = 7.
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val period = when { hour < 12 -> 0; hour < 18 -> 1; else -> 2 }
            expandedDays.add(day)
            expandedSlots.add(slotKey(day, period))
            initialExpansionSet = true
        }
        listado.removeAllViews()
        listado.visibility = View.VISIBLE
        estado.visibility = View.GONE
        for (day in 1..7) {
            val medicinesByPeriod = periods.indices.map { periodIndex -> matchingEntries(alarms, day, periodIndex) }
            val medicineCountForDay = medicinesByPeriod.sumOf { it.size }
            val dayCard = MaterialCardView(this).apply {
                radius = dp(24).toFloat(); cardElevation = 0f
                setCardBackgroundColor(getColor(R.color.surface_primary))
                strokeWidth = dp(1); strokeColor = getColor(R.color.divider_lavender)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            }
            val dayContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
            val dayExpanded = day in expandedDays
            val dayHeader = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = dp(48)
                isClickable = true
                isFocusable = false
                background = selectableItemBackgroundBorderless
                contentDescription = "${if (dayExpanded) "Contraer" else "Expandir"} ${weekdays[day - 1]}"
            }
            dayHeader.addView(FrameLayout(this).apply {
                background = getDrawable(R.drawable.bg_section_icon)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) }
                addView(ImageView(this@RecordatoriosActivity).apply {
                    setImageResource(R.drawable.ic_calendar_outline)
                    imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.brand_primary))
                    layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
                    contentDescription = null
                })
            })
            dayHeader.addView(TextView(this).apply {
                text = weekdays[day - 1]
                setTextAppearance(R.style.MedAlarmTextSectionTitle)
                setTextColor(getColor(R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            val dayCountBadge = countBadge(medicineCountForDay, "${weekdays[day - 1]}: $medicineCountForDay medicamentos").apply {
                visibility = if (dayExpanded) View.GONE else View.VISIBLE
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(4) }
            }
            dayHeader.addView(dayCountBadge)
            val periodsContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (dayExpanded) View.VISIBLE else View.GONE
            }
            val dayChevron = ImageView(this).apply {
                setImageResource(R.drawable.ic_chevron)
                imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_primary))
                rotation = if (dayExpanded) -90f else 90f
                contentDescription = null
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            }
            dayHeader.addView(dayChevron)
            dayHeader.setOnClickListener {
                val opening = periodsContent.visibility != View.VISIBLE
                if (opening) expandedDays.add(day) else expandedDays.remove(day)
                if (opening) {
                    TransitionManager.beginDelayedTransition(dayCard, AutoTransition().setDuration(220))
                } else {
                    TransitionManager.endTransitions(dayCard)
                    dayChevron.animate().cancel()
                }
                periodsContent.visibility = if (opening) View.VISIBLE else View.GONE
                dayCountBadge.visibility = if (opening) View.GONE else View.VISIBLE
                dayHeader.contentDescription = "${if (opening) "Contraer" else "Expandir"} ${weekdays[day - 1]}, $medicineCountForDay medicamentos"
                if (opening) dayChevron.animate().rotation(-90f).setDuration(220).start()
                else dayChevron.rotation = 90f
            }
            dayContent.addView(dayHeader)
            periods.forEachIndexed { periodIndex, period ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(12), 0, dp(8))
                }
                val key = slotKey(day, periodIndex)
                val expanded = key in expandedSlots
                val emptyOrMedicineEntries = medicinesByPeriod[periodIndex]
                val header = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    orientation = LinearLayout.HORIZONTAL
                }
                header.addView(TextView(this).apply {
                    text = "${period.replaceFirstChar { it.uppercase() }} · ${displayHour(storedHour(day, periodIndex))}"
                    setTextAppearance(R.style.MedAlarmTextBodyStrong)
                    setTextColor(getColor(R.color.brand_primary))
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
                val hour = storedHour(day, periodIndex)
                val details = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (expanded) View.VISIBLE else View.GONE
                }
                val addButton = ImageButton(this).apply {
                    setImageResource(R.drawable.ic_add)
                    imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.brand_primary))
                    background = selectableItemBackgroundBorderless
                    contentDescription = "Agregar medicamento a ${weekdays[day - 1]} en la ${period}"
                    visibility = if (expanded) View.VISIBLE else View.GONE
                    setOnClickListener { showMedicineDialog(day, periodIndex) }
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                }
                val editButton = ImageButton(this).apply {
                    setImageResource(R.drawable.ic_edit_filled)
                    imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_secondary))
                    background = selectableItemBackgroundBorderless
                    contentDescription = "Editar hora de ${weekdays[day - 1]} en la ${period}"
                    visibility = if (expanded) View.VISIBLE else View.GONE
                    setOnClickListener { showEditSlotDialog(day, periodIndex, hour) }
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                }
                val separator = TextView(this).apply {
                    text = "|"
                    setTextAppearance(R.style.MedAlarmTextSecondary)
                    setTextColor(getColor(R.color.divider_lavender))
                    gravity = Gravity.CENTER
                    visibility = if (expanded) View.VISIBLE else View.GONE
                    layoutParams = LinearLayout.LayoutParams(dp(12), dp(48))
                }
                header.addView(addButton)
                header.addView(editButton)
                header.addView(separator)
                val slotCountBadge = countBadge(
                    emptyOrMedicineEntries.size,
                    "${weekdays[day - 1]}, ${periodTitle(periodIndex)}: ${emptyOrMedicineEntries.size} medicamentos",
                ).apply {
                    visibility = if (expanded) View.GONE else View.VISIBLE
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(2); marginEnd = dp(2) }
                }
                header.addView(slotCountBadge)
                val slotChevron = ImageButton(this).apply {
                    setImageResource(R.drawable.ic_chevron)
                    imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.brand_primary))
                    background = selectableItemBackgroundBorderless
                    rotation = if (expanded) -90f else 90f
                    contentDescription = "${if (expanded) "Contraer" else "Expandir"} ${weekdays[day - 1]}, ${period}, ${emptyOrMedicineEntries.size} medicamentos"
                    setOnClickListener {
                        val opening = details.visibility != View.VISIBLE
                        if (opening) expandedSlots.add(key) else expandedSlots.remove(key)
                        if (opening) {
                            TransitionManager.beginDelayedTransition(dayCard, AutoTransition().setDuration(220))
                        } else {
                            TransitionManager.endTransitions(dayCard)
                            animate().cancel()
                        }
                        details.visibility = if (opening) View.VISIBLE else View.GONE
                        addButton.visibility = if (opening) View.VISIBLE else View.GONE
                        editButton.visibility = if (opening) View.VISIBLE else View.GONE
                        separator.visibility = if (opening) View.VISIBLE else View.GONE
                        slotCountBadge.visibility = if (opening) View.GONE else View.VISIBLE
                        contentDescription = "${if (opening) "Contraer" else "Expandir"} ${weekdays[day - 1]}, ${period}, ${emptyOrMedicineEntries.size} medicamentos"
                        if (opening) animate().rotation(-90f).setDuration(220).start()
                        else rotation = 90f
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                }
                header.addView(slotChevron)
                row.addView(header)
                if (emptyOrMedicineEntries.isEmpty()) details.addView(TextView(this).apply {
                    text = "Sin medicamentos"; setTextAppearance(R.style.MedAlarmTextSecondary); setTextColor(getColor(R.color.text_secondary))
                    setPadding(0, dp(4), 0, 0)
                }) else emptyOrMedicineEntries.forEach { entry -> details.addView(medicineRow(entry)) }
                row.addView(details)
                periodsContent.addView(row)
                if (periodIndex < 2) periodsContent.addView(View(this).apply { setBackgroundColor(getColor(R.color.divider_lavender)); layoutParams = LinearLayout.LayoutParams(-1, dp(1)) })
            }
            dayContent.addView(periodsContent)
            dayCard.addView(dayContent)
            listado.addView(dayCard)
        }
        val observer = scrollContainer.viewTreeObserver
        observer.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (scrollContainer.viewTreeObserver.isAlive) {
                    scrollContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
                scrollContainer.postOnAnimation { scrollContainer.scrollTo(0, scrollPosition) }
            }
        })
    }

    private fun slotKey(day: Int, period: Int) = "${day}_$period"

    private fun countBadge(count: Int, description: String) = TextView(this).apply {
        text = count.toString()
        setTextAppearance(R.style.MedAlarmTextSecondaryStrong)
        textSize = 12f
        setTextColor(getColor(R.color.brand_primary))
        gravity = Gravity.CENTER
        contentDescription = description
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(getColor(R.color.surface_lavender))
            setStroke(dp(1), getColor(R.color.divider_lavender))
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
        minimumHeight = dp(56)
        setPadding(dp(8), dp(6), 0, dp(6))
        addView(ImageView(this@RecordatoriosActivity).apply {
            setImageResource(R.drawable.ic_medication)
            imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.brand_primary))
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) }
            contentDescription = null
        })
        addView(LinearLayout(this@RecordatoriosActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(TextView(this@RecordatoriosActivity).apply {
                text = entry.name
                setTextAppearance(R.style.MedAlarmTextBodyStrong)
                setTextColor(getColor(R.color.text_primary))
            })
            if (entry.dose.isNotBlank()) addView(TextView(this@RecordatoriosActivity).apply {
                text = entry.dose
                setTextAppearance(R.style.MedAlarmTextSecondary)
                setTextColor(getColor(R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) }
            })
        })
        addView(ImageButton(this@RecordatoriosActivity).apply {
            setImageResource(R.drawable.ic_delete_outline); imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.error_red))
            background = selectableItemBackgroundBorderless; contentDescription = "Eliminar ${entry.name}"
            setOnClickListener { confirmDeleteMedicine(entry) }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        })
    }

    private fun confirmDeleteMedicine(entry: MedicineEntry) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_confirm_delete_medicine)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0.55f)
        dialog.findViewById<TextView>(R.id.deleteMedicineMessage).text =
            if (entry.dose.isBlank()) entry.name else "${entry.name} · ${entry.dose}"
        dialog.findViewById<View>(R.id.btnDeleteMedicineCancel).setOnClickListener { dialog.dismiss() }
        val deleteButton = dialog.findViewById<View>(R.id.btnDeleteMedicineConfirm)
        deleteButton.setOnClickListener {
            deleteButton.isEnabled = false
            val snapshot = alarmSnapshot?.child(entry.id)
            val legacyDays = snapshot?.let(AlarmSchedule::dias).orEmpty()
            val isSharedLegacyEntry = snapshot?.child("diaSemana")?.getValue(Int::class.java) == null &&
                entry.day in legacyDays && legacyDays.size > 1
            val deleteTask = if (isSharedLegacyEntry) {
                alarmsRef.child(entry.id).child("diasSemana").setValue(legacyDays.filter { it != entry.day }.sorted())
            } else {
                alarmsRef.child(entry.id).removeValue()
            }
            deleteTask
                .addOnSuccessListener {
                    DoseOccurrenceTracker.encolarReconciliacion(applicationContext, auth.currentUser?.uid.orEmpty())
                    dialog.dismiss()
                    Toast.makeText(this, "Medicamento eliminado.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    deleteButton.isEnabled = true
                    Toast.makeText(this, R.string.alarm_delete_error, Toast.LENGTH_LONG).show()
                }
        }
        dialog.show()
        dialog.window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(32), dp(400)), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        DialogActionButtonSizer.equalize(dialog, R.id.btnDeleteMedicineCancel, R.id.btnDeleteMedicineConfirm)
    }
    private fun showMedicineDialog(day: Int, period: Int) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_add_medicine)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0.55f)
        dialog.findViewById<TextView>(R.id.addMedicineSubtitle)?.text = "${weekdays[day - 1]} · ${periodTitle(period)}"
        val name = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMedicineName)
        val dose = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMedicineDose)
        val nameLayout = dialog.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.medicineInputLayout)
        val doseLayout = dialog.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.doseInputLayout)
        dialog.findViewById<View>(R.id.btnAddMedicineCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnAddMedicineConfirm).setOnClickListener {
            val medicineName = name.text?.toString()?.trim().orEmpty()
            val medicineDose = dose.text?.toString()?.trim().orEmpty()
            nameLayout.error = if (medicineName.isEmpty()) getString(R.string.alarm_medicine_validation) else null
            doseLayout.error = if (medicineDose.isEmpty()) getString(R.string.alarm_dose_validation) else null
            if (medicineName.isEmpty()) name.requestFocus()
            else if (medicineDose.isEmpty()) dose.requestFocus()
            else {
                val confirm = dialog.findViewById<View>(R.id.btnAddMedicineConfirm)
                confirm.isEnabled = false
                saveMedicine(medicineName, medicineDose, listOf(day), period,
                    onSuccess = { dialog.dismiss() },
                    onFailure = { confirm.isEnabled = true })
            }
        }
        dialog.show()
        val width = minOf(resources.displayMetrics.widthPixels - dp(32), dp(400))
        dialog.window?.setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        DialogActionButtonSizer.equalize(dialog, R.id.btnAddMedicineCancel, R.id.btnAddMedicineConfirm)
    }

    private fun saveMedicine(name: String, dose: String, days: List<Int>, period: Int, onSuccess: () -> Unit, onFailure: () -> Unit) {
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
            onSuccess()
            Toast.makeText(this, "Medicamento guardado.", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { onFailure(); Toast.makeText(this, "No se pudo guardar. Revisa tu conexión.", Toast.LENGTH_LONG).show() }
    }

    private fun showEditSlotDialog(day: Int, period: Int, current: String) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_edit_alarm_slot)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0.55f)
        dialog.findViewById<TextView>(R.id.editSlotSubtitle).text = "${weekdays[day - 1]} · ${periodTitle(period)}"
        val hourPicker = dialog.findViewById<NumberPicker>(R.id.editHourPicker)
        val minutePicker = dialog.findViewById<NumberPicker>(R.id.editMinutePicker)
        val meridiemPicker = dialog.findViewById<NumberPicker>(R.id.editMeridiemPicker)
        hourPicker.minValue = 1
        hourPicker.maxValue = 12
        hourPicker.wrapSelectorWheel = true
        minutePicker.minValue = 0
        minutePicker.maxValue = 59
        minutePicker.wrapSelectorWheel = true
        minutePicker.setFormatter { String.format(Locale.ROOT, "%02d", it) }
        meridiemPicker.minValue = 0
        meridiemPicker.maxValue = 1
        meridiemPicker.displayedValues = arrayOf("AM", "PM")
        meridiemPicker.wrapSelectorWheel = false
        val pieces = current.split(":")
        val hour24 = pieces.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: defaults[period].substringBefore(":").toInt()
        hourPicker.value = ((hour24 + 11) % 12) + 1
        minutePicker.value = (pieces.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
        meridiemPicker.value = if (hour24 >= 12) 1 else 0
        styleTimePicker(hourPicker, R.dimen.ui_text_time_wheel)
        styleTimePicker(minutePicker, R.dimen.ui_text_time_wheel)
        styleTimePicker(meridiemPicker, R.dimen.ui_text_time_period)
        dialog.findViewById<View>(R.id.btnEditSlotCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnSyncMedicines).setOnClickListener {
            startActivity(android.content.Intent(this, SincronizarMedicamentosActivity::class.java).apply {
                putExtra(SincronizarMedicamentosActivity.EXTRA_SOURCE_DAY, day)
                putExtra(SincronizarMedicamentosActivity.EXTRA_PERIOD, period)
            })
        }
        val save = dialog.findViewById<View>(R.id.btnEditSlotSave)
        save.setOnClickListener {
            val selectedHour = when {
                meridiemPicker.value == 1 && hourPicker.value == 12 -> 12
                meridiemPicker.value == 1 -> hourPicker.value + 12
                hourPicker.value == 12 -> 0
                else -> hourPicker.value
            }
            val inPeriod = when (period) {
                0 -> selectedHour in 0..11
                1 -> selectedHour in 12..17
                else -> selectedHour in 18..23
            }
            if (!inPeriod) {
                Toast.makeText(this, "La hora debe estar dentro de ${periodTitle(period).lowercase()}.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            save.isEnabled = false
            val time = String.format(Locale.ROOT, "%02d:%02d", selectedHour, minutePicker.value)
            val updates = mutableMapOf<String, Any?>(
                "agendaSemanal/$day/$period/hora" to time,
            )
            alarmSnapshot?.children?.forEach { item ->
                val days = AlarmSchedule.dias(item)
                val itemDay = item.child("diaSemana").getValue(Int::class.java)
                val itemPeriod = item.child("franja").getValue(Int::class.java)
                    ?: periodFor(item.child("hora").getValue(String::class.java).orEmpty())
                if (itemPeriod != period) return@forEach
                val id = item.key ?: return@forEach
                if (itemDay == day) {
                    updates["recordatorios/$id/hora"] = time
                    updates["recordatorios/$id/actualizadoEn"] = ServerValue.TIMESTAMP
                } else if (itemDay == null && day in days) {
                    if (days.size > 1) {
                        updates["recordatorios/$id/diasSemana"] = days.filter { it != day }.sorted()
                        val splitId = alarmsRef.push().key ?: return@forEach
                        val splitEntry = (item.value as? Map<*, *>)
                            ?.entries
                            ?.associate { it.key.toString() to it.value }
                            ?.toMutableMap() ?: mutableMapOf()
                        splitEntry.putAll(
                            mapOf(
                                "id" to splitId,
                                "diaSemana" to day,
                                "franja" to period,
                                "slotId" to "${day}_$period",
                                "diasSemana" to listOf(day),
                                "hora" to time,
                                "actualizadoEn" to ServerValue.TIMESTAMP,
                            ),
                        )
                        updates["recordatorios/$splitId"] = splitEntry
                    } else {
                        updates["recordatorios/$id/diaSemana"] = day
                        updates["recordatorios/$id/franja"] = period
                        updates["recordatorios/$id/slotId"] = "${day}_$period"
                        updates["recordatorios/$id/hora"] = time
                        updates["recordatorios/$id/actualizadoEn"] = ServerValue.TIMESTAMP
                    }
                }
            }
            userRef.updateChildren(updates)
                .addOnSuccessListener {
                    DoseOccurrenceTracker.encolarReconciliacion(applicationContext, userId)
                    dialog.dismiss()
                    Toast.makeText(this, "Hora guardada.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    save.isEnabled = true
                    Toast.makeText(this, "No se pudo guardar la hora. Revisa tu conexión.", Toast.LENGTH_LONG).show()
                }
        }
        dialog.show()
        dialog.window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(32), dp(400)), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        DialogActionButtonSizer.equalize(dialog, R.id.btnEditSlotCancel, R.id.btnEditSlotSave)
    }

    private fun styleTimePicker(picker: NumberPicker, textSizeResource: Int) {
        val textSizePx = resources.getDimension(textSizeResource)
        runCatching {
            NumberPicker::class.java.getDeclaredField("mSelectionDivider").apply { isAccessible = true }.set(picker, null)
        }
        runCatching {
            NumberPicker::class.java.getMethod("setSelectionDividerHeight", Int::class.javaPrimitiveType).invoke(picker, 0)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) picker.setTextSize(textSizePx)
        runCatching {
            val paint = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint").apply { isAccessible = true }.get(picker) as? android.graphics.Paint
            paint?.apply {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) textSize = textSizePx
                typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.NORMAL)
            }
        }
        for (index in 0 until picker.childCount) (picker.getChildAt(index) as? EditText)?.apply {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSizePx)
            includeFontPadding = false
        }
        picker.invalidate()
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
}

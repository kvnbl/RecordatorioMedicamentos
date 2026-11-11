package com.example.recordatoriomedicamentos

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {
    private lateinit var autenticacion: FirebaseAuth
    private var procesando = false
    private var escuchaAlarmas: ValueEventListener? = null
    private var referenciaAlarmas: DatabaseReference? = null
    private var contenidoActual: Int? = null
    private var claveDosisSolicitada: String? = null
    private var callbackVolverSidebar: OnBackPressedCallback? = null
    private var vistaConEnfoqueAnterior: View? = null
    private var accesibilidadPanelAntesSidebar = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    private var dialogoCerrarSesion: Dialog? = null
    private val receptorTomas =
        object : BroadcastReceiver() {
            override fun onReceive(contexto: Context, intencion: Intent) {
                mostrarConfirmacionPendiente()
            }
        }
    private var receptorTomasRegistrado = false

    private val escucharSesion =
        FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) mostrarInicio() else mostrarLogin()
        }

    override fun onCreate(estadoGuardado: Bundle?) {
        super.onCreate(estadoGuardado)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        autenticacion = FirebaseAuth.getInstance()
        if (autenticacion.currentUser == null) mostrarLogin() else mostrarInicio()
    }

    override fun onStart() {
        super.onStart()
        autenticacion.addAuthStateListener(escucharSesion)
        if (!receptorTomasRegistrado) {
            ContextCompat.registerReceiver(
                this,
                receptorTomas,
                IntentFilter(DeviceConnectionService.ACCION_TOMA_PENDIENTE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receptorTomasRegistrado = true
        }
        if (autenticacion.currentUser != null && contenidoActual == R.layout.activity_inicio) {
            DoseOccurrenceTracker.iniciar(applicationContext, autenticacion.currentUser!!.uid)
            escucharProximaAlarma()
            mostrarConfirmacionPendiente()
        }
    }

    private fun mostrarConfirmacionPendiente() {
        if (isFinishing || isDestroyed || autenticacion.currentUser == null) return
        val preferencias = getSharedPreferences("pending_intake", 0)
        val identificadorGrupo = preferencias.getStringSet("groups", emptySet())?.firstOrNull()
        if (identificadorGrupo != null) {
            if (claveDosisSolicitada == identificadorGrupo) return
            claveDosisSolicitada = identificadorGrupo
            val titulo = preferencias.getString("group_title_$identificadorGrupo", "").orEmpty()
            AlertDialog.Builder(this)
                .setTitle(R.string.device_confirm_group_title)
                .setMessage(titulo)
                .setPositiveButton(R.string.device_confirm_yes) { _, _ ->
                    DoseAnswerReceiver.guardarRespuestaDeGrupo(this, identificadorGrupo, true) {
                        claveDosisSolicitada = null
                        mostrarConfirmacionPendiente()
                    }
                }
                .setNegativeButton(R.string.device_confirm_no) { _, _ ->
                    DoseAnswerReceiver.guardarRespuestaDeGrupo(this, identificadorGrupo, false) {
                        claveDosisSolicitada = null
                        mostrarConfirmacionPendiente()
                    }
                }
                .setOnCancelListener { claveDosisSolicitada = null }
                .show()
            return
        }
        val clave = preferencias.getStringSet("keys", emptySet())?.firstOrNull() ?: return
        if (claveDosisSolicitada == clave) return
        claveDosisSolicitada = clave
        val medicamento = preferencias.getString("medicine_$clave", "Medicamento") ?: "Medicamento"
        val dosisIndicada = preferencias.getString("dose_$clave", "").orEmpty()
        AlertDialog.Builder(this)
            .setTitle("¿Tomaste $medicamento?")
            .setMessage(dosisIndicada)
            .setPositiveButton("Sí, la tomé") { _, _ ->
                DoseAnswerReceiver.guardarRespuesta(this, clave, true) {
                    claveDosisSolicitada = null
                    mostrarConfirmacionPendiente()
                }
            }
            .setNegativeButton("No la tomé") { _, _ ->
                DoseAnswerReceiver.guardarRespuesta(this, clave, false) {
                    claveDosisSolicitada = null
                    mostrarConfirmacionPendiente()
                }
            }
            .setOnCancelListener { claveDosisSolicitada = null }
            .show()
    }

    override fun onStop() {
        autenticacion.removeAuthStateListener(escucharSesion)
        if (receptorTomasRegistrado) {
            unregisterReceiver(receptorTomas)
            receptorTomasRegistrado = false
        }
        detenerEscuchaAlarmas()
        super.onStop()
    }

    private fun prepararInsets() {
        val raiz = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, inserciones ->
            val barras = inserciones.getInsets(WindowInsetsCompat.Type.systemBars())
            val teclado = inserciones.getInsets(WindowInsetsCompat.Type.ime())
            val insercionInferior =
                if (
                    contenidoActual == R.layout.activity_main &&
                        inserciones.isVisible(WindowInsetsCompat.Type.ime())
                )
                    maxOf(barras.bottom, teclado.bottom)
                else barras.bottom
            vista.setPadding(barras.left, barras.top, barras.right, insercionInferior)
            if (contenidoActual == R.layout.activity_inicio) {
                (vista as? ViewGroup)?.apply {
                    clipToPadding = false
                    clipChildren = false
                }
                findViewById<View>(R.id.dashboardSidebarOverlay)?.let { superposicion ->
                    val parametros = superposicion.layoutParams as? FrameLayout.LayoutParams
                    if (parametros != null) {
                        parametros.topMargin = -barras.top
                        parametros.bottomMargin = -barras.bottom
                        superposicion.layoutParams = parametros
                    }
                }
                findViewById<View>(R.id.sidebarMainContent)?.let { contenido ->
                    contenido.setPadding(
                        contenido.paddingLeft,
                        contenido.paddingTop,
                        contenido.paddingRight,
                        resources.getDimensionPixelSize(R.dimen.ui_space_16) + barras.bottom,
                    )
                }
            }
            if (
                contenidoActual == R.layout.activity_main &&
                    inserciones.isVisible(WindowInsetsCompat.Type.ime())
            )
                raiz.post { asegurarCampoEnVista() }
            inserciones
        }
        ViewCompat.requestApplyInsets(raiz)
    }

    private fun mostrarLogin() {
        if (contenidoActual == R.layout.activity_main) return
        detenerEscuchaAlarmas()
        contenidoActual = R.layout.activity_main
        setContentView(R.layout.activity_main)
        prepararInsets()

        val correo = findViewById<EditText>(R.id.etCorreo)
        val clave = findViewById<EditText>(R.id.etContrasena)
        val ingresar = findViewById<MaterialButton>(R.id.btnIngresar)
        val crear = findViewById<MaterialButton>(R.id.btnRegistrarse)
        ingresar.setOnClickListener { autenticar(false, correo, clave, ingresar, crear) }
        crear.setOnClickListener { autenticar(true, correo, clave, ingresar, crear) }
        val alCambiarFoco =
            View.OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) view.post { asegurarCampoEnVista() }
            }
        correo.onFocusChangeListener = alCambiarFoco
        clave.onFocusChangeListener = alCambiarFoco
    }

    private fun asegurarCampoEnVista() {
        if (contenidoActual != R.layout.activity_main) return
        val desplazamiento = findViewById<ScrollView>(R.id.loginScrollView)
        val inserciones = ViewCompat.getRootWindowInsets(desplazamiento) ?: return
        if (!inserciones.isVisible(WindowInsetsCompat.Type.ime())) return
        val campoEnfocado = currentFocus ?: return
        if (campoEnfocado.id != R.id.etCorreo && campoEnfocado.id != R.id.etContrasena) return
        val limites = Rect()
        campoEnfocado.getDrawingRect(limites)
        desplazamiento.offsetDescendantRectToMyCoords(campoEnfocado, limites)
        val espacio = (12 * resources.displayMetrics.density).toInt()
        val bordeInferiorVisible = desplazamiento.scrollY + desplazamiento.height - desplazamiento.paddingBottom
        val desborde = limites.bottom + espacio - bordeInferiorVisible
        if (desborde > 0) desplazamiento.scrollBy(0, desborde)
    }

    private fun mostrarInicio() {
        if (contenidoActual == R.layout.activity_inicio) {
            return
        }
        contenidoActual = R.layout.activity_inicio
        setContentView(R.layout.activity_inicio)
        // El fondo fijo sigue visible tras las barras del sistema mientras se desplaza el panel.
        prepararInsets()
        adaptarDashboardAjustesDelDispositivo()
        configurarTarjeta(
            R.id.btnRecordatorios,
            R.string.nav_alarms,
            R.string.feature_alarms,
            R.drawable.ic_alarm,
            R.color.brand_primary,
            R.drawable.bg_feature_lavender,
            R.drawable.bg_feature_icon_lavender,
        ) {
            abrirRecordatorios()
        }
        configurarTarjeta(
            R.id.btnHistorial,
            R.string.nav_history,
            R.string.feature_history,
            R.drawable.ic_bar_chart,
            R.color.feature_blue,
            R.drawable.bg_feature_blue,
            R.drawable.bg_feature_icon_blue,
        ) {
            startActivity(Intent(this, HistorialActivity::class.java))
        }
        configurarTarjeta(
            R.id.btnInformes,
            R.string.nav_reports,
            R.string.feature_reports,
            R.drawable.ic_trending_up,
            R.color.feature_green,
            R.drawable.bg_feature_mint,
            R.drawable.bg_feature_icon_mint,
        ) {
            startActivity(Intent(this, InformesActivity::class.java))
        }
        configurarTarjeta(
            R.id.btnPastillero,
            R.string.nav_device,
            R.string.feature_device,
            R.drawable.ic_bluetooth,
            R.color.feature_orange,
            R.drawable.bg_feature_peach,
            R.drawable.bg_feature_icon_peach,
        ) {
            startActivity(Intent(this, PastilleroActivity::class.java))
        }

        findViewById<View>(R.id.btnNuevaAlarma).setOnClickListener { abrirNuevaAlarma() }
        findViewById<View>(R.id.tvAlarmState).setOnClickListener {
            if (findViewById<TextView>(R.id.tvAlarmState).text == getString(R.string.alarm_error)) {
                escucharProximaAlarma()
            }
        }
        configurarSidebar()
        findViewById<View>(R.id.btnMenu).setOnClickListener { abrirSidebar() }
        autenticacion.currentUser?.uid?.let { DoseOccurrenceTracker.iniciar(applicationContext, it) }
        escucharProximaAlarma()
        mostrarConfirmacionPendiente()
    }

    /**
     * Reorganiza el panel según el ancho de ventana y la escala de texto configurados en Android.
     */
    private fun adaptarDashboardAjustesDelDispositivo() {
        val anchoDp =
            resources.configuration.screenWidthDp.takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val escalaFuente = resources.configuration.fontScale.coerceAtLeast(1f)
        val anchoDisponible = (minOf(anchoDp, 720) - 32).coerceAtLeast(1)
        val maximoColumnas = if (anchoDisponible >= 650) 4 else 2
        val columnas = (anchoDisponible / (168f * escalaFuente)).toInt().coerceIn(1, maximoColumnas)
        val cuadricula = findViewById<GridLayout>(R.id.dashboardFeatureGrid)
        cuadricula.columnCount = columnas
        val margen = (6 * resources.displayMetrics.density).toInt()
        for (indice in 0 until cuadricula.childCount) {
            val tarjeta = cuadricula.getChildAt(indice)
            val parametros =
                (tarjeta.layoutParams as? GridLayout.LayoutParams) ?: GridLayout.LayoutParams()
            parametros.width = 0
            parametros.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            parametros.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            parametros.setMargins(margen, margen, margen, margen)
            tarjeta.layoutParams = parametros
        }

        val apilado = escalaFuente >= 1.3f || anchoDp < 360
        val contenidoAlarma = findViewById<android.widget.LinearLayout>(R.id.alarmContent)
        val filaPrincipalAlarma = findViewById<android.widget.LinearLayout>(R.id.alarmMainRow)
        val separador = findViewById<View>(R.id.alarmDivider)
        val detallesAlarma = findViewById<View>(R.id.alarmDetails)
        val textoHora = findViewById<TextView>(R.id.tvAlarmTime)
        if (apilado) {
            if (textoHora.parent === filaPrincipalAlarma) {
                filaPrincipalAlarma.removeView(textoHora)
            }
            if (separador.parent === filaPrincipalAlarma) {
                filaPrincipalAlarma.removeView(separador)
            }
            detallesAlarma.layoutParams =
                (detallesAlarma.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                    width = 0
                    weight = 1f
                    marginStart = (12 * resources.displayMetrics.density).toInt()
                }
            textoHora.gravity = android.view.Gravity.START
            if (textoHora.parent !== contenidoAlarma) {
                (textoHora.parent as? android.view.ViewGroup)?.removeView(textoHora)
                contenidoAlarma.addView(textoHora)
            }
            textoHora.layoutParams =
                android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    .apply {
                        marginStart =
                            resources.getDimensionPixelSize(R.dimen.history_record_icon_size) +
                                (12 * resources.displayMetrics.density).toInt()
                        topMargin = (8 * resources.displayMetrics.density).toInt()
                    }
        } else {
            if (textoHora.parent === contenidoAlarma) {
                contenidoAlarma.removeView(textoHora)
            }
            detallesAlarma.layoutParams =
                (detallesAlarma.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                    width = 0
                    weight = 1f
                    marginStart = (12 * resources.displayMetrics.density).toInt()
                }
            if (separador.parent !== filaPrincipalAlarma) {
                (separador.parent as? android.view.ViewGroup)?.removeView(separador)
                val indiceDetalles = filaPrincipalAlarma.indexOfChild(detallesAlarma)
                filaPrincipalAlarma.addView(separador, indiceDetalles + 1)
            }
            if (textoHora.parent !== filaPrincipalAlarma) {
                (textoHora.parent as? android.view.ViewGroup)?.removeView(textoHora)
                filaPrincipalAlarma.addView(textoHora)
            }
            textoHora.gravity = android.view.Gravity.CENTER
            textoHora.layoutParams =
                android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                )
        }
    }

    private fun configurarTarjeta(
        idVista: Int,
        titulo: Int,
        subtitulo: Int,
        icono: Int,
        tinte: Int,
        fondo: Int,
        fondoIcono: Int,
        accion: () -> Unit,
    ) {
        val tarjeta = findViewById<View>(idVista)
        tarjeta.setBackgroundResource(fondo)
        tarjeta.contentDescription = "${getString(titulo)}. ${getString(subtitulo)}"
        tarjeta.findViewById<TextView>(R.id.featureTitle).setText(titulo)
        tarjeta.findViewById<TextView>(R.id.featureSubtitle).setText(subtitulo)
        tarjeta.findViewById<ImageView>(R.id.featureIcon).apply {
            setImageResource(icono)
            setColorFilter(getColor(tinte))
            setBackgroundResource(fondoIcono)
        }
        tarjeta.setOnClickListener { accion() }
    }

    private fun configurarSidebar() {
        val cuenta = findViewById<TextView>(R.id.sidebarAccountEmail)
        cuenta.text =
            autenticacion.currentUser?.email?.takeIf(String::isNotBlank) ?: getString(R.string.greeting_user)

        configurarItemSidebar(
            R.id.sidebarHome,
            R.string.nav_home,
            R.drawable.ic_home_filled,
            true,
        ) {
            cerrarSidebar()
        }
        configurarItemSidebar(R.id.sidebarAlarms, R.string.nav_alarms, R.drawable.ic_alarm) {
            cerrarSidebar(restaurarEnfoque = false)
            abrirRecordatorios()
        }
        configurarItemSidebar(R.id.sidebarHistory, R.string.nav_history, R.drawable.ic_bar_chart) {
            cerrarSidebar(restaurarEnfoque = false)
            startActivity(Intent(this, HistorialActivity::class.java))
        }
        configurarItemSidebar(
            R.id.sidebarReports,
            R.string.nav_reports,
            R.drawable.ic_trending_up,
        ) {
            cerrarSidebar(restaurarEnfoque = false)
            startActivity(Intent(this, InformesActivity::class.java))
        }
        configurarItemSidebar(R.id.sidebarDevice, R.string.nav_device, R.drawable.ic_bluetooth) {
            cerrarSidebar(restaurarEnfoque = false)
            startActivity(Intent(this, PastilleroActivity::class.java))
        }

        findViewById<View>(R.id.dashboardSidebarScrim).setOnClickListener { cerrarSidebar() }
        findViewById<View>(R.id.dashboardSidebarPanel)
            .setOnClickListener { /* Evita que los toques salgan del panel lateral. */ }
        findViewById<MaterialButton>(R.id.sidebarSignOut).setOnClickListener {
            cerrarSidebar(restaurarEnfoque = false)
            mostrarConfirmacionCerrarSesion()
        }
    }

    private fun configurarItemSidebar(
        idFila: Int,
        idTitulo: Int,
        idIcono: Int,
        seleccionado: Boolean = false,
        accion: () -> Unit,
    ) {
        val fila = findViewById<View>(idFila)
        fila.contentDescription = getString(idTitulo)
        fila.isSelected = seleccionado
        fila.setBackgroundResource(
            if (seleccionado) R.drawable.bg_sidebar_item_active else R.drawable.bg_sidebar_item_ripple
        )
        fila.findViewById<ImageView>(R.id.sidebarItemIcon).apply {
            setImageResource(idIcono)
            imageTintList = ColorStateList.valueOf(getColor(R.color.brand_primary))
        }
        fila.findViewById<TextView>(R.id.sidebarItemLabel).setText(idTitulo)
        fila.setOnClickListener { accion() }
    }

    private fun abrirSidebar() {
        val overlay = findViewById<View>(R.id.dashboardSidebarOverlay)
        if (overlay.visibility == View.VISIBLE) return

        val raiz = findViewById<View>(R.id.main)
        val panel = findViewById<View>(R.id.dashboardSidebarPanel)
        val densidad = resources.displayMetrics.density
        val escalaFuente = resources.configuration.fontScale.coerceAtLeast(1f)
        val anchoDisponiblePx = (raiz.width - raiz.paddingLeft - raiz.paddingRight).coerceAtLeast(1)
        val anchoDisponibleDp = anchoDisponiblePx / densidad
        val anchoMaximoDp = if (escalaFuente >= 1.25f) 400f else 360f
        val anchoPanelDp =
            maxOf(anchoDisponibleDp * 0.66f, 250f * escalaFuente)
                .coerceAtMost(anchoMaximoDp)
                .coerceAtMost(anchoDisponibleDp)
        panel.layoutParams =
            (panel.layoutParams as FrameLayout.LayoutParams).apply {
                width = (anchoPanelDp * densidad).toInt()
                height = ViewGroup.LayoutParams.MATCH_PARENT
                gravity = Gravity.START
            }

        vistaConEnfoqueAnterior = currentFocus ?: findViewById(R.id.btnMenu)
        val desplazamientoDashboard = findViewById<ScrollView>(R.id.dashboardScrollView)
        accesibilidadPanelAntesSidebar = desplazamientoDashboard.importantForAccessibility
        desplazamientoDashboard.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        ViewCompat.setAccessibilityPaneTitle(panel, getString(R.string.sidebar_accessibility_title))
        findViewById<View>(R.id.sidebarHome).post {
            findViewById<View>(R.id.sidebarHome)?.requestFocus()
        }

        callbackVolverSidebar?.remove()
        callbackVolverSidebar =
            object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        cerrarSidebar()
                    }
                }
                .also(onBackPressedDispatcher::addCallback)
    }

    private fun cerrarSidebar(restaurarEnfoque: Boolean = true) {
        val overlay = findViewById<View?>(R.id.dashboardSidebarOverlay) ?: return
        if (overlay.visibility != View.VISIBLE) return
        overlay.visibility = View.GONE
        callbackVolverSidebar?.remove()
        callbackVolverSidebar = null

        findViewById<ScrollView?>(R.id.dashboardScrollView)?.let { desplazamiento ->
            desplazamiento.importantForAccessibility = accesibilidadPanelAntesSidebar
        }
        if (restaurarEnfoque) vistaConEnfoqueAnterior?.takeIf { it.isShown }?.requestFocus()
        vistaConEnfoqueAnterior = null
    }

    private fun mostrarConfirmacionCerrarSesion() {
        dialogoCerrarSesion?.dismiss()
        val dialogo = Dialog(this)
        dialogo.setContentView(R.layout.dialog_logout_confirmation)
        dialogo.setCancelable(true)
        dialogo.setCanceledOnTouchOutside(true)
        dialogo.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogo.window?.setTitle(getString(R.string.logout_confirmation_title))
        dialogoCerrarSesion = dialogo
        dialogo.setOnDismissListener { if (dialogoCerrarSesion === dialogo) dialogoCerrarSesion = null }

        dialogo.findViewById<MaterialButton>(R.id.btnLogoutNo).setOnClickListener {
            dialogo.dismiss()
        }
        dialogo.findViewById<MaterialButton>(R.id.btnLogoutYes).setOnClickListener {
            dialogo.dismiss()
            confirmarCerrarSesion()
        }

        dialogo.show()
        dialogo.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val anchoPantalla = resources.displayMetrics.widthPixels
            val margenHorizontal = (32 * resources.displayMetrics.density).toInt()
            val anchoMaximo = (400 * resources.displayMetrics.density).toInt()
            setLayout(
                minOf(anchoPantalla - margenHorizontal, anchoMaximo),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.55f }
        }
    }

    private fun confirmarCerrarSesion() {
        autenticacion.currentUser?.uid?.let { DoseOccurrenceTracker.detener(applicationContext, it) }
        DeviceConnectionService.desconectar(this)
        autenticacion.signOut()
    }

    private fun abrirRecordatorios() {
        startActivity(Intent(this, RecordatoriosActivity::class.java))
    }

    private fun abrirNuevaAlarma() {
        startActivity(Intent(this, NuevaAlarmaActivity::class.java))
    }

    private fun autenticar(
        registrar: Boolean,
        campoCorreo: EditText,
        campoClave: EditText,
        botonIngresar: MaterialButton,
        botonCrear: MaterialButton,
    ) {
        if (procesando) return
        val correo = campoCorreo.text.toString().trim()
        val clave = campoClave.text.toString()
        campoCorreo.error = null
        campoClave.error = null
        ocultarError()

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            campoCorreo.error = getString(R.string.auth_email_invalid)
            campoCorreo.requestFocus()
            return
        }
        if (clave.isEmpty()) {
            campoClave.error = getString(R.string.auth_password_empty)
            campoClave.requestFocus()
            return
        }
        if (registrar && clave.length < 12) {
            campoClave.error = getString(R.string.auth_password_weak)
            campoClave.requestFocus()
            return
        }

        procesando = true
        botonIngresar.isEnabled = false
        botonCrear.isEnabled = false
        campoCorreo.isEnabled = false
        campoClave.isEnabled = false
        botonIngresar.text = ""
        botonIngresar.icon = null
        findViewById<ProgressBar>(R.id.loginProgress).visibility = View.VISIBLE
        val tarea =
            if (registrar) autenticacion.createUserWithEmailAndPassword(correo, clave)
            else autenticacion.signInWithEmailAndPassword(correo, clave)

        tarea.addOnCompleteListener { resultado ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            procesando = false
            if (contenidoActual != R.layout.activity_main) return@addOnCompleteListener
            botonIngresar.isEnabled = true
            botonCrear.isEnabled = true
            campoCorreo.isEnabled = true
            campoClave.isEnabled = true
            botonIngresar.setText(R.string.action_sign_in)
            botonIngresar.setIconResource(R.drawable.ic_login)
            findViewById<ProgressBar>(R.id.loginProgress).visibility = View.GONE
            campoClave.text?.clear()

            if (resultado.isSuccessful) {
                Toast.makeText(
                        this,
                        getString(
                            if (registrar) R.string.auth_register_success
                            else R.string.auth_login_success
                        ),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            } else {
                val mensaje =
                    when (resultado.exception) {
                        is FirebaseNetworkException -> getString(R.string.auth_network_error)
                        is FirebaseAuthUserCollisionException -> getString(R.string.auth_collision)
                        is FirebaseAuthWeakPasswordException ->
                            getString(R.string.auth_password_weak)
                        else ->
                            getString(
                                if (registrar) R.string.auth_register_error
                                else R.string.auth_login_error
                            )
                    }
                mostrarError(mensaje)
            }
        }
    }

    private fun mostrarError(mensaje: String) {
        findViewById<TextView>(R.id.tvAuthError).text = mensaje
        findViewById<View>(R.id.authError).visibility = View.VISIBLE
    }

    private fun ocultarError() {
        findViewById<View>(R.id.authError).visibility = View.GONE
    }

    private fun escucharProximaAlarma() {
        detenerEscuchaAlarmas()
        val idUsuario = autenticacion.currentUser?.uid ?: return
        val referencia =
            FirebaseDatabase.getInstance(
                    "https://recordatoriodemedicament-90e4f-default-rtdb.firebaseio.com/"
                )
                .getReference("usuarios")
                .child(idUsuario)
                .child("recordatorios")
        referenciaAlarmas = referencia
        val escucha =
            object : ValueEventListener {
                override fun onDataChange(instantanea: DataSnapshot) {
                    if (isFinishing || isDestroyed || autenticacion.currentUser?.uid != idUsuario) return
                    AlarmSchedule.migrarEsquemaAnterior(referencia, instantanea)
                    mostrarSiguienteAlarma(instantanea)
                }

                override fun onCancelled(errorBaseDatos: DatabaseError) {
                    if (isFinishing || isDestroyed || autenticacion.currentUser?.uid != idUsuario) return
                    mostrarEstadoAlarma(getString(R.string.alarm_error), reintentar = true)
                }
            }
        escuchaAlarmas = escucha
        referencia.addValueEventListener(escucha)
    }

    private fun mostrarSiguienteAlarma(instantanea: DataSnapshot) {
        val ahora = System.currentTimeMillis()
        val candidatas = sortedMapOf<Long, MutableList<CandidataAlarma>>()
        for (registro in instantanea.children) {
            if (registro.child("activo").getValue(Boolean::class.java) != true) continue
            if (registro.child("tipoRepeticion").getValue(String::class.java) == "una_vez") continue
            val medicamento = registro.child("medicamento").getValue(String::class.java).orEmpty()
            if (medicamento.isBlank()) continue
            val dosis = registro.child("dosis").getValue(String::class.java).orEmpty()
            val textoHora = registro.child("hora").getValue(String::class.java) ?: continue
            val idZona = registro.child("zonaHoraria").getValue(String::class.java) ?: continue
            val zona = TimeZone.getTimeZone(idZona)
            val partes = textoHora.split(":")
            if (partes.size != 2) continue
            val hora = partes[0].toIntOrNull() ?: continue
            val minuto = partes[1].toIntOrNull() ?: continue
            if (hora !in 0..23 || minuto !in 0..59) continue
            val dias = AlarmSchedule.dias(registro)
            if (dias.isEmpty()) continue

            val calendarioBase = Calendar.getInstance(zona).apply { timeInMillis = ahora }
            for (desfase in 0..7) {
                val calendarioCandidato =
                    (calendarioBase.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_YEAR, desfase)
                        set(Calendar.HOUR_OF_DAY, hora)
                        set(Calendar.MINUTE, minuto)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                val diaSemana = AlarmSchedule.diaSemanaDesdeLunes(calendarioCandidato)
                val marcaTiempo = calendarioCandidato.timeInMillis
                if (diaSemana in dias && marcaTiempo > ahora) {
                    candidatas
                        .getOrPut(marcaTiempo) { mutableListOf() }
                        .add(CandidataAlarma(marcaTiempo, medicamento, dosis, zona, calendarioCandidato))
                }
            }
        }
        val siguiente =
            candidatas.values.firstOrNull()?.let { grupo ->
                val primera = grupo.first()
                primera.copy(
                    medicamento = grupo.map { it.medicamento }.distinct().joinToString(", "),
                    dosis =
                        grupo
                            .map { it.dosis }
                            .filter(String::isNotBlank)
                            .distinct()
                            .joinToString(" · "),
                )
            }
        if (siguiente == null) {
            findViewById<View>(R.id.alarmContent).visibility = View.GONE
            findViewById<TextView>(R.id.tvAlarmDay).visibility = View.GONE
            mostrarEstadoAlarma(getString(R.string.alarm_empty))
            return
        }
        findViewById<View>(R.id.alarmContent).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvAlarmMedicine).text = siguiente.medicamento
        findViewById<TextView>(R.id.tvAlarmDose).text = siguiente.dosis
        findViewById<TextView>(R.id.tvAlarmTime).text =
            AlarmTimeFormatter.formatear(siguiente.marcaTiempo, siguiente.zona)
        val hoy = Calendar.getInstance(siguiente.zona)
        val manana = (hoy.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val indicadorDia = findViewById<TextView>(R.id.tvAlarmDay)
        indicadorDia.text =
            when {
                mismaFecha(siguiente.calendario, hoy) -> getString(R.string.alarm_today)
                mismaFecha(siguiente.calendario, manana) -> getString(R.string.alarm_tomorrow)
                else ->
                    SimpleDateFormat("EEE d MMM", Locale.getDefault())
                        .apply { timeZone = siguiente.zona }
                        .format(Date(siguiente.marcaTiempo))
            }
        indicadorDia.visibility = View.VISIBLE
        mostrarEstadoAlarma("")
    }

    private fun mismaFecha(primera: Calendar, segunda: Calendar): Boolean =
        primera.get(Calendar.YEAR) == segunda.get(Calendar.YEAR) &&
            primera.get(Calendar.DAY_OF_YEAR) == segunda.get(Calendar.DAY_OF_YEAR)

    private fun mostrarEstadoAlarma(mensaje: String, reintentar: Boolean = false) {
        findViewById<TextView>(R.id.tvAlarmState).apply {
            text = mensaje
            isClickable = reintentar
            contentDescription =
                if (reintentar) "$mensaje ${getString(R.string.action_retry)}" else mensaje
        }
    }

    private fun detenerEscuchaAlarmas() {
        escuchaAlarmas?.let { escucha -> referenciaAlarmas?.removeEventListener(escucha) }
        escuchaAlarmas = null
        referenciaAlarmas = null
    }

    private data class CandidataAlarma(
        val marcaTiempo: Long,
        val medicamento: String,
        val dosis: String,
        val zona: TimeZone,
        val calendario: Calendar,
    )
}

# MedAlarm — Especificación de animaciones (Android XML + Kotlin)

> **Destinatario:** Codex, trabajando sobre la aplicación Android existente.  
> **Alcance:** inicio de sesión, dashboard, gestión de alarmas, historial, informes y conexión del pastillero Arduino UNO mediante Bluetooth HC-05, únicamente donde esas vistas o interacciones ya existan o vayan a implementarse.  
> **Tecnología:** Android Views (layouts XML + Kotlin). **No Jetpack Compose.**  
> **Prioridad absoluta:** consistencia con `UI_SPEC_MEDALARM.md`, funcionalidad, accesibilidad, respuesta inmediata y simplicidad (YAGNI).

## 0. Objetivo y jerarquía de decisiones

Las animaciones **refuerzan** las acciones y cambios de estado; nunca deben distraer de una alarma, ocultar un error, retrasar una interacción con el pastillero ni cambiar el diseño visual aprobado.

Antes de programar, **inspeccionar** los componentes, layouts, IDs, navegación, dependencias y versión mínima Android presentes en el repositorio. Aprovechar los componentes y estilos existentes y añadir el mínimo código necesario. Esta especificación **no autoriza** crear pantallas nuevas, menús, interruptores, popovers, un sistema de navegación nuevo ni reconstruir las vistas de referencia.

Orden de prioridad ante conflictos:

1. Funcionamiento de alarmas, registro de tomas, Firebase, conexión HC-05, autenticación y accesibilidad.
2. Estados inmediatamente comprensibles: error, éxito, conectando, desconectado, carga y acciones deshabilitadas.
3. Consistencia con el sistema visual del login y dashboard: gradientes morados, tarjetas, tipografía, iconos outlined, mismos radios y tuteo.
4. Animaciones sobrias que permitan entender qué ha cambiado.
5. Adornos visuales, que pueden omitirse por completo.

No copiar animaciones de otros sistemas visuales solo por moda. Las ilustraciones, ondas y gradientes del fondo permanecen **estáticos**.

---

## 1. Evaluación de las 20 propuestas

**Viabilidad:** «Sí» indica que se puede implementar con Android Views, XML y/o Kotlin; **no** implica que debamos hacerlo. «Condicional» significa que se aplicará solo si la interacción existe en la app, sin crearla para justificar una animación.

| N.º | Propuesta recibida | ¿Factible con Kotlin + XML? | Decisión | Evaluación aplicada a MedAlarm |
|---:|---|---|---|---|
| **1** | Ease-out for entrances | Sí | **Adoptar** | Entrada suave de tarjetas y bloques cuando aparecen por primera vez; no animar toda la pantalla cada vez que vuelve a primer plano. |
| **2** | Dropdowns at 150–250 ms | Sí | **Adoptar condicional** | Solo para desplegables que realmente existan en formularios de alarmas o filtros. Usar **200 ms** como referencia cuando se controle la animación; si el componente nativo gestiona su animación, respetarla. |
| **3** | Stagger list rows by 30 ms | Sí | **Adoptar condicional** | Filas visibles iniciales de alarmas o historial con **30 ms** entre ellas, máximo **6 filas**. No repetir al hacer scroll ni al recibir cada actualización de Firebase. |
| **4** | Scale to 0.97 on press | Sí | **Adoptar** | Retroalimentación sutil para botones principales y tarjetas pulsables: escala **1 → 0,97**, retorna a **1** al soltar o cancelar. Mantener ripple y accesibilidad. |
| **5** | Stretch moving knobs 12% | Sí, con vista personalizada | **Descartar** | «Knobs» no corresponden a un control definido del login/dashboard ni se ha especificado qué perilla se estiraría. Añadirlo sería inventar interfaz. |
| **6** | Stagger as details settle | Técnicamente sí | **Descartar** | «As details settle» no identifica elementos ni un cambio de estado concreto; se superpone con la regla 3 sin aportar una especificación comprobable. |
| **7** | Arcs for object movement | Sí, mediante `Path`/`ObjectAnimator` | **Descartar** | No hay objetos que deban desplazarse por trayectorias curvas para gestionar alarmas o Bluetooth. Distraería del contenido clínico/operativo. |
| **8** | Pulse to confirm success | Sí | **Adoptar** | **Una sola** pulsación discreta en icono/indicador al guardar una alarma o confirmar emparejamiento/conexión. Debe acompañarse de texto visible que confirme la operación. |
| **9** | Ease-in-out for moves | Sí | **Adoptar condicional** | Solo para un elemento que realmente cambie de posición o tamaño (p. ej., expandir un detalle existente). No mover datos críticos por estética. |
| **10** | Start popovers at 0.95 | Sí | **Descartar** | No se necesitan popovers personalizados en las vistas definidas. Preferir diálogos y menús Android existentes cuando correspondan. |
| **11** | Enter from the trigger | Sí, pero requiere anclaje y geometría | **Descartar** | Depende de overlays/popovers anclados que no están especificados. |
| **12** | Exit to the trigger | Sí, pero requiere anclaje y geometría | **Descartar** | Mismo motivo que 11. |
| **13** | No bounce on everyday UI | Sí (regla de diseño) | **Adoptar** | Sin rebotes, muelles pronunciados u overshoot en campos, botones, tarjetas, errores y navegación normal. |
| **14** | Don't tween while held | Sí (regla de interacción) | **Adoptar** | Durante una pulsación prolongada o arrastre, no mantener una animación automática continua. La escala del botón llega una vez a 0,97 y queda estable mientras esté presionado. |
| **15** | Make exits 20% shorter | Sí | **Adoptar** | Una salida equivalente dura **0,8 ×** la entrada (ej. 220 → **176 ms**). Solo para pares de transiciones bajo control de la app; no alterar transiciones del sistema. |
| **16** | Overlap old & new states | Sí | **Adoptar condicional** | Crossfade **breve** entre estados visuales compatibles, como indicador de conexión o el contenido de «Próxima alarma». El estado correcto debe mantenerse legible; no superponer mensajes contradictorios. |
| **17** | Masks for reveals & colour | Sí, con clip/path/transiciones | **Descartar** | Mayor complejidad y mantenimiento; no mejora las tareas principales frente a un simple fade o expansión. |
| **18** | Crossfade icons + blur | Crossfade: sí; blur: más costoso y dependiente del dispositivo/API | **Descartar como conjunto** | El desenfoque no aporta valor ni es necesario. Si solo cambia un icono de estado, usar el crossfade simple de la regla 16, **sin blur**. |
| **19** | Rubber band past bounds | Sí, con implementación personalizada | **Descartar** | Deformaciones/rebotes fuera de límites inadecuados para formularios, alarmas, historiales y confirmaciones de medicación. |
| **20** | Reuse the same layoutId | No como norma de Android Views tradicional | **Descartar** | `layoutId` en este contexto pertenece a otros enfoques (p. ej. Compose MotionLayout). En XML se usan **IDs estables** (`android:id`) para los elementos, pero no se añade un sistema MotionLayout para obtener esta regla. |

**Resultado:** 10 reglas aceptadas (4 de ellas condicionadas al componente/estado real) y 10 descartadas. **Solo las reglas aceptadas de las secciones siguientes son instrucciones de implementación.** La tabla anterior documenta las decisiones, no habilita a Codex a implementar las descartadas.

---

## 2. Tokens de movimiento compartidos

Definir una **única fuente de verdad** para estos valores, idealmente en un pequeño objeto Kotlin `MedAlarmMotion` junto con sus interpoladores. No duplicar números arbitrarios en cada Activity, Fragment o adapter.

| Token lógico | Valor | Uso |
|---|---:|---|
| `motion_enter_ms` | **220 ms** | Entrada de bloque visible (opacity + pequeño desplazamiento) |
| `motion_exit_ms` | **176 ms** | Salida equivalente (20 % más corta que la entrada) |
| `motion_dropdown_enter_ms` | **200 ms** | Desplegable personalizado, solo si ya existe |
| `motion_dropdown_exit_ms` | **160 ms** | Salida del mismo desplegable |
| `motion_stagger_delay_ms` | **30 ms** | Intervalo entre filas visibles iniciales |
| `motion_stagger_max_rows` | **6** | Límite para impedir cascadas largas |
| `motion_press_down_ms` | **80 ms** | Llegar a escala 0,97 |
| `motion_press_up_ms` | **120 ms** | Volver a escala 1,00 |
| `motion_press_scale` | **0,97** | Escala de la superficie interactiva, no del texto aislado |
| `motion_move_ms` | **200 ms** | Desplazamientos/cambios de tamaño puntuales |
| `motion_crossfade_ms` | **160 ms** | Sustitución visual de estado, con solapamiento temporal |
| `motion_success_pulse_ms` | **240 ms** | Una pulsación completa, nunca en bucle |
| `motion_success_peak_scale` | **1,04** | Pico máximo del icono de confirmación |
| `motion_enter_offset_dp` | **8 dp** | Desplazamiento vertical máximo de entrada |

### Interpolación obligatoria

| Intención | Interpolador Android recomendado | Motivo |
|---|---|---|
| **Entrar** | `DecelerateInterpolator()` | Movimiento rápido al comenzar y suave al terminar (ease-out) |
| **Salir** | `AccelerateInterpolator()` | Se retira sin parecer lento |
| **Mover/cambiar dimensiones** | `AccelerateDecelerateInterpolator()` | Arranque y parada controlados (ease-in-out) |
| **Pulsación y retorno** | `DecelerateInterpolator()` | Respuesta clara, sin rebote |
| **Crossfade** | `LinearInterpolator()` | Cambio de opacidad sencillo y predecible |

Usar **`alpha`, `translationX/Y`, `scaleX/Y`** por defecto. Evitar animar reiteradamente `layoutParams`, sombras, gradientes, blur o paths grandes si no existe una necesidad explícita. Mantener fondos y ondas decorativas inmóviles.

---

## 3. Reglas concretas por superficie y evento

### 3.1. Login

**Entrada inicial (reglas 1 y 13):**

- Solo la **tarjeta completa del formulario** puede hacer fade `alpha 0 → 1` más `translationY +8dp → 0` durante **220 ms** con ease-out.
- **No** animar separadamente correo, contraseña, enlace, separador y botones; evita una cascada excesiva en una pantalla de autenticación.
- La barra de estado, fondo ondulado y gradiente permanecen estáticos.
- No reiniciar esta animación al enfocar un `EditText`, mostrar el teclado, volver de segundo plano o cambiar el estado del formulario.

**Botones `Iniciar sesión` y `Crear cuenta` (reglas 4 y 14):**

- Al presionar: animar **una vez** a `scaleX = scaleY = 0,97` en **80 ms**.
- Mientras el dedo permanezca: mantener 0,97, **sin loop** ni nuevas interpolaciones.
- Al levantar o recibir `ACTION_CANCEL`: volver a `1,00` en **120 ms**.
- Mantener el fondo, borde, ripple, texto e icono de la especificación visual. No ocultar ni sustituir el feedback estándar.
- No aplicar la escala a un botón deshabilitado ni interferir con `onClick`, lector de pantalla o navegación por teclado.

**Errores de credenciales:**

- El error debe aparecer **inmediatamente cuando se conoce el resultado** (tras la respuesta real de autenticación o validación local pertinente). Se permite fade-in **≤160 ms**, sin deslizamientos ni sacudidas.
- El mensaje permanece hasta que el usuario lo corrija o el flujo cambie; **no** usar un Toast como única información de error, pulsaciones ni autoocultado por temporizador.
- Conservar borde/fondo de error, texto de alto contraste y posibilidad de enfocarlo/anunciarlo mediante accesibilidad. No mostrar «Contraseña incorrecta» antes de un intento fallido.
- Nunca animar cada pulsación de teclado ni realizar shake del campo de contraseña.

**Indicador de carga:**

- Si ya existe, usar el componente Android habitual (p. ej. `ProgressBar`) durante la autenticación; deshabilitar acciones duplicadas. No crear una animación ornamental que haga más difícil detectar una operación pendiente.

### 3.2. Dashboard

- La cabecera ondulada, saludo e ilustración del pastillero **no se mueven**.
- Las **cuatro tarjetas** (`Alarmas`, `Historial`, `Informes`, `Dispositivo`) pueden aparecer como **un grupo** en 220 ms al entrar por primera vez. No introducir un stagger entre las cuatro tarjetas si ya se ve la información inmediatamente; prima estabilidad visual.
- Cada tarjeta pulsable y el botón `Nueva alarma` comparten la respuesta **0,97** de los botones; en las tarjetas se escala toda su superficie, no el icono o texto individual.
- La tarjeta `Próxima alarma` puede entrar al mismo tiempo que el resto del contenido, sin ocultar hora, dosis o nombre mientras la pantalla ya es interactiva.
- Si Firebase actualiza la próxima alarma y hay transición entre **dos estados visuales compatibles**, aplicar crossfade corto **160 ms** (regla 16). Evitar mostrar dos horarios superpuestos ilegibles. Si hay una alarma inminente, actualizar el texto **inmediatamente** sin retraso visual.
- **No** hacer que el botón «Nueva alarma» pulse permanentemente ni que el pastillero se balancee.

### 3.3. Alarmas e historial

**Filas iniciales (regla 3):**

- Aplicar **solo cuando se muestran por primera vez filas ya cargadas**. Máximo 6 filas **visibles** en el primer render; retrasos `0, 30, 60, 90, 120, 150 ms`.
- Cada fila entra con **alpha 0 → 1** y, opcionalmente, desplazamiento máximo **+8dp → 0** usando ease-out de **220 ms**.
- Las filas que no están visibles se muestran normalmente al hacer scroll; **no** repetir la animación en `onBindViewHolder`, al reciclar vistas, al volver a la pantalla ni por cada escucha de Firebase.
- Si se usa `RecyclerView`, elegir **una sola** estrategia para cada evento: stagger inicial **o** `ItemAnimator` de inserción/eliminación. No superponer dos animaciones de entrada sobre la misma fila.
- En inserciones/eliminaciones posteriores usar preferentemente el `RecyclerView.ItemAnimator` existente, sin añadir una capa nueva si ya funciona adecuadamente.

**Filtros y desplegables (regla 2):**

- Solo si hay selectores reales (p. ej., frecuencia de alarma, filtro de historial) y se controla su animación: apertura **200 ms**, cierre **160 ms**.
- No crear un menú personalizado si `Spinner`, `PopupMenu`, `AutoCompleteTextView` o el componente Material ya cumple la tarea y aporta comportamiento accesible.
- No animar los elementos del desplegable uno por uno.

**Expansión de detalles (regla 9):**

- Solo para una sección expandible que ya exista. `AccelerateDecelerateInterpolator()` y **200 ms** de movimiento; si puede resolverse mostrando/ocultando contenido sin animación, preferir lo más sencillo.
- No aplicar cambios de tamaño animados a toda la lista ni interrumpir su scroll.

### 3.4. Confirmaciones y dispositivo HC-05

**Éxito (regla 8):**

- Tras **confirmación real** de guardado de una alarma o de conexión/emparejamiento del HC-05, animar **solo el icono/indicador de éxito** en `1,00 → 1,04 → 1,00`, una vez, duración total **240 ms**.
- Mostrar además un mensaje claro en texto, siguiendo el tuteo de la guía visual: «Alarma guardada» o «Pastillero conectado» según el evento real.
- **Nunca** pulsar por una operación pendiente ni anticipar éxito solo porque se envió una orden Bluetooth; distinguir «Conectando…» de «Conectado» y evitar inferir que el medicamento fue tomado.
- Si la acción falla, usar el estado de error correspondiente, sin pulso de éxito.
- No aplicar la pulsación a una alarma activa (podría parecer una confirmación de toma).

**Cambio de conexión (regla 16):**

- Para iconos de un mismo indicador de conexión, se puede cruzar opacidad `antiguo 1 → 0 / nuevo 0 → 1` en **160 ms**.
- El texto «Desconectado» / «Conectando…» / «Conectado» se actualiza **sin espera**; los cambios importantes deben ser perceptibles sin depender de animación o color.
- No utilizar desenfoque, halo recurrente o trayectorias curvas para representar Bluetooth.

### 3.5. Salidas y cambios visuales (reglas 15 y 16)

- Al quitar un panel que entró en **220 ms**, usar **176 ms** de salida cuando sea una transición propia.
- Si entró en **200 ms**, salir en **160 ms**. Si entró con un crossfade de **160 ms**, no hay obligación de hacerlo asimétrico si afecta legibilidad; la regla de 20 % se aplica a pares de entrada/salida espacialmente equivalentes.
- Cuando un contenedor sustituye un icono o bloque visual por otro **sin cambiar la intención de la pantalla**, pueden coexistir temporalmente por un crossfade. Evitar que ambos elementos sean enfocables/clicables simultáneamente.
- No aplicar transiciones largas a una alarma urgente, error, permiso Bluetooth o confirmación de medicación.
- La navegación de Android y los diálogos nativos conservan sus animaciones de plataforma, salvo una necesidad verificada.

---

## 4. Guía mínima de implementación para Codex

### 4.1. Elegir APIs simples y existentes

- **Animaciones de propiedades (`ViewPropertyAnimator`)** para entrada, salida, alpha, escala y traducción. Son compatibles con Android Views y permiten combinar varias propiedades.
- **Interpoladores estándar** `DecelerateInterpolator`, `AccelerateInterpolator`, `AccelerateDecelerateInterpolator`, `LinearInterpolator`; no añadir librerías para easing.
- **`ObjectAnimator` / `AnimatorSet`** solo si una animación realmente necesita varias fases, como el pulso de éxito.
- **`RecyclerView.ItemAnimator`** existente para inserciones/eliminaciones; no reimplementar todo el mecanismo de la lista.
- **XML:** puede contener estados visuales, ripple, `selector`, dimensiones, y opcionalmente animadores XML si ya existe ese patrón. No introducir MotionLayout ni Compose únicamente por estas reglas.
- Crear **como máximo un helper pequeño** de movimiento compartido (p. ej. `MedAlarmMotion.kt`) si evita duplicación. No construir un motor de animaciones, DSL ni catálogo de clases por pantalla.

### 4.2. Ejemplo orientativo — helper compacto (Kotlin)

El siguiente código demuestra el enfoque, **no** debe copiarse sin adaptarlo al ciclo de vida y a los componentes reales del repositorio:

```kotlin
import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import android.view.animation.DecelerateInterpolator

object MedAlarmMotion {
    const val ENTER_MS = 220L
    const val EXIT_MS = 176L
    const val STAGGER_MS = 30L
    const val PRESS_SCALE = 0.97f

    private fun animatorsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ValueAnimator.areAnimatorsEnabled()

    fun enter(view: View) {
        view.animate().cancel()
        if (!animatorsEnabled()) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }

        val offsetPx = 8f * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = offsetPx
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ENTER_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun setPressedScale(view: View, isPressed: Boolean) {
        view.animate().cancel()
        val target = if (isPressed && animatorsEnabled()) PRESS_SCALE else 1f
        if (!animatorsEnabled()) {
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(if (isPressed) 80L else 120L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}
```

**Integración del estado presionado** en controles que ya son pulsables:

```kotlin
button.setOnTouchListener { view, event ->
    when (event.actionMasked) {
        android.view.MotionEvent.ACTION_DOWN -> MedAlarmMotion.setPressedScale(view, true)
        android.view.MotionEvent.ACTION_UP,
        android.view.MotionEvent.ACTION_CANCEL -> MedAlarmMotion.setPressedScale(view, false)
    }
    false // Dejar que el botón siga manejando click, ripple y accesibilidad.
}
```

**Precauciones obligatorias:**

- El listener anterior es **ilustrativo**: en componentes con listeners táctiles propios, no sustituir ni interferir con su comportamiento; centralizar la respuesta de pulsación con el mecanismo apropiado para ese control.
- Una vista puede recibir `ACTION_CANCEL` cuando el usuario comienza a deslizar. Siempre recuperar escala **1,00**, incluso si se abandona la pantalla; cancelar animadores pendientes al desmontar/destruir la vista cuando corresponda.
- Evitar que se acumulen animadores por varios toques rápidos (`animate().cancel()` antes de reemplazar la animación).
- No lanzar `enter()` en cada render, observación de Firebase o reanudación: **solo en la primera aparición real** de ese elemento.
- No confundir **duraciones previstas** con tiempo de red: el login, Firebase y Bluetooth continúan según sus callbacks/estados reales.

### 4.3. Animar filas sin perjudicar RecyclerView

Si se implementa el stagger inicial en una lista existente, aplicarlo **solo una vez después de que sus primeras filas estén colocadas**, con máximo seis hijos visibles y `delay = índiceVisible × 30 ms`. El `RecyclerView` puede reciclar hijos, así que el adapter debe devolver siempre `alpha = 1`, `scaleX/Y = 1`, `translationY = 0` en estados normales y **jamás depender de `adapterPosition` para repetir una animación**.

No disparar animación por cada `notifyDataSetChanged`, `submitList`, rebind ni por el simple hecho de hacer scroll. Si complica el adapter o crea parpadeos, **omitir el stagger**: la ausencia de animación es preferible a una lista inestable.

### 4.4. Accesibilidad y ajustes del sistema

- Respetar la escala de duración de animadores de Android; si el sistema las deshabilita, **mostrar inmediatamente el estado final visible**. En API 26+ puede consultarse `ValueAnimator.areAnimatorsEnabled()`. En versiones anteriores, las animaciones de propiedades deben seguir el escalado normal del sistema; no forzarlas mediante temporizadores manuales.
- Si una animación se cancela, restaurar valores finales coherentes para evitar tarjetas transparentes, botones pequeños o capas superpuestas atrapadas.
- No animar continuamente elementos de lectura, avisos de medicación o estados Bluetooth.
- Mantener objetivos táctiles **≥48dp**, contraste, orden de foco, descripciones accesibles y semántica de error/éxito. No depender solo de color o movimiento.
- Con lector de pantalla, teclado o navegación asistida, los controles deben seguir siendo operables sin que un gesto sea requisito.
- Probar en dispositivos modestos: preferir alpha/translation/scale a efectos de blur o redibujado pesado.

---

## 5. Orden de implementación (YAGNI)

Implementar solo lo que exista en el proyecto, en este orden:

1. **Sistema de tokens de movimiento** (valores de duración/curvas compartidos; sin duplicación).
2. **Feedback de pulsación 0,97** para los dos botones de login y controles principales del dashboard; sin interferir con `onClick` o ripple.
3. **Entrada suave de la tarjeta de login** y, si visualmente aporta valor, entrada agrupada del dashboard.
4. **Error de autenticación claro e inmediato**, sin animación innecesaria; si hace falta, fade-in ≤160 ms.
5. **Pulso único de éxito** solamente en resultados confirmados de guardado y Bluetooth.
6. **Crossfade de estados** únicamente donde un indicador o dato existente cambia visualmente.
7. **Dropdowns y stagger de listas** solo si el proyecto realmente contiene esas interacciones y no introducen parpadeo ni complejidad injustificada.

Las reglas aceptadas **no obligan** a añadir una animación en todas las pantallas. No sacrificar estabilidad por completar una checklist decorativa.

---

## 6. Criterios de aceptación para revisión

- [ ] Las pantallas mantienen la misma paleta, radios, textos y familia de iconos definida en `UI_SPEC_MEDALARM.md`.
- [ ] Todas las entradas equivalentes usan una curva y una duración coherentes: 220 ms/ease-out; salidas propias 176 ms.
- [ ] Los botones y tarjetas seleccionados escalan a 0,97 y vuelven a 1,00 al soltar/cancelar. No quedan deformados tras interrupciones.
- [ ] Ningún botón pierde ripple, click, foco accesible o retroalimentación por el efecto de pulsación.
- [ ] No hay bounce, overshoot, rubber-band, blur, paths decorativos, ondas en movimiento o loops ornamentales.
- [ ] Los errores del login son legibles y persisten hasta su resolución, sin ocultarse automáticamente.
- [ ] El pulso de éxito solo ocurre **después** de una confirmación real. Se acompaña de mensaje textual.
- [ ] Los cambios importantes (alarma próxima, conexión, autenticación) se reflejan inmediatamente y no esperan a que finalice una animación.
- [ ] Si hay listas, no se repite la entrada al hacer scroll ni después de recibir actualizaciones de Firebase; máximo seis filas y 30 ms de separación.
- [ ] Las animaciones pueden desactivarse desde el sistema y la vista final aparece correctamente, sin elementos invisibles.
- [ ] No se añaden dependencias, vistas o animaciones descartadas para «cumplir» la especificación.
- [ ] Se prueba al menos: login exitoso/fallido, teclado visible, regreso a pantalla, clics rápidos, scroll de listas, desconexión Bluetooth, recreación de Activity y animaciones desactivadas.

---

## 7. Fuentes técnicas de referencia

Se incluyen para confirmar los mecanismos Android, **no** para sustituir las reglas de MedAlarm:

- **Android Developers — ViewPropertyAnimator:** https://developer.android.com/reference/android/view/ViewPropertyAnimator
- **Android Developers — Property Animation Overview:** https://developer.android.com/develop/ui/views/animations/prop-animation
- **Android Developers — ValueAnimator.areAnimatorsEnabled (API 26+):** https://developer.android.com/reference/android/animation/ValueAnimator#areAnimatorsEnabled()
- **Android Developers — RecyclerView.ItemAnimator:** https://developer.android.com/reference/androidx/recyclerview/widget/RecyclerView.ItemAnimator
- **Android Developers — Animate layout changes using a transition:** https://developer.android.com/develop/ui/views/animations/transitions

> **Nota final para Codex:** este documento complementa `UI_SPEC_MEDALARM.md`: **no lo reemplaza**. Si una animación entra en conflicto con el diseño, la claridad de un error, el estado real de Bluetooth o una operación de medicación, **reducirla u omitirla**.

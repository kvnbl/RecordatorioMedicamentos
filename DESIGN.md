# MedAlarm — Especificación visual y de implementación (Android XML + Kotlin)

> **Destinatario:** Codex, trabajando dentro del repositorio Android existente.  
> **Objetivo:** reproducir con alta fidelidad visual las dos pantallas de referencia —**Inicio de sesión** y **Dashboard**— y, ante cualquier conflicto, mantener un **sistema visual y lingüístico coherente**.  
> **Tecnología obligatoria:** Android Views con layouts **XML** y lógica **Kotlin**. **No utilizar Jetpack Compose, WebView ni una captura como fondo de una pantalla interactiva.**

## 0. Referencias y prioridades

Las imágenes son la **fuente de verdad visual**. Deben revisarse antes de escribir código:

- `referencias/login.png` — Login púrpura con estado de contraseña incorrecta.
- `referencias/dashboard.png` — Inicio de la aplicación, tarjetas de accesos y próxima alarma.

El ZIP entregado junto a este documento contiene ambas imágenes. Si este `.md` se copia individualmente al repositorio, también hay que copiar las referencias o facilitarle a Codex sus rutas reales.

**Orden de decisión cuando dos objetivos entren en conflicto:**

1. Mantener **funcionalidad, seguridad, legibilidad y accesibilidad** en un Android real.
2. Mantener **consistencia entre pantallas**: mismos tokens, componentes, estados y convenciones.
3. Conservar la **composición visual** de las referencias: estructura, jerarquía, ondas, tarjetas, colores y espaciamiento.
4. Ajustar detalles de píxeles, sombras, iconos y proporciones mediante comparación de capturas.

Las referencias son **mockups generados**, no capturas de un layout Android ejecutable. Sus píxeles incluyen marco del teléfono, barra de estado y fondo externo. **No convertir píxeles de la imagen directamente a `dp`**, ni dibujar un teléfono dentro de la app. Las dimensiones propuestas más adelante son **valores iniciales aproximados** para un dispositivo de ~393 dp de ancho; validarlas contra el emulador y ajustarlas.

**Alcance:** construir/pulir dos vistas y sus estados visibles. Inspeccionar el proyecto antes de decidir clases, navegación, temas o dependencias. Conservar la autenticación, Firebase Realtime Database, conexión Bluetooth HC-05 y lógica ya existente; no reescribirlas para implementar el aspecto visual.

---

## 1. Reglas de consistencia — decisiones obligatorias

| Propuesta | Decisión | Regla aplicable |
|---|---|---|
| **1. Usa el mismo componente** | **Sí, por función.** | Botones primarios comparten una implementación/estilo; botones secundarios otra; campos de entrada comparten otra; cuatro accesos del dashboard usan una única plantilla. Las variantes solo cambian mediante propiedades/tokens. No forzar un componente idéntico para funciones distintas. |
| **2. Mismo nombre para el texto del componente** | **Sí, con semántica.** | Una misma acción o destino conserva nombre, capitalización y terminología en todas las pantallas; no reutilizar textos de acciones diferentes por estética. Centralizar todas las cadenas en `strings.xml`. |
| **3. Una familia de iconos, outline y mismo grosor** | **Sí.** | Usar exclusivamente **Material Symbols Outlined** (`FILL=0`, peso visual equivalente a `wght=400`) exportados como VectorDrawable oficial de Google; todos los glifos funcionales miden **24 dp**. No mezclar emojis, iconos rellenos, Font Awesome, Lucide y símbolos Unicode. Excepción: **ilustración decorativa del pastillero**, que no es un icono de interfaz. |
| **4. Mismos radios** | **Sí, por escala fija.** | No un único radio universal. Usar `radius_card=24dp`, `radius_field=14dp`, `radius_small=12dp` y `radius_pill=28dp` para todos sus respectivos componentes. Ajustar ambos diseños **globalmente** si la comparación visual demuestra que alguno debe cambiar. |
| **5. Un solo tratamiento verbal: «tú»** | **Sí, obligatorio.** | Tuteo en todos los textos dirigidos al usuario. Usar «Gestiona», «Consulta», «Revisa», «Conecta», «Verifica», «Inténtalo», «¿Olvidaste…?». Nunca alternar con «usted», «su», «ingrese», «verifique», «inténtelo» al dirigirse a la persona. Los textos neutros como «Iniciar sesión» y «Crear cuenta» son válidos. |

### 1.1. Diccionario canónico de la interfaz

Usar exactamente estas etiquetas en los destinos y acciones correspondientes:

| Clave sugerida | Texto visible |
|---|---|
| `nav_alarms` | `Alarmas` |
| `nav_history` | `Historial` |
| `nav_reports` | `Informes` |
| `nav_device` | `Dispositivo` |
| `action_new_alarm` | `Nueva alarma` |
| `section_next_alarm` | `Próxima alarma` |
| `action_sign_in` | `Iniciar sesión` |
| `action_create_account` | `Crear cuenta` |
| `action_forgot_password` | `¿Olvidaste tu contraseña?` |
| `field_email` | `Correo electrónico` |
| `field_password` | `Contraseña` |
| `alarm_today` | `Hoy` |
| `hero_greeting` | `Hola, %1$s` |

«Pastillero» designa el equipo físico dentro de descripciones («Conecta tu pastillero»), mientras que **Dispositivo** es el nombre de su apartado. «Toma» es el acto de consumir medicamento; «Alarma» es el recordatorio programado. No sustituir un concepto por otro arbitrariamente.

---

## 2. Sistema visual compartido — design tokens

### 2.1. Paleta

Los colores son **aproximaciones extraídas visualmente y de muestras de los mockups**. Refinarlos a partir de capturas reales, pero **no crear colores ligeramente distintos en cada pantalla**.

| Token sugerido | HEX inicial | Uso |
|---|---|---|
| `brand_purple_light` | `#AD88F4` | extremo claro del encabezado |
| `brand_purple_mid` | `#986FEF` | centro de gradiente del encabezado |
| `brand_purple_dark` | `#643CD3` | extremo oscuro del encabezado |
| `brand_primary` | `#6138CB` | acciones, enlaces e iconos principales |
| `brand_primary_pressed` | `#4922B2` | estado presionado |
| `brand_button_light` | `#7549DF` | inicio del gradiente de botones principales |
| `brand_button_dark` | `#5426BF` | final del gradiente de botones principales |
| `wave_lavender` | `#E6DBFC` | onda lila clara del encabezado |
| `wave_lavender_secondary` | `#C7B0F6` | segunda tonalidad de la onda |
| `page_background` | `#FCFAFF` | fondo principal blanco cálido |
| `surface_primary` | `#F9F7FE` | tarjetas y formulario |
| `surface_lavender` | `#EFE9FE` | tarjeta Alarmas y panel Próxima alarma |
| `surface_blue` | `#EAF2FE` | tarjeta Historial |
| `surface_mint` | `#E6F7F3` | tarjeta Informes |
| `surface_peach` | `#FFF2E4` | tarjeta Dispositivo |
| `text_primary` | `#151235` | títulos y valores |
| `text_secondary` | `#596084` | descripciones y datos secundarios |
| `field_stroke` | `#B9B3D5` | contorno de entradas en reposo |
| `divider_lavender` | `#D8CFF5` | separadores |
| `error_red` | `#D30A20` | texto e icono de error |
| `error_surface` | `#FCE3E9` | bloque de error |
| `feature_blue` | `#1378BC` | icono de Historial |
| `feature_green` | `#13A160` | icono de Informes |
| `feature_orange` | `#E67C13` | icono de Dispositivo |
| `white` | `#FFFFFF` | texto sobre botones / superficies claras |

**No** pintar de azul el botón de login ni de turquesa «Crear cuenta»: ambos mockups ahora usan **morado como acción y color de marca**. El azul, verde y naranja quedan como colores semánticos de las tarjetas del dashboard.

### 2.2. Tipografía

- Una familia para toda la app. El tema actual usa Google Sans; conservar esa familia en todas las pantallas.
- Título de pantalla: **22 sp / bold (700)**. Título de sección o tarjeta: **18 sp / bold (700)**.
- Texto principal, etiquetas de campos y acciones: **16 sp**; texto secundario, subtítulos pequeños, estados y hints: **14 sp / regular (400)**.
- Excepciones de visualización con función explícita: hora de próxima alarma **24 sp / bold**, porcentaje principal de Informes **32 sp / bold**, rueda de hora/minutos **38 sp / bold** y periodo a. m./p. m. **23 sp**.
- Centralizar tamaños y pesos en `TextAppearance.MedAlarm.*` y `dimens.xml`; no introducir tamaños arbitrarios por pantalla. Las excepciones deben tener un token semántico propio.
- Evitar textos renderizados dentro de imágenes, `textSize` en `dp`, o fuentes distintas para cada pantalla.
- Los tamaños son bases en `sp`: respetar la escala del sistema y permitir aumento de fuente sin recortar textos; comprobar al menos **100 % y 130 %**.

### 2.2.1. Reglas globales de iconos y contenedores

- Todos los iconos funcionales de la app usan **Material Symbols Outlined**, `wght=400`, `FILL=0`, exportados desde el repositorio oficial de Google como VectorDrawable. Usar **24 dp** para cada glifo; los botones mantienen una zona táctil mínima de **48 dp**. El selector de contraseña también usa los vectores Material Symbols del proyecto.
- Tarjetas, alertas, paneles con fondo y botones compuestos usan **16 dp de padding interior** en sus cuatro lados. Los chips y controles compactos son la única excepción y comparten **12 dp horizontal / 6 dp vertical**.
- El espacio entre el icono y el texto de un botón compuesto es **8 dp**. No cambiar el tamaño del glifo para resolver espacio: adaptar el contenedor o el flujo del texto.
- La ilustración decorativa del pastillero y el icono de launcher no son iconos funcionales y quedan fuera de la regla de 24 dp.

### 2.3. Geometría y elevación

Definir recursos en `res/values/dimens.xml` y **reutilizarlos**:

```xml
<dimen name="ui_space_4">4dp</dimen>
<dimen name="ui_space_8">8dp</dimen>
<dimen name="ui_space_12">12dp</dimen>
<dimen name="ui_space_16">16dp</dimen>
<dimen name="ui_space_20">20dp</dimen>
<dimen name="ui_space_24">24dp</dimen>
<dimen name="ui_space_32">32dp</dimen>
<dimen name="ui_radius_small">12dp</dimen>
<dimen name="ui_radius_field">14dp</dimen>
<dimen name="ui_radius_card">24dp</dimen>
<dimen name="ui_radius_pill">28dp</dimen>
<dimen name="ui_touch_target">48dp</dimen>
<dimen name="ui_field_height">50dp</dimen>
<dimen name="ui_button_height">52dp</dimen>
<dimen name="ui_page_padding">16dp</dimen>
<dimen name="ui_container_padding">16dp</dimen>
<dimen name="ui_icon_size">24dp</dimen>
```

- Tarjetas principales: esquinas **24 dp**, borde muy ligero o ausente, sombra lila **difusa y sutil**. No sombras grises intensas ni bordes negros.
- Inputs: **14 dp**, trazo **1–1,5 dp** y superficie blanco-lila.
- Botones principales: píldora de **28 dp** y gradiente morado; botones secundarios: mismo radio y borde morado aprox. **1,5 dp**.
- Los iconos de acceso pueden ir sobre pequeños fondos de **12–14 dp** de radio. Usar iconografía de **24–28 dp**, sin variar el estilo de trazo.
- Espacio horizontal de pantalla: **16 dp**. Separación de tarjetas de cuadrícula: **12 dp**. Separaciones interiores: múltiplos de **4 dp**.
- **No** imitar las medidas en píxeles del marco del móvil. Mantener proporciones y adaptar a densidad, orientación y altura real.

### 2.4. Fondo y ondas: componente visual único

Ambas vistas deben usar **la misma implementación** de fondo decorativo, por ejemplo `MedAlarmBackgroundView` o un conjunto de drawables reutilizables.

Composición de arriba a abajo:

1. **Banda superior** con gradiente **lila claro (izquierda) → morado saturado (derecha)**. La barra de estado real del sistema aparece encima con iconos blancos.
2. **Onda ancha lila pálido** que cruza la pantalla alrededor del primer sexto de altura. Su silueta es orgánica: sube ligeramente en el lado izquierdo, desciende en el centro y vuelve a ascender suavemente hacia el derecho.
3. **Gran región lavanda translúcida** bajo la onda superior, con una segunda curva blanca de transición hacia el contenido.
4. Fondo del cuerpo `page_background`; degradados lavanda tenues junto a los extremos, nunca ruido ni textura.

El **dashboard** muestra este fondo detrás de su saludo y encabezado. El **login** conserva **la misma banda y ondas**, pero sin ningún título, logotipo, ilustración ni eslogan arriba. Se permite variar la **altura de la región disponible** según pantalla, no su paleta, dirección de gradiente ni lenguaje de curvas.

Implementar ondas escalables con **VectorDrawable/Path** o un único `View` Kotlin que dibuje con `Canvas` y `Path`. Es preferible a introducir imágenes gigantes rasterizadas. Si se usan gradientes, deben estar centralizados en drawables/recursos y comportarse bien al escalar. **No duplicar dos fondos con valores independientes.**

---

## 3. Pantalla A — Inicio de sesión

**Referencia:** `referencias/login.png`.

### 3.1. Estructura visual exacta

- Pantalla **sin AppBar**, sin flecha de volver, sin logotipo ni subtítulo.
- Barra de estado Android real sobre franja superior morada; iconos claros. **No dibujar hora/batería manualmente.**
- Parte superior con ondas moradas y espacio limpio; la tarjeta del formulario flota delante del fondo y comienza aproximadamente después de la cabecera ondulada.
- **Una única tarjeta principal**, casi todo el ancho útil, de superficie blanco-lila translúcida, radio **24 dp**, sombra tenue. Centrarla en el espacio disponible; debe quedar aire suficiente arriba y abajo.
- Padding interno de tarjeta ~**20 dp** y separación de secciones de **16–24 dp**. No usar layouts con posiciones absolutas o alturas de pantalla fijas.

**Secuencia obligatoria dentro de la tarjeta (arriba → abajo):**

1. **Etiqueta `Correo electrónico`.** Icono de **sobre outlined** a la izquierda, dentro de pequeño contenedor lila de esquinas suaves; texto oscuro, negrita. Alineación vertical centrada.
2. **Entrada de correo.** Campo rectangular de bordes redondeados, fondo muy claro, trazo lila grisáceo. La referencia muestra `usuario@ejemplo.com` como ejemplo; implementarlo como **hint**, no como credencial rellenada.
3. **Etiqueta `Contraseña`.** Icono de **candado outlined** con el mismo contenedor, tamaño, padding y grosor de línea que el sobre.
4. **Entrada de contraseña.** Misma base de campo que el correo. Al escribir, ocultar contenido mediante transformación de contraseña. Icono outlined de **ojo** al extremo derecho con área táctil accesible; alterna mostrar/ocultar sin borrar el valor ni mover inesperadamente el cursor.
5. **Error inline (solo cuando hay error).** Caja rosa claro, radio **12 dp**, icono outlined de advertencia rojo y texto rojo de alto contraste en una o dos líneas. La referencia muestra: `Contraseña incorrecta. Verifica tu contraseña e inténtalo nuevamente.` Este estado **no se debe mostrar antes de intentar iniciar sesión**.
6. **Botón primario `Iniciar sesión`.** Ancho del contenedor (~100 %), alto **52 dp**, forma píldora, gradiente morado claro→oscuro, texto blanco semibold e icono outlined de **entrar** en blanco a la izquierda del texto. Sombra púrpura difusa, no intensa.
7. **Enlace `¿Olvidaste tu contraseña?`.** Centrado, morado, sin recuadro; objetivo táctil ≥48 dp y estado presionado visible.
8. **Separador `o`.** Dos líneas horizontales finas lavanda a izquierda y derecha, letra minúscula centrada en morado oscuro.
9. **Botón secundario `Crear cuenta`.** Ancho completo, alto **50–52 dp**, fondo casi blanco, borde morado, esquinas de píldora, icono outlined de **persona con +** y texto morado oscuro. No debe confundirse con el botón primario.

No agregar banners inferiores, toasts persistentes, ilustraciones ni campos extra (nombre, teléfono, recordar sesión). No añadir botones sociales no presentes en la referencia.

### 3.2. Estados de interacción

| Estado | Representación | Comportamiento |
|---|---|---|
| Inicial | Dos campos vacíos con hints; **sin error**; primario visible | Foco razonable, teclado oculto hasta que se toque un campo |
| Campo enfocado | Contorno más morado y cursor visible | Mantener forma y dimensiones del campo |
| Validación local | Ayuda inmediata debajo del campo o en región de error | Correo vacío/no válido, contraseña vacía; redacción en **tú** |
| Credenciales rechazadas | **Caja rosa de error** como la referencia | El mensaje tiene que verse en pantalla, sin depender solo de `Toast` |
| Cargando | Indicador de progreso visible en el botón, impedir dobles toques | No cambiar geometría ni desplazar la interfaz |
| Mostrar contraseña | Ojo cambia de estado y campo alterna visibilidad | Mantener selección y permitir TalkBack |
| Restablecimiento | Acción sobre `¿Olvidaste tu contraseña?` | Abrir el flujo ya existente; no simular un envío correcto |

**Mensaje de demostración fiel a la imagen:** `Contraseña incorrecta. Verifica tu contraseña e inténtalo nuevamente.`  
**Mensaje de autenticación recomendado cuando el servicio no distingue el motivo:** `Correo o contraseña incorrectos. Revísalos e inténtalo nuevamente.`  
Elegir según la **información realmente conocida**. No afirmar que la contraseña es incorrecta si el error puede ser una cuenta inexistente, un fallo de red u otra causa. Usar mensajes específicos para red no disponible / demasiados intentos cuando corresponda. No revelar en logs el correo completo ni la contraseña.

### 3.3. Responsive e IME

- Contenido en `NestedScrollView` con `fillViewport=true`, o estructura equivalente, **sin recortes**.
- Anchura máxima opcional del formulario en tablets, aprox. **440 dp**, centrada.
- Cuando aparece el teclado, permitir scroll hasta el campo enfocado y el error; no tapar definitivamente acciones esenciales. Usar gestión correcta de insets / `adjustResize` según la configuración del proyecto.
- Aumento de fuente y textos de error de varias líneas deben ampliar **la altura del formulario**, no superponerse ni salirse de la tarjeta.
- Inputs con `inputType="textEmailAddress"` y `textPassword`; autofill/hints apropiados; `imeOptions` de siguiente/terminar.
- Contraste legible del texto, tamaño táctil de ojo/enlace no inferior a **48 dp**.

---

## 4. Pantalla B — Dashboard / Inicio

**Referencia:** `referencias/dashboard.png`.

### 4.1. Encabezado

- **Mismo componente de fondo y ondas** que Login, con el morado de la marca.
- Barra de estado Android real (blanca sobre morado), nunca simulada.
- Primera fila: **menú hamburguesa outlined** a la izquierda y **campana outlined** a la derecha; ambos blancos, 24 dp, áreas táctiles ≥48 dp. Un pequeño **punto rojo** aparece en campana **únicamente cuando hay notificaciones sin leer**.
- Debajo: avatar circular morado/violáceo (con icono de persona de la **misma familia outlined**) a la izquierda; a su derecha saludo oscuro de peso alto `Hola, Usuario` y texto secundario en **dos líneas**: `Gestiona tus alarmas y monitorea tu tratamiento`.
- A la derecha del encabezado, **ilustración suave del pastillero inteligente** con tapa abierta, compartimentos y pequeño símbolo Bluetooth; debe ocupar la región derecha sin competir con el saludo. Es **arte decorativo**, no un botón de emparejamiento.
- La región de saludo descansa sobre una onda lavanda muy clara que termina suavemente en el cuerpo blanco.

**Importante sobre fidelidad:** la ilustración del pastillero es un recurso artístico, no un icono estándar. Para reproducirla *idéntica* hace falta una ilustración original con transparencia. **No recortar de la captura un rectángulo con fondo y pegarlo**, porque produciría un bloque visible sobre las ondas. Si no hay asset original, crear una ilustración vectorial sencilla y coherente, y **reportar esa diferencia**. No utilizar un emoji como sustituto.

### 4.2. Cuadrícula de funciones principales

Mostrar **exactamente cuatro** tarjetas en una cuadrícula **2 columnas × 2 filas**, con el mismo componente reutilizable; gaps aproximados **12 dp**, margen exterior **16 dp** y altura inicial **76 dp** (ampliable por texto).

**Plantilla única de tarjeta:**

- Superficie pastel de categoría, radio **24 dp**.
- A la izquierda, icono outlined grande dentro de cuadrado pastel ligeramente más intenso, radio **12 dp**.
- A la derecha del icono: título oscuro bold (una línea en anchos normales) y subtítulo de una o dos líneas en gris violáceo.
- Chevron outlined hacia la derecha, tono gris-morado, en el extremo derecho.
- Toda la tarjeta es clicable, no solo el chevron; ripple suave sin alterar el radio.
- Mantener **misma altura base, padding, alineación y estilo** en las cuatro; si una tiene más texto, resolver alturas de forma equivalente.

| Orden | Título | Subtítulo exacto | Icono outlined | Fondo | Color del icono |
|---|---|---|---|---|---|
| Arriba izquierda | `Alarmas` | `Gestiona tus horarios` | Despertador (`alarm`) | lavanda `surface_lavender` | `brand_primary` |
| Arriba derecha | `Historial` | `Consulta tus tomas` | Barras estadísticas (`bar_chart`) | azul `surface_blue` | `feature_blue` |
| Abajo izquierda | `Informes` | `Revisa tu progreso` | Tendencia ascendente (`trending_up`) | menta `surface_mint` | `feature_green` |
| Abajo derecha | `Dispositivo` | `Conecta tu pastillero` | Bluetooth (`bluetooth`) | durazno `surface_peach` | `feature_orange` |

**Navegación:** cada tarjeta abre su destino **real del proyecto**. No inventar pantallas o rutas si ya existen. Si alguna no existe, dejar su navegación explícitamente pendiente; no crear pantallas vacías que aparenten funcionalidad.

### 4.3. Tarjeta «Próxima alarma»

Una tarjeta ancha bajo la cuadrícula, de superficie lavanda pálido y radio **24 dp**, con contorno de baja opacidad o sin borde, padding aprox. **12 dp**.

**Fila superior:** icono outlined de reloj morado, texto `Próxima alarma` en negrita y chip pequeño `Hoy` alineado a la derecha, fondo lavanda ligeramente más saturado y texto morado. El chip indica el día relativo real; **no mostrar `Hoy` para una alarma de mañana**.

**Fila interna:** tarjeta más clara (blanco-lavanda) con radio **16–20 dp**. Izquierda: icono outlined de cápsula/pastilla, morado. Centro: medicamento en negrita (`Paracetamol` en mockup) y debajo dosis (`1 tableta` en mockup). Derecha: hora destacada `14:00` (24 sp, bold, morado oscuro). Alineaciones verticales centradas, sin adornos superfluos.

Los datos `Paracetamol`, `1 tableta`, `14:00` y `Hoy` son **ejemplos visuales**: en la app deben provenir de los datos de alarmas reales (Firebase Realtime Database o la fuente ya definida en el proyecto). No conservarlos hardcodeados en producción.

**Estados que también deben diseñarse, manteniendo la tarjeta:**

- **Cargando:** placeholder de tamaño estable o indicador discreto.
- **Sin alarmas:** `No tienes alarmas programadas.` y acceso claro a `Nueva alarma`.
- **Error de datos:** mensaje concreto y opción de reintento, sin mostrar datos inventados.
- **Siguiente día:** chip `Mañana` o fecha adecuada. Respetar zona horaria y formato local de hora.

### 4.4. Acción principal inferior

- Botón píldora morado **`+ Nueva alarma`** debajo de la tarjeta de próxima alarma, centrado horizontalmente.
- Usar **el mismo componente base de botón primario** que `Iniciar sesión`: mismo gradiente, tipografía, radio, elevación, estados presionado/deshabilitado. En el dashboard la **anchura es ajustada al contenido**, mientras que en Login ocupa el ancho disponible.
- Icono **más (`add`) outlined** de 24 dp a la izquierda; no utilizar el símbolo `+` escrito en un `TextView` como sustituto del icono.
- Separación superior aprox. **20–24 dp**; inferior con insets seguros para navegación del sistema.
- Si el contenido no cabe en un móvil pequeño, la vista completa debe **desplazarse** para alcanzar el botón. No superponerla permanentemente a los datos salvo que el diseño se revise explícitamente.

---

## 5. Biblioteca mínima de componentes compartidos

**Crear únicamente abstracciones con reutilización real (YAGNI)**. Estructura sugerida, adaptar a la arquitectura existente:

| Componente conceptual | Empleo | Qué debe quedar compartido |
|---|---|---|
| `MedAlarmBackgroundView` | Login + Dashboard | gradiente, ondas, dimensiones relativas, colores e insets |
| `PrimaryActionButton` (style o widget reutilizable) | Iniciar sesión + Nueva alarma | gradiente, radio, ripple, tipografía, alturas mínimas, estados |
| `SecondaryOutlineButton` | Crear cuenta y futuras acciones secundarias | borde, radio, tipografía, foco/ripple |
| `AuthInputField` (estilo XML / `TextInputLayout`) | Correo + contraseña | alto, stroke, fondo, tipografía, hint, foco, error |
| `SectionIconLabel` | Dos etiquetas de login | caja de icono, tamaño, alineación, separación y tipografía |
| `FeatureTileView` | Cuatro tarjetas del dashboard | layout, radio, padding, chevron, iconos y textos; variantes por tokens |
| `NextAlarmCard` | Dashboard | estructura y estados de la próxima alarma |

No copiar y pegar cuatro tarjetas con distintas medidas; preferir **un layout XML de tarjeta** reutilizado por `<include>`/binding o una pequeña vista Kotlin que infle un solo XML. Evitar librerías de diseño adicionales solo para obtener una onda, un borde o una tarjeta.

**Iconografía:** crear los VectorDrawables concretos que se usan, importándolos de una única familia (Material Symbols Outlined) y aplicando `tint` desde recursos. Nombres sugeridos: `ic_mail`, `ic_lock`, `ic_visibility`, `ic_visibility_off`, `ic_warning`, `ic_login`, `ic_person_add`, `ic_menu`, `ic_notifications`, `ic_person`, `ic_alarm`, `ic_bar_chart`, `ic_trending_up`, `ic_bluetooth`, `ic_schedule`, `ic_medication`, `ic_chevron_right`, `ic_add`. Si un glyph no tiene equivalente exacto, escoger el **más cercano dentro de la misma familia**.

**Excepción expresiva:** los estados vacíos pueden usar un icono de 64–72 dp dentro de un contenedor de 112 dp con fondo suave. El mayor tamaño ayuda a que el estado vacío se entienda y gane jerarquía visual. Esta excepción no cambia los 24 dp de iconos funcionales en controles, listas, navegación y botones.

---

## 6. Arquitectura Android y reglas de implementación

1. **Primero inspeccionar el repositorio:** `build.gradle(.kts)`, versión de Android, tema actual, Activity/Fragment de login e inicio, navegación, archivos XML, drawables, `strings.xml`, autenticación y repositorios Firebase. **No presuponer** nombres o estructura.
2. Conservar **Kotlin + XML**. Las vistas deben ser XML, conectadas por ViewBinding o el mecanismo ya empleado. No reescribir la app con Compose.
3. Usar la mínima cantidad de archivos necesaria, separando **recursos compartidos** de lógica existente. Crear `colors.xml`, `dimens.xml`, estilos y drawables compartidos cuando aporten coherencia.
4. Usar `ConstraintLayout` para zonas complejas y `LinearLayout`/`GridLayout` o contenedores simples cuando correspondan. Para scroll vertical, emplear `NestedScrollView`/`ScrollView` con insets correctos. Evitar `AbsoluteLayout` y offsets fijos ligados a una resolución.
5. Preferir `TextInputLayout`/`TextInputEditText` **si ya están disponibles o introducir Material Components tiene sentido**. La etiqueta exterior de cada campo se mantiene, aunque el control gestione hint, icono final y error. El error destacado en rosa es **un bloque visible propio**, no solo un subtexto minúsculo.
6. Los botones pueden implementarse como `AppCompatButton` o widget equivalente con drawable gradiente + ripple. **No reemplazar ingenuamente el background interno de un `MaterialButton`** si eso invalida sus estados o forma; verificar el resultado real.
7. Respetar estado de barras del sistema: status bar integrada al morado, iconos claros, navigation bar del dispositivo con contraste apropiado. No dibujar una cámara frontal, marco negro ni iconos del sistema dentro de la jerarquía.
8. Al configurar el teclado, respetar IME/insets modernos, navegación por teclado y cierre adecuado; evitar que los botones y errores queden invisibles de forma permanente.
9. **No alterar el modelo de datos ni el flujo de Arduino UNO + HC-05** para una tarea visual. «Dispositivo» debe navegar a la conexión real cuando exista. No representar «conectado» si no hay estado confirmado de Bluetooth.
10. **Firebase:** reutilizar la fuente y autenticación reales ya presentes. No crear usuarios falsos, credenciales hardcodeadas ni implementar un segundo backend. Si no existe todavía autenticación, construir la UI y una frontera de integración clara; **no simular un inicio de sesión exitoso**.
11. Mantener strings en `strings.xml`. Usar recursos para colores/dimensiones y `contentDescription` para elementos interactivos; no escribir textos visibles en drawables o bitmaps.
12. Mantener componentes compatibles con TalkBack: etiquetas anunciables, errores audibles, iconos decorativos sin lecturas duplicadas, targets de 48 dp, y orden de foco lógico.

### 6.1. Estructura de archivos ilustrativa (no obligatoria)

```text
app/src/main/
├── java/<paquete_existente>/
│   ├── ... Activity/Fragment de login e inicio existentes
│   └── ui/components/MedAlarmBackgroundView.kt   # solo si es necesario
├── res/
│   ├── layout/
│   │   ├── ...layout_login.xml                    # nombre según proyecto
│   │   ├── ...layout_dashboard.xml                # nombre según proyecto
│   │   ├── view_feature_tile.xml                  # una plantilla para cuatro tarjetas
│   │   └── view_next_alarm.xml                    # si aporta reutilización
│   ├── drawable/
│   │   ├── bg_primary_button.xml
│   │   ├── bg_secondary_button.xml
│   │   ├── bg_auth_error.xml
│   │   ├── bg_input.xml
│   │   └── ic_*.xml                               # una familia vectorial
│   └── values/
│       ├── colors.xml
│       ├── dimens.xml
│       ├── styles.xml / themes.xml
│       └── strings.xml
└── ...
```

**No** crear clases/componentes solo porque aparecen en el árbol de ejemplo. Si el proyecto ya tiene alternativas coherentes, reutilizarlas o ajustarlas.

---

## 7. Matriz transversal de coherencia

Esta tabla permite detectar regresiones cuando se itere una pantalla:

| Propiedad | Login | Dashboard | Invariante |
|---|---|---|---|
| Encabezado | morado ondulado, sin contenido de marca | morado ondulado, con menú/saludo/pastillero | **mismo fondo** |
| Fondo del cuerpo | blanco lavanda | blanco lavanda | **mismo color** |
| Primario | `Iniciar sesión`, ancho completo | `Nueva alarma`, compacto | **mismo botón, variante de ancho** |
| Tipografía | campos, acciones, error | cards, secciones, hora | **misma familia y escala** |
| Iconos funcionales | correo, candado, ojo, entrar | menú, campana, módulos, reloj, + | **misma familia outline** |
| Radios | tarjeta 24 / campos 14 / botones 28 | tarjetas 24 / botón 28 / chips pequeños | **misma escala de tokens** |
| Sombra | sutil lavanda bajo formulario y botón | sutil lavanda bajo tarjetas y botón | **mismo estilo de elevación** |
| Tono verbal | `Verifica`, `inténtalo`, `¿Olvidaste…?` | `Gestiona`, `Consulta`, `Revisa`, `Conecta` | **solo tú** |
| Estados | inicial/error/loading | loading/vacío/datos/error | **feedback explícito y legible** |

**No son inconsistencias:** que el Login no tenga ilustración de pastillero o menú, que el Dashboard tenga tarjetas de distintas tonalidades pastel según categoría, o que el botón primario sea ancho en Login y compacto en Dashboard. Estas son **variantes intencionales**, no componentes distintos.

---

## 8. Procedimiento obligatorio para Codex

### Fase 1 — Auditoría breve

- Identificar pantallas reales, navegación, tema, bibliotecas, recursos y dónde están los datos.
- Revisar **ambas** imágenes de referencia y este documento.
- Elaborar lista mínima de archivos a crear/modificar; no iniciar una reestructuración global.

### Fase 2 — Sistema de diseño primero

- Definir tokens compartidos (`colors`, `dimens`, textos, estilos de botones, superficies, iconos).
- Crear y validar fondo ondulado **una sola vez**.
- Crear o adaptar componentes reutilizables antes de duplicarlos en las pantallas.

### Fase 3 — Pantallas

- Login: construir primero estado inicial; después error, loading, ojo, enlace y registro.
- Dashboard: construir cabecera, cuadrícula, próxima alarma y CTA; conectar a **fuentes de datos existentes**, no a datos inventados.
- Aplicar los mismos tokens a ambas y revisar que no existan estilos locales que diverjan.

### Fase 4 — Validación y ajuste

Generar capturas de la app en emulador/dispositivo **en la misma anchura aproximada de 393 dp** y compararlas visualmente con las referencias. Verificar por separado:

1. Curvas y gradiente del encabezado (dirección, amplitud, transición a blanco).
2. Márgenes horizontales y alineación de tarjetas.
3. Radios de tarjetas/campos/botones y sombras.
4. Alturas y espaciados verticales (que la jerarquía se sienta igual, sin huecos accidentales).
5. Tipografía y saltos de línea, especialmente subtítulos de las cuatro tarjetas y mensaje de error.
6. Iconos de **una** familia con grosor uniforme.
7. Contraste del primario, secundario, enlaces y error.
8. Interacción con IME, pantalla estrecha (≈360 dp), grande (≈430 dp) y aumento de fuente.

Si el entorno no permite ejecutar la app o capturar un emulador, **informarlo explícitamente**. No afirmar «replicación exacta» sin inspeccionar capturas reales.

### Fase 5 — Entrega verificable

- Compilar si el proyecto y entorno lo permiten.
- Informar archivos creados/modificados y dependencias añadidas (si las hay).
- Indicar qué componentes y tokens se comparten y dónde están.
- Reportar diferencias visuales todavía existentes (en especial ilustración del pastillero) y fallos de compilación/prueba.
- **No** declarar terminada la pantalla si faltan navegación, acceso por teclado, estado de error o scroll necesarios.

---

## 9. Criterios de aceptación (checklist)

- [ ] Las dos pantallas usan el **mismo fondo** de ondas moradas y la misma paleta.
- [ ] Login **no** contiene logo, título, subtítulo ni banner inferior.
- [ ] Login contiene solo correo, contraseña, su error contextual, iniciar sesión, recuperar contraseña y crear cuenta.
- [ ] Los mensajes de error son claros, inline, visibles y con lenguaje **tú**; el error no aparece en estado inicial.
- [ ] Mostrar/ocultar contraseña funciona y mantiene el contenido del campo.
- [ ] La acción primaria usa **el mismo componente/estilo** en ambas pantallas (solo varía el ancho).
- [ ] Todos los iconos de interfaz pertenecen a **Material Symbols Outlined** y mantienen grosor coherente.
- [ ] Los componentes comparten radios por categoría; no hay valores arbitrarios duplicados.
- [ ] Las cuatro tarjetas del dashboard comparten una única plantilla y muestran textos/colores correctos.
- [ ] Dashboard contiene menú, notificaciones, saludo, ilustración, cuatro tarjetas, próxima alarma y nueva alarma.
- [ ] Próxima alarma muestra datos reales cuando se conecte la fuente, con estados de carga/vacío/error.
- [ ] `strings.xml` centraliza texto y se usa **solo tuteo**.
- [ ] Sin Compose, captura completa como fondo, textos rasterizados, dimensiones fijas de pantalla ni datos falsos en producción.
- [ ] Los botones son reconocibles y accesibles; no quedan recortados con teclado o fuente ampliada.
- [ ] Las capturas reales se compararon con los mockups y las diferencias se describieron.

---

## 10. Instrucción de ejecución para Codex

> Lee `UI_SPEC_MEDALARM.md` y abre las imágenes `referencias/login.png` y `referencias/dashboard.png`. Antes de modificar código, inspecciona el proyecto. Implementa las dos pantallas con Android Views XML y Kotlin siguiendo el sistema visual compartido. Prioriza consistencia, fidelidad al mockup, accesibilidad y reutilización YAGNI. No cambies arquitecturas, Firebase ni lógica Bluetooth salvo lo indispensable para integrar las vistas. Compila y compara capturas si el entorno lo permite. Documenta cualquier discrepancia o recurso gráfico faltante; no finjas funcionalidades ni validación realizada.

#include <EEPROM.h>
#include <RtcDS1302.h>
#include <SoftwareSerial.h>
#include <ThreeWire.h>
#include <string.h>

// HC-05 TX -> Arduino pin 12 (RX), HC-05 RX <- Arduino pin 13 (TX, usar
// divisor).
SoftwareSerial serieBluetooth(12, 13);
ThreeWire cableRTC(10, 11, 9);
RtcDS1302<ThreeWire> relojRTC(cableRTC);

const uint8_t PIN_BOTON = 2, PIN_BUZZER = 3;
// Lunes a domingo. A0/A1 se usan como salidas digitales en el UNO.
const uint8_t PIN_LEDS[7] = {4, 5, 6, 7, 8, A0, A1};
const uint16_t MAGIA_ANTIGUA = 0x504E, MAGIA_VERSION_1 = 0x504D, MAGIA_SEMANAL = 0x5051;
const uint16_t MAGIA_EVENTOS = 0x4556;
const uint8_t MAX_HORARIOS =
    168; // 24 combinaciones distintas día/hora por cada día.
const uint8_t MAX_EVENTOS = 4;
const uint8_t COMPARTIMENTO_NINGUNO = 0, COMPARTIMENTO_SEMANAL = 1, COMPARTIMENTO_UNICO = 2;

struct HorarioDia {
  uint16_t datos;
};
struct AgendaSemana {
  uint16_t firma;
  uint8_t cantidad;
  HorarioDia horarios[MAX_HORARIOS];
};

// Formatos previos, usados solo para migrar la EEPROM de firmware anterior.
struct ConfiguracionCompartimentoAntiguo {
  uint8_t tipo;
  uint8_t dias;
  uint8_t hora;
  uint8_t minuto;
  char fecha[11];
};
struct PersistenciaAntigua {
  uint16_t firma;
  ConfiguracionCompartimentoAntiguo compartimentos[2];
};
struct ConfiguracionCompartimentoV1 {
  uint8_t dias;
  uint8_t hora;
  uint8_t minuto;
};
struct PersistenciaV1 {
  uint16_t firma;
  ConfiguracionCompartimentoV1 compartimentos[2];
};

struct EventoDetenido {
  uint8_t dia;
  char fecha[11];
  char hora[6];
};
struct ColaEventos {
  uint16_t firma;
  uint8_t cantidad;
  EventoDetenido eventos[MAX_EVENTOS];
};
const int EEPROM_EVENTOS_SEMANA = sizeof(AgendaSemana);
const int EEPROM_HORARIOS_SEMANA = sizeof(uint16_t) + sizeof(uint8_t);
const int EEPROM_EVENTOS_ANTIGUOS = sizeof(PersistenciaAntigua);
const int EEPROM_EVENTOS_VERSION_1 = sizeof(PersistenciaV1);

AgendaSemana agendaPendiente;
ColaEventos colaEventos;
uint8_t horariosEsperados = 0;
uint8_t horariosRecibidos = 0;
bool sincronizando = false;

bool alarmaSonando = false, estadoLED = LOW, estadoBeep = false;
uint8_t diaActivo = 0;
RtcDateTime instanteAlarma;
uint32_t ultimoParpadeoLED = 0, ultimoTiempoBeep = 0;
bool estadoAnteriorBoton = HIGH;
String mensajeBluetoothEntrante = "";
String ultimaAlarma = "";

void apagarLEDs() {
  for (uint8_t indice = 0; indice < 7; indice++)
    digitalWrite(PIN_LEDS[indice], LOW);
}

String fechaTexto(const RtcDateTime &fechaHora) {
  char texto[11];
  snprintf(texto, sizeof(texto), "%04u-%02u-%02u", fechaHora.Year(), fechaHora.Month(), fechaHora.Day());
  return String(texto);
}
String horaTexto(const RtcDateTime &fechaHora) {
  char texto[6];
  snprintf(texto, sizeof(texto), "%02u:%02u", fechaHora.Hour(), fechaHora.Minute());
  return String(texto);
}
uint8_t diaSemana(const RtcDateTime &fechaHora) {
  // RtcDateTime::DayOfWeek(): domingo=0; protocolo: lunes=1, domingo=7.
  return (fechaHora.DayOfWeek() + 6) % 7 + 1;
}
uint8_t horarioDia(const HorarioDia &horario) { return (horario.datos & 0x07) + 1; }
uint8_t horarioHora(const HorarioDia &horario) { return (horario.datos >> 3) & 0x1F; }
uint8_t horarioMinuto(const HorarioDia &horario) { return (horario.datos >> 8) & 0x3F; }
HorarioDia crearHorario(uint8_t dia, uint8_t hora, uint8_t minuto) {
  HorarioDia h;
  h.datos = (uint16_t)((dia - 1) | (hora << 3) | (minuto << 8));
  return h;
}
bool fechaValida(const RtcDateTime &fechaHora) {
  return fechaHora.Year() >= 2024 && fechaHora.Year() <= 2099;
}

String identificadorEvento(const EventoDetenido &evento) {
  String identificador = String(evento.dia) + "_" + evento.fecha + "_" + evento.hora;
  identificador.replace(":", "");
  return identificador;
}

void enviarEventosPendientes() {
  for (uint8_t indice = 0; indice < colaEventos.cantidad; indice++) {
    EventoDetenido &evento = colaEventos.eventos[indice];
    serieBluetooth.print("ALARM_STOPPED;");
    serieBluetooth.print(identificadorEvento(evento));
    serieBluetooth.print(';');
    serieBluetooth.print(evento.dia);
    serieBluetooth.print(';');
    serieBluetooth.print(evento.fecha);
    serieBluetooth.print(';');
    serieBluetooth.println(evento.hora);
  }
}

bool agregarHorario(AgendaSemana &destino, uint8_t dia, uint8_t hora,
                    uint8_t minuto) {
  if (dia < 1 || dia > 7 || hora > 23 || minuto > 59)
    return false;
  for (uint8_t indice = 0; indice < destino.cantidad; indice++) {
    HorarioDia &horarioExistente = destino.horarios[indice];
    if (horarioDia(horarioExistente) == dia && horarioHora(horarioExistente) == hora &&
        horarioMinuto(horarioExistente) == minuto)
      return true;
  }
  if (destino.cantidad >= MAX_HORARIOS)
    return false;
  destino.horarios[destino.cantidad++] = crearHorario(dia, hora, minuto);
  return true;
}

void vaciarAgenda(AgendaSemana &destino) {
  destino.firma = MAGIA_SEMANAL;
  destino.cantidad = 0;
  for (uint8_t indice = 0; indice < MAX_HORARIOS; indice++) {
    destino.horarios[indice].datos = 0;
  }
}

bool esColaValida(const ColaEventos &cola) {
  return cola.firma == MAGIA_EVENTOS && cola.cantidad <= MAX_EVENTOS;
}

bool leerColaAntigua(int direccion, ColaEventos &resultado) {
  ColaEventos colaCandidata;
  EEPROM.get(direccion, colaCandidata);
  if (!esColaValida(colaCandidata))
    return false;
  resultado = colaCandidata;
  return true;
}

void migrarEventosAntiguos(ColaEventos &cola) {
  for (uint8_t indice = 0; indice < cola.cantidad; indice++) {
    EventoDetenido &evento = cola.eventos[indice];
    int ano, mes, dia;
    if (sscanf(evento.fecha, "%d-%d-%d", &ano, &mes, &dia) == 3) {
      RtcDateTime fechaRTC(ano, mes, dia, 0, 0, 0);
      evento.dia = diaSemana(fechaRTC);
    } else
      evento.dia = 1;
  }
  cola.firma = MAGIA_EVENTOS;
}

void migrarAgendaAntigua(const PersistenciaAntigua &persistenciaAntigua) {
  vaciarAgenda(agendaPendiente);
  for (uint8_t indiceCompartimento = 0; indiceCompartimento < 2; indiceCompartimento++) {
    const ConfiguracionCompartimentoAntiguo &configuracion =
        persistenciaAntigua.compartimentos[indiceCompartimento];
    if (configuracion.tipo != COMPARTIMENTO_SEMANAL)
      continue; // Las alarmas únicas se retiran en el nuevo modelo.
    for (uint8_t dia = 1; dia <= 7; dia++) {
      if (configuracion.dias & (1 << (dia - 1)))
        agregarHorario(agendaPendiente, dia, configuracion.hora, configuracion.minuto);
    }
  }
}

void migrarAgendaV1(const PersistenciaV1 &persistenciaAnterior) {
  vaciarAgenda(agendaPendiente);
  for (uint8_t indiceCompartimento = 0; indiceCompartimento < 2; indiceCompartimento++) {
    const ConfiguracionCompartimentoV1 &configuracion =
        persistenciaAnterior.compartimentos[indiceCompartimento];
    for (uint8_t dia = 1; dia <= 7; dia++) {
      if (configuracion.dias & (1 << (dia - 1)))
        agregarHorario(agendaPendiente, dia, configuracion.hora, configuracion.minuto);
    }
  }
}

void guardarEventoDetenido(uint8_t dia, const RtcDateTime &cuando) {
  if (colaEventos.cantidad >= MAX_EVENTOS)
    return;
  EventoDetenido &evento = colaEventos.eventos[colaEventos.cantidad++];
  evento.dia = dia;
  fechaTexto(cuando).toCharArray(evento.fecha, sizeof(evento.fecha));
  horaTexto(cuando).toCharArray(evento.hora, sizeof(evento.hora));
  EEPROM.put(EEPROM_EVENTOS_SEMANA, colaEventos);
}

void confirmarEvento(String identificador) {
  identificador.replace(":", "");
  for (uint8_t indice = 0; indice < colaEventos.cantidad; indice++) {
    if (identificadorEvento(colaEventos.eventos[indice]) == identificador) {
      for (uint8_t siguiente = indice + 1; siguiente < colaEventos.cantidad; siguiente++)
        colaEventos.eventos[siguiente - 1] = colaEventos.eventos[siguiente];
      colaEventos.cantidad--;
      EEPROM.put(EEPROM_EVENTOS_SEMANA, colaEventos);
      return;
    }
  }
}

void detenerAlarma(bool informarDetencion) {
  uint8_t dia = diaActivo;
  RtcDateTime cuando = instanteAlarma;
  alarmaSonando = false;
  diaActivo = 0;
  noTone(PIN_BUZZER);
  digitalWrite(PIN_BUZZER, LOW);
  apagarLEDs();
  serieBluetooth.println("ALARM_OFF");
  if (informarDetencion && dia >= 1 && dia <= 7) {
    guardarEventoDetenido(dia, cuando);
    enviarEventosPendientes();
  }
}

void iniciarAlarma(uint8_t dia, const RtcDateTime &ahora) {
  if (dia < 1 || dia > 7)
    return;
  alarmaSonando = true;
  diaActivo = dia;
  instanteAlarma = ahora;
  estadoLED = LOW;
  ultimoTiempoBeep = millis();
  ultimoParpadeoLED = millis();
  apagarLEDs();
  serieBluetooth.print("ALARM_ON;");
  serieBluetooth.print(dia);
  serieBluetooth.print(';');
  serieBluetooth.print(fechaTexto(ahora));
  serieBluetooth.print(';');
  serieBluetooth.println(horaTexto(ahora));
}

void procesarComando(String comando) {
  comando.trim();
  if (comando == "CAPS") {
    serieBluetooth.println("CAPS;WEEKDAYS7;168");
    return;
  }

  if (comando == "SYNC_END") {
    if (!sincronizando || horariosRecibidos != horariosEsperados) {
      serieBluetooth.println("ERR;cantidad de horarios incompleta");
      sincronizando = false;
      return;
    }
    agendaPendiente.firma = MAGIA_SEMANAL;
    EEPROM.put(0, agendaPendiente);
    EEPROM.put(EEPROM_EVENTOS_SEMANA, colaEventos);
    sincronizando = false;
    serieBluetooth.println("SYNC_OK");
    return;
  }

  if (comando.startsWith("SYNC_BEGIN;")) {
    int cantidad = comando.substring(11).toInt();
    if (cantidad < 0 || cantidad > MAX_HORARIOS) {
      serieBluetooth.println("ERR;máximo 168 horarios semanales");
      return;
    }
    vaciarAgenda(agendaPendiente);
    horariosEsperados = (uint8_t)cantidad;
    horariosRecibidos = 0;
    sincronizando = true;
    return;
  }

  if (comando.startsWith("DAY_TIME;")) {
    if (!sincronizando) {
      serieBluetooth.println("ERR;sin inicio de sincronización");
      return;
    }
    int dia, hora, minuto;
    if (sscanf(comando.c_str(), "DAY_TIME;%d;%d:%d", &dia, &hora, &minuto) != 3 ||
        dia < 1 || dia > 7 || hora < 0 || hora > 23 || minuto < 0 ||
        minuto > 59 ||
        !agregarHorario(agendaPendiente, (uint8_t)dia, (uint8_t)hora,
                        (uint8_t)minuto)) {
      serieBluetooth.println("ERR;horario diario inválido o capacidad excedida");
      return;
    }
    horariosRecibidos++;
    return;
  }

  if (comando.startsWith("TIME;")) {
    int ano, mes, dia, hora, minuto, segundo;
    if (sscanf(comando.c_str(), "TIME;%d-%d-%d;%d:%d:%d", &ano, &mes, &dia, &hora, &minuto,
               &segundo) != 6 ||
        ano < 2024 || ano > 2099 || mes < 1 || mes > 12 || dia < 1 || dia > 31 || hora < 0 ||
        hora > 23 || minuto < 0 || minuto > 59 || segundo < 0 || segundo > 59) {
      serieBluetooth.println("ERR;hora inválida");
      return;
    }
    relojRTC.SetDateTime(RtcDateTime(ano, mes, dia, hora, minuto, segundo));
    return;
  }

  if (comando == "STATUS") {
    serieBluetooth.println(alarmaSonando ? "ESTADO;SONANDO" : "ESTADO;LISTO");
  } else if (comando == "SYNC_STATUS") {
    uint8_t cantidad = 0;
    EEPROM.get(sizeof(uint16_t), cantidad);
    for (uint8_t indice = 0; indice < cantidad && indice < MAX_HORARIOS; indice++) {
      HorarioDia horario = {};
      EEPROM.get(EEPROM_HORARIOS_SEMANA + indice * sizeof(HorarioDia), horario);
      char linea[24];
      snprintf(linea, sizeof(linea), "DAY_TIME;%u;%02u:%02u", horarioDia(horario),
               horarioHora(horario), horarioMinuto(horario));
      serieBluetooth.println(linea);
    }
    serieBluetooth.println("CONFIG_END");
    enviarEventosPendientes();
  } else if (comando == "TELEMETRY") {
    RtcDateTime fechaHoraActual = relojRTC.GetDateTime();
    char reloj[9];
    snprintf(reloj, sizeof(reloj), "%02u:%02u:%02u", fechaHoraActual.Hour(),
             fechaHoraActual.Minute(), fechaHoraActual.Second());
    serieBluetooth.print("TELEMETRY;");
    serieBluetooth.print((unsigned long)millis());
    serieBluetooth.print(';');
    serieBluetooth.print(fechaTexto(fechaHoraActual));
    serieBluetooth.print(';');
    serieBluetooth.print(reloj);
    serieBluetooth.print(';');
    serieBluetooth.print(alarmaSonando ? "SONANDO" : "LISTO");
    serieBluetooth.print(';');
    serieBluetooth.println(alarmaSonando ? diaActivo : 0);
  } else if (comando.startsWith("EVENT_ACK;")) {
    confirmarEvento(comando.substring(10));
  } else if (comando == "STOP") {
    detenerAlarma(false);
  } else if (comando == "SET_LUNES" || comando == "SET_MARTES") {
    RtcDateTime ahora = relojRTC.GetDateTime();
    iniciarAlarma(comando == "SET_LUNES" ? 1 : 2, ahora);
  }
}

void setup() {
  Serial.begin(9600);
  serieBluetooth.begin(9600);
  pinMode(PIN_BOTON, INPUT_PULLUP);
  pinMode(PIN_BUZZER, OUTPUT);
  for (uint8_t indice = 0; indice < 7; indice++)
    pinMode(PIN_LEDS[indice], OUTPUT);
  apagarLEDs();

  relojRTC.Begin();
  relojRTC.SetIsRunning(true);

  uint16_t magiaGuardada = 0;
  uint8_t cantidadGuardada = 0;
  PersistenciaAntigua persistenciaAntigua;
  PersistenciaV1 persistenciaAnterior;
  EEPROM.get(0, magiaGuardada);
  EEPROM.get(sizeof(uint16_t), cantidadGuardada);
  EEPROM.get(0, persistenciaAntigua);
  EEPROM.get(0, persistenciaAnterior);

  if (magiaGuardada == MAGIA_SEMANAL && cantidadGuardada <= MAX_HORARIOS) {
    EEPROM.get(0, agendaPendiente);
    if (!leerColaAntigua(EEPROM_EVENTOS_SEMANA, colaEventos)) {
      colaEventos.firma = MAGIA_EVENTOS;
      colaEventos.cantidad = 0;
    }
  } else if (persistenciaAntigua.firma == MAGIA_ANTIGUA) {
    migrarAgendaAntigua(persistenciaAntigua);
    if (!leerColaAntigua(EEPROM_EVENTOS_ANTIGUOS, colaEventos)) {
      colaEventos.firma = MAGIA_EVENTOS;
      colaEventos.cantidad = 0;
    }
    migrarEventosAntiguos(colaEventos);
  } else if (persistenciaAnterior.firma == MAGIA_VERSION_1) {
    migrarAgendaV1(persistenciaAnterior);
    if (!leerColaAntigua(EEPROM_EVENTOS_VERSION_1, colaEventos)) {
      colaEventos.firma = MAGIA_EVENTOS;
      colaEventos.cantidad = 0;
    }
    migrarEventosAntiguos(colaEventos);
  } else {
    vaciarAgenda(agendaPendiente);
    if (!leerColaAntigua(EEPROM_EVENTOS_ANTIGUOS, colaEventos) &&
        !leerColaAntigua(EEPROM_EVENTOS_VERSION_1, colaEventos)) {
      colaEventos.firma = MAGIA_EVENTOS;
      colaEventos.cantidad = 0;
    } else
      migrarEventosAntiguos(colaEventos);
  }
  if (!esColaValida(colaEventos)) {
    colaEventos.firma = MAGIA_EVENTOS;
    colaEventos.cantidad = 0;
  }
  EEPROM.put(0, agendaPendiente);
  EEPROM.put(EEPROM_EVENTOS_SEMANA, colaEventos);

  // Mantener un RTC válido: su hora debe sobrevivir a reinicios con la batería.
  if (!relojRTC.IsDateTimeValid())
    relojRTC.SetDateTime(RtcDateTime(__DATE__, __TIME__));
  serieBluetooth.println("READY;WEEKDAYS7;168");
  enviarEventosPendientes();
}

void loop() {
  while (serieBluetooth.available() > 0) {
    char caracter = (char)serieBluetooth.read();
    if (caracter == '\n') {
      procesarComando(mensajeBluetoothEntrante);
      mensajeBluetoothEntrante = "";
    } else if (caracter != '\r' && mensajeBluetoothEntrante.length() < 100)
      mensajeBluetoothEntrante += caracter;
  }

  RtcDateTime ahora = relojRTC.GetDateTime();
  if (!alarmaSonando && fechaValida(ahora) && ahora.Second() == 0) {
    String clave = String(diaSemana(ahora)) + "-" + fechaTexto(ahora) + "-" +
                   horaTexto(ahora);
    if (clave != ultimaAlarma) {
      uint8_t hoy = diaSemana(ahora);
      uint8_t cantidad = 0;
      EEPROM.get(sizeof(uint16_t), cantidad);
      for (uint8_t indice = 0; indice < cantidad && indice < MAX_HORARIOS; indice++) {
        HorarioDia horario = {};
        EEPROM.get(EEPROM_HORARIOS_SEMANA + indice * sizeof(HorarioDia), horario);
        if (horarioDia(horario) == hoy && horarioHora(horario) == ahora.Hour() &&
            horarioMinuto(horario) == ahora.Minute()) {
          ultimaAlarma = clave;
          iniciarAlarma(hoy, ahora);
          break;
        }
      }
    }
  }

  bool boton = digitalRead(PIN_BOTON);
  if (estadoAnteriorBoton == HIGH && boton == LOW) {
    if (alarmaSonando)
      detenerAlarma(true);
    delay(50);
  }
  estadoAnteriorBoton = boton;

  if (alarmaSonando) {
    uint32_t ahoraMs = millis();
    if (ahoraMs - ultimoTiempoBeep >= (estadoBeep ? 150 : 200)) {
      estadoBeep = !estadoBeep;
      if (estadoBeep)
        tone(PIN_BUZZER, 2500);
      else
        noTone(PIN_BUZZER);
      ultimoTiempoBeep = ahoraMs;
    }
    if (ahoraMs - ultimoParpadeoLED >= 250) {
      estadoLED = !estadoLED;
      apagarLEDs();
      digitalWrite(PIN_LEDS[diaActivo - 1], estadoLED);
      ultimoParpadeoLED = ahoraMs;
    }
  }
}

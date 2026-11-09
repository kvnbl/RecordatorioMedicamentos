#include <SoftwareSerial.h>
#include <ThreeWire.h>
#include <RtcDS1302.h>
#include <EEPROM.h>

// HC-05 TX -> Arduino pin 12 (RX), HC-05 RX <- Arduino pin 13 (TX, usar divisor).
SoftwareSerial BTSerial(12, 13);
ThreeWire myWire(10, 11, 9);
RtcDS1302<ThreeWire> Rtc(myWire);

const uint8_t PIN_BOTON = 2, PIN_BUZZER = 3;
const uint8_t PIN_LEDS[2] = {4, 5};
const uint16_t MAGIC = 0x504D;

struct SlotConfig { uint8_t dias; uint8_t hora; uint8_t minuto; };
struct Persistencia { uint16_t magic; SlotConfig slots[2]; };
Persistencia agenda;
Persistencia agendaPendiente;
uint8_t slotsRecibidos = 0;
uint8_t slotsEsperados = 0;
bool sincronizando = false;

bool alarmaSonando = false, estadoLED = LOW, estadoBeep = false;
int slotActivo = -1;
RtcDateTime instanteAlarma;
uint32_t ultimoParpadeoLED = 0, ultimoTiempoBeep = 0;
bool estadoAnteriorBoton = HIGH;
String mensajeBTEntrante = "";
String ultimaAlarma = "";

void apagarLEDs() { for (uint8_t i = 0; i < 2; i++) digitalWrite(PIN_LEDS[i], LOW); }

String fechaTexto(const RtcDateTime& t) {
  char b[11]; snprintf(b, sizeof(b), "%04u-%02u-%02u", t.Year(), t.Month(), t.Day());
  return String(b);
}
String horaTexto(const RtcDateTime& t) {
  char b[6]; snprintf(b, sizeof(b), "%02u:%02u", t.Hour(), t.Minute());
  return String(b);
}
uint8_t diaSemanaBit(const RtcDateTime& t) {
  // RtcDateTime::DayOfWeek(): domingo=0; protocolo: lunes=bit 0.
  uint8_t dow = t.DayOfWeek();
  uint8_t dia = (dow + 6) % 7;
  return (uint8_t)(1 << dia);
}
bool fechaValida(const RtcDateTime& t) { return t.Year() >= 2024 && t.Year() <= 2099; }

void detenerAlarma(bool confirmar) {
  int slot = slotActivo;
  RtcDateTime cuando = instanteAlarma;
  alarmaSonando = false;
  slotActivo = -1;
  noTone(PIN_BUZZER);
  digitalWrite(PIN_BUZZER, LOW);
  apagarLEDs();
  BTSerial.println("ALARM_OFF");
  if (confirmar && slot >= 0) {
    BTSerial.print("TAKEN;"); BTSerial.print(slot + 1); BTSerial.print(';');
    BTSerial.print(fechaTexto(cuando)); BTSerial.print(';');
    BTSerial.println(horaTexto(cuando));
  }
}

void iniciarAlarma(uint8_t slot, const RtcDateTime& ahora) {
  alarmaSonando = true;
  slotActivo = slot;
  instanteAlarma = ahora;
  estadoLED = LOW;
  ultimoTiempoBeep = millis();
  ultimoParpadeoLED = millis();
  apagarLEDs();
  BTSerial.print("ALARM_ON;"); BTSerial.print(slot + 1); BTSerial.print(';');
  BTSerial.print(fechaTexto(ahora)); BTSerial.print(';');
  BTSerial.println(horaTexto(ahora));
}

void procesarComando(String cmd) {
  cmd.trim();
  if (cmd == "SYNC_END") {
    if (!sincronizando || slotsRecibidos != slotsEsperados) {
      BTSerial.println("ERR;cantidad de horarios incompleta");
      sincronizando = false;
      return;
    }
    agenda = agendaPendiente;
    agenda.magic = MAGIC;
    EEPROM.put(0, agenda);
    sincronizando = false;
    BTSerial.println("SYNC_OK");
    return;
  }

  if (cmd.startsWith("SYNC_BEGIN;")) {
    int n = cmd.substring(11).toInt();
    if (n < 0 || n > 2) { BTSerial.println("ERR;máximo dos horarios"); return; }
    agendaPendiente.magic = MAGIC;
    for (uint8_t i = 0; i < 2; i++) agendaPendiente.slots[i] = {0, 0, 0};
    slotsEsperados = (uint8_t)n;
    slotsRecibidos = 0;
    sincronizando = true;
    return;
  }

  if (cmd.startsWith("TIME;")) {
    int y, mo, d, h, mi, s;
    if (sscanf(cmd.c_str(), "TIME;%d-%d-%d;%d:%d:%d", &y, &mo, &d, &h, &mi, &s) != 6 ||
        y < 2024 || y > 2099 || mo < 1 || mo > 12 || d < 1 || d > 31 ||
        h < 0 || h > 23 || mi < 0 || mi > 59 || s < 0 || s > 59) {
      BTSerial.println("ERR;hora inválida"); return;
    }
    Rtc.SetDateTime(RtcDateTime(y, mo, d, h, mi, s));
    return;
  }

  if (cmd.startsWith("SLOT;")) {
    if (!sincronizando) { BTSerial.println("ERR;sin inicio de sincronización"); return; }
    int slot, dias, hora, minuto;
    char id[32];
    if (sscanf(cmd.c_str(), "SLOT;%d;%d;%d:%d;%31[^\r\n]", &slot, &dias, &hora, &minuto, id) != 5 ||
        slot < 1 || slot > 2 || dias < 0 || dias > 127 || hora < 0 || hora > 23 || minuto < 0 || minuto > 59) {
      BTSerial.println("ERR;horario inválido"); return;
    }
    agendaPendiente.slots[slot - 1] = {(uint8_t)dias, (uint8_t)hora, (uint8_t)minuto};
    slotsRecibidos++;
    return;
  }

  if (cmd == "STATUS") {
    BTSerial.println(alarmaSonando ? "ESTADO;SONANDO" : "ESTADO;LISTO");
  } else if (cmd == "STOP") {
    detenerAlarma(false);
  } else if (cmd == "SET_LUNES" || cmd == "SET_MARTES") {
    RtcDateTime ahora = Rtc.GetDateTime();
    iniciarAlarma(cmd == "SET_LUNES" ? 0 : 1, ahora);
  }
}

void setup() {
  Serial.begin(9600);
  BTSerial.begin(9600);
  pinMode(PIN_BOTON, INPUT_PULLUP);
  pinMode(PIN_BUZZER, OUTPUT);
  for (uint8_t i = 0; i < 2; i++) pinMode(PIN_LEDS[i], OUTPUT);
  apagarLEDs();

  Rtc.Begin();
  Rtc.SetIsRunning(true);
  EEPROM.get(0, agenda);
  if (agenda.magic != MAGIC) {
    agenda.magic = MAGIC;
    for (uint8_t i = 0; i < 2; i++) agenda.slots[i] = {0, 0, 0};
    EEPROM.put(0, agenda);
  }
  // No sobrescribir un RTC válido: debe sobrevivir a reinicios usando su batería.
  if (!Rtc.IsDateTimeValid()) Rtc.SetDateTime(RtcDateTime(__DATE__, __TIME__));
  BTSerial.println("READY");
}

void loop() {
  while (BTSerial.available() > 0) {
    char c = (char)BTSerial.read();
    if (c == '\n') { procesarComando(mensajeBTEntrante); mensajeBTEntrante = ""; }
    else if (c != '\r' && mensajeBTEntrante.length() < 100) mensajeBTEntrante += c;
  }

  RtcDateTime ahora = Rtc.GetDateTime();
  if (!alarmaSonando && fechaValida(ahora) && ahora.Second() == 0) {
    String clave = fechaTexto(ahora) + "-" + horaTexto(ahora);
    if (clave != ultimaAlarma) {
      for (uint8_t i = 0; i < 2; i++) {
        SlotConfig slot = agenda.slots[i];
        if ((slot.dias & diaSemanaBit(ahora)) && slot.hora == ahora.Hour() && slot.minuto == ahora.Minute()) {
          ultimaAlarma = clave;
          iniciarAlarma(i, ahora);
          break;
        }
      }
    }
  }

  bool boton = digitalRead(PIN_BOTON);
  if (estadoAnteriorBoton == HIGH && boton == LOW) {
    if (alarmaSonando) detenerAlarma(true);
    delay(50);
  }
  estadoAnteriorBoton = boton;

  if (alarmaSonando) {
    uint32_t ahoraMs = millis();
    if (ahoraMs - ultimoTiempoBeep >= (estadoBeep ? 150 : 200)) {
      estadoBeep = !estadoBeep;
      if (estadoBeep) tone(PIN_BUZZER, 2500); else noTone(PIN_BUZZER);
      ultimoTiempoBeep = ahoraMs;
    }
    if (ahoraMs - ultimoParpadeoLED >= 250) {
      estadoLED = !estadoLED;
      digitalWrite(PIN_LEDS[slotActivo], estadoLED);
      ultimoParpadeoLED = ahoraMs;
    }
  }
}

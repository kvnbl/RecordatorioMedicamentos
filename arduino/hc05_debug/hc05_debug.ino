#include <SoftwareSerial.h>

// Puente de diagnóstico: HC-05 TX -> D12; RX <- D13 (con divisor de tensión).
// No modifica EEPROM ni el RTC. Abrir el monitor serie USB a 115200 baudios.
SoftwareSerial hc05(12, 13);
unsigned long ultimoEstado = 0;

void setup() {
  Serial.begin(115200);
  hc05.begin(9600);
  Serial.println(F("HC-05 DEBUG: puente USB <-> D12/D13 a 9600 baudios"));
  Serial.println(F("Los bytes recibidos del HC-05 apareceran aqui."));
}

void loop() {
  while (hc05.available()) Serial.write(hc05.read());
  while (Serial.available()) hc05.write(Serial.read());
  if (millis() - ultimoEstado >= 5000) {
    ultimoEstado = millis();
    Serial.println(F("[Arduino activo; esperando datos del HC-05]"));
  }
}

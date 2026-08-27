
# Documento de diseño: integración de robótica móvil (RaspTank) y visión por instantáneas en Noema

---

## 1. Propósito y alcance

El objetivo de este documento es definir la arquitectura física, eléctrica y de software para integrar un chasis robótico móvil basado en Raspberry Pi (Adeept RaspTank) con capacidades de **captura de imágenes fijas bajo demanda** en el agente Noema.

El diseño se fundamenta en exponer las capacidades físicas del tanque (desplazamiento, orientación de cámara, captura de imágenes y telemetría) como **herramientas estándar del agente (`AgentTool`)** sobre un servidor HTTP local expuesto por la Raspberry Pi.

---

## 2. Arquitectura de hardware seleccionada

### 2.1. Chasis y tracción
* **Chasis:** Adeept RaspTank (orugas de goma con tracción diferencial).
* **Controlador de motores:** Placa de expansión basada en I2C/PWM (chip PCA9685 o similar) para control de velocidad de motores DC.

### 2.2. Reparto de masas y visión (Pan/Tilt)
* **Visión en la torreta articulada (Pan/Tilt):** Módulo de cámara nativo para Raspberry Pi por cable cinta (CSI), de 5 a 10 gramos de peso (ej. *Raspberry Pi Camera Module 3* o *Arducam*).
  * *Justificación:* Aligerar la masa en el extremo del brazo elimina el temblor (*jitter*), reduce la corriente pico de los servos SG90/MG90S y evita el desgaste de engranajes.
* **Audio y sensores en la base:** Micrófono y altavoz USB (o webcam USB secundaria) montados de forma fija en la parte inferior del chasis principal.
  * *Justificación:* Mantiene el centro de gravedad bajo y evita la torsión de cables durante la rotación de la torreta.

### 2.3. Sistema de energía y recarga automática
* **Alimentación del robot:** Placa UPS/BMS para 2 baterías Li-Ion 18650 que entregan corriente continua de 5V/3A a la Raspberry Pi y controladores de motores.
* **Base de recarga (Dock):** Contactos físicos mediante pletinas de cobre y pines con muelle (*pogo pins*) apoyados por pequeños imanes de neodimio para auto-alineación magnética por proximidad.

---

## 3. Capa de software en la Raspberry Pi (Servidor REST local)

La Raspberry Pi ejecuta un servidor web ligero en Python (**FastAPI** o **Bottle**) que arranca al iniciar el sistema operativo y expone un API REST en la red local (`http://rasptank.local:8000`).

```
[ Agente Noema (Java) ]
       │
       │ Peticiones HTTP REST (WiFi)
       v
[ Servidor FastAPI en la RPi (Puerto 8000) ]
       │
       ├──► Python Driver (PCA9685 / GPIO) ──► Motores / Servos
       ├──► libcamera / OpenCV ────────────► Captura JPEG (/dev/video0)
       └──► Lectura I2C / ADC ──────────────► Batería y Ultrasonidos
```

### Endpoints del API REST:

| Método | Endpoint | Parámetros (JSON) | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/drive` | `{ "direction": "forward", "duration_ms": 1000, "speed": 70 }` | Desplazamiento de orugas. |
| `POST` | `/api/v1/gimbal` | `{ "pan": 90, "tilt": 45 }` | Orientación de la torreta Pan/Tilt. |
| `GET` | `/api/v1/snapshot` | *Ninguno* | Captura un fotograma y devuelve la imagen en formato JPEG. |
| `GET` | `/api/v1/telemetry` | *Ninguno* | Devuelve `{ "battery_v": 7.8, "charging": true, "distance_cm": 35 }`. |

---

## 4. Integración en Noema (Capa Java)

### 4.1. Herramientas del Agente (`AgentTool`)

Noema registra un conjunto de herramientas dedicadas que consumen el API REST del RaspTank mediante peticiones HTTP sincrónicas (`HttpClient` de Java):

1. **`RaspTankDriveTool` (`MODE_WRITE`):**
   * Invoca `POST /api/v1/drive`. Permite al agente avanzar, retroceder o girar durante un tiempo determinado.
2. **`RaspTankGimbalTool` (`MODE_WRITE`):**
   * Invoca `POST /api/v1/gimbal`. Ajusta los ángulos horizontales y verticales de la cámara.
3. **`RaspTankSnapshotTool` (`MODE_READ`):**
   * Invoca `GET /api/v1/snapshot`. Recibe el chorro de bytes JPEG, guarda temporalmente la imagen en la carpeta `tmp` de Noema y construye un objeto `ImageContent` de LangChain4j.
4. **`RaspTankTelemetryTool` (`MODE_READ`):**
   * Invoca `GET /api/v1/telemetry`. Permite al agente consultar el nivel de batería o la presencia de obstáculos.

### 4.2. Soporte Multimodal y Persistencia

* **Inferencia Visual:** El objeto `ImageContent` generado por `RaspTankSnapshotTool` se adjunta al mensaje de respuesta de la herramienta. Si el modelo configurado en el `ReasoningService` es multimodal (ej. `Qwen2-VL`, `GPT-4o`, `Claude 3.5 Sonnet` o `Llama 3.2 Vision`), el LLM inspecciona directamente la imagen.
* **Persistencia en `SourceOfTruth`:** Las llamadas a las herramientas y los datos de las observaciones se registran en la base de datos H2 (`turnos`). El soporte de persistencia de imágenes en sesiones ya está contemplado en `Session.java` a través del adaptador `ContentAdapter`.

---

## 5. Caso de uso: Inspección autónoma proactiva

El siguiente diagrama de secuencia ilustra un ciclo completo de inspección física iniciado por un evento de sistema:

```
[SensorsService / Scheduler]    [ReasoningServiceImpl]    [RaspTankSnapshotTool]    [Servidor REST RaspTank]
             │                            │                          │                         │
 1. Alarma   │                            │                          │                         │
──── Alarma ─┼───────────────────────────>│                          │                         │
  programada │                            │                          │                         │
             │                    2. Evalúa evento                   │                         │
             │                       y decide ver                    │                         │
             │                            │                          │                         │
             │                    3. Ejecuta tool                    │                         │
             │                       snapshot                        │                         │
             │                            ├─────────────────────────>│                         │
             │                            │                          │ 4. GET /snapshot        │
             │                            │                          ├────────────────────────>│
             │                            │                          │                         │ Captura foto
             │                            │                          │ 5. Devuelve JPEG        │ con libcamera
             │                            │                          │<────────────────────────┤
             │                            │                          │                         │
             │                            │ 6. Devuelve ImageContent │                         │
             │                            │<─────────────────────────┤                         │
             │                            │                          │                         │
             │                    7. LLM Multimodal                  │                         │
             │                       inspecciona la foto             │                         │
             │                            │                          │                         │
             │                    8. Responde por voz /              │                         │
             │                       Telegram con el informe         │                         │
```

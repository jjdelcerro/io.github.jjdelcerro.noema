# Documento de Diseño Arquitectónico: Integración de Conversaciones de Voz en Tiempo Real en Noema


---

## 1. Introducción y objetivo

El propósito de este documento es definir el diseño técnico y arquitectónico para dotar al agente conversacional autónomo **Noema** de capacidades de interacción por voz en tiempo real (*full-duplex*, manos libres y con detección natural de pausas y turnos).

El objetivo fundamental es añadir la voz no como un parche monolítico o un subsistema aislado, sino como una **nueva interfaz de usuario (UI)** que se integra limpiamente en la arquitectura modular existente de Noema. La voz debe ser un canal de presentación e interacción que consuma capacidades expuestas como **Servicios del Agente** (`AgentService`), manteniendo el núcleo cognitivo, el motor de razonamiento, la persistencia determinista y el sistema sensorial intactos e independientes del medio de transporte.

---

## 2. Contexto breve de Noema

Noema es un agente conversacional autónomo desarrollado en **Java 21/25**, diseñado para investigaciones y análisis de larga duración sobre una línea temporal continua. Entre sus pilares arquitectónicos destacan:

1. **Memoria Híbrida Determinista:** Desacopla la memoria de trabajo del contexto a largo plazo mediante puntos de consolidación narrativa (*CheckPoints*) y trazabilidad inmutable basada en identificadores de cita (`{cite:ID}`), respaldados por una base de datos embebida H2 (`SourceOfTruth`).
2. **Sistema Sensorial Proactivo:** Implementa el patrón `pool_event` y gestiona eventos de variada naturaleza (discretos, fusionables, agregables, de estado y de usuario) procedentes de múltiples canales.
3. **Multicanalidad por Subcanales:** Modela las interacciones mediante el concepto de `subchannel` (o `terminalId`), lo que permite aislar o sincronizar diferentes líneas de conversación.
4. **Servidor Web Embebido (`NoemaWebServer`):** Basado en Javalin, expone APIs REST y eventos SSE para clientes web y herramientas externas.
5. **Arquitectura Modular de Servicios (`AgentService`):** Las capacidades principales (Embeddings, Memoria, Razonamiento, Documentos, Email, Telegram, Scheduler) se estructuran como servicios independientes con ciclo de vida propio, registrados a través de `AgentServiceFactory` y configurados dinámicamente mediante `settings.json`.
6. **Múltiples UIs Existentes:** Noema ya soporta actualmente UIs desacopladas: consola REPL mediante JLine3 (`MainConsole`), interfaz gráfica Java Swing (`MainGUI`) y la interfaz web en desarrollo sobre Javalin.

---

## 3. Funcionalidades del UI de conversación de voz

La nueva interfaz de voz debe ofrecer una experiencia conversacional fluida, humana y continua, con las siguientes características operativas:

* **Modo "Manos Libres" (Full-Duplex):** El usuario no necesita pulsar botones para hablar (*push-to-talk*). El micrófono permanece abierto y el sistema detecta automáticamente cuándo el usuario empieza y termina de hablar.
* **Detección de Actividad de Voz (VAD) en Tiempo Real:** Análisis continuo del flujo de audio a nivel de tramas milimétricas para discriminar voz humana de ruidos de fondo o silencios.
* **Segmentación Natural de Turnos (*Endpointing*):** Determinación inteligente del final de una frase mediante temporización adaptativa de silencios (evitando cortar al usuario si hace una pausa breve para respirar o pensar).
* **Capacidad de Interrupción (*Barge-in*):** Si el agente está sintetizando voz o razonando y el usuario comienza a hablar, el sistema debe cancelar inmediatamente la locución y el proceso de generación actual, dando prioridad a la nueva intervención del usuario.
* **Sinergia y Sincronización Visual Multicanal:** Dado que la UI de voz comparte el mismo `subchannel` (`terminalId`) que la UI Web/REST/SSE, el texto transcribo de la voz del usuario y la respuesta textual del agente se reflejarán simultáneamente en la pantalla del cliente web si está abierta.
* **Sanitización de la Salida de Audio:** El canal de voz debe filtrar la jerga técnica, JSONs brutos o bloques de código derivados de la ejecución de herramientas (`tool_execution`), sintetizando únicamente la respuesta conversacional dirigida al usuario.

---

## 4. Arquitectura general y modelo de capas

El principio guía del diseño es la **estricta separación entre la Capa de Presentación (UI de Voz) y la Capa de Servicios del Agente (`AgentService`)**.

```
+-----------------------------------------------------------------------+
|                       CAPA DE PRESENTACIÓN (UI)                       |
|                                                                       |
|  [ Cliente Web (JS / WebAudio) ] <--- WebSocket ---> [ Voice UI ]    |
|                                                     (Javalin WS)      |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                    CONTROLADOR DE SESIÓN DE VOZ                       |
|                                                                       |
|                   [ VoiceConversation ]                               |
|                   - Búferes de tramas PCM                             |
|                   - Máquina de Estados (Listening/Speaking)           |
|                   - Silero VAD (ONNX Runtime)                         |
+-----------------------------------------------------------------------+
                                   |
         +-------------------------+-------------------------+
         |                         |                         |
         v                         v                         v
+------------------+     +-------------------+     +--------------------+
|  STTService      |     |  Agent Core       |     |  TTSService        |
|  (AgentService)  |     |  (putUsersMessage)|     |  (AgentService)    |
|  Audio -> Texto  |     |  Reasoning / H2   |     |  Texto -> Audio    |
+------------------+     +-------------------+     +--------------------+
```

### Principio de Responsabilidad Única
* **Capa UI de Voz (`NoemaWebServer` + Handler WebSocket + `VoiceConversation`):** Responsable exclusivamente de mantener la conexión WebSocket, procesar las tramas de audio entrantes con VAD, gestionar la máquina de estados del turno de voz y canalizar los datos.
* **Capa de Servicios de Agente (`STTService` y `TTSService`):** Encargada de la transformación de formato (Audio $\leftrightarrow$ Texto) como capacidades reutilizables del sistema.
* **Núcleo del Agente (`Agent` / `ReasoningServiceImpl`):** Recibe texto limpio procesado a través de sus métodos estándar (`agent.putUsersMessage`), de modo que no requiere conocer la existencia de tramas de audio, frecuencias de muestreo ni bytes PCM.

---

## 5. Nuevos Servicios del Agente: STTService y TTSService

Para dotar al agente de la capacidad de procesar voz sin acoplarlo a una UI concreta, se definen dos nuevos servicios modulares bajo la interfaz `AgentService`.

### 5.1. Servicio de Reconocimiento de Voz (`STTService`)
* **Propósito:** Transformar bloques o flujos de audio PCM en texto plano normalizado.
* **Abstracción mediante `AgentServiceFactory`:** Permitirá intercambiar el motor subyacente mediante configuración en `settings.json`:
  * *Proveedor Local:* Integración con modelos Whisper en formato ONNX o vía bindings nativos (ej. `whisper.cpp` / `inference4j`).
  * *Proveedor en la Nube:* Integración con APIs de alta velocidad (ej. Groq Whisper, OpenAI Whisper).
* **Reutilización Arquitectónica:** Al ser un `AgentService`, el servicio queda disponible no solo para la UI de voz, sino también para herramientas del agente (`AgentTool`), permitiendo en el futuro que el agente transcriba archivos de audio presentes en el workspace (`file_transcribe`).

### 5.2. Servicio de Síntesis de Voz (`TTSService`)
* **Propósito:** Convertir respuestas textuales del agente en tramas de audio sintetizado.
* **Diseño Orientado a Streaming:** Para minimizar la latencia percibida, el API de este servicio no operará únicamente con bloques de texto cerrados, sino que aceptará un flujo de texto entrante (tokens o oraciones completas delimitadas por signos de puntuación) e ir devolviendo fragmentos de audio a medida que los genera.
* **Abstracción mediante `AgentServiceFactory`:**
  * *Proveedor Local:* Motores ligeros como Piper TTS o Kokoro ONNX.
  * *Proveedor en la Nube:* APIs de baja latencia como Cartesia, ElevenLabs u OpenAI TTS.

---

## 6. Controlador de Sesión de Voz (`VoiceConversation`) y Abstracción de I/O

La clase `VoiceConversation` es el componente central que gobierna una sesión de voz individual. Se instancia al establecerse una conexión de voz y se destruye al cerrarse.

### 6.1. Contrato del Constructor y Desacoplamiento
Para garantizar la independencia total del framework web (Javalin), `VoiceConversation` **no recibe directamente objetos de infraestructura web** (como `WsContext`). En su lugar, el constructor recibe:

1. **`terminalId` (`subchannel`):** Identificador de la sesión conversacional en Noema.
2. **Instancia de `Agent`:** Para acceder a los servicios `STTService`, `TTSService` y al envío de mensajes.
3. **Callback Emisor de Audio (`audioEmitter`):** Una función o interfaz funcional (ej. `Consumer<OutputStream>` o `Consumer<byte[]>`) que encapsula el mecanismo de entrega de audio al cliente.

Gracias a este diseño, `VoiceConversation` es una clase Java pura, aislada y fácil de probar mediante tests unitarios sin necesidad de levantar un servidor HTTP/WebSocket.

### 6.2. Abstracción de Memoria I/O (`InputStream` / `OutputStream` vs. `byte[]`)
Para evitar la asignación obligatoria e ineficiente de arrays `byte[]` en el *heap* de la JVM en cada trama de audio (31 veces por segundo), la interfaz de `VoiceConversation` se abstrae mediante flujos:

* **En la Entrada (`processAudio`):** Recibe un flujo de entrada (`InputStream`) o un envoltorio ligero sobre los datos recibidos. Si el servidor WebSocket entrega un array de bytes, se envuelve en un `ByteArrayInputStream` (operación $O(1)$ sin copia de datos en memoria). Si la fuente es un archivo o un canal NIO, se procesa directamente sin pasar por el *heap*.
* **En la Salida (`audioEmitter`):** El callback de emisión recibe un `OutputStream` proporcionado por el llamante:
  * *Caso WebSocket (Javalin):* Se entrega el flujo directo del socket de red de Jetty/Javalin. El sintetizador escribe los bytes y estos viajan a la red sin crear arrays intermedios en la JVM.
  * *Caso Archivo/Debug:* Se entrega un `FileOutputStream` para grabar la sesión en disco.
  * *Caso Pruebas/Memoria:* Se entrega un `ByteArrayOutputStream`.

### 6.3. Búfer Interno de Acumulación
Internamente, mientras el usuario habla, `VoiceConversation` requiere acumular las pequeñas tramas de 32 ms recibidas hasta que el VAD decrete el fin de la frase. Para esta tarea interna, utiliza un `ByteArrayOutputStream` como acumulador dinámico, el cual se resetea (`reset()`) tras enviar el audio completo al `STTService`.

---

## 7. Procesamiento en Tiempo Real y VAD (Silero ONNX)

El análisis de la actividad de voz en el servidor requiere procesar el audio de entrada con baja latencia y bajo consumo de recursos.

### 7.1. Especificación de las Tramas de Audio
* **Formato:** PCM lineal de 16 bits (Int16), Mono (1 canal), a una frecuencia de muestreo de **16 kHz**.
* **Tamaño de Trama:** 512 muestras por iteración (que equivalen a 1024 bytes de audio y 32 milisegundos de duración).

### 7.2. Pipeline de Conversión y Normalización en Java
1. La trama de 1024 bytes entrante se interpreta como enteros de 16 bits (*Shorts*).
2. Cada valor entero se convierte a flotante y se normaliza dividiéndolo por `32768.0f`, produciendo una matriz de flotantes de dimensión `[1][512]` con valores en el rango `[-1.0, 1.0]`.

### 7.3. Ejecución de Silero VAD en ONNX Runtime
En cada ciclo de 32 ms, el modelo `silero_vad.onnx` se ejecuta pasando cuatro tensores de entrada:
1. `input`: La matriz de audio flotante `[1][512]`.
2. `sr`: Tensor escalar con el valor `16000` (Int64).
3. `h`: Tensor de estado oculto de la red recurrente (inicialmente ceros, dimensión `[2][1][64]`).
4. `c`: Tensor de estado celular de la red recurrente (inicialmente ceros, dimensión `[2][1][64]`).

### 7.4. Conservación de Estados Recurrentes
La inferencia devuelve la probabilidad de voz (`output`) y los nuevos estados ocultos (`hn` y `cn`). **Es un requisito crítico que `VoiceConversation` almacene `hn` y `cn` en su estado interno** para pasarlos como entradas `h` y `c` en la siguiente trama de 32 ms. Esto permite a Silero VAD mantener la memoria del contexto acústico entre tramas.

### 7.5. Máquina de Estados del Turno de Voz
`VoiceConversation` gestiona el turno mediante la siguiente máquina de estados:

```
                  +-----------------------------------+
                  |                                   |
                  v                                   | (Pausa corta)
          +---------------+  Prob > 0.5   +-----------------------+
-------> |   LISTENING   | ------------> |       SPEAKING        |
          +---------------+               +-----------------------+
                  ^                                   |
                  |                                   | Prob < 0.5
                  | (Cancelación /                    v
                  |  Fin de TTS)          +-----------------------+
                  |                       |   SILENCE_COUNTING    |
                  |                       +-----------------------+
                  |                                   |
                  |                                   | Silencio > 800ms
                  |                                   v
          +---------------+  Fin de STT   +-----------------------+
          | AGENT_SPEAKING| <------------ |      PROCESSING       |
          +---------------+  + LLM + TTS  +-----------------------+
```

* **`LISTENING` (Escuchando):** Espera que la probabilidad de voz supere el umbral de activación (ej. `0.5`).
* **`SPEAKING` (Hablando):** El usuario está emitiendo voz. Las tramas PCM se escriben continuamente en el acumulador interno.
* **`SILENCE_COUNTING` (Conteo de Silencio):** La probabilidad baja de `0.5`. Se inicia un temporizador de silencio. Si la probabilidad vuelve a subir antes de alcanzar el umbral de corte (ej. 600–800 ms), se regresa a `SPEAKING`.
* **`PROCESSING` (Procesando):** El tiempo de silencio superó el umbral. Se cierra la acumulación de audio, se detiene temporalmente la ingesta de VAD y se dispara el proceso asíncrono (STT $\rightarrow$ Agente $\rightarrow$ TTS).
* **`AGENT_SPEAKING` (Agente Hablando):** El agente está transmitiendo audio al cliente. El VAD continúa activo en segundo plano para detectar posibles interrupciones (*barge-in*).

---

## 8. Integración con el Servidor Web Javalin y WebSockets

El servidor web embebido `NoemaWebServer` expone las capacidades de voz extendiendo sus rutas sobre Javalin.

### 8.1. Mapeo de Rutas y Conexión
* **Endpoint WebSocket:** `/api/voice/{terminalId}`
* **Conexión Inicial (`onConnect`):**
  1. Extrae el parámetro `terminalId` del contexto de la conexión (`WsContext`).
  2. Define la función lambda emisora: `outputStream -> wsContext.send(...)`.
  3. Instancia la clase `VoiceConversation(terminalId, agent, emitter)`.
  4. Registra la instancia en el mapa de sesiones activas de `NoemaWebServer` y la adjunta como atributo a la conexión WS (`ctx.attribute("voiceSession", session)`).

### 8.2. Delegación de Mensajes Binarios (`onBinaryMessage`)
Cuando el cliente web envía una trama PCM de audio por el WebSocket:
1. Javalin recibe los bytes.
2. Recupera la instancia `VoiceConversation` del atributo de la conexión.
3. Invoca `session.processAudioChunk(new ByteArrayInputStream(ctx.data()))` (aprovechando el envoltorio de coste cero sin duplicar memoria).

### 8.3. Cierre y Limpieza (`onClose` / `onError`)
Cuando la conexión WebSocket se interrumpe:
1. Recupera la sesión `VoiceConversation`.
2. Invoca `session.close()`, lo que libera los tensores de ONNX Runtime, vacía los búferes y cancela los hilos de TTS activos.
3. Elimina la sesión del mapa de conexiones activas.

---

## 9. Control de Turnos, Proactividad y Cancelación (*Barge-in*)

La interacción conversacional en tiempo real requiere gestionar la concurrencia entre la emisión del agente y la entrada del usuario.

### 9.1. Flujo Normal de Turno
1. `VoiceConversation` detecta el final de la frase del usuario mediante el VAD.
2. Invoca `STTService.transcribe(audioAcumulado)` y obtiene el texto.
3. Envía el texto al agente mediante `agent.putUsersMessage(terminalId, texto, callback)`.
4. El motor de razonamiento de Noema procesa la entrada en su bucle estándar (`eventDispatcher`).
5. A medida que el agente genera la respuesta textual, se alimenta al `TTSService`.
6. El audio sintetizado se transmite en tramas al cliente web a través del callback `audioEmitter`.

### 9.2. Mecanismo de Interrupción (*Barge-in*)
Si el agente se encuentra en el estado `AGENT_SPEAKING` (sintetizando o reproduciendo audio) y el usuario comienza a hablar:

1. El VAD de `VoiceConversation` (que sigue analizando el audio entrante) detecta que la probabilidad de voz supera el umbral `0.5`.
2. `VoiceConversation` conmuta de inmediato el estado a `SPEAKING`.
3. **Cancelación en el Agente:** Invoca el método de aborto sobre la generación en curso. Dado que `ReasoningServiceImpl` de Noema ya soporta la cancelación mediante la señal `MutableBoolean abort` en `model.generate()`, se detiene de forma inmediata la inferencia del LLM.
4. **Cancelación del TTS:** Se interrumpe la síntesis y se limpia el búfer de salida del WebSocket para frenar la locución en el cliente web.
5. El acumulador de audio de `VoiceConversation` comienza a guardar la nueva frase del usuario, reiniciando el ciclo sin inconsistencias de estado.

### 9.3. Integración con el Sistema Sensorial Proactivo
Si el agente recibe un evento proactivo externo (por ejemplo, una alarma del `SchedulerService` o una notificación entrante de `TelegramService` en el mismo `subchannel`), el evento se encola mediante `putEvent`. Si la sesión de voz está activa, la respuesta generada por el agente se canalizará automáticamente a través del `TTSService` hacia el usuario en forma de aviso hablado.

---

## 10. Persistencia, Historial y Sinergia Multicanal

Uno de los mayores beneficios de este diseño es que la voz se integra sin costuras con el sistema de persistencia y memoria determinista de Noema.

### 10.1. Registro en la Fuente de la Verdad (`SourceOfTruth`)
Cuando el `STTService` convierte el audio del usuario en texto, la llamada a `agent.putUsersMessage(terminalId, texto, ...)` provoca que la interacción se registre en la base de datos H2 (`turnos`) como un turno estándar de tipo `chat`.
Del mismo modo, la respuesta textual del agente se almacena en la base de datos antes de ser sintetizada por el `TTSService`.

### 10.2. Transparencia para la Memoria Híbrida
El servicio de memoria (`MemoryServiceImpl`) y el protocolo de compactación de Puntos de Guardado (`CheckPoint`) no requieren modificación alguna. Las conversaciones de voz se consolidan, resumen y citan (`{cite:ID}`) exactamente igual que las conversaciones de texto.

### 10.3. Sinergia en Pantalla (Espejado Multicanal)
Debido a que las lecturas y escrituras de la sesión de voz utilizan el `subchannel` nativo (`terminalId`):
* Si un usuario mantiene abierta la interfaz web gráfica (REST/SSE) y de forma paralela habla por el canal de voz usando el mismo `terminalId`, **verá aparecer la transcripción de su voz y el texto del agente en la pantalla en tiempo real** mientras escucha la locución.

---

## 11. Rendimiento, Memoria Nativa (*Off-Heap*) y Seguridad

La ejecución de modelos de IA en tiempo real sobre la JVM exige controles rigurosos de rendimiento y contención de recursos.

### 11.1. Liberación Estricta de Memoria Nativa en JNI
ONNX Runtime asigna memoria nativa fuera del *heap* de la JVM para representar tensores C++.
* Dado que el VAD ejecuta inferencia 31 veces por segundo por cada sesión de voz activa, es una regla de diseño obligatoria que **todos los tensores temporales creados en cada trama de 32 ms se cierren explícitamente (`OrtTensor.close()`)** inmediatamente después de la inferencia.
* Dejar la liberación de tensores en manos del recolector de basura (*Garbage Collector*) provocará indefectiblemente el agotamiento de la memoria RAM del sistema operativo (*out-of-memory* nativo).

### 11.2. Compartición de Sesiones ONNX (*Thread-Safety*)
El entorno global de ONNX (`OrtEnvironment`) y la sesión del modelo cargado (`OrtSession` para `silero_vad.onnx`) son seguros para acceso concurrente desde múltiples hilos.
* Se debe cargar una **única instancia compartida** de `OrtSession` al iniciar el servicio.
* Cada instancia de `VoiceConversation` mantendrá exclusivamente sus propios tensores de estado recurrente (`h` y `c`), reutilizando la sesión del modelo global para realizar sus inferencias.

### 11.3. Impacto en Red y Ancho de Banda
El flujo continuo de PCM lineal de 16 bits a 16 kHz mono supone una transferencia constante de aproximadamente **32 KB/s (256 kbps)**.
* En entornos de red local, aplicaciones de escritorio o conexiones de banda ancha, esta carga de red es despreciable y garantiza la mínima latencia al eliminar algoritmos de compresión/descompresión pesados en los extremos.

---

## 12. Resumen de la Estructura de Componentes

| Componente / Clase | Ubicación Arquitectónica | Responsabilidad Principal |
| :--- | :--- | :--- |
| **`STTService`** | `AgentService` (Servicios) | Transcribir audio a texto (Whisper local/API). Reutilizable por UIs y herramientas. |
| **`TTSService`** | `AgentService` (Servicios) | Sintetizar texto a audio en flujo continuo (Piper/API). Reutilizable por UIs y herramientas. |
| **`NoemaWebServer`** | Presentación / Red | Exponer el endpoint WebSocket `/api/voice/{terminalId}` y mapear eventos de red hacia `VoiceConversation`. |
| **`VoiceConversation`** | Controlador de UI de Voz | Gestionar el VAD (Silero ONNX), el búfer de tramas PCM, la máquina de estados de turno, el *barge-in* y el callback de salida. |
| **`SourceOfTruth`** | Persistencia (Core) | Registrar las transcripciones y respuestas textuales en H2 como turnos estándar de conversación. |
| **`ReasoningServiceImpl`** | Núcleo de Razonamiento | Procesar los mensajes recibidos, ejecutar herramientas y generar respuestas con soporte de aborto (`MutableBoolean abort`). |
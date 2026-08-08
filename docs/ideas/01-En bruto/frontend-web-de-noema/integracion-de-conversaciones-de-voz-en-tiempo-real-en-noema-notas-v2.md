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

### 4.1. Principio de Separación de Responsabilidades

El diseño arquitectónico para la voz en Noema se fundamenta en una estricta separación entre la **Capa de Presentación e I/O (UI)**, la **Capa de Control de Sesión**, la **Capa de Servicios de Agente (`AgentService`)** y el **Núcleo Cognitivo y de Persistencia (Core)**.

El objetivo de esta estructuración en capas es evitar que las complejidades del canal de voz (tramas de audio de 32 ms, frecuencias de muestreo, modelos VAD, reconexiones WebSocket o asignación de memoria nativa *off-heap*) contaminen el motor de razonamiento del agente. 

Para Noema, una interacción por voz no modifica su ciclo de vida cognitivo: el motor de razonamiento sigue recibiendo prompts textuales de usuario, ejecutando herramientas cuando lo requiere y registrando turnos conversacionales de forma determinista en la base de datos H2 (`SourceOfTruth`).

---

### 4.2. Diagrama Estructural de Capas

```
+-----------------------------------------------------------------------------------+
|                        1. CAPA DE PRESENTACIÓN / TRANSPORTE                       |
|                                                                                   |
|   [ Navegador Web / Cliente ]  <--- (REST / SSE / WS) --->  [ NoemaWebServer ]    |
|   - WebAudio (PCM 16kHz)                                    (Javalin 6)           |
+-----------------------------------------------------------------------------------+
                                                              |
                                                              v
+-----------------------------------------------------------------------------------+
|                     2. CAPA DE MULTIPLEXADO DE SALIDA (CONSOLE)                   |
|                                                                                   |
|                                [ CompositeConsole ]                               |
|                     - Registro único por terminalId / subchannel                  |
|                     - Slot CHAT_SSE  --->  [ SseAgentConsole ]                   |
|                     - Slot VOICE_WS  --->  [ VoiceConsole ]                       |
+-----------------------------------------------------------------------------------+
                                                              |
                                +-----------------------------+
                                |
                                v
+-----------------------------------------------------------------------------------+
|                         3. CAPA DE CONTROL DE SESIÓN DE VOZ                       |
|                                                                                   |
|                               [ VoiceConversation ]                               |
|   - Instancia por conexión de voz activa                                          |
|   - Silero VAD (ONNX Runtime) + Máquina de Estados (Listening / Speaking)         |
|   - Desacoplado de Javalin vía callback: audioEmitter(Consumer<OutputStream>)    |
|   - Acumulador interno de frases (ByteArrayOutputStream)                          |
+-----------------------------------------------------------------------------------+
                                |
        +-----------------------+-----------------------+
        |                                               |
        v                                               v
+---------------------------------------+   +---------------------------------------+
|   4. CAPA DE SERVICIOS (`AgentService`) |   |  5. CAPA COGNITIVA Y PERSISTENCIA     |
|                                       |   |                                       |
|  [ STTService ] (Whisper Local/API)   |   |  [ Agent / ReasoningServiceImpl ]     |
|  [ TTSService ] (Piper/API Streaming) |   |  - Bucle de eventos (eventDispatcher) |
|  [ SensorsService ] [ MemoryService ] |   |  - Cancelación activa (abort)         |
|  [ DocumentsService ] [ Scheduler ]   |   |  [ SourceOfTruth ] (H2 DB)            |
+---------------------------------------+   +---------------------------------------+
```

---

### 4.3. El Modelo de Multiplexado de Salida: `CompositeConsole`

El punto neurálgico que conecta el Núcleo del Agente con las diferentes interfaces de usuario sin generar acoplamientos ni duplicidades es la **`CompositeConsole`**.

#### 4.3.1. Contrato Único con el Agente
Para el motor de razonamiento (`ReasoningServiceImpl`), solo existe **una única consola registrada por cada `terminalId` (`subchannel`)**, la cual es siempre una instancia de `CompositeConsole`. El agente invoca sus métodos estándar de salida (como `printModelResponse` o `printSystemError`) sobre esta consola sin conocer qué medios de transporte (pantalla web, altavoz de voz o logs) están escuchando al otro lado.

#### 4.3.2. Slots Tipados por Canal
La `CompositeConsole` no almacena una lista ciega e indiferenciada de escuchadores, sino que gestiona **slots explícitos y tipados por canal de salida**:
* **Slot `CHAT_SSE`:** Alberga la instancia de `SseAgentConsole`, encargada de enviar eventos de texto Server-Sent Events hacia el navegador.
* **Slot `VOICE_WS`:** Alberga la instancia de `VoiceConsole` (asociada a `VoiceConversation`), encargada de recibir el texto generado, llamar a `TTSService` y emitir tramas de audio sintetizado.

#### 4.3.3. Difusión Transparente (Broadcast)
Cuando el agente emite un mensaje —ya sea en respuesta inmediata a una pregunta del usuario o de forma asíncrona debido a un evento proactivo (como una alarma programada o la llegada de un correo)—, la `CompositeConsole` retransmite el texto a todos los slots activos en ese momento. Si ambos slots están ocupados, el mensaje se muestra visualmente en pantalla y se reproduce simultáneamente por el altavoz.

---

### 4.4. Delimitación de Fronteras y Responsabilidades

#### A. `NoemaWebServer` (Servidor HTTP / WebSocket)
* **Responsabilidad:** Fontanería de red e infraestructura web.
* **Función:** Expone los endpoints REST, SSE y WebSocket. Mantiene el registro de instancias `CompositeConsole` por `terminalId`. Inspecciona los slots para determinar si debe añadir clientes a una consola existente o reemplazar una sesión de voz previa.

#### B. `VoiceConversation` (Controlador de Sesión de Voz)
* **Responsabilidad:** Procesamiento de audio en tiempo real y lógica del turno de voz.
* **Función:** Recibe tramas PCM, ejecuta la inferencia de Silero VAD con ONNX Runtime, gestiona la máquina de estados del habla (*LISTENING*, *SPEAKING*, *WAITING_SILENCE*) y orquesta la conversión Audio $\rightarrow$ Texto $\rightarrow$ Agente $\rightarrow$ Texto $\rightarrow$ Audio.
* **Desacoplamiento:** Se comunica con la red únicamente a través de un callback emisor basado en flujos (`Consumer<OutputStream>`), ignorando por completo la existencia de Javalin o WebSockets.

#### C. `STTService` y `TTSService` (Servicios del Agente)
* **Responsabilidad:** Transformación de formato entre medios (Voz $\leftrightarrow$ Texto).
* **Función:** Implementados como `AgentService` de pleno derecho, abstraen los motores concretos (Whisper, Piper, Cartesia, Groq) detrás de interfaces estándar. Son reutilizables tanto por la UI de voz como por herramientas internas del agente.

#### D. `Agent` y `SourceOfTruth` (Núcleo y Persistencia)
* **Responsabilidad:** Razonamiento, ejecución de herramientas y persistencia de memoria.
* **Función:** Procesa los prompts enviados a través de `agent.putUsersMessage`, ejecuta la lógica de herramientas y persiste la conversación en la base de datos H2. No contiene lógica ni dependencias relacionadas con voz o audio.

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

### 6.1. Propósito y Principios de Diseño

La clase `VoiceConversation` es el componente encargado de encapsular el estado y la lógica de procesamiento de una sesión de voz activa. Actúa como un **traductor de frecuencia y formato**: transforma el flujo continuo de alta frecuencia (32 milisegundos por trama de audio) en eventos conversacionales discretos que el núcleo de Noema puede comprender.

Los principios rectores de su diseño son:
1. **Aislamiento de Estado:** Cada sesión de voz mantiene sus propios búferes de audio, sus propios tensores de memoria para la red VAD y su propia máquina de estados conversacional.
2. **Independencia del Transporte:** No tiene dependencias con frameworks web, Servlets ni WebSockets. Es una clase Java pura.
3. **Gestión Eficiente del Heap:** Minimiza la asignación de memoria controlada por el recolector de basura (*Garbage Collector*) mediante abstracciones de flujo en lugar de arrays de bytes fijos.

---

### 6.2. Contrato del Constructor y Desacoplamiento de Red

Para garantizar que `VoiceConversation` sea completamente agnóstica de la infraestructura de red, su constructor **no acepta objetos de contexto de Javalin** (como `WsContext`). En su lugar, su contrato de instanciación requiere únicamente tres parámetros:

1. **`terminalId` (`subchannel`):** Identificador del subcanal de conversación en Noema.
2. **Referencia a `Agent`:** Objeto central para acceder a los servicios `STTService`, `TTSService` y al envío de mensajes al agente.
3. **Callback Emisor de Audio (`audioEmitter`):** Una función o interfaz funcional con la firma `Consumer<OutputStream>`.

### Beneficios del Callback basado en `OutputStream`
Mediante este callback, cuando `VoiceConversation` genera un fragmento de audio sintetizado procedente del `TTSService`, no se lo devuelve a un cliente específico, sino que invoca a `audioEmitter.accept(outputStream)`. 

El emisor (suministrado por el servidor web) le entrega el `OutputStream` donde debe depositar los bytes:
* **En producción (Javalin / WebSocket):** La lambda pasa el flujo de salida del *socket* de red. Los bytes viajan directamente a la tarjeta de red sin instanciar objetos intermedios.
* **En grabación / auditoría:** La lambda pasa un `FileOutputStream` para almacenar la voz en disco.
* **En tests unitarios:** La lambda pasa un `ByteArrayOutputStream` para inspeccionar la respuesta en memoria.

---

### 6.3. Abstracción de Memoria I/O: Eficiencia y Gestión del Heap

Uso de `byte[]` frente a flujos en el API público de `VoiceConversation`:

#### 6.3.1. El problema de `byte[]` en el API Público
Forzar el uso de `byte[]` en los métodos de entrada y salida impone restricciones de memoria rígidas:
* Obliga a instanciar arrays en el *heap* de la JVM a una frecuencia de 31 veces por segundo por cada sesión activa.
* Exige que el 100% de la trama esté cargado en memoria RAM administrada.
* Provoca copias de memoria innecesarias si el destino final era un archivo o un *socket* nativo.

#### 6.3.2. La solución mediante Flujos (`InputStream` / `OutputStream`)
Al definir el método de entrada como `processAudioChunk(InputStream input)` y la salida como `Consumer<OutputStream>`, el control de la ubicación física de la memoria se traslada al llamante:
* **Zero-Copy hacia la red:** Las tramas de audio sintetizadas se escriben directamente en el flujo de salida de la red sin permanecer en el *heap* de Java.
* **Zero-Copy hacia disco:** Si se requiere registrar el audio, se canaliza directamente a un flujo de archivo.

---

### 6.4. Búfer Interno de Acumulación de Frases

Es necesario distinguir entre el procesamiento de tramas milimétricas (VAD) y la acumulación de la frase completa para el reconocedor de voz (STT):

```
Entrada: Tramas de 32 ms (1024 bytes)
  |--> Inferencia VAD (Silero ONNX) ----------------------> Probabilidad de voz
  |
  +--> Si estado == SPEAKING 
         |--> Escribir en ByteArrayOutputStream interno
         |
         +--> Si VAD decreta Fin de Frase (>800 ms silencio)
                |
                +--> outputStream.toByteArray() -> STTService
                +--> outputStream.reset()
```

1. **Fase de Evaluación (Cada 32 ms):** La trama de entrada se procesa inmediatamente en el modelo Silero VAD para determinar si contiene voz.
2. **Fase de Acumulación (Estado `SPEAKING`):** Mientras el VAD confirme que el usuario está hablando, cada trama recibida se escribe en un `ByteArrayOutputStream` interno que actúa como acumulador de la frase.
3. **Fase de Cierre de Frase (`PROCESSING`):** Cuando el VAD detecta una pausa continua superior al umbral configurado (ej. 800 ms), el acumulador se cierra, entrega el bloque completo de audio a `STTService.transcribe()` y ejecuta inmediatamente un `reset()` para quedar limpio y reutilizable en el siguiente turno.

---

### 6.5. Adaptación para Entradas basadas en `byte[]` (Garantía de Coste $O(1)$)

En caso de que el framework de transporte (como el handler WebSocket de Javalin) entregue los paquetes de red directamente como un array de bytes (`byte[]`):

* El servidor web envuelve el array mediante `new ByteArrayInputStream(bytes)` antes de invocar a `VoiceConversation.processAudioChunk(...)`.
* **Garantía de Rendimiento:** En Java, crear un `ByteArrayInputStream` sobre un array existente es una operación de coste constante $O(1)$. No duplica el array en memoria ni realiza copias de datos; simplemente instancia un puntero ligero con índices de posición sobre el array original.
* Esto demuestra la simetría del diseño: soporta fuentes de flujo nativas sin copia y fuentes basadas en paquetes de bytes sin penalización de rendimiento.

---

### 6.6. Ciclo de Vida, Destrucción y Liberación de Memoria Nativa (*Off-Heap*)

La clase `VoiceConversation` gestiona recursos nativos C++ asignados por ONNX Runtime fuera de la memoria administrada por el recolector de basura de Java. Su ciclo de vida incluye un protocolo de destrucción estricto.

#### 6.1.1. Inicio de Sesión
Al crearse la sesión, `VoiceConversation` inicializa los tensores recurrentes `h` y `c` (de tipo `OrtTensor`, dimensión `[2][1][64]`) rellenos de ceros en memoria *off-heap*, listos para mantener el estado contextual del VAD.

#### 6.1.2. Fin de Sesión y Método `close()`
Cuando la conexión de red se rompe, el usuario cambia de canal o el servidor web destruye la sesión, se invoca de forma obligatoria el método `close()` de `VoiceConversation`, el cual ejecuta en secuencia:

1. **Cierre de Tensores ONNX:** Invoca explícitamente `close()` sobre los objetos `OrtTensor` de los estados `h` y `c`. Esto libera inmediatamente la memoria RAM nativa asignada en C++, evitando fugas de memoria *off-heap*.
2. **Liberación de Búferes:** Vaciado y liberación del `ByteArrayOutputStream` interno de acumulación de frases.
3. **Cancelación de Tareas Activas:** Interrupción inmediata de cualquier hilo de síntesis de voz (`TTSService`) o proceso de transcripción (`STTService`) que estuviera en ejecución para esa sesión.
4. **Desenganche de la Consola:** Eliminación de la referencia de la sesión en el slot correspondiente de la `CompositeConsole`.

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

## 8. Integración con Javalin y `NoemaWebServer`

### 8.1. Responsabilidad del Servidor Web y Gestión de Enrutamiento

El servidor web embebido `NoemaWebServer` (construido sobre **Javalin 6**) actúa como la **puerta de enlace de infraestructura de red** de Noema. Su función es exponer los endpoints HTTP REST, los eventos SSE y los canales de WebSocket, canalizando el tráfico desde y hacia el agente.

`NoemaWebServer` se mantiene completamente al margen de la lógica de procesamiento de audio, VAD o sintaxis de modelos. Su único cometido en la capa de voz es gestionar las conexiones WebSocket, mantener la trazabilidad de los clientes y delegar los eventos binarios a la sesión conversacional correspondiente.

---

### 8.2. Mapa Central de Consolas Compuestas (`terminalConsoles`)

Para eliminar cualquier posibilidad de sobrescribir consolas o perder eventos de salida cuando coexisten múltiples clientes sobre el mismo `terminalId` (`subchannel`), `NoemaWebServer` sustituye la gestión individual de consolas por un mapa centralizado de multiplexado:

* **Estructura del Registro:** `Map<String, CompositeConsole> terminalConsoles`
* **Garantía de Instancia Única en el Agente:** Para un `terminalId` determinado, el servidor registra **una única vez** la `CompositeConsole` en el agente mediante `agent.setConsole(terminalId, compositeConsole)`.
* **Persistencia del Contenedor:** La `CompositeConsole` permanece asociada al `terminalId` en el agente mientras exista al menos un canal activo (sea de texto web o de voz).

---

### 8.3. Registro y Slots Tipados por Tipo de Canal

La `CompositeConsole` no maneja una lista plana e indocumentada de escuchadores, sino un conjunto de **slots tipados explícitamente**:

1. **Slot `CHAT_SSE` (Texto Web):** Gestionado por la clase `SseAgentConsole`. Diseñado para manejar concurrencia multicliente, ya que mantiene una lista interna hilos-segura (`List<SseClient>`) con todas las pestañas web o navegadores conectados a ese subcanal.
2. **Slot `VOICE_WS` (Audio WebSocket):** Gestionado por la clase `VoiceConsole` (asociada a `VoiceConversation`). Diseñado para canalizar la entrada/salida de audio en tiempo real de una sesión de voz activa.

VoiceConsole es una implementación de AgentConsole que recibe texto y lo pasa a VoiceConversation para su síntesis y emisión por WebSocket.

---

### 8.4. Gestión Determinista de Reconexiones, Pestañas Múltiples y Reemplazo

El servidor web utiliza la API de consulta de la `CompositeConsole` (`hasConsole(type)`) para resolver de forma totalmente determinista las colisiones de conexión según el tipo de canal:

#### 8.4.1. Flujo de Entrada de Chat Web (`/api/console/{terminalId}`)
1. Un cliente abre una conexión SSE en el navegador.
2. `NoemaWebServer` solicita la `CompositeConsole` para ese `terminalId` (creándola y registrándola en el agente si era la primera conexión del subcanal).
3. `NoemaWebServer` consulta si el slot `CHAT_SSE` ya existe:
   * **Si NO existe:** Instancia una nueva `SseAgentConsole`, registra el cliente SSE en ella y la conecta al slot `CHAT_SSE` de la `CompositeConsole`.
   * **Si YA existe (Pestaña duplicada o recarga):** **No crea una nueva consola**. Recupera la `SseAgentConsole` existente y simplemente le añade el nuevo cliente (`sseConsole.addClient(client)`).

#### 8.4.2. Flujo de Entrada de Voz WebSocket (`/api/voice/{terminalId}`)
1. Un cliente establece una conexión WebSocket de voz.
2. `NoemaWebServer` solicita la `CompositeConsole` para ese `terminalId` (creándola y registrándola en el agente si era la primera conexión).
3. `NoemaWebServer` consulta si el slot `VOICE_WS` ya existe:
   * **Si NO existe:** Instancia `VoiceConversation` (pasándole la lambda emisora `data -> wsContext.send(data)`), crea su envoltorio `VoiceConsole` y la conecta al slot `VOICE_WS`.
   * **Si YA existe (Reconexión o nueva llamada por voz):** Para evitar que dos micrófonos/altavoces compitan sobre el mismo subcanal:
     1. Extrae la `VoiceConversation` antigua del slot `VOICE_WS`.
     2. Invoca explícitamente su método de limpieza (`oldVoice.close()`), liberando inmediatamente los tensores de ONNX Runtime en C++ y cerrando sus búferes.
     3. Instancia la nueva `VoiceConversation` y reemplaza el slot `VOICE_WS`.

---

### 8.5. Delegación de Tramas Binarias y Adaptación Zero-Copy

Durante el mantenimiento de una sesión de voz activa:

1. El navegador envía tramas PCM de audio a través del WebSocket.
2. Javalin captura el evento de mensaje binario (`onBinaryMessage`).
3. `NoemaWebServer` recupera la `VoiceConversation` asociada a esa conexión desde los atributos del contexto WebSocket.
4. `NoemaWebServer` entrega la trama invocando `voiceSession.processAudioChunk(new ByteArrayInputStream(ctx.data()))`.
5. **Cero Impacto en Memoria:** La creación del `ByteArrayInputStream` sobre el array de bytes que entrega Javalin no realiza copias de datos en la JVM ($O(1)$), permitiendo que `VoiceConversation` procese las tramas a 32 ms con máxima eficiencia.

---

### 8.6. Protocolo de Desconexión y Limpieza Total de Recursos

La destrucción de conexiones se gestiona de forma escalonada para evitar fugas de memoria nativa o punteros colgados:

1. **Desconexión de Pestaña Web SSE:**
   * Javalin detecta el cierre del cliente SSE.
   * `SseAgentConsole` elimina al cliente de su lista interna.
   * Si la `SseAgentConsole` se queda sin clientes activos, vacía el slot `CHAT_SSE` de la `CompositeConsole`.

2. **Desconexión de Canal de Voz WebSocket:**
   * Javalin detecta el cierre o error del WebSocket (`onClose` / `onError`).
   * `NoemaWebServer` vacía el slot `VOICE_WS` de la `CompositeConsole`.
   * Invoca `voiceConversation.close()`, lo que ejecuta la liberación de tensores nativos C++ de Silero VAD y limpia los acumuladores de audio.

3. **Limpieza del Subcanal en el Agente:**
   * Tras cualquier desconexión, `NoemaWebServer` verifica si la `CompositeConsole` del `terminalId` se ha quedado sin slots activos (sin Chat y sin Voz).
   * Si no quedan canales escuchando, el servidor elimina la `CompositeConsole` de su mapa `terminalConsoles` y notifica al agente liberando el subcanal: `agent.setConsole(terminalId, null)`.

---

## 9. Control de Turnos, Proactividad y Cancelación (*Barge-in*)

### 9.1. Tipología de Turnos: Reactivos vs. Proactivos

Para gestionar la salida vocal de Noema sin generar fallos de sincronización, la arquitectura distingue claramente entre dos tipos de turnos conversacionales:

1. **Turnos Reactivos (Iniciados por el Usuario):** Ciclos de solicitud-respuesta donde el usuario emite una frase por voz, el sistema la transcribe y el agente genera una respuesta directa a dicha consulta.
2. **Turnos Proactivos (Iniciados por el Agente):** Mensajes generados de forma asíncrona por el motor de razonamiento debido a eventos de sensores en segundo plano (por ejemplo, una alarma del `SchedulerService` que salta a los 10 minutos, la llegada de un correo importante de `EmailService` o una notificación de sistema). En estos turnos no existe una petición previa del usuario en ese instante.

---

### 9.2. Flujo de Turnos Reactivos

En un turno reactivo, la secuencia de ejecución sigue este protocolo:

1. **Captura y VAD:** `VoiceConversation` procesa las tramas de entrada. Cuando el VAD decreta el fin de la frase (silencio continuo > 800 ms), cierra el acumulador interno de audio.
2. **Transcripción:** Invoca a `STTService.transcribe(audioAcumulado)` y obtiene el texto plano.
3. **Ingreso en el Agente:** `VoiceConversation` envía el texto al agente mediante `agent.putUsersMessage(terminalId, texto, callback)`.
4. **Procesamiento Cognitivo:** El motor de razonamiento de Noema (`ReasoningServiceImpl`) procesa el mensaje en su bucle estándar (`eventDispatcher`), ejecutando herramientas si la intención lo requiere.

---

### 9.3. Flujo de Turnos Proactivos y Eventos de Sensores

En un turno proactivo, la iniciativa nace en la capa sensorial:

1. **Inyección del Evento:** Un servicio de fondo (por ejemplo, `SchedulerService`) deposita un evento en la cola de percepciones del `SensorsService` para un `subchannel` (`terminalId`) determinado.
2. **Procesamiento del Evento:** El `eventDispatcher` de Noema consume el evento de la cola. El modelo de lenguaje evalúa la notificación y decide emitir un mensaje hacia el usuario (ejemplo: *"Recuerda que han pasado 10 minutos y debes revisar el proceso"*).
3. **Emisión de Salida:** Puesto que en este turno no hubo un mensaje previo del usuario ni un callback asociado, el `eventDispatcher` envía la respuesta textual directamente a la consola del subcanal invocando `console.printModelResponse(texto)`.

---

### 9.4. Unificación de Salida y Prevención de Duplicidad de Audio

Para evitar que el canal de voz procese la misma respuesta dos veces (una vía callback y otra vía consola), la arquitectura establece un **canal único de emisión de salida conversacional**:

```
                               +-----------------------------------+
                               |     ReasoningServiceImpl          |
                               |  (Turno Reactivo o Proactivo)     |
                               +-----------------------------------+
                                                 |
                                                 v
                               +-----------------------------------+
                               |   CompositeConsole (terminalId)   |
                               +-----------------------------------+
                                       |                   |
                     +-----------------+                   +-----------------+
                     |                                                       |
                     v                                                       v
         [ Slot CHAT_SSE ]                                       [ Slot VOICE_WS ]
         (SseAgentConsole)                                    (VoiceConversationConsole)
                 |                                                       |
                 v                                                       v
   Texto SSE -> Pantalla Web                               Texto -> TTSService -> Audio WS
```

#### Reglas de Unificación:
1. **Todas las respuestas conversacionales** generadas por el agente para un `terminalId` (tanto si provienen de un turno reactivo como proactivo) se canalizan exclusivamente a través de `console.printModelResponse(texto)`.
2. La `CompositeConsole` registrada para ese `terminalId` recibe el texto y lo retransmite en paralelo a sus slots activos:
   * **Slot `CHAT_SSE`:** Envía el evento de texto al cliente web para mostrarlo en pantalla.
   * **Slot `VOICE_WS` (`VoiceConversationConsole`):** Entrega el texto a `VoiceConversation`, que lo transfiere a `TTSService.synthesize(texto)` y transmite los paquetes de audio resultantes a través del WebSocket.
3. Al canalizar toda la salida conversacional por la `CompositeConsole`, se garantiza que:
   * No existe duplicación de audio (el callback de `putUsersMessage` se limita a notificar el fin de turno sin reemitir texto).
   * La voz y la pantalla están 100% sincronizadas en eventos reactivos y proactivos.
   * Si la web está cerrada pero el canal de voz está activo, los avisos proactivos (alarmas) se escuchan correctamente por el altavoz.

---

### 9.5. Mecanismo de Cancelación e Interrupción Activa (*Barge-in*)

El fenómeno de *Barge-in* ocurre cuando el agente está sintetizando voz o reproduciendo audio por el altavoz del cliente y el usuario comienza a hablar, interrumpiendo al agente.

```
Estado Agente: AGENT_SPEAKING (Sintetizando / Reproduciendo Audio)
                               |
Usuario emite voz ------------>| VAD (Silero ONNX) detecta Prob > 0.5
                               |
                               +---> 1. Cancelar flujo de audio WebSocket (Limpiar búfer)
                               +---> 2. Invocar TTSService.stop()
                               +---> 3. Activar abort.setValue(true) en ReasoningServiceImpl
                               +---> 4. Conmutar estado a SPEAKING (Acumular voz usuario)
```

#### Protocolo de Interrupción en Cascada:
1. **Detección Acústica Continuada:** Durante el estado `AGENT_SPEAKING`, el VAD de `VoiceConversation` continúa analizando las tramas de audio de 32 ms que el micrófono del usuario sigue enviando por el WebSocket.
2. **Disparo por Umbral:** Cuando la probabilidad de voz de Silero VAD supera el umbral `0.5`, `VoiceConversation` detecta la intención de interrupción.
3. **Freno Inmediato de Salida de Audio:**
   * `VoiceConversation` ordena detener la generación en el `TTSService`.
   * Envia una señal de control/limpieza sobre la conexión WebSocket para que el cliente web purgue su búfer de reproducción de `WebAudio` local de inmediato, silenciando el altavoz.
4. **Cancelación del Razonamiento en el Núcleo:**
   * Invoca el mecanismo nativo de cancelación de Noema pasando `abort.setValue(true)` al hilo de generación de `ReasoningServiceImpl`. Esto detiene inmediatamente la inferencia del LLM si el modelo aún estaba generando tokens.
5. **Conmutación de Estado:**
   * `VoiceConversation` conmuta su estado de `AGENT_SPEAKING` a `SPEAKING`.
   * Resetea el acumulador interno de audio y comienza a guardar la nueva frase del usuario, iniciando un nuevo turno reactivo sin colisiones de estado.

   
El TTSService debe exponer un método de cancelación para interrumpir la síntesis en curso, y VoiceConversation debe tener acceso a él.

---

### 9.6. Sanitización de Contenido para Síntesis Vocal (Filtro de Herramientas)

El canal de texto muestra rutinariamente logs de sistema, llamadas a herramientas (`tool_call`) y resultados técnicos. El canal de voz requiere un **filtro de sanitización** antes de entregar el texto a `TTSService`:

1. **Filtrado de Logs Operativos:** Las salidas de ejecución de herramientas (`tool_execution` o `tool_execution_summarized`) no se envían a sintetizar en voz. El usuario no debe escuchar estructuras JSON brutos ni códigos de estado.
2. **Filtrado de Markdown y Bloques de Código:** El texto destinado a `TTSService` se despoja de etiquetas HTML, caracteres de formato Markdown (asteriscos, almohadillas) y bloques de código complejos, convirtiéndolos en lenguaje hablado natural.
3. **Conversión de Expresiones:** Marcadores como citas de memoria (`{cite:123}`) o URLs se eliminan o transforman en menciones conversacionales para evitar que el sintetizador vocal lea código de control.

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

## Anexo I

Como ejercicio de exploracion de ideas me he planteado lo posibilidad de usar Noema como un asistente de voz como pueden ser Alexa, Google Assistant o Siri.

### La integración con el entorno (El ecosistema de herramientas)

Para controlar dispositivos, Noema no necesitaría reventar su arquitectura. Bastaría con crear un servicio `HomeAssistantService` o una herramienta `HomeAssistantTool` que consuma la API REST/WebSocket de un servidor de automatización del hogar (como *Home Assistant*).

Con una única herramienta que exponga métodos como `set_device_state(entity_id, state)`, el LLM pasaría a controlar luces, termostatos o persianas de forma inmediata.

### La ventaja real: Asistencia cognitiva vs. Control remoto por voz

Donde Noema supera conceptualmente a Alexa no es en encender bombillas, sino en la **capacidad de razonamiento, memoria y proactividad**:

* **Alexa:** "Pon una alarma a las 7:00." (Ejecuta un comando rígido sin contexto).
* **Noema:** Dispone de memoria narrativa (`SourceOfTruth`), contexto de usuario y sensores proactivos. Puede evaluar la situación global:

> *"He visto que mañana tienes una reunión importante a las 8:00 y la previsión indica lluvia intensa a primera hora `{cite:102}`. Si quieres, puedo programar la alarma a las 6:45 y avisarte si el tráfico empeora."*


### Viabilidad técnica y opciones en la JVM

Usar OpenWakeWord (Opción ideal para Noema). Es un proyecto *open source* diseñado específicamente para ejecutarse sobre ONNX Runtime. 
* **Ventaja clave:** Al usar el mismo *runtime* que Silero VAD (`onnxruntime` en Java), **no requiere añadir ninguna dependencia nativa nueva al `pom.xml`**.
* **Consumo:** Analiza ventanas de audio de unos 80 ms. El consumo de CPU es mínimo (entorno al 1% - 3% de un solo núcleo).
* **Modelos:** Incluye modelos preentrenados para palabras comunes (*"Hey Jarvis"*, *"Alexa"*, *"OK Google"*, *"Computer"*).


### Cómo se integraría en la arquitectura de Noema

El detector de *Wake Word* se situaría como la **primera barrera de filtrado** dentro de `VoiceConversation`, modificando ligeramente la máquina de estados:

```
[Entrada PCM 16kHz desde WebSocket / Micrófono]
                         │
                         v
              +--------------------+
              |   Estado: IDLE     | <── (Modo reposo: solo escucha Wake Word)
              +--------------------+
                         │
        (Model OpenWakeWord ONNX: Prob > 0.7)
                         │
                         v
              +--------------------+
              |  Estado: LISTENING | <── (Se activa Silero VAD)
              +--------------------+
                         │
             (Inicia flujo normal VAD -> STT -> Agente)
```

#### Flujo de procesamiento:
1. **Estado `IDLE` (Reposo):** Las tramas de audio de 32 ms que llegan por la red **no pasan por Silero VAD ni se acumulan en memoria**. Solo se evalúan en el modelo ONNX de *Wake Word*.
2. **Detección:** Cuando la probabilidad del *Wake Word* supera el umbral, la sesión emite un pitido corto de confirmación (*beep*) por el altavoz y conmuta al estado `LISTENING`.
3. **Procesamiento conversacional:** A partir de ese momento, se activa Silero VAD para capturar la frase del usuario, enviarla a `STTService` y procesarla con el agente.
4. **Retorno a Reposo:** Tras responder el agente (o tras un tiempo de inactividad sin que el usuario vuelva a hablar), la sesión conmuta de nuevo a `IDLE`.

---

### El principal reto: Entrenar el nombre "Noema"

Si se utilizan palabras de activación estándar como *"Computer"* o *"Hey Jarvis"*, existen modelos ONNX preentrenados listos para descargar y usar.

Si se desea que la palabra de activación sea exactamente **"Noema"**:
* Entrenar un modelo para OpenWakeWord no requiere código complejo, pero exige generar un conjunto de datos con muestras de audio de la palabra mezcladas con ruidos de fondo.
* El modelo resultante es un archivo `.onnx` de apenas 2 MB que se coloca en la carpeta `var/models/` del agente.

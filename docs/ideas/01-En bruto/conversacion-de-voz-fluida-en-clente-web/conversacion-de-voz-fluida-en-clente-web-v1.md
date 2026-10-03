# Documento de Definición y Diseño Técnico: Conversación de Voz Fluida en Noema Web

---

## 1. ¿Qué queremos? (Objetivos y Principios)

### 1.1. Propósito
Dotar a Noema de **capacidad de conversación oral interactiva y fluida** a través de su interfaz web de usuario. El objetivo no es implementar un botón de dictado puntual ni un lector de pantalla asistencial, sino transformar la interacción en una **experiencia conversacional en tiempo real** (manos libres, baja latencia e interrupciones naturales), equiparable a una llamada telefónica con un compañero de laboratorio.

### 1.2. Principios Fundamentales de Diseño
1. **Unicidad de Memoria y Contexto:** La voz no opera en un silo desconectado. Las interacciones habladas comparten la misma sesión, la misma memoria operativa (`RecentMemory`) y el mismo registro histórico inmutable (`EpisodicMemory`) que la consola de texto activa. Se puede alternar entre teclado y voz dentro del mismo hilo conversacional sin fisuras.
2. **Desacoplamiento Estricto entre Oralidad y Densidad Visual:** Hablar no es leer un documento. La salida hablada debe ser concisa, cálida y directa. Los bloques densos de información (código fuente, tablas, listas largas, diferencias unificadas) no deben ser dictados por el sintetizador, sino proyectados visualmente en la pantalla a través de herramientas dedicadas.
3. **Soberanía y Máximo Control en el Servidor:** La captura y reproducción física ocurren en el navegador (terminal tonto), pero el procesamiento de audio (detección de habla, transcripción, síntesis y control de turnos) está coordinado por el servidor web embebido de Noema. Se descartan las voces y motores de reconocimiento nativos del navegador por su falta de homogeneidad, latencia errática y tono robótico.
4. **Respeto a la Arquitectura Central de Noema:** El motor cognitivo (`ReasoningService`), la memoria proyectada y el catálogo de herramientas siguen operando sobre texto. La voz es una capa sensorial periférica (transporte bidireccional), sin requerir modelos multimodales nativos cerrados que desmantelarían el sistema de control de memoria de Noema.

---

## 2. Descripción Funcional

### 2.1. Modelo Mental: "La Llamada con Pizarra Compartida"
El usuario percibe la sesión web como una llamada continua con Noema mientras ambos tienen delante una pantalla común:
* Cuando el usuario habla, sus palabras se transcriben en pantalla en tiempo real como su burbuja de mensaje.
* Cuando Noema responde, habla de forma coloquial y breve. Al mismo tiempo, sus palabras se escriben en pantalla a modo de subtítulos continuos.
* Si durante la conversación se debate una solución técnica, Noema dice oralmente: *"Te lo he dejado en pantalla; fíjate en el bloque synchronized"*, mientras en la conversación web aparece una tarjeta visual con el bloque de código completo.

### 2.2. Flujo Paso a Paso: Turno de Voz Estándar
1. **Detección y Escucha Activa:** El usuario habla de forma natural por el micrófono. Un detector de actividad de voz (VAD) identifica el inicio del habla y determina el momento exacto en que el usuario hace una pausa concluyente (silencio de fin de turno).
2. **Ingesta y Transcripción:** El audio se transcribe a texto en el servidor. Inmediatamente, la interfaz web muestra el texto transcrito como un mensaje de usuario en el chat.
3. **Razonamiento Oral:** Noema procesa el estímulo bajo directivas de "modo audio", generando una respuesta conversacional breve (habitualmente entre 30 y 80 tokens).
4. **Desdoblamiento de Salida (Dual Output):**
   * **Vía Pantalla (Subtítulos):** El texto de la respuesta viaja hacia la interfaz web y se renderiza en la burbuja del modelo a medida que se genera.
   * **Vía Audio (Locución):** El mismo texto se segmenta en oraciones sintácticas completas y se envía al sintetizador de voz (TTS). Los fragmentos de audio resultantes se reproducen en los altavoces/auriculares del usuario con latencia mínima.

### 2.3. Flujo Paso a Paso: Consulta con Contenido Visual Rico ("Uso de Pizarra")
1. **Petición del Usuario:** El usuario solicita verbalmente ver una estructura: *"¿Cómo implementarías el método de guardado en disco?"*.
2. **Decisión del Modelo:** El LLM detecta que la respuesta requiere sintaxis densa. Al estar bajo directivas de voz, tiene prohibido dictar el código por el canal oral. En su lugar, invoca la herramienta visual (`display_screen`).
3. **Proyección Visual Silenciosa:**
   * La herramienta se ejecuta en el servidor y envía el bloque de código directamente a la pantalla web.
   * La interfaz gráfica renderiza una tarjeta de artefacto con resaltado de sintaxis, formato monoespaciado y botón de copiado.
   * **El motor de síntesis de voz ignora totalmente este contenido.**
4. **Explicación Verbal Acompañante:** El modelo emite su respuesta hablada para comentar la solución: *"Te acabo de proyectar el método. Utiliza un bloque try-with-resources para garantizar que los canales de escritura se cierren si ocurre un fallo"*.
5. **Resultado Percibido:** El usuario ve el código aparecer en la pantalla y escucha la explicación oral de forma sincronizada.

### 2.4. Flujo de Interrupción en Caliente (*Barge-in*)
1. Durante una locución de Noema, el usuario empieza a hablar (*"espera, no uses esa librería..."*).
2. El VAD en el servidor detecta inmediatamente energía de voz entrante.
3. El servidor emite una señal de corte prioritaria hacia el navegador.
4. El navegador detiene la reproducción del audio en ese milisegundo y vacía el búfer de reproducción.
5. El servidor aborta la síntesis pendiente y abre un nuevo ciclo de razonamiento para atender la interrupción.

---

## 3. Diseño Técnico y Arquitectura de Software

```
                     ┌──────────────────────────────────────────────┐
                     │              NAVEGADOR WEB (SPA)             │
                     │                                              │
                     │  [AudioWorklet]                [AudioBuffer] │
                     │  Captura Micro                  Reproductor  │
                     └──────┬───────────────────────────────▲───────┘
                            │ Binary                        │ Binary
                            │ (Opus/PCM)                    │ (Audio Chunks)
                            │                               │
                            │        WEBSOCKET CANAL        │
                            │   /api/voice/{terminalId}     │
                            │                               │
                            ▼                               │
┌───────────────────────────────────────────────────────────┴──────────────────────────────┐
│ SERVIDOR WEB EMBEBIDO (NoemaWebServer / Jetty)                                           │
│                                                                                          │
│  ┌────────────────────────┐                   ┌───────────────────────────────────────┐  │
│  │     ENTRADA AUDIO      │                   │             SALIDA AUDIO              │  │
│  │                        │                   │                                       │  │
│  │ [VAD: Silero Engine]   │                   │ [Buffer Oraciones] -> [TTS Engine]    │  │
│  │          │             │                   │                             ▲         │  │
│  │          ▼             │                   └─────────────────────────────┼─────────┘  │
│  │ [STT: Whisper/SenseV.] │                                                 │            │
│  └──────────┬─────────────┘                                                 │            │
│             │                                                               │            │
│             │ Texto Usuario                               Texto Respuesta   │            │
│             ▼                                             (Solo Oral)       │            │
│  ┌──────────────────────────────────────────────────────────────────────────┴─────────┐  │
│  │ KERNEL DE NOEMA                                                                    │  │
│  │                                                                                    │  │
│  │  agent.putUsersMessage(terminalId, texto)                                          │  │
│  │          │                                                                         │  │
│  │          ▼                                                                         │  │
│  │  [ReasoningService] ◄─── Inyecta "Modo Audio" efímero en ProjectedMemory           │  │
│  │          │                                                                         │  │
│  │          ├──────► Invoca Tool: display_screen(code/tables)                         │  │
│  │          │             │                                                           │  │
│  │          │             ▼                                                           │  │
│  │          │        AgentConsole.printDisplayContent(...)                            │  │
│  │          │             │                                                           │  │
│  │          ▼             │ (Bypassa el TTS)                                          │  │
│  │   StreamResponse(...)  │                                                           │  │
│  └──────────┬─────────────┴───────────────────────────────────────────────────────────┘  │
│             │                                             │                              │
│             │ Event: "response" (Subtítulos)              │ Event: "display" (Artefacto) │
│             │                                             │                              │
│             └───────────────────────┬─────────────────────┘                              │
│                                     │                                                    │
│                              CANAL SSE TEXTO                                             │
│                          /api/console/{terminalId}                                       │
│                                     │                                                    │
└─────────────────────────────────────┼────────────────────────────────────────────────────┘
                                      ▼
                     ┌──────────────────────────────────────────────┐
                     │              NAVEGADOR WEB (SPA)             │
                     │                                              │
                     │  * Burbuja Usuario (Transcripción)           │
                     │  * Burbuja Noema   (Subtítulos texto)        │
                     │  * Tarjeta Visual  (Código / Artefacto)      │
                     └──────────────────────────────────────────────┘
```

### 3.1. Topología de Red y Protocolos (Estrategia Híbrida)
Se preserva la infraestructura existente en `NoemaWebServer` y se añade un canal especializado en tiempo real:

1. **Canal Visual y de Eventos (SSE - `/api/console/{terminalId}`):**
   * Mantiene su función actual sin cambios disruptivos.
   * Transmite los eventos `user-message`, `thinking`, `response` (texto progresivo), `log` y el nuevo evento `display`.
2. **Canal de Audio Bidireccional (WebSocket - `/api/voice/{terminalId}`):**
   * **Subida (Cliente → Servidor):** Paquetes binarios de audio continuo capturados por el micrófono en fragmentos pequeños (100 ms).
   * **Bajada (Servidor → Cliente):** Chunks binarios de audio sintetizado para reproducción inmediata.
   * **Señalización (JSON):** Tramas de control ligeras para indicar eventos asíncronos (`{"action": "INTERRUPT"}`, `{"action": "VAD_SPEECH_START"}`, `{"action": "VAD_SPEECH_END"}`).

### 3.2. Extensión del Contrato `AgentConsole`
Para formalizar la separación entre contenido locutable y contenido exclusivamente visual sin romper las implementaciones de consola existentes (Swing, Lanterna, CLI):

* **Nuevo método en la interfaz `AgentConsole`:**
  * `printDisplayContent(String title, String contentType, String content)`
* **Comportamiento polimórfico según la consola activa:**
  * **En CLI / TUI (Lanterna):** Imprime el bloque visual directamente en el flujo de la terminal envuelto en un recuadro de caracteres ASCII o Markdown coloreado.
  * **En GUI (Swing):** Inyecta el contenido en un componente visual dedicado (como el visor de código con `RSyntaxTextArea`).
  * **En Web (`SseAgentConsole`):** Serializa los datos y emite un evento SSE específico (`display`). **No invoca los búferes de locución ni notifica al pipeline de audio.**

### 3.3. Herramienta de Proyección Visual (`DisplayScreenTool`)
Se introduce una herramienta registrada en el catálogo de Noema con firma abierta al modelo:
* **Identificador técnico:** `display_screen`
* **Parámetros:**
  * `title`: Título descriptivo del elemento visual (ej: *"Método FileReadUtils.java"*).
  * `contentType`: Tipo MIME o de sintaxis (ej: `java`, `markdown`, `json`, `sql`).
  * `content`: Texto íntegro estructurado que se desea proyectar.
* **Comportamiento interno:** 
  1. Resuelve la consola del subcanal en ejecución mediante el contexto contextual del turno.
  2. Invoca `console.printDisplayContent(title, contentType, content)`.
  3. Devuelve al LLM una respuesta breve de confirmación: `{"status": "DISPLAYED"}`.

### 3.4. Instrucciones del Turno en "Modo Audio"
Cuando el mensaje entrante procede del canal de voz (WebSocket):
1. **Inyección en `ProjectedMemory`:** Se añade una directiva transitoria al inicio de la proyección de mensajes del turno:
   > *"CANAL ACTIVO: CONVERSACIÓN ORAL. Tu respuesta será sintetizada a voz en tiempo real. Responde de forma concisa, cálida y exclusivamente en lenguaje hablado natural. ESTÁ PROHIBIDO dictar código fuente, tablas, listas largas o sintaxis Markdown técnica en tu mensaje oral. Si la explicación requiere código, esquemas o datos estructurados, invoca obligatoriamente la herramienta 'display_screen' para proyectarlos en la pantalla del usuario y limítate a explicar verbalmente su funcionamiento."*
2. **Registro Histórico Limpio:** El mensaje del usuario y la respuesta de Noema se guardan en la memoria episódica como turnos de texto conversacional estándar, garantizando que el historial sea legible y sintetizable en las consolidaciones narrativas periódicas.

### 3.5. Pipeline de Procesamiento de Audio (En Servidor)

#### A. Pipeline de Entrada (Speech-to-Text)
* **Ingesta:** Los paquetes de audio recibidos por el WebSocket se acumulan en un búfer circular en memoria.
* **VAD (Voice Activity Detection):** Un hilo ligero analiza el flujo con un modelo de alta precisión (tipo Silero VAD). Detecta transiciones de voz/silencio.
* **Fin de Turno:** Al detectarse una ventana de silencio configurable (ej. 600–800 ms tras habla continua), el búfer acumulado se entrega al motor de transcripción (STT).
* **Inyección al Kernel:** El texto resultante se remite a `agent.putUsersMessage(terminalId, text)`. Al mismo tiempo, se emite por SSE el evento de mensaje de usuario para dibujarlo en pantalla.

#### B. Pipeline de Salida (Text-to-Speech)
* **Captura:** `SseAgentConsole` recibe los fragmentos de la respuesta del modelo (`StreamResponse`).
* **Búfer de Oraciones:** Se acumulan los tokens hasta detectar un signo delimitador prosódico (`.`, `?`, `!`, salto de línea doble).
* **Síntesis por Lotes Progresivos:** Cada oración delimitada se transfiere inmediatamente al motor de TTS sin esperar a que el modelo termine el párrafo completo.
* **Emisión Streaming:** El audio codificado de la oración se remite como trama binaria por el WebSocket al navegador, reduciendo el *Time to First Audio* (TTFA) al mínimo.

---

## 4. Opciones de Ejecución del Motor de Audio

El diseño es agnóstico a la ubicación de los motores de STT y TTS, permitiendo dos modalidades configurables:

| Criterio | Modalidad 1: Soberana / Local (ONNX) | Modalidad 2: Proxy APIs Cloud |
| :--- | :--- | :--- |
| **Componentes STT** | Whisper (small/base) / SenseVoice en ONNX Runtime. | Groq Whisper / Deepgram Nova-2 (WebSocket). |
| **Componentes TTS** | Kokoro-82M / Piper TTS en ONNX Runtime. | Cartesia Sonic / Deepgram Aura / ElevenLabs Flash. |
| **Coste Monetario** | **0 €**. | ~0,80 € a 1,50 € por hora de charla activa. |
| **Privacidad** | Total (el audio no abandona el host). | Sujeto a la política de datos del proveedor cloud. |
| **Latencia estimada (TTFA)** | ~1,2 a 1,8 segundos (en CPUs modernas con AVX2). | ~600 a 900 milisegundos. |
| **Carga en el Servidor** | ~1 GB de RAM adicional y uso intensivo de CPU/GPU. | Prácticamente nula (solo tráfico de red de paso). |

---

## 5. Resumen del Impacto en el Código Existente

* **En el Kernel (`io.github.jjdelcerro.noema.lib`):**
  * Añadir el método `printDisplayContent(...)` en el contrato `AgentConsole`.
  * Añadir la herramienta base `DisplayScreenTool`.
* **En el Servidor Web (`NoemaWebServer.java`):**
  * Añadir el listener WebSocket de Javalin (`/api/voice/{terminalId}`).
  * Implementar el orquestador del pipeline VAD → STT y Texto → TTS.
  * Añadir soporte para el evento SSE `display` en `SseAgentConsole`.
* **En el Frontend (`chat-ui.js`, `index.html`, `styles.css`):**
  * Incluir botón de activación de voz continua (modo llamada) en la cabecera/composer.
  * Crear el módulo cliente de audio (`AudioWorklet` para micro, `AudioContext` con encolador para altavoz).
  * Añadir listener del evento `display` para pintar los bloques visuales como tarjetas de artefacto independientes en el área de chat.

  

## Anexo A: Revisión de Diseño — Configuración de la Oralidad mediante Prompt de Sistema frente a Notificaciones en Memoria Proyectada

### A.1. Motivación del Cambio de Enfoque
En el diseño inicial (sección 3.4) se planteó comunicar la activación del modo voz mediante avisos efímeros inyectados en la cola conversacional de `ProjectedMemory` (a través del mecanismo unificado de notificaciones asociado a `poll_event`). Tras un análisis técnico detallado sobre el comportamiento de atención de los modelos de lenguaje y la economía de tokens, se descarta este enfoque en favor de la **mutación directa del bloque de Identidad Nuclear (`core`) en el Prompt de Sistema**.

### A.2. Justificación Técnica

1. **Eficiencia de Caché de Prefijo (Prompt Caching):**
   * Los proveedores modernos compatibles con OpenAI (DeepSeek, Anthropic, OpenAI) computan sus cachés de contexto de forma estrictamente secuencial desde el prefijo superior hacia el final de la conversación.
   * La inyección de notificaciones dinámicas al final del contexto introduce variaciones constantes en la zona más inestable del prompt, forzando la reevaluación o consumiendo tokens no reutilizables en cada interacción.
   * Al incorporar las directivas orales directamente en el Prompt de Sistema, se asume una única invalidación de caché al iniciar la sesión de voz. A partir de ese momento, la totalidad de los turnos rápidos sucesivos de la conversación hablada (turnos 2 a *N*) reutilizan el prefijo inmutable del sistema, reduciendo drásticamente la latencia de primer token (*Time to First Token* - TTFT), parámetro crítico para una experiencia conversacional fluida.

2. **Disciplina de Formato y Rigor Cognitivo (Adherencia Estricta):**
   * Los modelos de lenguaje tratan las notificaciones conversacionales y los resultados de herramientas como contexto factual, pero son propensos a sufrir degradación atencional respecto a restricciones de estilo si estas compiten con el flujo de diálogo reciente.
   * Las restricciones del modo oral son severas e innegociables: prohibición absoluta de sintaxis Markdown, ausencia de bloques de código y límites estrictos de extensión. El lugar natural donde el modelo asimila estas restricciones como reglas de comportamiento constitutivas es el Prompt de Sistema, no un aviso en la cola de mensajes.

3. **Pureza Semántica del Sistema Sensorial:**
   * En la arquitectura de Noema, la simulación de llamadas a `poll_event` está concebida para la entrada de estímulos del mundo físico exterior (alarmas, correos entrantes, eventos de fondo).
   * Utilizar ese mismo mecanismo para recordarle al modelo cómo debe hablar constituía una fuga conceptual. La oralidad no es un evento transitorio que llega al agente: es un protocolo operativo de su identidad que debe activarse y desactivarse limpiamente.
  

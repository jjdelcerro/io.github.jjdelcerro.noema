El diseño de integración de voz propuesto es completamente viable y se acopla de manera limpia a la arquitectura modular existente de Noema. La estrategia de separar el transporte de audio y la inferencia del VAD de la capa de razonamiento del agente es la decisión correcta.

A continuación, presento un análisis técnico del documento, validando las decisiones de diseño e identificando algunos puntos ciegos y desafíos de concurrencia que deben resolverse antes de la implementación.

### Puntos validados de la arquitectura

1.  **Gestión de memoria nativa y Zero-Copy**: El diseño aborda directamente los cuellos de botella clásicos en Java para procesamiento de audio en tiempo real. El uso de `InputStream/OutputStream` sobre `byte[]` en la API pública de `VoiceConversation` y la invocación estricta de `OrtTensor.close()` son fundamentales para evitar fugas de memoria *off-heap* y saturación del Garbage Collector al evaluar tramas a 32 ms.
2.  **Multiplexado de salida (`CompositeConsole`)**: Centralizar la salida del agente en una única consola con *slots* tipados (`CHAT_SSE` y `VOICE_WS`) resuelve de forma elegante la sincronización multicanal para un mismo `terminalId`. Evita que el `ReasoningServiceImpl` tenga que conocer a quién le está hablando.
3.  **Abstracción de STT y TTS como `AgentService`**: Tratar la transcripción y la síntesis como servicios de pleno derecho reutiliza el bus del sistema y permite futuras capacidades de uso de herramientas (ej. que el agente analice un MP3 del disco usando el propio `STTService`).
4.  **Enrutamiento determinista de turnos proactivos**: El diseño cubre correctamente el flujo donde el agente decide hablar por su cuenta (ej. alarma del `SchedulerService`), ruteando el texto directamente al *slot* activo de voz.

### Análisis crítico y riesgos a mitigar

Como revisor del diseño, he analizado el código fuente adjunto frente a las especificaciones del documento de voz y he detectado los siguientes puntos que requieren ajustes en la implementación.

#### 1. Concurrencia en la señal de aborto (Barge-in)
El documento indica que el mecanismo de interrupción (barge-in) se basa en cambiar el valor de `abort.setValue(true)` en `ReasoningServiceImpl`. 
En el código actual (`ReasoningServiceImpl.java`), `abort` está definido como `org.apache.commons.lang3.mutable.MutableBoolean`. Esta clase **no es thread-safe**. Si el hilo del WebSocket (gestionando el VAD) modifica este valor, no hay garantías de visibilidad en el hilo del LLM (`eventDispatcher`) debido a la caché de la CPU.

*   **Solución**: Se debe refactorizar `abort` en `ReasoningServiceImpl` y `ChatModelImpl` para utilizar `java.util.concurrent.atomic.AtomicBoolean`.

#### 2. Trazabilidad del turno interrumpido en la persistencia
El documento detalla que, al ocurrir un *barge-in*, se cancela el flujo, se silencia el audio y el estado pasa a `SPEAKING`. Sin embargo, no especifica qué ocurre con la persistencia. Si el LLM estaba a mitad de generar una respuesta y el usuario lo interrumpe, el estado del razonamiento debe quedar registrado.
*   **Solución**: Si se lanza la `InterruptedModelGenerateException` en `ChatModelImpl`, el `ReasoningServiceImpl` debe capturarla y persistir el fragmento parcial generado hasta ese momento en el `SourceOfTruth`, idealmente adjuntando una nota del sistema tipo `[Locución interrumpida por el usuario]`. Esto permite al LLM tener el contexto de que fue cortado a mitad de una frase en el siguiente turno.

#### 3. Latencia y Time-To-First-Audio (TTFA)
Actualmente, `ReasoningServiceImpl` genera la respuesta completa de la inferencia y luego llama a `console.printModelResponse(aiText)`. Para una interacción de voz fluida, esperar a que el LLM termine el turno completo antes de pasarlo al `TTSService` generará una latencia inaceptable (segundos de silencio).
*   **Solución**: Será necesario que `ReasoningServiceImpl` consuma el flujo de tokens (vía `StreamingChatResponseHandler`) y envíe fragmentos parciales (por ejemplo, oraciones completas delimitadas por signos de puntuación) a la `CompositeConsole`. El `TTSService` debe ser capaz de ir sintetizando y encolando esos fragmentos mientras el LLM sigue generando el resto de la respuesta.

#### 4. Sanitización del texto para TTS (Bloques de código)
El documento menciona el filtrado de Markdown, lo cual es correcto. Sin embargo, si el LLM incluye un bloque de código Java o un comando de consola en la respuesta (aun en modo conversacional), el TTS intentará leer literalmente los símbolos (`llave de apertura`, `punto y coma`, etc.), arruinando la experiencia.
*   **Solución**: El filtro en el slot `VOICE_WS` debe interceptar patrones de bloques de código (``` ... ```) y reemplazarlos por una marca verbal, por ejemplo: *"Te he adjuntado el bloque de código en la pantalla"*. De este modo, la voz se mantiene fluida y el usuario recibe el código en la UI web simultáneamente.

---

El documento es una base sólida. Define el "qué" y el "cómo" de forma robusta.

¿Procedo con la refactorización de la señal de `abort` a `AtomicBoolean` y la creación de las interfaces base para `STTService`, `TTSService` y `CompositeConsole` en el código de Noema?
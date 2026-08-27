
# Documento de Diseño: Implementación de Agente de Voz en Tiempo Real con Javalin y ONNX (Silero VAD)

## 1. Visión General de la Arquitectura
El objetivo de esta implementación es dotar al agente Java de una interfaz de voz natural, *full-duplex* (flujo bidireccional continuo) y sin botones ("manos libres"). La arquitectura consta de tres pilares:
1. **Frontend Web:** Captura el audio en crudo del usuario y lo envía por WebSockets.
2. **Servidor Javalin:** Actúa como puente, gestionando las conexiones WebSocket de múltiples clientes y manteniendo el estado de cada sesión.
3. **Motor de Inferencia (ONNX Runtime):** Evalúa el flujo de audio en tiempo real usando Silero VAD para detectar la actividad de voz humana y controlar los turnos de la conversación.

---

## 2. Captura y Transmisión en el Frontend Web
Para evitar la latencia que supondría grabar un archivo completo y subirlo, el frontend debe realizar un *streaming* continuo.

* **Adquisición del Audio:** Se debe utilizar la API `navigator.mediaDevices.getUserMedia({ audio: true })`.
* **Muestreo (Sample Rate):** Es crucial solicitar o forzar mediante `AudioContext` que el audio se capture a **16 kHz** (16000 Hz) en **mono** (1 solo canal). Esto evita tener que hacer el costoso remuestreo en el backend de Java, ya que Silero VAD trabaja óptimamente a 16 kHz.
* **Procesamiento y Envío:** Utilizando un `AudioWorklet` o `ScriptProcessorNode`, el navegador recogerá buffers de audio periódicamente.
* **Formato de Red:** Se recomienda convertir los datos de audio a formato **PCM lineal de 16 bits (Int16)** antes de enviarlos a través del WebSocket mediante mensajes binarios (`ArrayBuffer` o `Blob`).

---

## 3. Gestión de Conexiones WebSocket en Javalin
Javalin será el responsable de orquestar el tráfico. Cada usuario conectado al frontend representará una **Sesión de WebSocket activa**.

* **Estado de la Sesión:** Por cada conexión (`WsContext`), el backend en Java debe instanciar un objeto de estado de sesión. Este objeto guardará:
  1. Un buffer de bytes para acumular el audio entrante.
  2. Los tensores recurrentes de ONNX (`h` y `c`) específicos de ese usuario.
  3. El estado actual de la conversación (ej. `ESCUCHANDO`, `HABLANDO`, `PROCESANDO`).
  4. Marcas de tiempo (*timestamps*) para medir la duración de los silencios.
* **Recepción Binaria:** Javalin utilizará el manejador de mensajes binarios (`ws.onBinaryMessage(...)`) para recibir los fragmentos PCM del cliente web y encolarlos en el buffer de la sesión.

---

## 4. Transformación de Datos de Audio (Java)
ONNX Runtime y Silero VAD no entienden bytes directamente; requieren tensores matemáticos.

* **Acumulación de Chunks:** Silero VAD requiere ventanas fijas. A 16 kHz, lo ideal es pasarle **512 muestras** por iteración (que equivalen a 32 milisegundos de audio). El servidor debe acumular bytes hasta tener 1024 bytes (ya que 1 muestra de 16 bits = 2 bytes).
* **Conversión de Endianness a Flotantes:**
  * Al llegar a los 1024 bytes, Java debe leerlos como *Shorts* (enteros de 16 bits).
  * Luego, cada *Short* debe convertirse a `float` y **normalizarse** dividiéndolo por `32768.0f`.
  * El resultado será un array bidimensional de flotantes (ej. `float[1][512]`) donde los valores están entre `-1.0` y `1.0`.

---

## 5. Integración de Silero VAD con ONNX Runtime
Dado que ya usas ONNX Runtime para embeddings, reutilizarás el `OrtEnvironment`. Solo necesitas cargar el archivo `silero_vad.onnx`.

### Configuración de Inferencia (Input Tensors)
Por cada bloque de 512 muestras, crearás un `OrtSession.Result` pasando un mapa (Map) con 4 tensores de entrada:
1. `input`: El array de audio `float[1][512]` generado en el paso anterior.
2. `sr`: Tensor escalar con el *Sample Rate* (16000 de tipo Int64).
3. `h`: Tensor de estado oculto inicializado con ceros en la primera iteración. Forma esperada: `float[2][1][64]`.
4. `c`: Tensor de contexto (Cell state) inicializado con ceros. Forma esperada: `float[2][1][64]`.

### Salida del Modelo (Output Tensors)
La inferencia devuelve 3 cosas clave:
1. **Probabilidad (`output`):** Un flotante entre `0.0` y `1.0`. Indica la confianza del modelo de que el bloque actual contiene voz humana.
2. **Nuevos estados (`hn` y `cn`):** Estos son los nuevos tensores `[2][1][64]`. **Concepto crítico:** Debes extraer estos valores y guardarlos en el estado de la sesión del WebSocket. En el siguiente bloque de audio de 32ms que recibas del cliente, usarás este `hn` y `cn` como los inputs `h` y `c`.

---

## 6. Lógica de Control de Turnos (Máquina de Estados VAD)
El valor de probabilidad por sí solo no basta. Necesitas una lógica temporal que suavice las transiciones para no cortar al usuario cuando respira entre palabras.

* **Umbral de Activación (Speech Start):**
  Si el estado es `ESCUCHANDO` y la probabilidad de Silero supera un umbral (ej. `0.5`), se marca el inicio del habla. El estado pasa a `HABLANDO`. Se empieza a guardar todo el audio original en un buffer general de la "frase completa".
* **Umbral de Desactivación (Speech End / Endpointing):**
  Mientras el estado es `HABLANDO`, el usuario seguirá enviando voz. Ocasionalmente, la probabilidad bajará de `0.5` (pausas, espacios entre palabras). 
  * Inicias un temporizador interno cada vez que la probabilidad baja del umbral.
  * Si la probabilidad vuelve a subir, reinicias el temporizador.
  * Si el temporizador de silencio continuo supera un **margen de corte** (ej. **600 a 800 milisegundos**), determinas concluyentemente que el usuario ha terminado de hablar.

---

## 7. Orquestación del Pipeline Completo
Una vez que la Máquina de Estados detecta el final del habla (han pasado >800ms de silencio continuo):

1. **Cierre de Turno:** El estado pasa a `PROCESANDO`. El sistema deja momentáneamente de acumular el audio entrante (o lo ignora/descarta) para no procesar ruido mientras "piensa".
2. **STT (Speech-to-Text):** Tomas el gran buffer con la frase completa acumulada, y lo pasas a tu motor STT para obtener el texto del usuario.
3. **Inferencia LLM:** El texto pasa al Agente y generas el texto de respuesta.
4. **TTS (Text-to-Speech):** La respuesta del LLM se convierte en audio.
5. **Streaming de Salida:** A través del *mismo* WebSocket de Javalin por el que el usuario te envió el audio, le devuelves los bytes del audio sintetizado para que el navegador los reproduzca (usando la API Web Audio o asignando los chunks a un reproductor).
6. **Reinicio de Ciclo:** Al terminar de hablar el Agente, el estado de sesión de Javalin vuelve a `ESCUCHANDO`, reseteando el buffer de frase y los temporizadores, listo para el siguiente turno del usuario.

---

## 8. Consideraciones Técnicas y de Rendimiento para Java
* **Limpieza de Recursos:** ONNX Runtime en Java (usando C++ por debajo) requiere liberar manualmente la memoria no manejada por el Garbage Collector (GC). Es de vital importancia cerrar explícitamente (`.close()`) los tensores `OnnxTensor` creados en cada iteración de 32ms para no provocar fugas de memoria nativa (*memory leaks*).
* **Pooling de Sesiones ONNX:** El `OrtEnvironment` y el modelo (`OrtSession` cargando el `.onnx` de Silero) son seguros para hilos (thread-safe). Cárgalos solo una vez al arrancar Javalin y permite que todos los hilos de los WebSockets compartan la misma instancia del modelo para evaluar sus propios tensores.
* **Calibración Dinámica:** Es una buena práctica permitir configurar el tiempo de pausa por variable de entorno (el de 600-800ms) ya que los usuarios hablan a diferentes velocidades.

Este diseño te garantiza una implementación altamente eficiente, con una huella de memoria pequeñísima y una experiencia de usuario extremadamente reactiva y fluida, manteniendo todo el control dentro de tu ecosistema Java actual.

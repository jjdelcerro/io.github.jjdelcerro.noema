El documento está muy bien trabajado. Captura con precisión el espíritu de la arquitectura de Noema y propone una integración de voz que, lejos de ser un parche, se inserta como un nuevo canal de UI desacoplado, reutilizando los mecanismos existentes. Es un diseño maduro y consistente.

**Aciertos principales:**

- **Separación UI/Servicios:** La distinción entre `VoiceConversation` (controlador de sesión) y los servicios `STTService`/`TTSService` es impecable. La voz se convierte en una interfaz más, sin contaminar el núcleo cognitivo.
- **Abstracción de I/O:** Pasar `InputStream`/`OutputStream` en lugar de `byte[]` es una decisión inteligente que evita copias innecesarias y permite probar la lógica sin depender de Javalin.
- **Máquina de estados:** La definición de los estados (`LISTENING`, `SPEAKING`, `SILENCE_COUNTING`, `PROCESSING`, `AGENT_SPEAKING`) y la lógica de transición es clara y cubre los casos de uso de interrupción y pausas naturales.
- **Gestión de recursos nativos:** La insistencia en liberar tensores ONNX explícitamente es crítica y bien señalada. Compartir la `OrtSession` global es la estrategia correcta.
- **Sinergia multicanal:** El uso del mismo `terminalId` para compartir historial entre voz y texto es elegante y añade valor a la experiencia del usuario.

**Puntos a considerar o matizar:**

1. **Eventos proactivos y enrutamiento al TTS:** El documento menciona que las respuestas a eventos proactivos (alarmas, notificaciones) se canalizarían por voz si la sesión está activa. Sin embargo, no detalla cómo se enlaza esa respuesta al `TTSService`. En el flujo normal, `VoiceConversation` usa el callback de `putUsersMessage` para recibir la respuesta. Para eventos proactivos, el agente genera una respuesta sin que haya un `putUsersMessage` asociado. Habría que definir un mecanismo: o bien `VoiceConversation` se suscribe a un bus de eventos interno y escucha las respuestas del agente, o bien se extiende el sistema de sensores para que las respuestas generadas se publiquen en un canal específico que `VoiceConversation` consuma. Sugiero añadir un apartado que aclare este punto, probablemente usando el sistema de sensores (`SensorsService`) para que `VoiceConversation` pueda escuchar eventos de tipo `RESPONSE_GENERATED` en el subcanal correspondiente.

2. **Configuración de STT/TTS:** Los servicios serán intercambiables, pero el documento no especifica cómo se seleccionará el proveedor (local vs. cloud) ni qué parámetros de configuración se necesitarán en `settings.json`. Recomiendo definir una estructura de propiedades clara (ej. `stt/provider`, `stt/model`, `stt/api_key`, etc.) y extender la UI de configuración para permitir elegir entre opciones. Esto es coherente con el enfoque de "soberanía tecnológica" que comentamos en el contexto.

3. **Manejo de colisiones en la salida:** Durante el flujo normal, `VoiceConversation` envía la respuesta del agente al TTS y la reproduce. Si mientras se está reproduciendo llega un nuevo evento proactivo que también genera respuesta, podría haber conflicto. El documento menciona el *barge-in* para interrupción del usuario, pero no para interrupción por eventos del sistema. Quizás se deba definir una política de prioridad (ej. siempre interrumpir por voz del usuario, pero encolar respuestas proactivas si el agente ya está hablando). Se podría añadir una cola de mensajes pendientes para gestionar esto.

4. **Validación del cliente web:** El diseño asume que el cliente envía PCM a 16kHz mono. Sería recomendable que el servidor valide la frecuencia y el formato de los primeros datos recibidos y, si no coincide, envíe un mensaje de error o solicite reconexión con los parámetros correctos. Esto evitaría fallos silenciosos.

5. **Pruebas de rendimiento:** La ejecución de Silero 31 veces por segundo por sesión es ligera, pero si se esperan muchas sesiones concurrentes, podría ser un cuello de botella. No es crítico para un prototipo, pero conviene tenerlo en cuenta y medir el consumo de CPU. El documento menciona compartir la sesión ONNX, lo cual ayuda.

**Conclusión:**

El diseño es sólido, viable y encaja perfectamente en Noema. Las consideraciones anteriores son más bien detalles de implementación que fallos de concepto. Veo el camino claro: empezar por implementar `STTService` y `TTSService` (con una implementación local mock o con API para pruebas), luego `VoiceConversation` con Silero VAD, y finalmente el endpoint WebSocket en `NoemaWebServer`. La máquina de estados y el manejo de barge-in son los puntos más delicados, pero están bien definidos.

Si te parece, podemos profundizar en cualquiera de estos puntos o pasar a discutir la elección concreta de motores STT/TTS (local vs. cloud, coste, latencia). Pero en líneas generales, el documento está listo para ser la base de la implementación.
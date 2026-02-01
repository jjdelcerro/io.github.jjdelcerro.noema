
# Nota Técnica: Rediseño de la Persistencia de Sesión Activa (DHMA)

**Estado:** Pendiente de Implementación (Programado para it2/PoC1)  
**Contexto:** Refactorización del orquestador `ConversationAgent` para separar la memoria de protocolo de la memoria de archivo.

## 1. Descripción del Problema: El desajuste de impedancia

En la implementación inicial de la PoC, intentamos que la tabla `turnos` de la base de datos H2 cumpliera una doble función:
1.  **Función de Archivo:** Almacenar de forma colapsada y procesada lo ocurrido para que el `MemoryManager` pueda narrarlo y el buscador vectorial pueda encontrarlo.
2.  **Función de Sesión:** Reconstruir la conversación activa (los turnos no consolidados) para reinyectarlos al LLM tras un reinicio.

Esta dualidad ha revelado un **error de base estructural**: el protocolo de los LLMs (secuencia estricta de mensajes con roles `User`, `Assistant/ToolCall` y `Tool`) no se mapea 1:1 con un registro de "Turno" diseñado para la síntesis.

### Puntos de fricción detectados:
*   **Pérdida de la "Intención":** Un registro en la DB guarda la llamada y el resultado juntos. Al reconstruir, el LLM recibe un resultado de una herramienta que no "recuerda" haber pedido en ese contexto exacto, violando la coherencia del protocolo.
*   **Fragmentación de pensamientos:** Si el LLM lanza tres herramientas en un solo mensaje, la implementación actual genera tres registros inconexos, rompiendo la estructura de planificación original.
*   **Hacks de coherencia:** El uso de `pendingUserText` para "parchear" la pregunta del usuario en registros de herramientas es un síntoma de que estamos forzando los datos en un molde que no les corresponde.

## 2. Solución Propuesta: Arquitectura de Doble Capa

Proponemos desacoplar totalmente la **Memoria de Protocolo** de la **Memoria Semántica**.

### Capa A: Memoria de Protocolo (Sesión Efímera)
*   **Propósito:** Mantener la lista exacta de objetos `ChatMessage` (LangChain4j) que el LLM necesita para razonar sin errores de secuencia.
*   **Persistencia:** Volcado atómico a un fichero `session.json` tras cada interacción.
*   **Ciclo de vida:** Se limpia parcialmente cada vez que se produce un Punto de Guardado (compactación).

### Capa B: Memoria Semántica (Archivo Permanente)
*   **Propósito:** Alimentar al `MemoryManager` (DHMA) y permitir búsquedas históricas.
*   **Persistencia:** Tabla `turnos` en H2 (SQL + BLOBs).
*   **Ciclo de vida:** Inmutable y acumulativo. Es una "proyección" de la realidad para consumo analítico.

## 3. Estrategia de Implementación: El Volcado JSON

En lugar de una persistencia incremental en SQL para la sesión activa, utilizaremos un **mecanismo de "Snapshot" atómico**.

1.  **Estructura:** El fichero `data/active_session.json` contendrá la lista serializada de los mensajes actuales en la RAM del `ConversationAgent`.
2.  **Garantía de integridad (Escritura en dos pasos):** 
    - Escribir en `active_session.json.tmp`.
    - Cerrar descriptor de archivo.
    - `Files.move(..., REPLACE_EXISTING)` para asegurar que el fichero final nunca quede corrupto a mitad de una escritura si el proceso cae.
3.  **Recuperación al arranque:**
    - El sistema busca el JSON. Si existe, lo carga y restaura la lista de `messages` tal cual estaba.
    - Si no existe (o tras una compactación exitosa), el historial arranca limpio desde el último Punto de Guardado.

## 4. Problemas identificados y consideraciones técnicas

Durante el diseño de esta solución, hemos identificado los siguientes puntos de atención obligatoria:

*   **Polimorfismo en la Serialización:** Los objetos `ChatMessage` son interfaces. Al serializar a JSON, perderemos el tipo concreto (UserMessage, AiMessage, etc.).
    *   *Solución:* Configurar Gson/Jackson con un `RuntimeTypeAdapter` que inyecte un campo `"type"` en el JSON para poder instanciar la clase correcta al leer.
*   **Sincronización Session vs. Archive:** Existe el riesgo de que el JSON y la DB se desincronicen si una escritura falla. 
    *   *Solución:* La Capa A (JSON) manda. Si hay discrepancia, el archivo histórico (Capa B) es solo para investigación, no para el razonamiento en curso.
*   **Higiene del Snapshot:** El orquestador debe ser riguroso al eliminar el JSON de sesión inmediatamente después de una compactación exitosa para evitar re-procesar turnos que ya están en el Punto de Guardado.
*   **Tamaño de los resultados de herramientas:** Aunque la DB recorta los resultados grandes (>2KB), el JSON de sesión **debe mantener el contenido íntegro** (o el truncado que el LLM soporte) para no romper el hilo de pensamiento actual.

# El reto técnico para un Agente de IA

Para que la refactorización salga bien, el modelo va a tener que demostrar que entiende tres conceptos de "senior" que suelen atragantarse a las IAs:
*   **Polimorfismo en JSON:** LangChain4j usa interfaces para los mensajes. Si el modelo no implementa un `RuntimeTypeAdapterFactory` o algo similar en Gson, el JSON no se podrá leer de vuelta.
*   **Escritura Atómica:** Veremos si tiene el "olfato" de proponer el movimiento de fichero (`Files.move`) o si simplemente hace un `FileWriter` a lo bruto que podría corromper la sesión si se corta la luz.
*   **Inyección de Dependencias:** Tendrá que tocar el `Main.java` para inyectar el nuevo `SessionManager` en el `ConversationAgent`.




# Informe de Arquitectura Cognitiva: Gestión de Observaciones y Persistencia Selectiva

## 1. Introducción: La Dualidad del Toolset
En la arquitectura de Noema, las herramientas (tools) se dividen en dos categorías fundamentales basadas no en su ejecución, sino en su **dirección de flujo de información**:

*   **Herramientas Operacionales (Efectores/Sensores):** Son la interfaz con el mundo exterior. Permiten al agente percibir (`file_read`, `web_search`) y actuar (`file_write`, `telegram_send`). Su resultado es un **dato crudo** que suele tener una vigencia inmediata y, a menudo, un volumen masivo.
*   **Herramientas de Memoria (Introspección):** Son la interfaz con el mundo interior. Permiten al agente consultar su propio registro histórico (`lookup_turn`, `search_full_history`). Su resultado es un **recuerdo**, siempre conciso y de alto valor semántico.

## 2. El Problema: La "Amnesia de las Operaciones"
Para garantizar la salud y el rendimiento de la base de datos (H2), Noema implementa una **política de recorte inteligente**: los resultados de las herramientas operacionales que superan un umbral de tamaño se guardan truncados en la base de datos episódica, conservándose íntegros únicamente en la memoria de trabajo (RAM) durante la sesión activa.

### La Fricción Cognitiva
Esta política crea un conflicto cuando el usuario da instrucciones de larga duración. 
**Escenario de ejemplo:**
> Usuario: *"Lee este fichero con mis gustos musicales y tenlo en cuenta a lo largo de toda nuestra conversación futura"*.

1.  El agente ejecuta `file_read`. El contenido (supongamos 500 líneas de artistas y géneros) entra en la **Session (RAM)**.
2.  El agente responde confirmando que conoce los gustos del usuario.
3.  Al cabo de 40 turnos, se dispara el protocolo de **compactación**.
4.  El `MemoryManager` lee el historial de turnos en la base de datos. Al llegar al turno de lectura, solo encuentra: `tool_result: [Truncated: Data stored in memory only]`.
5.  **Resultado:** El nuevo CheckPoint ("El Viaje") registrará que "el agente leyó un fichero de gustos musicales", pero **la sustancia de la información se habrá perdido**. En la siguiente sesión, el agente sabrá que leyó el fichero, pero no recordará qué música le gusta al usuario.

## 3. La Solución: Anclaje Episódico Voluntario
Para resolver esta fricción sin renunciar a la eficiencia de la base de datos, introducimos un mecanismo de "toma de notas consciente" por parte del agente.

### La Herramienta `annotate_observation(origen, nota)`
Esta nueva herramienta actúa como un puente que transforma una observación volátil en un conocimiento persistente.

*   **Tipo de herramienta:** Memoria (Tipo 1). Esto garantiza que su contenido **nunca será recortado** por el `SourceOfTruth` al persistirse en la base de datos.
*   **Argumentos:**
    *   `origen`: La fuente de la información (ej. la ruta del archivo, una URL o "instrucción del usuario").
    *   `nota`: El resumen destilado o la información clave que el agente desea que sobreviva a la compactación.

### Mecanismo de Funcionamiento
El flujo de "fijación de memoria" sigue tres pasos críticos:

1.  **Ejecución y Persistencia íntegra:** Cuando el agente decide que algo es importante (ej. tras leer el archivo de música), llama a `annotate_observation`. El sistema guarda este turno en la base de datos con el texto completo de la nota. Se le asigna un ID único (ej. `ID-123`).
2.  **Higiene de la Memoria de Trabajo (RAM):** Para evitar redundancias y ahorrar tokens en la sesión activa, el `ConversationService` intercepta el resultado de esta herramienta. En lugar de inyectar la nota completa en la conversación actual, inyecta un marcador ligero:
    > `[Nota guardada en memoria episódica {cite: ID-123} sobre el origen: 'fichero_musica.txt']`
3.  **Consolidación Narrativa:** Al llegar la compactación, el `MemoryManager` encontrará en el CSV de turnos la herramienta `annotate_observation` con su contenido íntegro. Esto le permite redactar un CheckPoint rico en detalles: *"El usuario compartió sus preferencias musicales, las cuales el agente analizó y registró, destacando su interés por el Jazz y el Blues clásico {cite: ID-123}"*.

## 4. Por qué esta solución es robusta
*   **Eficiencia:** Solo se guarda de forma permanente lo que el agente (o el usuario) considera relevante. El "ruido" de las herramientas operacionales sigue siendo recortado.
*   **Trazabilidad:** La nota siempre está anclada a su origen. Si el agente recupera la nota en el futuro, sabrá de dónde extrajo esa conclusión.
*   **Consistencia:** Si el agente necesita el detalle máximo de una nota antigua, la herramienta `lookup_turn(ID-123)` le devolverá el texto completo, ya que en la base de datos no hubo recorte.
*   **Autoconsciencia:** El agente recibe feedback inmediato en su ventana de contexto de que la información ha sido "citada" y "archivada", reforzando su modelo mental de cómo funciona su propia memoria.


Este documento constituye el **Anexo I** del informe "Gestión de Observaciones y Persistencia Selectiva". Su propósito es detallar la evolución arquitectónica del sistema de memoria de Noema, tras identificar la necesidad de distinguir entre **Hechos Biográficos** y **Marcos de Gobierno**.

---

# Anexo I: Taxonomía de la Persistencia y Memoria de Gobierno

Se ha identificado una fricción en el sistema de memoria: la información leída mediante herramientas operacionales (`file_read`) desaparece tras la compactación. Para resolverlo, distinguimos dos naturalezas de información que requieren persistencia:

*   **Hechos Episódicos:** Datos biográficos o preferencias que el agente debe "saber" (ej. "A Joaquin le gusta el Jazz").
*   **Marcos de Gobierno:** Reglas, estilos o protocolos que el agente debe "obedecer" permanentemente (ej. "Las llaves de Java van en la misma línea").


## 1. El Desafío del Contexto Profundo
A través del análisis de casos de uso reales (como la gestión de un "Documento de Contexto y Protocolo"), se ha identificado que no toda la información que el agente debe recordar tiene la misma naturaleza ni debe ocupar el mismo espacio en su arquitectura cognitiva.

Se distinguen dos tipos de persistencia de largo alcance que la herramienta `annotate_observation` (propuesta en el documento principal) no puede resolver por sí sola:

*   **Información de Hechos (Música/Anécdotas):** Datos que enriquecen la narrativa del "Viaje" pero no alteran las reglas de funcionamiento del agente.
*   **Información de Gobierno (Protocolos/Identidad):** Instrucciones que deben regir cada palabra y acción del agente (ej. guías de estilo de código, protocolos de seguridad, marcos relacionales).

## 2. El Triaje Cognitivo: Tres Destinos para la Información
Para resolver la fricción entre la necesidad de recordar y la salud de la ventana de contexto, se establece un protocolo de decisión (triaje) que el agente debe ejecutar siempre que el usuario marque una información como "importante".

El agente debe seguir esta lógica cuando el usuario le pida "tener algo en cuenta":

1.  **¿Es un dato puntual para la tarea actual?** -> Destino: Papelera Cognitiva
2.  **¿Es un hecho biográfico o un evento del proyecto?** -> Destino: Memoria Episódica
3.  **¿Es una regla, estilo o protocolo de comportamiento?** -> Destino: Marco de Referencia


### A. Destino: Papelera Cognitiva (Dato Transitorio)
*   **Definición:** Información necesaria para la tarea inmediata pero sin valor futuro.
*   **Mecanismo:** Uso de herramientas operacionales estándar (`file_read`, `web_search`). El contenido se queda en RAM y se pierde en la compactación.
*   **Ejemplo:** Análisis de un log temporal para corregir un bug puntual.

### B. Destino: Memoria Episódica (Hecho/Nota)
*   **Definición:** Información que el usuario desea que el agente "sepa", pero que no dicta su comportamiento.
*   **Mecanismo:** Herramienta `annotate_observation(origen, nota)`.
*   **Persistencia:** Se guarda íntegra en la base de datos de Turnos (H2) y se cristaliza en la narrativa de "El Viaje".
*   **Ejemplo:** "A Joaquin le gusta el Jazz clásico".

### C. Destino: Marco de Referencia (Memoria de Gobierno)
*   **Definición:** Documentos fundacionales que establecen las reglas del juego para el agente.
*   **Mecanismo:** Nueva herramienta `pin_document_as_context(ruta)`.
*   **Persistencia:** El documento se indexa estructuralmente en el `DocumentsService` y se marca como "Anclado" en el CheckPoint.
*   **Ejemplo:** Manual de arquitectura del proyecto o Protocolo de Interacción con el usuario.

## 3. Protocolo de "Memoria por Delegación"
Se establece un cambio fundamental en la autonomía del agente: **El agente no decide por sí solo qué es importante para el futuro; espera la señal del usuario.**

1.  **Activación por Intencionalidad:** El proceso se dispara solo cuando el usuario utiliza comandos como: *"Ten esto en cuenta"*, *"Recuerda esto para el futuro"* o *"Anota esta regla"*.
2.  **Clasificación por Razonamiento:** Una vez recibida la orden de importancia, el agente analiza el contenido en su `thinking` y decide si es una **Anotación** (Hecho) o un **Anclaje** (Gobierno).
3.  **Ejecución y Confirmación:** El agente propone la herramienta adecuada y el usuario la autoriza mediante el sistema de confirmación de Noema.

## 4. Implementación Técnica del Anclaje de Gobierno
La herramienta `pin_document_as_context(ruta)` no es una simple nota, es un proceso de integración sistémica:

**Indexación Estructural**

Al anclar un documento, el sistema invoca al `DocumentMapper` para generar:
*   Un **Resumen Técnico** del documento.
*   Un **Índice Jerárquico (TOC)** completo.
Estos metadatos se almacenan en la base de datos de servicios.


## 5. Especificaciones de Herramientas (ToolSpecs)

### Herramienta A: `annotate_observation`
*   **Tipo:** `TYPE_MEMORY` (Garantiza persistencia íntegra en H2).
*   **Descripción para el LLM:** *"Úsala para 'tomar notas' sobre hechos relevantes o preferencias del usuario tras una lectura. No la uses para reglas de trabajo, sino para información que Joaquín querría que recordaras en una conversación meses después."*
*   **Parámetros:**
    *   `source`: (String) El origen de la información (ej: ruta del archivo o URL).
    *   `note`: (String) El resumen detallado de la información a recordar.

### Herramienta B: `pin_document_as_context`
*   **Tipo:** `TYPE_OPERATIONAL / MODE_WRITE`.
*   **Descripción para el LLM:** *"Úsala SOLO para documentos fundacionales. Si el archivo contiene reglas de programación, marcos técnicos o protocolos que deben gobernar tu comportamiento de forma permanente, ánclalo. Esta acción es costosa en atención, úsala con rigor."*
*   **Parámetros:**
    *   `path`: (String) Ruta del documento a indexar y anclar.

Esta herramienta ademas añadira en la configuracion del agente "settings.properties/json" informacion que indique que ese documento ya indixado forma parte del "marco de referencia" o "memoria de gobierno" del agente.
    
## 6. Modificaciones en el System Prompt
Se debe insertar el siguiente bloque en el `prompt-system-conversationmanager.md` para dotar al agente de este criterio:

> ### Gestión del Conocimiento Permanente
> Por defecto, toda la información que leas es **transitoria** y se perderá en la próxima compactación.
> **Solo debes persistir información** cuando el usuario lo solicite explícitamente (ej: "Anota esto", "Tenlo en cuenta", "Recuerda esto").
> 
> Una vez recibida la orden, elige el formato:
> 1. Usa `annotate_observation` para hechos concretos, descubrimientos o preferencias personales.
> 2. Usa `pin_document_as_context` para manuales, guías de estilo, protocolos o documentos de identidad que deban regir tu comportamiento futuro.

**Reflexión**. Asegúrarse de que el agente tenga una instrucción clara en la Capa 1 que diga: "Tienes documentos de gobierno anclados. Si ves un título relevante en el índice pero no conoces el detalle, usa get_partial_document antes de asumir una regla".

## 7. Gestión de la Sesión Activa (RAM)
Para optimizar la ventana de contexto, el `ConversationService` debe aplicar "Higiene de RAM" tras la ejecución de estas herramientas.

**Formato de Reemplazo**

En lugar de mantener el texto completo de la nota en la sesión (que ya está en los turnos recientes), se inyectará un mensaje sintético:
> `[Nota guardada en memoria episódica {cite: ID-XXX} sobre el origen: 'nombre_archivo.txt']`

*   **Regla de Oro:** Aunque en la **Session (RAM)** el resultado de `annotate_observation` se recorte por higiene, el objeto `Turn` que se envía al `SourceOfTruth` debe contener el texto original completo en su campo `tool_result`. 
*   **Por qué:** Para asegurar que un futuro `lookup_turn` sobre esa nota devuelva la información íntegra y no el marcador de "Nota guardada...".


## 8. Arquitectura del "Sandwich de Contexto"
La implementación técnica de la **Memoria de Gobierno** requiere que el `ConversationService.java` gestione una **caché persistente y reactiva** del bloque de gobierno. El System Prompt se reconstruye en cada turno siguiendo esta estructura:

1.  **CAPA 1: Instrucciones Core (Estático):** El prompt del sistema base.
2.  **CAPA 2: Marco de Referencia (Dinámico - CACHEADO):** 
    *   El `ConversationService` mantiene en RAM el texto de los marcos de gobierno activos.
    *   Este bloque solo se reconstruye si cambia la lista de documentos en `settings.json` (detectado vía `AgentActions`), cuando se llama a la herramienta pin_document_as_context o en el inicio de la aplicacion.
    *   Contenido: Resúmenes e Índices (TOC) de los documentos anclados.
3.  **CAPA 3: El Viaje (Dinámico - CheckPoint):** Narrativa biográfica consolidada.
4.  **CAPA 4: Memoria de Trabajo (RAM):** Mensajes recientes.


**Ventaja del Índice (TOC) en el Prompt**

Inyectar el índice en lugar del texto completo del documento de gobierno permite al agente ser consciente de las reglas sin agotar los tokens. Si el agente detecta que va a escribir código Java y su "Capa de Gobierno" le indica que hay una sección de "Estilo Java" en el documento anclado, puede usar de forma autónoma `get_partial_document` para leer la regla específica antes de responder.

## 9. Conclusión de la Reflexión
Al separar el **Relato** (lo que hemos hecho) del **Marco** (cómo debemos ser), Noema logra:

*   **Ahorro de tokens:** No se inyectan manuales enteros, solo su índice (TOC).
*   **Precisión operativa:** El agente sabe que la información existe y dónde buscarla (`get_partial_document`) si la necesita.
*   **Soberanía del usuario:** El usuario decide qué se convierte en "Ley" o "Historia" y qué es simple "Ruido".

Este modelo resuelve la paradoja de la memoria en agentes autónomos. Al separar el **Relato** (lo que hemos hecho) del **Marco** (cómo debemos ser), Noema puede mantener una coherencia biográfica y técnica total, independientemente de cuántas veces se compacte su memoria episódica. El agente no solo "recuerda", sino que "se rige" por el conocimiento adquirido.


**Nota para el futuro:** *Este anexo sienta las bases para modificar el `System Prompt` de Noema e implementar el sistema de inyección dinámica de documentos de gobierno en el arranque de cada sesión.*

# Anexo II: Gestión Dinámica de Marcos de Gobierno mediante AgentSettings

## 1. El Rol de la Configuración en la Memoria de Gobierno
Como se estableció en el Anexo I, los **Marcos de Gobierno** (instrucciones, protocolos, estilos) no deben residir en la memoria episódica (CheckPoints), ya que no son eventos narrativos sino reglas permanentes. Su lugar natural es el subsistema de configuración (**AgentSettings**).

Para permitir una gestión flexible y evitar la "saturación cognitiva" del agente, la lista de documentos de gobierno no será una simple lista de rutas, sino una estructura de control que permita activar o desactivar contextos sin necesidad de eliminarlos o re-indexarlos.

## 2. El tipo `AgentSettingsCheckedList`
Se introduce un nuevo tipo de dato en la arquitectura de configuración diseñado específicamente para la gestión de estados booleanos asociados a valores de texto: el **`AgentSettingsCheckedList`**.

*   **Estructura:** Una colección de pares `{ "checked": boolean, "value": string }`.
*   **Representación en `settings.json`**:
    ```json
    "governance": {
      "pinned_documents": [
        {"checked": true, "value": "protocolo_interaccion.md"},
        {"checked": false, "value": "estilo_java_legacy.md"}
      ]
    }
    ```

## 3. Gestión de Documentos: Estados "Activo" vs. "Durmiente"
El uso de este nuevo tipo de elemento permite al usuario (a través de la GUI) gestionar la atención del agente con precisión quirúrgica:

*   **Estado Activo (`checked: true`):** El documento forma parte del "Marco de Referencia". Su resumen y TOC se inyectan en el prompt del sistema en cada turno.
*   **Estado Durmiente (`checked: false`):** El documento permanece indexado en el `DocumentsService` y registrado en la configuración, pero es **invisible** para el agente en el turno actual. 

Esto permite al usuario "despertar" manuales técnicos o protocolos específicos solo cuando la tarea actual lo requiera, ahorrando tokens y minimizando el ruido en la ventana de contexto.

## 4. Flujo de Trabajo y Rehidratación de la Capa 2
La arquitectura del "Sandwich de Contexto" se refina para integrar el estado de los checkboxes:

1.  **Detección de Cambio:** El usuario desmarca un documento en la `JList` de ajustes. Al guardar, se dispara la acción `REFRESH_GOVERNANCE_CACHE`.
2.  **Filtrado en el `ConversationService`:** El servicio intercepta la acción y accede a la propiedad `governance/pinned_documents`.
3.  **Reconstrucción de la Caché:**
    *   El sistema itera por la lista de la configuración.
    *   **Ignora** todos los elementos con `checked: false`.
    *   Para los elementos con `checked: true`, recupera los metadatos del `DocumentsService`.
4.  **Inyección:** El nuevo bloque de texto cacheado se utiliza para la **Capa 2** del prompt en todos los turnos subsiguientes.

## 5. Extensión a la Gestión de Capacidades (Tools)
El mismo mecanismo se aplica a la activación de herramientas operacionales. Mediante una entrada de tipo `AgentSettingsCheckedList` denominada `active_tools`, el usuario puede revocar o conceder "poderes" al agente en tiempo real (ej. desactivar el acceso a la Shell o a la Web) simplemente desmarcando la opción correspondiente en la interfaz.

## 6. Conclusión
La integración de la Memoria de Gobierno en los `AgentSettings` mediante elementos de tipo `CheckedList` transforma al agente en un sistema modular. El usuario deja de ser un mero interlocutor para convertirse en el gestor del **Presupuesto de Atención** del asistente, decidiendo qué reglas le rigen y qué herramientas tiene a su disposición en cada fase del proyecto.

**Nota para el futuro:** *Este anexo vincula directamente el diseño de la memoria con la especificación técnica de la refactorización de los settings, estableciendo el componente de UI `JList con Checkboxes` como la herramienta principal de control de gobierno.*


--- 
*Este documento queda registrado como base para la implementación del sistema de anotaciones proactivas en el servicio de conversación de Noema.*



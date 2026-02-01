
# Análisis detallado del proyecto **io.github.jjdelcerro.chatagent**  
*(versión aproximada febrero 2026 – según fuentes entregadas)*

### 1. Visión general

**ChatAgent** es un **agente conversacional autónomo** escrito en **Java 21** que implementa un sistema de **memoria híbrida determinista** de muy largo plazo, diseñado específicamente para evitar la degradación cognitiva típica de los LLMs en conversaciones extensas (ventana de contexto limitada, olvido catastrófico, alucinaciones por falta de trazabilidad).

A diferencia de la mayoría de agentes/chatbots que mantienen todo en RAM o en un vector store “opaco”, este proyecto combina:

- **Narrativa evolutiva humana-legible** (“El Viaje”) guardada en checkpoints markdown
- **Turnos atómicos** persistidos en base de datos H2 (con embeddings locales)
- **Recuperación dual**: semántica (coseno sobre embeddings) + determinista (lookup por ID exacto `{cite:XXX}`)
- **Compactación periódica** asistida por LLM (MemoryManager) que fusiona historia antigua + reciente

El resultado es un agente que puede mantener coherencia durante **meses o años** de interacción, con trazabilidad auditable, sin depender de ventanas de contexto gigantes ni de bases vectoriales externas costosas.

### 2. Stack tecnológico (febrero 2026)

| Capa                        | Tecnología principal                          | Versión       | Notas importantes                                                                 |
|-----------------------------|-----------------------------------------------|---------------|-----------------------------------------------------------------------------------|
| Lenguaje                    | Java                                          | 21            | Virtual Threads, mejoras en colecciones, records, pattern matching               |
| Orquestación LLM / Tools    | **LangChain4j**                               | 0.35.0        | Abstracción de proveedores + motor de tools ReAct                                 |
| Modelos conversacionales    | OpenAI / OpenRouter / Local (gguf)            | —             | vía langchain4j-open-ai                                                           |
| Embeddings                  | **all-MiniLM-L6-v2** (ONNX)                   | —             | 100% local, ~22–50 MB RAM, muy rápido                                            |
| Base de datos / vectores    | **H2** embebida                               | 2.2.224       | Tablas + BLOBs para vectores (no usa pgvector ni chroma aún)                     |
| Almacenamiento narrativo    | Markdown en filesystem                        | —             | checkpoints/checkpoint-XXX-YYY-ZZZ.md                                            |
| Procesamiento documentos    | **Apache Tika**                               | 2.8.0         | Lee PDF, DOCX, HTML, etc. para herramientas de extracción                       |
| Interfaz CLI                | **JLine 3**                                   | 3.21.0        | Multilínea, history, Alt+Enter, confirmaciones                                   |
| Logging                     | Log4j 2 (vía SLF4J)                           | 2.23.1        | Consola + rolling file en data/agente.log                                        |
| Telegram                    | java-telegram-bot-api                         | 7.1.1         | Sensor asíncrono + envío mensajes                                                |
| Email                       | Jakarta Mail (Angus)                          | 2.0.3         | IMAP + SMTP (lectura y envío)                                                    |
| Diferencias / parches       | java-diff-utils                               | 4.12          | Usado en FilePatchTool                                                           |
| JSON                        | Gson                                          | 2.10.1        | Persistencia sesión activa (active_session.json)                                 |
| Construcción                | Maven + shade plugin                          | 3.2.4         | ServicesResourceTransformer obligatorio para SPI                                 |

### 3. Estructura de paquetes (separación clara contrato vs implementación)

```
io.github.jjdelcerro.chatagent
├── lib                        ← API pública y contratos (muy limpia)
│   ├── Agent.java
│   ├── AgentSettings.java
│   ├── AgentConsole.java
│   ├── PathAccessControl.java
│   ├── persistence
│   │   ├── Turn.java (interface)
│   │   ├── CheckPoint.java (interface)
│   │   ├── SourceOfTruth.java (interface)
│   │   └── *Exception.java
│   └── tools
│       └── AgenteTool.java (interface)
├── lib.impl                   ← Implementaciones reales
│   ├── persistence
│   │   ├── SourceOfTruthImpl.java       ← corazón de la memoria
│   │   ├── TurnImpl.java
│   │   ├── CheckPointImpl.java
│   │   └── Counter.java                 ← autoincremental simple
│   └── tools                            ← un paquete por dominio
│       ├── file
│       ├── mail
│       ├── memory
│       ├── telegram
│       └── web
├── ui
│   ├── console                  ← implementación CLI con JLine
│   └── AgentUIManager.java      ← abstracción futura (web, swing, etc.)
└── main
    └── Main.java                ← punto de entrada + bootstrap
```

### 4. Arquitectura y mecanismos principales

#### 4.1 Gestión de memoria (el núcleo diferenciador)

Tres niveles de persistencia y cuatro formas de recuperación:

Nivel          | Dónde vive                 | Persistencia | Tamaño típico     | Uso principal                             | Recuperación
---------------|----------------------------|--------------|-------------------|-------------------------------------------|-------------------------------
**Sesión activa** | RAM + active_session.json  | Temporal     | ~20–50 turnos     | Contexto inmediato del LLM                | Directa (en el prompt)
**Turnos atómicos** | H2 (tabla `turnos`)       | Permanente   | Cientos–miles     | Recuperación semántica + determinista     | `SearchFullHistoryTool` + `LookupTurnTool`
**CheckPoints**   | Markdown en /checkpoints   | Permanente   | 1 cada ~20–50 turnos | Narrativa legible a largo plazo           | Inyectado en prompt cuando existe

**Flujo de compactación** (MemoryManager):

1. Sesión > umbral (ej: 20 turnos no consolidados)
2. `MemoryManager` recibe:
   - CSV de turnos no consolidados
   - Último checkpoint.md (si existe)
3. Genera nuevo checkpoint con dos secciones:
   - **Resumen** — hechos clave, estado actual, próximos pasos
   - **El Viaje** — narrativa cronológica detallada + citas `{cite:ID}`
4. Se guarda .md en disco + metadatos en tabla `checkpoints`
5. Los turnos ya consolidados pueden (en teoría) borrarse físicamente (no implementado aún)

**Recuperación híbrida**:

- `{cite:ID-45}` → `LookupTurnTool` → recupera exactamente ese turno
- Pregunta semántica → `SearchFullHistoryTool` → cosine similarity manual sobre BLOBs de H2
- CheckPoint reciente → se inyecta entero (o parte) en el system prompt

#### 4.2 Gestión de eventos (Telegram + Email como sensores)

- Los servicios Telegram y Email están constantemente escuchando (hilos daemon).
- Cuando llega mensaje/correo → `putEvent(channel, priority, text)`
- `ConversationManager` simula una **tool result** especial: `pool_event`
- El LLM ve el evento como si fuera el resultado de una herramienta → lo procesa dentro del flujo ReAct normal → mantiene coherencia del historial.

#### 4.3 Seguridad / Sandbox

`PathAccessControlImpl`

- **Jail** obligatorio: todo path se resuelve y debe estar bajo raíz del proyecto o en lista blanca explícita.
- Diferencia **lectura** vs **escritura**.
- Bloquea escritura en `.git`, `/etc`, `/home`, etc.
- Muy útil cuando el agente tiene herramientas `FilePatchTool`, `FileSearchAndReplaceTool`, etc.

#### 4.4 Flujo principal del ConversationManager (ReAct extendido)

1. Usuario escribe → `processTurn(input)`
2. Se añade turno (user message)
3. Se construye prompt:
   - System prompt fijo
   - Último CheckPoint (si existe)
   - Últimos N turnos de sesión
4. LLM responde → puede pedir tools
5. Si pide tools → se ejecutan (con sandbox)
6. Resultado tool → nuevo turno (tool call + result)
7. Bucle hasta que el LLM decida responder al usuario
8. Respuesta final → se muestra y se guarda como turno

### 5. Herramientas del agente (AgenteTool)

Dominio       | Herramientas principales                               | Modo     | Notas
--------------|--------------------------------------------------------|----------|-----------
**File**      | FilePatchTool, FileSearchAndReplace, FileGrep, ExtractText (Tika) | r/w     | Sandbox estricto
**Web**       | WebSearchTool (Brave), WebGetTikaTool                  | read     | Lectura limpia
**Memory**    | SearchFullHistoryTool, LookupTurnTool                  | read     | `{cite:ID}` y búsqueda vectorial
**Telegram**  | SendMessage, GetUpdates…                               | r/w      | Sensor + acción
**Email**     | SendEmail, ReadInbox, etc.                             | r/w      | Sensor + acción

### 6. Construcción y despliegue

- **Maven** estándar
- **maven-shade-plugin 3.2.4** → fat JAR
- **ServicesResourceTransformer** → imprescindible (LangChain4j + Tika usan SPI)
- **Exclusión de firmas** (.SF, .DSA, .RSA) → evita fallos en runtime
- Salida → `target/io.github.jjdelcerro.chatagent.main-1.0.0.jar` (executable)

### 7. Conclusión y puntos destacados

**Fortalezas principales**

- Memoria **determinista** + narrativa legible → reduce enormemente alucinaciones
- Embeddings **100% locales** → privacidad total, baja latencia
- Arquitectura **muy limpia** (interfaces vs impl, inyección manual)
- Compactación narrativa asistida por LLM → mantiene coherencia a muy largo plazo
- Sandbox serio → permite herramientas de escritura sin miedo excesivo
- Sensores asíncronos (Telegram+Email) → agente proactivo posible

**Aspectos mejorables / por madurar (2026)**

- Borrado físico de turnos antiguos tras consolidación (ahorro espacio)
- Soporte pgvector / PostgreSQL para >100k turnos
- Compresión de checkpoints muy largos (el LLM MemoryManager puede tener límite)
- Interfaz web / móvil (hoy solo CLI rica)
- Métricas de uso de memoria y latencia en compactación
- Evaluación automática de calidad de compactación (drift semántico)

En resumen: estamos ante una de las implementaciones más serias y pensadas que he visto de **RAG narrativo + memoria híbrida determinista** en Java, orientada a proyectos reales de larga duración (desarrollo de software, investigación, gestión personal/empresarial de conocimiento).


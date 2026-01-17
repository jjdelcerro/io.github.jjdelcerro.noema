
# Documentación Técnica: Agente de Memoria Híbrida Determinista

## 1. Visión General del Proyecto
### 1.1. Objetivo y Filosofía
*   Descripción de la arquitectura de Memoria Híbrida.
*   El problema del contexto infinito vs. memoria consolidada ("El Viaje").
*   Diferencias con sistemas RAG tradicionales.

### 1.2. Stack Tecnológico
*   Java 21 (Records, Virtual Threads compatibility).
*   LangChain4j (Low-level API).
*   H2 Database (Modo embebido + BLOBs).
*   ONNX Runtime (Embeddings locales).

## 2. Arquitectura de Software
### 2.1. Estructura de Paquetes
*   Separación de API (`lib.*`) e Implementación (`lib.impl.*`).
*   `persistence`: El contrato de datos.
*   `agent`: La lógica cognitiva.
*   `tools`: Capacidades del agente.
*   `main`: Inyección de dependencias y punto de entrada.

### 2.2. Diagrama de Componentes Conceptual
*   Relación entre `ConversationAgent`, `MemoryManager` y `SourceOfTruth`.

## 3. Capa de Persistencia (Source of Truth)
### 3.1. Modelo de Datos
*   **Entidad `Turn`:** Estructura atómica inmutable.
*   **Entidad `CheckPoint`:** Dualidad Metadatos (BD) vs Contenido (Markdown en Disco).
*   **Gestión de Identidad:** El patrón de "Inicialización Retardada" (IDs transitorios `-1` vs IDs persistidos).

### 3.2. Estrategia de Almacenamiento Vectorial
*   Por qué H2 y no una VectorDB dedicada.
*   Almacenamiento de embeddings como BLOBs binarios.
*   Búsqueda por "Full Scan" optimizado con operaciones SIMD (vía Java Math).
*   Generación de Embeddings locales (ONNX `all-minilm-l6-v2`) en el momento de la inserción.

### 3.3. Inversión de Control en la Persistencia
*   El rol de `SourceOfTruth` como factoría transaccional.
*   Mecanismo de escritura segura de ficheros (garantía de IDs consistentes).

## 4. El Motor Cognitivo (ConversationAgent)
### 4.1. Ciclo de Vida del Turno (`processTurn`)
*   Flujo Reactivo: Usuario -> Contexto -> LLM -> (Tools) -> Respuesta.
*   Gestión del input de usuario (`pendingUserText`) para evitar duplicidad en cadenas de herramientas.

### 4.2. Gestión de Estado "Dividido" (Split State Strategy)
*   **Memoria Activa (RAM):** Fidelidad total (JSONs completos, respuestas de tools sin recortar).
*   **Almacenamiento (DB):** Política de higiene y recorte (`applyStoragePolicy`).
*   Justificación: Análisis en caliente vs. Almacenamiento eficiente.

### 4.3. Estrategia de Compactación
*   El disparador (`COMPACTION_THRESHOLD`).
*   Algoritmo de **Ventana Deslizante**: Compactación de la mitad más antigua `n/2` y preservación del *overlap* en memoria activa.
*   Evitación de "cortes" de contexto para el usuario.

## 5. El Gestor de Memoria (MemoryManager)
### 5.1. Protocolo de Compactación
*   Prompt del Sistema (Protocolo v4).
*   Modos de operación: Creación vs Actualización.
*   Generación del formato CSV para el LLM.

### 5.2. Independencia de la Persistencia
*   Cómo el `MemoryManager` delega la creación física en `SourceOfTruth`.

## 6. Sistema de Herramientas
### 6.1. Arquitectura de Tools
*   Interfaz `AgenteTool`.
*   Parseo de argumentos JSON con validación de tipos.

### 6.2. Herramientas Implementadas
*   `search_full_history`: Búsqueda semántica pura.
*   `lookup_turn`: Recuperación determinista y reconstrucción de contexto temporal.

## 7. Guía de Uso y Configuración
### 7.1. Requisitos Previos
*   Variables de entorno (`OPENAI_API_KEY`).
*   Estructura de directorios (`./data`).

### 7.2. Ejecución
*   Compilación con Maven (Shade Plugin).
*   Ejecución del JAR.

## 8. Decisiones de Diseño y Futuras Mejoras
*   Discusión sobre el manejo de errores en el bucle de herramientas.
*   Posibles optimizaciones para H2 cuando la tabla crezca (Índices vectoriales o migración a PostgreSQL).
*   Extensibilidad para nuevos modelos de LLM.

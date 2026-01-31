# GEMINI.md - Contexto del Proyecto: ChatAgent de Memoria Híbrida

Este documento proporciona el contexto arquitectónico y técnico para el proyecto `io.github.jjdelcerro.chatagent`, un sistema de agente conversacional diseñado para mantener una memoria coherente a largo plazo sin degradación cognitiva.

## 1. Visión General del Proyecto
El proyecto implementa un **Agente de Memoria Híbrida Determinista**. A diferencia de los sistemas RAG estándar que fragmentan la información, este agente mantiene una continuidad narrativa ("El Viaje") combinada con una base de conocimiento atómica e inmutable.

### Características Clave:
- **Memoria Híbrida:** Combina un relato narrativo en Markdown (CheckPoints) con eventos individuales en base de datos (Turnos).
- **Determinismo:** Utiliza referencias explícitas `{cite:ID}` para garantizar la trazabilidad y evitar alucinaciones en la recuperación de información.
- **Autonomía Semántica:** Genera embeddings de forma local mediante **ONNX Runtime** (all-minilm-l6-v2), permitiendo búsquedas vectoriales sin dependencia de APIs externas.
- **Protocolo de Compactación:** Un gestor de memoria especializado (`MemoryManager`) consolida periódicamente la historia reciente en un nuevo resumen narrativo.

## 2. Stack Tecnológico
- **Lenguaje:** Java 21 (LTS).
- **Framework IA:** LangChain4j (0.35.0) utilizando la API de bajo nivel para control total del flujo.
- **Modelos LLM:**
    - Razonamiento/Conversación: GLM-4.5 (vía OpenRouter).
    - Consolidación de Memoria: DeepSeek R1 (vía OpenRouter).
- **Persistencia:**
    - Base de Datos: H2 (Modo embebido).
    - Almacenamiento Narrativo: Ficheros Markdown (.md).
- **Motor de Embeddings:** Local (ONNX Runtime).
- **Interfaz de Usuario:** CLI enriquecida con JLine.
- **Herramientas Integradas:** Gestión de ficheros, navegación web (Tika/Brave), Telegram, Email (Jakarta Mail).

## 3. Arquitectura de Software
El proyecto sigue principios de **Inversión de Dependencias** y arquitectura modular:

- `io.github.jjdelcerro.chatagent.lib.persistence`: Contratos para el almacenamiento (Source of Truth).
- `io.github.jjdelcerro.chatagent.lib.impl.agent`: El núcleo cognitivo (`ConversationAgent` y `MemoryManager`).
- `io.github.jjdelcerro.chatagent.lib.impl.persistence`: Implementación física en H2 y Sistema de Ficheros.
- `io.github.jjdelcerro.chatagent.lib.impl.tools`: Capacidades del agente (File, Web, Memory, etc.).

## 4. Guía de Ejecución y Desarrollo

### Construcción
El proyecto utiliza Maven para generar un "Fat JAR" que incluye todas las dependencias y transformadores de servicios necesarios para LangChain4j.

```bash
mvn clean package
```

### Ejecución
Es obligatorio configurar la clave de API del LLM como variable de entorno.

```bash
export LLM_API_KEY="tu_clave_aqui"
java -jar target/io.github.jjdelcerro.chatagent.main-1.0.0.jar
```

### Variables de Envorno Opcionales
- `BRAVE_SEARCH_API_KEY`: Para habilitar búsquedas web.
- `TELEGRAM_API_KEY` y `TELEGRAM_CHAT_ID`: Para integración con Telegram.
- `EMAIL_USER` y `EMAIL_PASS`: Para herramientas de correo electrónico.

## 5. Convenciones de Desarrollo
- **Inmutabilidad:** Los objetos `Turn` y `CheckPoint` son inmutables una vez persistidos.
- **Gestión de IDs:** Los IDs son deterministas y gestionados por `SourceOfTruth`. No se deben generar IDs manualmente fuera de la capa de persistencia.
- **Política de Recorte:** La base de datos H2 aplica un recorte automático a resultados de herramientas que superen los 2KB para mantener la base de datos ligera, manteniendo el contenido completo solo en la memoria activa (RAM) durante la sesión.
- **Protocolo Narrativo:** Cualquier cambio en la forma en que el agente "recuerda" debe reflejarse en el `SYSTEM_PROMPT` de `MemoryManager.java` (Protocolo v4).
- **Logging:** Se utiliza Log4j2 gestionado a través de SLF4J. La configuración se encuentra en `src/main/resources/log4j2.properties`.
- **Tests:** El proyecto actualmente no dispone de tests unitarios automáticos en `src/test/java`. Se recomienda añadir tests para la lógica de compactación y búsqueda vectorial.

## 6. Estructura de Datos (Runtime)
Al ejecutarse, el sistema genera una carpeta `./data/` con:
- `memoria.mv.db`: Base de datos H2.
- `checkpoints/`: Ficheros Markdown con la historia consolidada.
- `turns.csv`: Log de transacciones para depuración y re-entrenamiento del `MemoryManager`.

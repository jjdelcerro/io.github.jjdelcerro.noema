
# Memoria Procedimental y Catálogo de Habilidades (Skills)

La arquitectura de Noema se completa con la integración de la **Memoria Procedimental**. Mientras que el Core define las leyes y el Environ define el contexto, las **Habilidades (Skills)** representan el «know-how» técnico del agente: protocolos específicos, flujos de trabajo y manuales de ejecución paso a paso que el agente puede invocar para realizar tareas con precisión quirúrgica. Al igual que en el sistema de entorno, Noema utiliza el patrón de ficheros gemelos para mantener un catálogo ligero de capacidades descubribles.

## 1. Estructura de Almacenamiento: `/var/identity/skills/`
Los protocolos de habilidad residen en una carpeta dedicada, permitiendo una gestión independiente de la identidad personal. Cada habilidad consta de dos componentes:
1.  **El Protocolo Maestro (`skill_id.md`):** El documento denso que contiene la lógica procedimental, comandos exactos, guías de estilo o flujos de decisión (ej: `deploy_gvsig_plugin.md`).
2.  **La Referencia de Habilidad (`skill_id.ref.md`):** Un párrafo conciso que define **para qué sirve** la habilidad y en qué situaciones el agente debe considerar su uso.

## 2. El Catálogo de Descubrimiento (Capa 2d)
El `ReasoningServiceImpl` añade una cuarta sección al sándwich de contexto bajo el encabezado **[MEMORIA PROCEDIMENTAL]**. En lugar de inyectar los manuales, el orquestador escanea los archivos `.ref.md` y presenta un menú de opciones:

> **[MEMORIA PROCEDIMENTAL]**
> Posees protocolos técnicos para tareas complejas. Si no estás seguro de cómo proceder, usa `list_skills()` para ver tu catálogo y `load_skill(id)` para recuperar el manual:
> - `format_scifi`: Protocolo para formatear artículos de ciencia ficción.
> - `java_refactor`: Guía para refactorizaciones seguras en entornos legacy.

## 3. Herramientas de Gestión de Habilidad
El agente interactúa con su propia memoria procedimental mediante dos herramientas de introspección:

*   **`list_skills()`**: Devuelve el listado completo de IDs y descripciones extraídas de los ficheros de referencia. Es el punto de partida cuando el agente detecta una tarea desconocida.
*   **`load_skill(skill_id)`**: Realiza la carga del protocolo denso en la RAM (Session). Al igual que con el entorno, esta carga es **volátil** y solo persiste en la base de datos como una etiqueta de «Habilidad cargada», preservando la higiene del historial.

## 4. Protocolo de Generación Automática de Referencias
Para asegurar que el catálogo sea coherente y fácil de procesar por el LLM, Noema utiliza un **Prompt de Generación de Skill-Ref** cada vez que se añade un nuevo protocolo:

> **Prompt de Referencia de Habilidad Noema:**
> "Actúa como un Instructor Técnico para Noema. Te voy a proporcionar un manual de procedimiento (`.md`). Tu tarea es generar su archivo de referencia (`.ref.md`) siguiendo estas reglas:
> 
> 1. **Propósito Único:** Escribe un párrafo de máximo 3 líneas que describa exactamente QUÉ problema resuelve este protocolo.
> 2. **Contexto de Activación:** Define claramente en qué fase de una conversación el agente debería sentir la necesidad de leer este manual.
> 3. **ID de Habilidad:** Usa el nombre del archivo como identificador único."

## 5. El Escenario de Uso: Del Pensamiento a la Acción
La utilidad de este sistema radica en la **precisión operativa**. 
*   **Situación:** El usuario le pide a Noema que prepare un despliegue de código.
*   **Metacognición:** El agente razona: *"Conozco gvSIG, pero no recuerdo la política de nombres para el despliegue. Veo en mi [MEMORIA PROCEDIMENTAL] que tengo un módulo `deploy_policy`. Voy a consultarlo antes de dar una respuesta errónea"*.
*   **Acción:** `load_skill('deploy_policy')`.

Este anexo dota a Noema de un **Cinturón de Herramientas Cognitivo**. El agente ya no tiene que "adivinar" cómo trabajar; tiene una biblioteca de manuales técnicos que consulta de forma consciente, garantizando que su comportamiento sea siempre profesional, predecible y alineado con los estándares del usuario.


Aquí tienes el documento de diseño arquitectónico. Mantiene el tono formal, riguroso y directo de la identidad del proyecto, detallando tanto el cambio de paradigma conceptual como la implementación técnica y las trampas a evitar.

---

# Documento de Diseño Técnico: Bifurcación Cognitiva y Herencia de Memoria en Subagentes (Memory Overlay)

* **Módulo:** `io.github.jjdelcerro.noema.lib.services.subagents` / `memory`
* **Ámbito:** Kernel, Pipeline de Memoria, Persistencia H2
* **Estado:** Propuesta Arquitectónica

---

## 1. El Problema: El Cuello de Botella de la Serialización

En la implementación actual, los subagentes operan bajo el paradigma de **"Subcontrata Externa"**. Nacen en un entorno aislado, completamente amnésicos, y dependen de que el agente principal les transfiera todo el contexto necesario a través de los parámetros en formato texto (`prompt_ini`, argumentos JSON).

Este enfoque presenta deficiencias estructurales propias de los sistemas multi-agente clásicos:
1. **Compresión con pérdidas (*Lossy Compression*):** El agente principal, que acumula docenas de turnos de contexto, acuerdos de diseño y pruebas fallidas con el usuario, se ve obligado a resumir toda esa riqueza en una breve llamada a la herramienta `launch_subagent`. Inevitablemente, se omiten detalles críticos, casos límite o premisas sutiles.
2. **Fatiga de Contexto:** Exigir al LLM principal que redacte *briefings* exhaustivos consume tokens, aumenta el riesgo de generar JSON malformados y desvía su atención de la conversación principal hacia la microgestión de la delegación.
3. **Incapacidad de referenciar el pasado:** Un subagente amnésico no puede ejecutar `fetch_citation` sobre una decisión arquitectónica tomada hace dos días, porque su base de datos histórica nace vacía.

## 2. El Nuevo Paradigma: Bifurcación Cognitiva (*Fork* / *Copy-on-Write*)

Se propone abandonar el modelo de subcontrata a favor de un modelo de **Bifurcación Mental (*Fork*)**. Un subagente ya no es una entidad separada; es un clon temporal de la propia mente de Noema en un instante determinado.

El mecanismo técnico se inspira en los sistemas de archivos *OverlayFS* o en la memoria *Copy-on-Write* (CoW):
* **Capa Inferior (El Padre):** El historial completo (`EpisodicMemory`) y el estado conversacional actual (`RecentMemory`) del agente principal se montan en el subagente en modo **Solo Lectura**.
* **Capa Superior (El Hijo):** El subagente cuenta con su propia memoria RAM y su propia base de datos H2 temporal (`sub_memory`). Todas las mutaciones (nuevos pensamientos, llamadas a herramientas, resultados) se escriben exclusivamente en esta capa efímera.

Al finalizar la tarea, la capa superior se destruye (o destila un resultado) y el padre continúa su ejecución sin que su hilo principal haya sido contaminado por los decenas de turnos intermedios del subagente.

---

## 3. Arquitectura Técnica y Estratificación de la Memoria

La implementación requiere extender las interfaces de memoria actuales mediante patrones de diseño *Decorator* o *Proxy*.

### 3.1. `RecentMemory` en el Subagente
Al instanciar el subagente, su `RecentMemory` se inicializa clonando la lista de mensajes proyectados del padre en ese instante. A partir de ahí, las operaciones `add()` o `remove()` solo afectan a la colección local del subagente.

### 3.2. `OverlayEpisodicMemoryImpl`
Se requiere una nueva implementación de `EpisodicMemory` para el subagente que actúe como un enrutador inteligente:

* **Escritura (`add`):** Las inserciones de nuevos `Turn` o `ConsolidateMemory` se dirigen SIEMPRE a la base de datos H2 temporal del subagente.
* **Lectura Directa (`getTurnById`, `getConsolidateMemoryById`):** 
  1. Busca en la H2 del subagente. 
  2. Si no lo encuentra, delega la búsqueda en la H2 del padre. Esto garantiza que las citas (`{cite:ID}`) funcionen con total transparencia.
* **Búsqueda Semántica (`getTurnsByText`):**
  Lanza la búsqueda contra ambas bases de datos. Los vectores resultantes se introducen en el mismo `EmbeddingFilter` (MaxP), el cual se encarga de unificarlos y ordenarlos por similitud coseno devolviendo un único Top-K transparente al modelo.

---

## 4. Resolución de Trampas Arquitectónicas (CRÍTICO)

La adopción de este modelo rompe tres supuestos básicos del código actual que deben ser manejados quirúrgicamente para evitar la corrupción de datos o la esquizofrenia del modelo.

### Trampa A: Colisión de Identificadores (El `Counter` de H2)
**El Riesgo:** Si el subagente arranca su H2 temporal vacía, el primer turno que genere tendrá el `ID = 1`. Cuando el subagente intente recuperar la cita `{cite:1}`, el sistema leerá el turno de su H2 local, ocultando (haciendo *shadowing*) el turno 1 legítimo del padre.
**La Solución:** El inicializador de la memoria episódica del subagente no debe empezar en 0. Su `Counter` interno debe inicializarse explícitamente consultando el ID máximo del padre en el momento del *fork*:
`childCounter = new Counter(parentMemory.getMaxTurnId())`.

### Trampa B: La Consolidación Espuria
**El Riesgo:** Al heredar, por ejemplo, 30 turnos recientes del padre, basta con que el subagente ejecute 10 acciones para superar el umbral de consolidación (`MEMORY_CONSOLIDATION_TURNS = 40`). Esto disparará a `MemoryConsolidationService`, forzando al LLM a gastar tokens en redactar un resumen narrativo de un hilo efímero que va a ser destruido minutos después.
**La Solución:** En el entorno del subagente, el método `RecentMemory.needConsolidation()` debe ser forzado por diseño a devolver **siempre `false`**. Los trabajadores delegados no redactan autobiografías.

### Trampa C: El Sesgo Conversacional del Rol
**El Riesgo:** Al heredar la memoria reciente, el subagente verá que la conversación inicia con saludos, debates de tono coloquial o dudas del usuario. Si se deja guiar por la inercia del diálogo, puede "olvidar" que es un proceso en segundo plano y empezar a dialogar con un usuario que no le está leyendo.
**La Solución:** El `System Prompt` de las recetas XML debe incluir una directiva restrictiva (*override* cognitivo):
> *"Heredas el contexto de la conversación activa para comprender los antecedentes, pero TU NATURALEZA ES LA DE UN TRABAJADOR AISLADO. No tienes un usuario interactivo enfrente. Tienes estrictamente prohibido conversar, saludar o divagar. Tu única función es ejecutar la tarea técnica asignada y devolver el resultado final."*

---

## 5. Impacto Operativo y Evolución del Sistema

### 5.1. Fricción Cero en la Delegación
La herramienta `launch_subagent` experimenta una simplificación drástica. Ya no es necesario que el agente trate de pasar extensos diccionarios de variables por el parámetro `params`. El LLM principal puede delegar de la misma forma que un humano:
`launch_subagent(name: "test_runner", params: {instruction: "implementa la alternativa B de la que acabamos de hablar y verifica que pasa los tests"})`.

### 5.2. Evolución de las Recetas XML
Las definiciones en `var/subagents/*.xml` dejarán de ser tuberías rígidas de datos (ej. "Toma el archivo X y saca el archivo Y"). Evolucionan hacia **Especializaciones de Rol Cognitivo**.

Ejemplos de la nueva taxonomía de subagentes:
* **`auditor_seguridad.xml`**: Un rol pesimista que hereda el contexto de lo que el agente acaba de programar y utiliza herramientas para encontrarle vulnerabilidades antes de dar el visto bueno al usuario.
* **`refactorizador.xml`**: Un operario que recibe la orden, lee el grafo en `codebase-memory-mcp` aprovechando que ya sabe de qué partes del sistema se estaba hablando, ejecuta parches, invoca al LSP para comprobar errores y muere devolviendo un `STATUS: OK`.

### 5.3. Mantenimiento del Principio de Identidad
Esta mejora asegura que Noema no se comporta como un enjambre (*swarm*) de agentes inconexos debatiendo a través de sockets de texto, sino como **una única mente capaz de aislar hilos de ejecución asíncronos**, respetando el axioma fundamental de la arquitectura: la gestión eficiente y determinista de la atención.



# Punto de Guardado: La arquitectura "Albert" y la consolidación de la conciencia multiterminal


## 1. Resumen

*   **Objetivo:** Diseñar el modelo de consciencia y memoria para que Noema soporte múltiples terminales/subcanales simultáneos (inspirado en la IA Albert de la novela *Pórtico*), manteniendo una mente unificada pero sin contaminar los flujos de conversación individuales.
*   **Decisiones arquitectónicas clave:**
    *   **Aislamiento en caliente:** Las sesiones activas (`Session`) y los checkpoints en tiempo real se mantendrán estrictamente separados por subcanal para evitar el sangrado de contexto y la esquizofrenia narrativa durante la compactación.
    *   **Omnisciencia en frío (Base de datos):** La memoria a largo plazo (H2) elimina los filtros por subcanal en sus búsquedas. Herramientas como `search_full_history` operarán a nivel global.
    *   **Visión periférica JIT (Just-In-Time):** La consciencia de otras sesiones activas no se persistirá en el checkpoint, sino que se inyectará como un mensaje de sistema efímero justo antes de la última instrucción del usuario. Esto previene la contaminación de la memoria y protege el *Cache Hit* del *Prompt Caching* del LLM.
    *   **Consolidación en frío (El sueño de Noema):** Se esboza una nueva fase de arquitectura donde la unificación de los checkpoints individuales en un "Diario Global" se realizará de forma asíncrona mediante el `SchedulerService` durante los periodos de inactividad, imitando la consolidación de memoria biológica durante el sueño.
*   **Próximos pasos:** 
    *   Modificar las consultas de `SourceOfTruthImpl` para abrir el acceso global a las herramientas de memoria.
    *   Implementar la inyección efímera del estado periférico en `ReasoningServiceImpl.prepareContextForLLM`.
    *   Diseñar el proceso de "Consolidación en frío" y el prompt de fusión narrativa diaria.

## 2. El viaje

### 2.1. El fantasma de Albert y los canales paralelos
La sesión comenzó con una inquietud fundamental sobre la arquitectura actual de canales aislados en Noema. Rescatando la figura de Albert, el holograma terapéutico de *Pórtico* de Frederik Pohl, se planteó el objetivo de lograr una inteligencia artificial que, aunque mantenga conversaciones independientes (por ejemplo, una conmigo y otra con Javi), posea una conciencia de fondo unificada. La implementación actual en el repositorio, estrictamente segmentada por la cláusula `WHERE subchannel = ?`, impedía mecánicamente esta omnisciencia.

### 2.2. El dilema de la compactación y la paradoja del aislamiento
Al abordar cómo materializar esta mente unificada, exploramos inicialmente la idea de mantener las sesiones vivas separadas pero unificar los checkpoints en caliente. A primera vista parecía una solución limpia, pero rápidamente detectamos graves problemas de "fontanería" y carga cognitiva. Razonamos que un evento en el canal de Javi podría disparar un truncamiento en mi sesión activa, destruyendo el contexto vivo (como la lectura de un código fuente) por una dependencia temporal ajena. Peor aún, enfrentar al LLM encargado de la compactación a un CSV donde se intercalan debates de programación con chistes aleatorios garantizaba una esquizofrenia narrativa y un colapso en la calidad de "El Viaje". Acordamos entonces retroceder: el aislamiento en caliente debía mantenerse, trasladando la unificación puramente a la memoria episódica a través de herramientas de búsqueda globales.

### 2.3. La invención de la propiocepción mediante inyección efímera
A pesar de resolver la estructura de memoria, persistía una fricción. Se necesitaba que Noema tuviera una "visión periférica" para saber de la existencia de otras conversaciones sin conocer su contenido, proponiendo inicialmente guardar este dato en el checkpoint. Sin embargo, acoplar estados efímeros en un archivo biográfico estático se consideró un error de diseño.

La alternativa que surgió fue inyectar dinámicamente este estado en el `System Prompt`. En este punto se produjo un giro técnico brillante: alterar el `System Prompt` invalidaría la caché de prefijo del proveedor del LLM, disparando los costes y la latencia. La síntesis final fue elegante y letal: inyectar la visión periférica como un `SystemMessage` de un solo uso en la capa más profunda de `prepareContextForLLM`, justo antes del último mensaje del usuario. Se lograba así un conocimiento *Just-In-Time* que no contamina el historial, no rompe el *Cache Hit* y dota al modelo de la conciencia espacial deseada.

### 2.4. La consolidación offline y el nacimiento del "sueño de Noema"
Con la infraestructura de tiempo real estabilizada, la conversación derivó hacia una ambición mayor: ¿y si la unificación de los checkpoints no se hace nunca en caliente, sino cuando no hay conversaciones activas? 

Esta propuesta cerró la sesión con una resonancia casi poética, paralela a la neurociencia cognitiva. Denominamos a esta arquitectura "El sueño de Noema". Durante el día (modo caliente), el sistema opera en silos hiperenfocados; durante la inactividad (modo frío), un proceso en segundo plano orquestado por el `SchedulerService` recoge los hilos aislados, los purga y los consolida en una narrativa autobiográfica global. El debate concluyó con la constatación de que este enfoque asíncrono no solo resuelve la carga cognitiva del modelo y elimina el sangrado de contexto, sino que asienta una base estructural genuina para un agente verdaderamente proactivo y continuo.

## Apendice I


### Arquitectura de consciencia multiterminal: el modelo Albert en Noema

El objetivo de este diseño es dotar a Noema de una "mente unificada" capaz de atender múltiples terminales o canales de comunicación simultáneos. La inspiración original proviene de "Albert", la inteligencia artificial de la novela *Pórtico*, capaz de desdoblar su interfaz para dialogar con distintos usuarios a la vez, manteniendo un único núcleo de conocimiento y experiencia.

La implementación de este modelo en una arquitectura basada en LLMs presenta fricciones mecánicas inmediatas: cómo compartir conocimiento sin mezclar contextos, cómo consolidar la memoria sin bloquear las sesiones vivas, y cómo mantener la eficiencia en el consumo de tokens. Este documento detalla la solución arquitectónica estructurada en cuatro pilares.

#### El aislamiento de la memoria de trabajo y la prevención del sangrado de contexto

La primera intuición para lograr una mente unificada suele ser fusionar el estado de todas las conversaciones en un único "Checkpoint Global" en tiempo real. Sin embargo, este enfoque provoca el colapso inmediato de la memoria de trabajo, un fenómeno que identificamos como **sangrado de contexto** (*context bleed*).

Si unificamos la evaluación de compactación, el ciclo de vida de una sesión queda a merced de eventos externos. Por ejemplo: un usuario está realizando una depuración compleja, requiriendo que la memoria activa (`SessionImpl`) mantenga cargados varios fragmentos de código en crudo mediante la herramienta `read_paginated_resource`. Si en ese mismo instante otro usuario interactúa rápidamente por otro canal (ej. Telegram), el contador global de turnos superará el umbral, forzando una compactación general. 

El resultado es doblemente destructivo:
1.  **Amnesia local:** La sesión de depuración se trunca. El usuario pierde el código fuente de su memoria a corto plazo porque la sesión se ha "resumido" prematuramente.
2.  **Caos cognitivo:** El LLM encargado de generar la narrativa de "El Viaje" recibe un CSV de turnos donde se intercalan lógicas de programación con charlas triviales del segundo usuario. Al intentar forzar una causalidad inexistente entre eventos asíncronos, el modelo generará un texto fracturado o directamente alucinado.

**La solución:** El aislamiento en caliente. La memoria de trabajo (`SessionImpl`) y la generación de checkpoints en tiempo real deben mantenerse estrictamente segregadas por `subchannel`. Cada canal conserva el control absoluto sobre su propio ciclo de compactación, garantizando la coherencia narrativa del hilo y protegiendo el contexto vivo de interferencias externas.

#### La base de datos como subconsciente global unificado

Si las sesiones vivas operan en silos, la "mente unificada" debe materializarse en la capa de persistencia a largo plazo. Actualmente, la arquitectura restringe todas las consultas SQL en `SourceOfTruthImpl` mediante la cláusula `WHERE subchannel = ?`. 

Para implementar el modelo Albert, se invierte esta restricción en las herramientas de recuperación de memoria episódica:

*   **Búsqueda transversal:** Herramientas como `search_full_history` operarán sobre la totalidad de la tabla de turnos en H2 (`WHERE embedding_blob IS NOT NULL`), ignorando el canal de origen. 
*   **Identidad de origen:** Al devolver los resultados al LLM conversacional, el JSON generado por la herramienta debe incluir obligatoriamente el campo `subchannel`. Esto permite al modelo saber no solo *qué* ocurrió, sino *dónde* y *con quién*.
*   **Recuperación determinista:** Dado que `TurnCounter` es atómico y global, las citas (`{cite:1042}`) apuntan a un registro único en el sistema. La herramienta `lookup_turn` extraerá el turno exacto sin importar en qué canal se originó.

Esta decisión separa mecánicamente la memoria a corto plazo (silos hiperenfocados) de la memoria episódica (un *pool* global interconectado y recuperable por significado).

#### Inyección efímera para la propiocepción del sistema

El agente necesita saber que existen otras conversaciones activas en ese preciso instante para poder derivar la atención o utilizar sus herramientas de búsqueda si el usuario le pregunta por ellas. Guardar esta "visión periférica" en el archivo físico del Checkpoint contamina la biografía con datos volátiles que caducan en minutos.

La alternativa lógica es introducir esta consciencia en el `System Prompt` (ej: *"Tienes sesiones activas en el canal X e Y"*). Sin embargo, alterar el prompt de sistema en cada turno (para actualizar un *timestamp*, por ejemplo) invalida el *hash* de la petición en la API del proveedor. Esto destruye el **Prompt Caching** (caché de prefijo), obligando a recomputar todo el contexto base en cada interacción, disparando el coste económico y la latencia.

**La solución:** Inyección *Just-In-Time* de mensajes efímeros.

La arquitectura resolverá esto en la fase final de `prepareContextForLLM`. Se evaluará el estado del mapa de sesiones en memoria y, si existen otros canales activos, se construirá un bloque de texto descriptivo. En lugar de mutar el prompt del sistema, este bloque se inyectará como un objeto `SystemMessage` independiente dentro de la lista de mensajes, insertándolo exactamente en la posición `size - 1` (justo antes del último `UserMessage` que desencadena la inferencia).

Al no pasar por `session.add()`, este mensaje no se persiste ni engorda el historial. Logra contextualizar al modelo en tiempo real sin romper el caché estático del sistema y sin generar deuda de tokens en el largo plazo.

El mensaje a insertar podria contener informacion similar a:
```
Actualmente mantienes otras sesiones de chat activas de forma paralela. No tienes el contexto en memoria, pero sabes de su existencia:
- Subcanal 'javi' (Última actividad: hace 15 minutos)
- Subcanal 'monitoring_alert' (Última actividad: ayer)
Si el usuario actual te pregunta por ellas, utiliza {SEARCHFULLHISTORY} indicando el subcanal para recuperar los detalles de la base de datos global.
```


#### La consolidación en frío como el "sueño de Noema"

Si la memoria viva se mantiene aislada por canales para proteger el contexto, necesitamos un mecanismo para fusionar definitivamente estas experiencias separadas en una única entidad autobiográfica. Realizar esto en caliente detendría el servicio y bloquearía la interacción.

El diseño propone delegar esta unificación a una fase de **Consolidación en frío**, equivalente al sueño biológico (donde el hipocampo transfiere y entrelaza vivencias hacia el neocórtex).

1.  **Detección de inactividad:** Un proceso orquestado por el `SchedulerService` monitorizará la inactividad global (por ejemplo, mediante una ventana de 2 a 3 horas sin eventos en el `SensorsService`).
2.  **Fusión narrativa:** Al dispararse el evento de consolidación, un proceso en segundo plano recopilará el último "Checkpoint Global Maestro" y todos los checkpoints temporales y turnos no consolidados de los distintos subcanales. 
3.  **Redacción multihilo:** Se utilizará un prompt especializado instruido para comprender que está evaluando líneas temporales paralelas. Su tarea será redactar una nueva iteración de "El Viaje" que entrelace las distintas actividades de la jornada de forma coherente.
4.  **Reset de estado:** Tras asegurar la persistencia del nuevo Checkpoint Maestro, el sistema archivará los checkpoints de canal y vaciará las listas de memoria viva de las instancias de `SessionImpl`.

Al despertar de este proceso, la RAM del sistema estará limpia, pero al recibir un nuevo estímulo, el prompt de sistema cargará este Checkpoint Maestro. Noema iniciará el nuevo día con la consciencia absoluta e integrada de todo lo acontecido en sus terminales hasta ese momento.


## Apendice II

Otra aproximacion distinta a la del Anexo I para la inyeccion de la consciencia multiterminal en la conversacion.

### Arquitectura de memoria federada y consciencia multiterminal

El objetivo de este diseño es dotar al agente de una consciencia global sobre múltiples canales de interacción (el modelo "Albert"), sin incurrir en la complejidad técnica de procesos asíncronos pesados (consolidación offline o "fases de sueño") y protegiendo el rendimiento económico del sistema mediante la maximización del uso de la caché del proveedor del LLM (*Prompt Caching*).

La solución abandona la idea de un único macro-documento y adopta un modelo de "Checkpoints Federados", donde la memoria episódica global reside en la base de datos, mientras que el contexto de la ventana de chat se ensambla dinámicamente mediante una inyección estructurada por degradación temporal.

#### El aislamiento de las sesiones vivas y la memoria de trabajo

La memoria de trabajo (`SessionImpl`) y su correspondiente consolidación en disco (`checkpoint-<subchannel>.md`) se mantienen estrictamente aisladas por canal. 

Mecánicamente, esto resuelve dos problemas críticos:
1.  **Prevención del sangrado de contexto:** Si un usuario está depurando código, su ventana de memoria a corto plazo no se trunca ni se contamina si un segundo usuario satura otro canal y dispara el umbral de compactación.
2.  **Protección de la caché del modelo:** El *System Prompt* base, que contiene la constitución del agente, las referencias de entorno y el checkpoint de la **conversación actual**, se consolida como un bloque estático. Dado que este bloque solo muta cuando el canal actual ejecuta su propia compactación, el *Cache Hit* en la API de inferencia (Gemini, Claude, DeepSeek) se mantiene casi al 100% turno tras turno.

```xml
<!-- Bloque estático (Alta tasa de Cache Hit) -->
# CONSTITUCIÓN Y REGLAS OPERATIVAS
[... core y environ ...]

# MEMORIA DE LA CONVERSACIÓN ACTUAL
A continuación se detalla la memoria consolidada exclusiva de esta línea temporal.

<sesion_activa>
    [Contenido íntegro de checkpoint-<canal_actual>.md (Resumen + El Viaje)]
</sesion_activa>

# OTRAS CONVERSACIONES RECIENTES (CONCIENCIA PERIFÉRICA)
Nota: Mantienes otras sesiones activas de forma paralela. La siguiente información es contexto de fondo. No respondas a estos usuarios desde aquí, pero utiliza esta información si el usuario actual hace referencia a ellos.

<canal id="javi" ultima_actividad="hace 4 horas">
   [Solo sección 1. Resumen de checkpoint-javi.md]
</canal>

<canal id="monitoring_server" ultima_actividad="hace 3 días">
   [Canal inactivo recientemente. Detalles omitidos. Utiliza búsqueda en historial si requieres contexto.]
</canal>

```

#### La base de datos como fuente de vivencias globales

Dado que los checkpoints no unifican las historias, la mente única del agente reside en la capa de persistencia (H2).

Se elimina el filtrado por `subchannel` en las herramientas de recuperación de memoria episódica (`search_full_history` y `lookup_turn`). La base de datos opera como un registro absoluto de todos los turnos. Cuando el agente realiza una búsqueda semántica, la herramienta devuelve los resultados adjuntando el campo de origen (ej. `"subchannel": "javi"`). Esto permite al modelo saber qué ocurrió y en qué contexto espacial/humano se produjo, pudiendo traer recuerdos de otras conversaciones al diálogo actual solo cuando sea estrictamente necesario.

#### Inyección efímera de la consciencia periférica

Para que el modelo sepa que existen otros hilos de conversación sin necesidad de buscar a ciegas en la base de datos, se utiliza un mecanismo de inyección dinámica *Just-In-Time*.

Se ensambla un bloque de texto con el estado del resto de canales y se inyecta como un `SystemMessage` en la lista de mensajes de la sesión, concretamente en la posición `size - 1` (justo antes del último mensaje del usuario). 

Esta técnica funcional asegura que:
1.  El modelo procese esta información como contexto de fondo inmediato.
2.  No se rompa el hash del *System Prompt* principal (salvando la caché).
3.  No se registre en la base de datos ni engorde el historial, ya que la inyección ocurre después de la carga de la memoria persistente y se descarta al terminar el turno.

#### Consolidación por degradación temporal (Time-Decay)

Para evitar que la inclusión de la consciencia periférica sature el límite de tokens, el contenido inyectado se somete a un filtro de degradación temporal. La información de otros canales pierde resolución conforme más antigua es.

El algoritmo de ensamblado del punto de guardado de otros canales aplica tres niveles de poda:

1.  **Canales calientes (actividad reciente, ej. < 24h):** Se lee el archivo del checkpoint ajeno, pero **se inyecta únicamente la sección "Resumen"** (podria ser todo el checkpoint, hay que valorarlo). Se descarta "El Viaje". El modelo sabe qué se acordó recientemente en otra mesa, ahorrando miles de tokens.
2.  **Canales tibios (actividad media, ej. 1 a 7 días):** Se omite la lectura del archivo del checkpoint. Solo se inyecta la referencia de que el canal existe y su última marca de tiempo.
3.  **Canales fríos (inactivos, ej. > 7 días):** El canal se descarta por completo de la inyección en memoria RAM. Desaparece de la consciencia periférica del agente y solo es accesible si un usuario hace una mención explícita que obligue al modelo a tirar de `search_full_history` contra la base de datos.


Esta arquitectura transforma la complejidad de la concurrencia y los procesos en segundo plano en una solución de ensamblado de texto determinista. Manteniendo el control absoluto sobre el peso del contexto, garantizamos que el agente opere como una entidad única sin comprometer la estabilidad local de cada interacción.

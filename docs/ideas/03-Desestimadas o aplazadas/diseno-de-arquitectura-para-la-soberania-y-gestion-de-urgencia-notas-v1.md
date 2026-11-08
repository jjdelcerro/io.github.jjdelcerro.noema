
# Diseño de Arquitectura para la Soberanía y Gestión de Urgencia


# 1. Filosofía de la Soberanía Cognitiva

La mayoría de los agentes de IA actuales son prisioneros de su propio bucle de razonamiento. Una vez que inician una inferencia, el sistema se vuelve «sordo» y «ciego» hasta que el modelo devuelve el último token; cualquier estímulo que llegue durante esos segundos —ya sea una alerta crítica del sistema o un comando directo del usuario— es ignorado o encolado de forma pasiva. Esta arquitectura **FIFO** (*First-In, First-Out*) es insuficiente para un organismo digital que opera en el mundo real, donde la relevancia de la información es volátil y el tiempo es un factor crítico de supervivencia operativa.

La **Soberanía Cognitiva** es la capacidad del agente para negociar su atención con el entorno en tiempo real. No se trata solo de percibir estímulos, sino de poseer la autoridad arquitectónica para decidir que el pensamiento actual ha quedado obsoleto frente a una nueva realidad. Un agente soberano no es aquel que procesa más datos, sino el que sabe cuándo debe **dejar de pensar** en lo irrelevante para atender lo urgente. El objetivo final es alcanzar una **Homeostasis Cognitiva**: un estado de equilibrio donde la atención del agente no es un recurso estático entregado al mejor postor de la cola, sino un flujo dinámico gobernado por la prioridad y la voluntad del usuario.


### 2. El Mecanismo Atómico: `ReasoningService.cancelCurrentEvent()`

Este método representa el «freno de mano» del cerebro del agente. Su única responsabilidad es romper el bucle de pensamiento actual, permitiendo que el sistema recupere el control del hilo de ejecución. En Noema, este proceso se ha diseñado para ser **atómico y sin estado permanente**, evitando la complejidad de gestionar monitores globales que comprometerían la estabilidad del sistema.

#### 2.1. El Flag de Interrupción (`abortSignal`)
La pieza central de este mecanismo es un objeto de tipo `MutableBoolean`. Este flag no es un atributo estático del modelo, sino una señal transitoria que nace y muere con cada ciclo de razonamiento:

1.  **Instanciación y Anclaje:** Cuando el `ReasoningService` inicia un turno de palabra, crea una nueva instancia del flag (inicializada a `false`) y guarda su referencia en un atributo privado de corta duración.
2.  **Transmisión al Modelo:** Esta referencia se pasa como argumento al método `generate()` del `ChatModel`. De este modo, el cerebro (SNC) y el orquestador comparten un «cable» de comunicación único para esa tarea específica.
3.  **Ejecución de la Cancelación:** Al invocar `cancelCurrentEvent()`, el servicio simplemente localiza el flag activo y cambia su valor a `true`. 

#### 2.2. La Ruptura del Bloqueo: El mecanismo de Watchdog
Dado que la llamada al LLM es síncrona y bloqueante para el hilo del razonador, el sistema necesita despertar para consultar el estado del flag. Aquí es donde entra en juego el diseño de **Streaming Encapsulado** que implementa el `ChatModel`:

*   **Interrupción por Flujo (`onNext`):** Mientras el LLM está enviando tokens, el método `onNext` del handler de streaming consulta el flag en cada pedazo de texto recibido. Si detecta que la señal es `true`, lanza inmediatamente una **`InterruptedModelGenerateException`**, lo que provoca el cierre instantáneo del socket y detiene la generación.
*   **Interrupción por Tiempo (El Watchdog de 20s):** Si el modelo se encuentra en silencio (pensando o saturado) y no hay tokens entrantes que disparen el `onNext`, el hilo virtual no se queda bloqueado eternamente. El método `wait(20000)` dentro de la llamada al modelo despierta cada 20 segundos por puro tiempo. Al despertar, el bucle de control comprueba el flag `abort`, detecta la voluntad de cancelar y lanza la excepción de interrupción.

#### 2.3. Saneamiento y Recuperación
Al ser una operación atómica, `cancelCurrentEvent()` no se preocupa de la cola sensorial ni de las estadísticas; su único fin es provocar la excepción que libera al `eventDispatcher`. Una vez que el hilo despierta:

*   **Captura y Limpieza:** El orquestador atrapa la excepción de interrupción, lo que le permite detener el bucle ReAct actual de forma controlada.
*   **Higiene de Referencias:** Como el flag se pone a `null` al finalizar la llamada (en un bloque `finally`), el sistema garantiza que una interrupción no «manche» ni afecte a la siguiente percepción que el agente deba procesar.

Este diseño evita la sobreingeniería de monitores compartidos y asegura que el agente siempre tenga un punto de salida, garantizando que el razonamiento sea una tarea **cancelable bajo demanda**.

### 3. El SNA como Gestor de Atención: `SensorsService.userCancelRequested()`

Tradicionalmente, en un sistema de IA, el botón "Abortar" envía una señal directa al modelo para que deje de escribir. En Noema, esta acción es mucho más sofisticada. Al invocar el método `userCancelRequested()` en el sistema sensorial, el agente ejecuta un protocolo de **Sincronización de Voluntad**, asegurando que la interrupción no solo calle al LLM, sino que también limpie el estado de los deseos del usuario que aún no han sido procesados.

#### 3.1. Centralización de la Soberanía en el SNA
La decisión de ubicar este método en el `SensorsService` y no en el de razonamiento responde a un principio de **encapsulación de la percepción**:
1.  **Visibilidad Total:** Solo el servicio de sensores conoce la totalidad de la «percepción pendiente» (lo que hay en la cola). El razonador solo ve el evento que tiene entre manos.
2.  **Desacoplamiento de la GUI:** La interfaz de usuario (Swing o Consola) no necesita conocer la complejidad de cómo se cancela un LLM o cómo se limpia una cola. Solo emite una intención: *"El usuario ha pedido cancelar"*.
3.  **El Cuerpo manda sobre la Atención:** Es el SNA quien orquesta qué estímulos sobreviven a la interrupción, manteniendo al cerebro libre de gestionar la fontanería de la cola.

#### 3.2. La "Purga de Voluntad" (Higiene de la Cola)
Cuando un usuario pulsa el botón de abortar, generalmente lo hace por uno de dos motivos: porque el agente se está equivocando en la respuesta actual o porque el usuario se ha arrepentido de lo que envió. `userCancelRequested()` aborda este segundo punto mediante la **Purga de Naturaleza `USER`**:

*   **Identificación Semántica:** El método recorre la `deliveryQueue` y localiza todos los eventos cuya `SensorNature` sea **`USER`**. 
*   **Eliminación de Intenciones Obsoletas:** Borra físicamente estos eventos de la cola. Esto garantiza que si el usuario escribió tres mensajes seguidos y luego pulsó "Abortar", el sistema no intente procesar los otros dos mensajes una vez que el actual se detenga. Es el respeto técnico al "arrepentimiento" del humano.
*   **Preservación del Entorno:** Crucialmente, el SNA **no borra** los eventos de otras naturalezas (como `DISCRETE` de una alarma o `MERGEABLE` de un correo). Estos estímulos provienen del entorno, no de la voluntad del usuario, y siguen siendo hechos veraces que el agente debe conocer en cuanto recupere la calma.

#### 3.3. El "Apretón de Manos" con el Razonamiento
Una vez que el SNA ha limpiado el futuro (la cola), procede a detener el presente (la inferencia). 

1.  **Llamada al Ejecutor Atómico:** El `SensorsService` invoca el método `cancelCurrentEvent()` del `ReasoningService`.
2.  **Activación de la Reacción en Cadena:** Como vimos en el punto anterior, esto activa el flag de aborto, el streaming detecta la señal (vía `onNext` o vía Watchdog de 20s) y el hilo del razonador despierta por la excepción.
3.  **Preparación del Próximo Estado:** Mientras el razonador está muriendo y limpiando su rastro, el SNA ya ha dejado la cola preparada con el siguiente evento relevante (presumiblemente una alerta de sistema o el buzón vacío), asegurando una transición fluida.

#### 3.4. Consecuencias en la Experiencia de Usuario
Este diseño convierte al "Abortar" en un **Borrón y Cuenta Nueva Inteligente**:
*   El agente se calla de inmediato (o en un máximo de 20s).
*   Los mensajes que el usuario envió por error o impaciencia desaparecen del sistema.
*   El agente, al quedar libre, mira sus sensores y puede decir: *"He parado por tu orden. Mientras tanto, he visto que ha llegado este correo..."*.

En resumen, `userCancelRequested()` es el método donde reside la **inteligencia de la interrupción**. No es solo un corte eléctrico; es una maniobra de gestión de atención que protege la integridad del diálogo y la relevancia de la percepción ante la intervención soberana del usuario.


### 4. Lógica de Prioridad y «Golpe de Estado» Sensorial

Este protocolo es el encargado de romper la linealidad del procesamiento FIFO. Su objetivo es asegurar que Noema nunca se quede «atrapada» en una tarea trivial (como redactar un informe de bajo valor) mientras el entorno le grita un evento crítico (como un fallo de seguridad o una instrucción urgente). Aquí, el SNA asume la autoridad total sobre el tiempo y la atención del SNC.

#### 4.1. El Triaje Sensorial: Niveles de Prioridad
Para que este mecanismo funcione, cada estímulo que entra a través de `putEvent` debe llevar consigo un metadato de urgencia. En nuestra arquitectura, definimos una escala jerárquica:
1.  **Prioridad NORMAL:** Eventos estándar del entorno (correos, logs habituales, recordatorios).
2.  **Prioridad ALTA / URGENTE:** Estímulos que requieren una reorientación inmediata de la atención (alertas críticas, mensajes directos del usuario en ciertos contextos).

El SNA mantiene un registro de la **Prioridad de la Tarea Actual**. Mientras el `ReasoningService` está procesando un evento, el sistema sensorial sabe qué nivel de urgencia está ocupando el cerebro. Esta comparación constante es el motor del golpe de estado.

#### 4.2. El Protocolo de Interrupción Automática
Cuando el `SensorsService` recibe un estímulo, ejecuta el siguiente algoritmo de evaluación de soberanía:

1.  **Detección del Conflicto:** Se compara la prioridad del evento entrante con la prioridad de la tarea que el razonador tiene entre manos.
2.  **Validación del Umbral:** El «Golpe de Estado» solo se dispara si la prioridad entrante es **estrictamente superior** a la actual. Un evento URGENTE puede interrumpir a uno NORMAL, pero un evento URGENTE no interrumpirá a otro que ya sea URGENTE (soberanía del pensamiento en curso).
3.  **Activación del Protocolo:** Si se cumple la condición, el SNA no llama inmediatamente a cancelar; primero debe realizar una labor de **Ingeniería de Futuro**.

#### 4.3. «Preparar el Futuro»: El Paso Previo al Golpe
Llamar a `cancelCurrentEvent()` sin preparación previa dejaría al agente en un estado de confusión. Por ello, antes de silenciar al cerebro, el SNA prepara el escenario que el cerebro encontrará al despertar:

*   **Reordenación de la Cola:** El SNA asegura que el evento urgente que ha provocado el conflicto se sitúe en la **cima absoluta** de la `deliveryQueue` (o en su mapa de estados).
*   **Gestión del Desbordamiento:** Si la cola estaba llena de eventos de baja prioridad, el SNA aplica en ese momento la lógica de **Higiene por Inundación** (que detallaremos en el sándwich sensorial). Genera el `BatchID`, archiva el ruido en la base de datos y deja en la cola un único paquete de información coherente.
*   **Sello de Contexto:** Se marca el estado actual para que el agente sepa que lo que va a recibir no es una continuación, sino una ruptura.

#### 4.4. La Ejecución del Golpe
Solo cuando el SNA tiene la certeza de que el «siguiente pensamiento» del agente ya está perfectamente empaquetado y listo en la puerta de la consciencia, ejecuta la acción final:

1.  **Invocación de Cancelación:** El `SensorsService` llama a `reasoning.cancelCurrentEvent()`.
2.  **Ruptura Atómica:** El flag `abort` se activa, el razonador detecta la señal (por streaming o por el watchdog de 20s) y lanza la `InterruptedModelGenerateException`.
3.  **Relevo Inmediato:** El bucle del `eventDispatcher` atrapa la excepción, limpia el rastro de la tarea abortada y llama a `sensors.getEvent()`. 
4.  **Despertar Lúcido:** Como el SNA ya había «preparado el futuro», el despachador recibe instantáneamente el evento urgente (o el grupo de eventos). El agente pasa de procesar una tarea irrelevante a atender la urgencia en una fracción de segundo.

#### 4.5. Filosofía del Golpe: Eficacia sobre Cortesía
Este mecanismo trata el razonamiento del LLM como un recurso caro pero prescindible frente a la realidad del entorno. El «Golpe de Estado» sensorial garantiza que el agente no es solo un procesador de texto, sino un **organismo reactivo** capaz de pivotar su estrategia cognitiva en milisegundos. La prioridad deja de ser una sugerencia para convertirse en un mandato arquitectónico ejecutado por el cuerpo del agente.

### 5. Higiene de la Inundación: El Sándwich Sensorial

El «Sándwich Sensorial» es una estrategia de empaquetado inteligente ejecutada por el `SensorsService` (SNA). En lugar de entregar una lista plana y masiva de estímulos, el sistema construye un único evento de tipo **`SensorEventGroup`** que resume el pasado reciente de forma estructuralmente óptima para un Modelo de Lenguaje. Esta técnica permite que el agente recupere su «visión periférica» sin perder el foco en la urgencia actual.

#### 5.1. La Anatomía del Sándwich
Cuando el SNA detecta que hay demasiada información acumulada para ser procesada individualmente tras una interrupción, activa la lógica del sándwich, que divide el backlog en tres capas diferenciadas:

1.  **La Base: Los 10 eventos más antiguos (Génesis):** Representan el inicio de la ráfaga o la situación. Son vitales porque proporcionan la causa original de la acumulación (ej: *"El servidor empezó a dar avisos de latencia a las 10:00"*). Sin esta capa, el agente vería el desastre final pero no entendería cómo empezó.
2.  **La Capa Superior: Los 10 eventos más nuevos (Precursores):** Son los estímulos que ocurrieron justo antes de la interrupción o la urgencia. Proporcionan el contexto inmediato y fresco (ej: *"Segundos antes del fallo total, se detectaron picos de CPU"*). Es la información más relevante para la toma de decisiones inmediata.
3.  **El Relleno (The Gap): El contador de eventos omitidos:** Todo lo que ocurrió entre el evento 10 y el evento N-10 se colapsa en un metadato cuantitativo. El SNA informa al cerebro: *"Entre el origen y el final, han ocurrido otros 150 eventos de menor relevancia que he movido al archivo"*.

#### 5.2. El Objeto `SensorEventGroup`
Este contenedor no es solo una concatenación de textos; es un objeto JSON especializado que altera el protocolo de percepción. Cuando el `eventDispatcher` (SNC) lo recibe, no ve una ráfaga de mensajes, sino un **Reporte de Situación Estructurado**. El JSON del grupo incluye:
*   **Motivo de la creación:** (Aborto de usuario o Interrupción de prioridad).
*   **Trigger:** El evento exacto que disparó el cambio de atención.
*   **Backlog Sandwich:** Las dos listas de eventos (viejos y nuevos) perfectamente ordenadas.
*   **BatchID:** Una etiqueta única que actúa como un «puntero de memoria» hacia la totalidad del desbordamiento guardado en la base de datos.

#### 5.3. El SNA como Editor de Señal
Para construir este sándwich, el SNA deja de ser un mero conducto y se convierte en un **editor de señal**. Antes de meter un evento en el sándwich, aplica una última capa de «fisiología sensorial»:
*   **Fusión Forzada:** Si entre los 10 más nuevos hay mensajes repetitivos, los compacta según su naturaleza (`AGGREGATABLE`).
*   **Soberanía de Estado:** Si hay varios cambios de una misma variable, solo el valor más reciente entra en la lista.
*   **Higiene de Voluntad:** En caso de aborto manual, el SNA purga los eventos de naturaleza `USER` de estas listas, asegurando que el sándwich solo contenga información del entorno y no comandos de los que el usuario ya se ha arrepentido.

#### 5.4. Eficiencia Cognitiva y Tokens
El beneficio fundamental de esta técnica es la **economía de tokens**. Un agente que recibe 200 eventos tras una interrupción gastaría miles de tokens y varios turnos de "pensamiento" solo para ponerse al día. Con el Sándwich Sensorial, el agente consume exactamente lo mismo (20 eventos seleccionados) independientemente de si el desbordamiento fue de 50 o de 5000 estímulos.

El Sándwich Sensorial garantiza la **Homeostasis Cognitiva**: devuelve al agente a un estado de calma informativa, dándole las piezas clave para razonar sobre el pasado sin permitir que ese pasado «ahogue» el presente urgente. El agente despierta lúcido, sabiendo cómo empezó todo y qué pasó hace un segundo, pero con su ventana de contexto despejada para actuar sobre la urgencia.


### 6. Memoria de Trabajo Secundaria: Archivo por Lotes (`BatchID`)

Esta capa de la arquitectura permite que Noema gestione desbordamientos de cientos o miles de eventos de forma atómica. Cuando el `SensorsService` (SNA) ejecuta una cancelación y detecta que la cola de eventos supera el umbral de lo procesable, activa el protocolo de **Archivado por Lotes**. Este proceso traslada la información desde la memoria volátil de la cola (`deliveryQueue`) hacia una estructura persistente y etiquetada en la base de datos, liberando inmediatamente los recursos de atención del agente.

#### 6.1. El Concepto de Memoria de Trabajo Secundaria
A diferencia de los **Turnos** (que forman el relato vital y permanente del agente) o los **Checkpoints** (que consolidan el conocimiento a largo plazo), el **Archivo Sensorial** es una memoria de trabajo de segundo nivel. Su propósito no es formar parte de la historia conversacional por defecto, sino servir como un **registro técnico bruto** de lo que el agente «oyó» pero decidió no «escuchar» conscientemente en favor de una prioridad mayor. Es, en esencia, un búfer de percepción extendido en el disco.

#### 6.2. El `BatchID`: El Vínculo entre Crisis e Investigación
La pieza clave para la recuperación de esta información es el **`BatchID`**. Se trata de una etiqueta única generada por el SNA en el momento exacto de la interrupción (por ejemplo, siguiendo el formato `BATCH-TIMESTAMP-UUID`).

*   **Unicidad y Cohesión:** Todos los eventos que se encontraban en la cola en el momento del «Golpe de Estado» o del aborto manual se marcan con el mismo `BatchID`. Esto permite tratar a una ráfaga de 500 logs como una única unidad lógica.
*   **El Token de Referencia:** Este ID es lo único que el Sistema Nervioso Central (LLM) recibe dentro del JSON del `SensorEventGroup`. Para el cerebro del agente, el `BatchID` es la «llave» de una caja negra que contiene los detalles de la interrupción.

#### 6.3. La Tabla `SENSORY_ARCHIVE`: Estructura y Semántica
Para evitar saturar la tabla principal de `TURNOS` con ruido de sensores que quizás nunca se consulten, Noema utiliza una tabla dedicada llamada `SENSORY_ARCHIVE`. Su esquema está optimizado para capturar el estímulo en su estado más puro:

*   **`batch_id` (Indexado):** Permite recuperaciones instantáneas de lotes completos.
*   **`timestamp`:** Preserva la cronología exacta de cada evento dentro de la ráfaga.
*   **`channel`:** Identifica el sensor de origen (ej: `LOGS`, `TELEGRAM`, `SYSTEM`).
*   **`contents`:** Almacena el JSON o texto íntegro del evento.
*   **`priority`:** Registra la urgencia que tenía el evento original.
*   **`metadata`:** Campos adicionales capturados por el SNA durante la ingesta.

#### 6.4. El Protocolo de Volcado (Dumping Process)
El archivado se produce de forma síncrona dentro del hilo del `SensorsService` para garantizar que no se pierda ni un solo milisegundo de información durante la interrupción:

1.  **Bloqueo de Cola:** Se detiene momentáneamente la entrada de nuevos estímulos.
2.  **Generación del Sándwich:** Se extraen los 10 primeros y 10 últimos eventos para el `SensorEventGroup`.
3.  **Vaciado Atómico (`drainTo`):** El resto de la cola se vacía y se traslada a un lote de inserción en la base de datos.
4.  **Sello del Lote:** Se graban los registros en `SENSORY_ARCHIVE` bajo el `BatchID` correspondiente.
5.  **Liberación:** Se entrega el evento de grupo al cerebro y se reanuda la escucha sensorial.

#### 6.5. Higiene y Ciclo de Vida: El TTL (Time-To-Live)
Dado que el archivo sensorial puede crecer rápidamente en entornos ruidosos, su persistencia no es eterna. Sigue una política de **Higiene por Caducidad**:
*   **Memoria Efímera:** Los registros de `SENSORY_ARCHIVE` se consideran relevantes solo durante la «misión» o sesión actual. 
*   **Auto-limpieza:** El sistema ejecuta una tarea de limpieza (ej: cada 24 horas o al arrancar) que elimina cualquier `BatchID` cuya antigüedad supere un umbral (ej: 7 días). Esto asegura que la base de datos de conocimiento no se degrade con metadatos sensoriales obsoletos.

Esta arquitectura de **Archivo por Lotes** transforma el problema del desbordamiento en una ventaja competitiva: Noema no ignora el caos, simplemente lo **pospone y etiqueta**. El agente mantiene su claridad mental ante la urgencia, pero conserva la capacidad de ser un «investigador forense» si el razonamiento posterior lo requiere.


### 7. Herramientas de Metacognición: `lookup_sensory_archive`

Esta herramienta representa el puente final entre la memoria de trabajo primaria (la ventana de contexto del LLM) y la memoria sensorial secundaria (el archivo en base de datos). A través de `lookup_sensory_archive`, el agente ejerce su **Metacognición Sensorial**: el proceso de reflexionar sobre su propia percepción para decidir qué piezas de información del pasado reciente son necesarias para resolver un problema del presente.

#### 7.1. El Puntero de Atención: El Rol del `BatchID`
Como vimos en las secciones anteriores, tras una interrupción o desbordamiento, el agente recibe un `SensorEventGroup` que contiene un `BatchID` único. Este ID no es solo un dato administrativo; para el LLM es un **puntero de atención**. Al recibirlo, el cerebro del agente sabe que el «relleno» del sándwich sensorial (el *gap*) no ha desaparecido, sino que está «congelado» en el archivo esperando órdenes. La herramienta `lookup_sensory_archive` es la que permite descongelar esa información de forma granular.

#### 7.2. Especificación Técnica de la Herramienta
La herramienta se expone al LLM dentro de su repertorio estándar de acciones (ReAct), permitiendo una consulta quirúrgica del archivo `SENSORY_ARCHIVE`. Sus parámetros están diseñados para evitar, de nuevo, la inundación:

*   **`batch_id` (OBLIGATORIO):** La etiqueta recibida en el evento de grupo. Vincula la búsqueda a la ráfaga específica de interés.
*   **`offset` y `limit`:** Fundamentales para la **Paginación Cognitiva**. El agente puede pedir, por ejemplo, los eventos del 20 al 40 del archivo para no saturar su contexto de una sola vez.
*   **`channel_filter`:** Permite al agente filtrar por origen. Si el agente sospecha que el fallo de motor (la urgencia) está relacionado con el sensor de temperatura, puede pedir ver solo los eventos del canal `TEMPERATURE` dentro de ese `batch_id`.
*   **`priority_filter`:** Útil para ignorar eventos triviales (logs `DEBUG`) dentro del archivo y centrarse en avisos `NORMAL` o `ALTA` que fueron desplazados por una urgencia `CRÍTICA`.

#### 7.3. Casos de Uso Estratégico (Razonamiento Forense)
El agente no invoca esta herramienta de forma automática; lo hace basándose en su **razonamiento estratégico**. Existen tres escenarios claros donde Noema aplicará su metacognición:

1.  **Ambigüedad en el Sándwich:** Si el «origen» (los 10 más viejos) y el «precursor» (los 10 más nuevos) no muestran una conexión clara, el agente decidirá investigar el «relleno» para encontrar el eslabón perdido en la cadena de causalidad.
2.  **Diagnóstico de Causa Raíz:** Ante una urgencia (ej: *"Disco Lleno"*), el agente puede usar el archivo para ver qué proceso o sensor estuvo enviando datos masivos en los minutos previos, realizando un diagnóstico forense que el sándwich, por espacio, no podía mostrar.
3.  **Auditoría del Usuario:** Si el usuario pregunta *"¿Qué ha pasado exactamente mientras yo estaba fuera?"*, el agente sabe que el resumen del grupo no es suficiente. Usará la herramienta para leer el archivo completo y generar un relato detallado y cronológico para el humano.

#### 7.4. El Formato de Respuesta: Conocimiento Estructurado
Cuando el LLM ejecuta la herramienta, el orquestador no le devuelve texto plano. Le entrega un JSON estructurado que resume el fragmento del archivo solicitado:

```json
{
  "status": "success",
  "batch_info": { "total_in_batch": 150, "showing": "20-40" },
  "events": [
    { "time": "10:05:01", "channel": "LOGS", "content": "Latencia en DB: 500ms" },
    /* ... 20 eventos ... */
  ],
  "next_offset": 41,
  "hint": "Quedan 110 eventos más en este lote. Usa offset=41 para seguir leyendo."
}
```

#### 7.5. Soberanía e Investigación Selectiva
Esta herramienta garantiza que la **Soberanía Cognitiva** sea real. El agente ya no es una víctima del volumen de datos; es un investigador con un archivo a su disposición. Al elegir qué leer y cuánto leer del pasado sensorial, Noema optimiza su recurso más valioso (los tokens de contexto) mientras mantiene una **trazabilidad forense total**. 

En resumen, `lookup_sensory_archive` transforma el desbordamiento de una "maldición" en una **fuente de datos bajo demanda**, cerrando el círculo de la arquitectura de interrupción inteligente: el sistema es capaz de callar para actuar, pero también es capaz de recordar para entender.

## Anexo

* Habria que valorar la posibilidad de usar un tipo de agrupacion siempre que hayan cosas en la cola, en lugar de sacar solo el ultimo. Si hay varios, sacar por ejemplo los ultimos 10.

* En relacion al punto "4. Lógica de Prioridad y «Golpe de Estado» Sensorial", tendiramos que tener en cuenta que podemos necesitar comprobr si se han ejecutado herramientas o no. Y probablemente atrapar el flag de cancelacion en el bucle interno que recorre las herramientas.


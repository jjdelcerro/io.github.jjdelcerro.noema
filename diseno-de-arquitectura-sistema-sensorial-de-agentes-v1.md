
# Diseño de Arquitectura: Sistema Sensorial de Agentes (SNA/SNC)

## 1. Visión General de la Arquitectura: El Sistema Nervioso Dual del Agente

La arquitectura sensorial de este agente se fundamenta en una analogía biológica profunda: la distinción entre el **Sistema Nervioso Autónomo (SNA)** y el **Sistema Nervioso Central (SNC)**. Esta división no es meramente organizativa, sino una necesidad crítica para garantizar la supervivencia operativa del sistema en entornos con alta densidad de información. Tradicionalmente, los agentes de IA han sido diseñados como entidades puramente reactivas que procesan estímulos de forma lineal; este diseño rompe ese paradigma al proponer un agente como un organismo ciberntético compuesto por un **cuerpo (orquestador)** y un **cerebro (LLM)**.

### 1.1. El Sistema Nervioso Autónomo (SNA): La Capa de Fisiología Digital
El orquestador, actuando como el SNA, reside en la capa de ejecución del servicio (`SensorsService`). Su función primordial es la **homeostasis informativa**. El SNA es el encargado de la "fisiología" del agente: gestiona todas las funciones involuntarias y automáticas relacionadas con la percepción.

*   **Ingesta Asíncrona**: Mientras el cerebro del agente (el LLM) está ocupado razonando, ejecutando una herramienta compleja o simplemente en estado de reposo, el SNA permanece siempre alerta. Recibe ráfagas de datos de múltiples canales (Telegram, Email, Logs, Sensores IoT) sin interrumpir el flujo de pensamiento actual.
*   **Procesamiento de Señal (Digestión Sensorial)**: El SNA no actúa como una simple tubería de datos. Su responsabilidad es **digerir** los estímulos según su naturaleza predefinida. Aplica algoritmos de bajo nivel para transformar el "ruido" (datos en bruto) en "señal" (información con significado). Mediante la agregación de contadores, la fusión de textos narrativos y la sustitución de estados, el SNA reduce drásticamente la carga cognitiva que finalmente llegará al cerebro.
*   **Protección contra Saturación (DoS Cognitivo)**: En eventos de inundación sensorial (por ejemplo, un servidor lanzando miles de errores por segundo), el SNA actúa como un fusible. Al procesar estos datos de forma autónoma, evita que la ventana de contexto del LLM se desborde con información redundante, protegiendo la capacidad de razonamiento del sistema.

### 1.2. El Sistema Nervioso Central (SNC): Consciencia y Estrategia
El LLM representa el SNC, el núcleo del razonamiento consciente. En esta arquitectura, el SNC es liberado de las tareas mundanas de limpieza de datos.

*   **Atención Focalizada**: El SNC solo "levanta la cabeza" para procesar eventos cuando el SNA le entrega una señal ya consolidada. Esto optimiza el recurso más valioso y costoso del agente: los **tokens de contexto**.
*   **Control Voluntario (Los Párpados)**: Aunque el SNA es autónomo, el SNC mantiene la soberanía sobre el sistema. A través de la voluntad (instrucciones de filtrado), el SNC puede ordenar al SNA que ignore ciertos sentidos ("cierra los ojos a Telegram"), permitiendo una concentración profunda en la tarea actual sin que el cuerpo deje de registrar estadísticas de lo que ocurre en la sombra.

### 1.3. La Interfaz Tálamo-Cortical: El Mecanismo de Relevo
La comunicación entre ambos sistemas se produce mediante un mecanismo de **Atención Diferida**. El SNA mantiene una "Cubeta de Contexto" donde se van depositando las señales digeridas. 

*   **Inyección por Proactividad**: Cuando el agente está ocioso, el SNA tiene la autoridad de despertar al SNC mediante una inyección de percepción (simulando una llamada a la herramienta `pool_event`).
*   **Coherencia Temporal**: El sistema garantiza que la percepción del mundo exterior nunca interrumpa un razonamiento en curso. Si el SNC está "ocupado" (*busy*), el SNA acumula y ordena la realidad cronológicamente, esperando a que el turno de pensamiento actual finalice para entregar el "buzón sensorial" completo.

### 1.4. Objetivos Estratégicos de este Diseño
1.  **Escalabilidad Sensorial**: Permitir que el agente gestione cientos de fuentes de datos simultáneas sin degradar su lógica de respuesta.
2.  **Economía de Contexto**: Maximizar la información útil por cada token consumido, eliminando la redundancia antes de la inferencia.
3.  **Resiliencia**: Garantizar que el agente mantenga el control y la calma operativa incluso en situaciones de caos informativo externo.

En resumen, esta visión general establece que el Agente no es solo el modelo de lenguaje, sino la **interacción simbiótica** entre un cuerpo capaz de procesar señales físicas y un cerebro capaz de interpretar significados estratégicos.


## 2. Definiciones Base: Identidad y Naturaleza de los Sentidos

Para que el agente pueda interactuar con el mundo de forma coherente, no basta con recibir datos; el sistema debe poseer una estructura clara de **propiocepción agéntica**. Esto significa que el agente debe conocer qué "órganos sensoriales" (sensores) tiene, qué tipo de información proveen y cuáles son las reglas de comportamiento asociadas a su percepción. Esta sección define los pilares de esa identidad.

### 2.1. SensorInformation: La Cédula de Identidad del Sentido
Cada sensor que se conecta al sistema debe registrarse mediante una instancia de `SensorInformation`. Este objeto no contiene los datos en sí, sino los metadatos que definen al sensor ante el resto de la arquitectura (SNA y SNC). Los campos fundamentales son:

*   **Identificador del Canal (`channel`)**: Un nombre técnico único (ej: `telegram_bot_primary`, `server_log_monitor`). Es el ID que el orquestador utiliza para indexar mapas de estadísticas y de procesamiento.
*   **Etiqueta Legible (`label`)**: Un nombre humano para el sensor (ej: "Canal de Telegram de Soporte"). Se utiliza en las herramientas de administración y para que el usuario sepa qué está configurando.
*   **Descripción Semántica (`description`)**: Una explicación detallada de qué representa este sensor y qué tipo de información suele transmitir. Esta descripción es vital cuando se expone al Sistema Nervioso Central (LLM), ya que le permite entender el contexto de la observación (ej: "Este sensor detecta mensajes entrantes de clientes finales; su tono suele ser de urgencia").
*   **Naturaleza del Sensor (`nature`)**: Una referencia al enumerado `SensorNature`, que dictamina el algoritmo de procesamiento que el SNA aplicará a sus eventos de forma automática.

### 2.2. SensorNature: La Semántica del Estímulo
La naturaleza de un sensor define cómo el Sistema Nervioso Autónomo debe "digerir" la señal antes de presentarla. No todos los eventos del mundo real tienen la misma estructura lógica ni el mismo valor acumulativo. Se definen cuatro naturalezas fundamentales:

#### A. Naturaleza Discreta (`DISCRETE`)
Representa eventos que son **atómicos, irreducibles y críticos**. Cada instancia de un evento discreto es significativa por sí misma y su pérdida supondría una laguna en el conocimiento de la realidad del agente.
*   **Comportamiento**: El SNA no realiza ninguna operación de transformación. Cada evento se empaqueta y se envía a la cola de entrega de forma independiente.
*   **Ejemplos**: Alertas de intrusión, confirmaciones de pago, finalización de procesos críticos de sistema.

#### B. Naturaleza Fusionable (`MERGEABLE`)
Representa estímulos que forman parte de un **flujo narrativo o conversacional**. El valor informativo reside en la secuencia de mensajes más que en cada mensaje aislado.
*   **Comportamiento**: El SNA aplica un algoritmo de **concatenación**. Cuando llegan múltiples eventos seguidos de este tipo, se van uniendo en un único bloque de texto, preservando las marcas de tiempo individuales (`timestamps`) de cada entrada. Esto reduce el número de interrupciones al SNC, entregando una "intervención completa" en lugar de ráfagas inconexas.
*   **Ejemplos**: Chat de Telegram, hilos de Slack, comentarios en una tarea.

#### C. Naturaleza Agregable (`AGGREGATABLE`)
Representa eventos donde el detalle individual es redundante, pero el **volumen y la frecuencia** son reveladores. Es el tratamiento estadístico de la percepción.
*   **Comportamiento**: El SNA aplica un algoritmo de **cuantificación**. En lugar de almacenar el contenido de cada evento, el sistema mantiene un acumulador (contador). Al final del periodo de consolidación, se entrega un único evento informativo que resume la actividad: *"Se han detectado X eventos de este tipo"*.
*   **Ejemplos**: Logs de error de baja prioridad, pings de disponibilidad, latidos de corazón (heartbeats) de servicios.

#### D. Naturaleza de Estado (`STATE`)
Representa condiciones del entorno que son **volátiles y solo válidas en su versión más reciente**. El evento nuevo invalida completamente la información del evento anterior que aún no ha sido procesado.
*   **Comportamiento**: El SNA aplica un algoritmo de **sustitución**. El sistema mantiene un mapa de estados actuales; cuando llega un nuevo estímulo, este sobreescribe al anterior en el buffer de trabajo. El cerebro del agente siempre recibirá la "foto" más actual de la situación.
*   **Ejemplos**: Temperatura actual, número de usuarios conectados, "tienes correos sin leer".

### 2.3. El Mapa Sensorial: Propiocepción Activa
Mediante la combinación de `SensorInformation` y `SensorNature`, el `SensorsService` construye un mapa vivo de la periferia del agente. Esta estructura permite que el sistema sea extensible: añadir un nuevo sentido (por ejemplo, un monitor de precios de criptomonedas) solo requiere definir su identidad y declarar si se comporta como un estado, como un agregado de precios o como un flujo de noticias fusionables. 

Esta capa de definiciones garantiza que el agente no sea una "caja negra" que recibe texto, sino un sistema consciente de su propia arquitectura sensorial, capaz de explicar de dónde viene cada dato y por qué ha sido procesado de cierta manera.


## 3. Metacognición Sensorial: Estadísticas y Salud del Sistema

La **metacognición sensorial** es la capacidad del agente para observar, cuantificar y evaluar su propia actividad perceptiva. En esta arquitectura, el Sistema Nervioso Autónomo (SNA) no solo procesa los estímulos del entorno, sino que mantiene un registro exhaustivo del rendimiento y comportamiento de cada uno de sus "sentidos". Esta capa de introspección es fundamental para evitar la desorientación informativa y para permitir que el Sistema Nervioso Central (SNC) tome decisiones informadas sobre su propia atención.

### 3.1. SensorStatistics: El Cuadro de Mandos Fisiológico
Cada sensor registrado en el sistema lleva asociada una instancia de `SensorStatistics`. Este objeto actúa como un acumulador de métricas en tiempo real que describe la "vida" del sensor desde su activación. A diferencia de los eventos, que son datos que fluyen hacia el cerebro, las estadísticas son datos que describen el flujo mismo.

Los indicadores clave capturados por `SensorStatistics` incluyen:

*   **Contadores de Volumen por Estado**:
    *   **Eventos en Activo (`totalEventsActive`)**: Cantidad de estímulos recibidos y procesados mientras el sensor estaba habilitado.
    *   **Eventos en Inactivo/Silenciado (`totalEventsMuted`)**: Esta es una métrica crítica. Contabiliza cuántas veces el sensor disparó una señal mientras el agente tenía los "párpados cerrados" (sensor desactivado por voluntad del SNC). Esto permite al agente cuantificar qué se ha perdido durante su periodo de concentración.
*   **Análisis de Frecuencia y Ritmo**:
    *   **Ventanas Deslizantes**: El sistema monitoriza la actividad en la última hora, el último día y el último mes.
    *   **Frecuencia Media (`averageFrequency`)**: Calculada como eventos por unidad de tiempo. Permite distinguir entre un goteo constante de información y ráfagas súbitas de actividad.
*   **Métricas de Disponibilidad (Uptime/Downtime)**:
    *   Registro del tiempo total que el sensor ha estado escuchando frente al tiempo que ha permanecido silenciado, permitiendo calcular un ratio de disponibilidad sensorial.
*   **Trazabilidad Temporal**:
    *   **Marca de Última Actividad (`lastEventTimestamp`)**: El instante exacto del último estímulo recibido.
    *   **Marca de Última Transmisión (`lastDeliveryTimestamp`)**: Cuándo fue la última vez que el SNA entregó una señal consolidada de este sensor al SNC.

### 3.2. El SNA como Vigilante de la Salud Perceptiva
El `SensorsService` utiliza estas estadísticas para realizar diagnósticos automáticos sobre la salud del entorno. No es un recolector pasivo; es un sistema de alerta temprana.

*   **Detección de Anomalías por Saturación**: Si el SNA detecta que la frecuencia media de un sensor (ej: logs de sistema) ha pasado de 1 evento/minuto a 500 eventos/segundo, puede clasificar el sensor en estado de "Estrés". En este caso, el SNA puede activar de forma autónoma niveles de **compactación extrema**, protegiendo la cola de entrega.
*   **Detección de Silencios (Dead Sensor)**: Si un sensor vital (ej: latido de un servidor) supera su tiempo máximo esperado de silencio basándose en su histórico, el SNA puede generar un evento automático de sistema alertando sobre la posible caída de esa fuente de datos.

### 3.3. La Interfaz de Consciencia: La Herramienta `sensor_status`
Toda esta riqueza estadística se expone al Sistema Nervioso Central (LLM) a través de la herramienta operativa `sensor_status`. Esta herramienta no devuelve un simple estado booleano (encendido/apagado), sino un informe de diagnóstico que permite al agente razonar sobre su propia percepción.

**Utilidad para la autonomía del agente:**
Gracias a la metacognición, el agente puede realizar razonamientos complejos como:
> *"He tenido el sensor de Telegram silenciado durante las últimas 2 horas para concentrarme en este código, pero las estadísticas indican que han llegado 45 mensajes durante este tiempo. Dado el volumen inusual, voy a reactivar el sensor brevemente para verificar si hay alguna urgencia."*

### 3.4. Autoregulación Cognitiva y Gestión de Recursos
La metacognición sensorial garantiza la **Autoregulación cognitiva**. Al conocer la carga de trabajo que cada sentido impone al sistema, el SNA puede negociar con el SNC el filtrado de datos. Si el presupuesto de tokens es limitado o el coste computacional es alto, las estadísticas proporcionan el criterio objetivo para decidir qué sensores "ajustar" o "silenciar" para mantener el equilibrio interno del organismo digital.

En definitiva, este punto del diseño transforma los sentidos del agente de simples cables de entrada en componentes inteligentes y autorregulados, dotando al sistema de una consciencia de su propia capacidad y limitaciones perceptivas.


## 4. Procesamiento Autónomo: La Capa de SensorData

La interfaz **`SensorData`** representa la unidad mínima de procesamiento dentro del Sistema Nervioso Autónomo (SNA). Si el `SensorsService` es el cerebro del sistema autónomo, las implementaciones de `SensorData` son los núcleos especializados en el procesamiento de señales de cada sentido. Esta capa es la responsable de transformar los estímulos eléctricos (datos en bruto) en información biológicamente útil (eventos consolidados) para el Sistema Nervioso Central.

### 4.1. Filosofía de Encapsulación y Responsabilidad
A diferencia de un diseño tradicional donde un servicio centralizado maneja condicionales complejos para cada tipo de dato, este diseño utiliza el **polimorfismo** para delegar la lógica de "digestión" en el propio sensor. Cada instancia de `SensorData` actúa como un micro-estado soberano que encapsula tres pilares:

1.  **Su Identidad**: Posee una referencia a su `SensorInformation`.
2.  **Su Historial**: Gestiona su propia instancia de `SensorStatistics`.
3.  **Su Buffer de Trabajo**: Mantiene el evento que se está "cocinando" en ese momento (el `SensorEvent` actual).

### 4.2. Comportamientos Polimórficos por Naturaleza
El sistema no trata igual un mensaje de texto que un log de servidor. Cada implementación de la interfaz `SensorData` redefine los métodos de procesamiento según la naturaleza del estímulo:

#### A. DiscreteSensorData (El Canal Directo)
Es el procesador más simple. No mantiene buffers de larga duración. Su método de ingesta simplemente crea un `SensorEventDiscrete` y, dado que no hay acumulación posible, lo marca inmediatamente para su entrega. Es la respuesta "refleja" pura.

#### B. MergeableSensorData (El Tejedor de Historias)
Este procesador es responsable de mantener la continuidad narrativa. Internamente, gestiona un `SensorEventMergeable` que contiene un acumulador de texto (normalmente un `StringBuilder`).
*   **Lógica de Ingesta**: Cuando recibe un nuevo estímulo, no crea un evento nuevo. En su lugar, concatena el nuevo texto al buffer existente, añadiendo una marca de tiempo y un separador.
*   **Conservación de Contexto**: Mantiene la marca de tiempo del *primer* mensaje de la ráfaga como el "tiempo de inicio" del bloque, asegurando que la cronología en la cola de entrega sea justa.

#### C. AggregateSensorData (El Contador de Intensidad)
Diseñado para la máxima eficiencia técnica. Su buffer de trabajo es un `SensorEventAggregate` mutable que contiene un contador simple.
*   **Lógica de Ingesta**: Ante un nuevo estímulo, simplemente incrementa el valor numérico (ej: `count++`). No almacena el texto del evento, ya que su naturaleza dicta que el detalle es ruido y la cantidad es la señal. Esto permite procesar miles de señales con un consumo de memoria insignificante.

#### D. StateSensorData (El Monitor de Presente)
A diferencia de los demás, este procesador gestiona una lógica de **"Último Valor Gana"**.
*   **Lógica de Ingesta**: Cada vez que entra un estímulo, el procesador descarta el evento anterior y lo sustituye por el nuevo. No hay acumulación ni suma. Su objetivo es que, cuando el cerebro pregunte, la respuesta sea siempre la fotografía más reciente de la realidad.

### 4.3. El Mecanismo de "Flush" y el `currentSensor`
El `SensorsService` coordina estas instancias de `SensorData` mediante un puntero crítico llamado **`currentSensor`**. Este puntero identifica cuál es el sensor que actualmente tiene una "cubeta de contexto" abierta y en proceso de llenado.

*   **Sincronización por Interrupción**: Cuando llega un nuevo estímulo a través de `putEvent`, el SNA pregunta: *¿Este evento pertenece al `currentSensor`?* 
    *   **Si es el mismo**: Se le entrega el dato al procesador actual para que siga acumulando (juntando texto o sumando contadores).
    *   **Si es un sensor distinto o un evento DISCRETE**: El SNA ordena un **"Flush"** al `currentSensor`. El procesador activo en ese momento "sella" su evento (lo traslada a la cola de entrega definitiva) y limpia su buffer interno. Acto seguido, el nuevo sensor se convierte en el `currentSensor` y abre su propia cubeta.

### 4.4. Métodos Clave de la Interfaz
Para garantizar esta orquestación, cada `SensorData` implementa:
*   **`process(text, priority, status)`**: El método de entrada que decide cómo actualizar el buffer interno.
*   **`isMyEvent(SensorEvent)`**: Permite al servicio identificar rápidamente si un estímulo entrante debe ser digerido por esta instancia.
*   **`flushEvent()`**: El comando de clausura. El procesador emite el evento acumulado y reinicia su estado interno para la próxima ráfaga.
*   **`getStatistics()`**: Expone las métricas acumuladas para la monitorización del sistema.

### 4.5. Ventajas del Diseño
Esta capa de `SensorData` transforma el caos del mundo exterior en un flujo ordenado de objetos de conocimiento. Al encapsular la lógica en objetos especializados, el sistema gana en **mantenibilidad** (podemos añadir nuevas naturalezas de procesamiento sin tocar el servicio central) y en **robustez** (los errores en el procesamiento de un sensor no afectan la estabilidad de los demás). El SNA se convierte así en un sofisticado sistema de filtrado y preparación de datos que garantiza que el SNC siempre reciba información "lista para pensar".


## 4. Procesamiento Autónomo: La Capa de SensorData

La interfaz **`SensorData`** representa la unidad mínima de procesamiento dentro del Sistema Nervioso Autónomo (SNA). Si el `SensorsService` es el cerebro del sistema autónomo, las implementaciones de `SensorData` son los núcleos especializados en el procesamiento de señales de cada sentido. Esta capa es la responsable de transformar los estímulos eléctricos (datos en bruto) en información biológicamente útil (eventos consolidados) para el Sistema Nervioso Central.

### 4.1. Filosofía de Encapsulación y Responsabilidad
A diferencia de un diseño tradicional donde un servicio centralizado maneja condicionales complejos para cada tipo de dato, este diseño utiliza el **polimorfismo** para delegar la lógica de "digestión" en el propio sensor. Cada instancia de `SensorData` actúa como un micro-estado soberano que encapsula tres pilares:

1.  **Su Identidad**: Posee una referencia a su `SensorInformation`.
2.  **Su Historial**: Gestiona su propia instancia de `SensorStatistics`.
3.  **Su Buffer de Trabajo**: Mantiene el evento que se está "cocinando" en ese momento (el `SensorEvent` actual).

### 4.2. Comportamientos Polimórficos por Naturaleza
El sistema no trata igual un mensaje de texto que un log de servidor. Cada implementación de la interfaz `SensorData` redefine los métodos de procesamiento según la naturaleza del estímulo:

#### A. DiscreteSensorData (El Canal Directo)
Es el procesador más simple. No mantiene buffers de larga duración. Su método de ingesta simplemente crea un `SensorEventDiscrete` y, dado que no hay acumulación posible, lo marca inmediatamente para su entrega. Es la respuesta "refleja" pura.

#### B. MergeableSensorData (El Tejedor de Historias)
Este procesador es responsable de mantener la continuidad narrativa. Internamente, gestiona un `SensorEventMergeable` que contiene un acumulador de texto (normalmente un `StringBuilder`).
*   **Lógica de Ingesta**: Cuando recibe un nuevo estímulo, no crea un evento nuevo. En su lugar, concatena el nuevo texto al buffer existente, añadiendo una marca de tiempo y un separador.
*   **Conservación de Contexto**: Mantiene la marca de tiempo del *primer* mensaje de la ráfaga como el "tiempo de inicio" del bloque, asegurando que la cronología en la cola de entrega sea justa.

#### C. AggregateSensorData (El Contador de Intensidad)
Diseñado para la máxima eficiencia técnica. Su buffer de trabajo es un `SensorEventAggregate` mutable que contiene un contador simple.
*   **Lógica de Ingesta**: Ante un nuevo estímulo, simplemente incrementa el valor numérico (ej: `count++`). No almacena el texto del evento, ya que su naturaleza dicta que el detalle es ruido y la cantidad es la señal. Esto permite procesar miles de señales con un consumo de memoria insignificante.

#### D. StateSensorData (El Monitor de Presente)
A diferencia de los demás, este procesador gestiona una lógica de **"Último Valor Gana"**.
*   **Lógica de Ingesta**: Cada vez que entra un estímulo, el procesador descarta el evento anterior y lo sustituye por el nuevo. No hay acumulación ni suma. Su objetivo es que, cuando el cerebro pregunte, la respuesta sea siempre la fotografía más reciente de la realidad.

### 4.3. El Mecanismo de "Flush" y el `currentSensor`
El `SensorsService` coordina estas instancias de `SensorData` mediante un puntero crítico llamado **`currentSensor`**. Este puntero identifica cuál es el sensor que actualmente tiene una "cubeta de contexto" abierta y en proceso de llenado.

*   **Sincronización por Interrupción**: Cuando llega un nuevo estímulo a través de `putEvent`, el SNA pregunta: *¿Este evento pertenece al `currentSensor`?* 
    *   **Si es el mismo**: Se le entrega el dato al procesador actual para que siga acumulando (juntando texto o sumando contadores).
    *   **Si es un sensor distinto o un evento DISCRETE**: El SNA ordena un **"Flush"** al `currentSensor`. El procesador activo en ese momento "sella" su evento (lo traslada a la cola de entrega definitiva) y limpia su buffer interno. Acto seguido, el nuevo sensor se convierte en el `currentSensor` y abre su propia cubeta.

### 4.4. Métodos Clave de la Interfaz
Para garantizar esta orquestación, cada `SensorData` implementa:
*   **`process(text, priority, status)`**: El método de entrada que decide cómo actualizar el buffer interno.
*   **`isMyEvent(channel)`**: Permite al servicio identificar rápidamente si un estímulo entrante debe ser digerido por esta instancia.
*   **`flushEvent()`**: El comando de clausura. El procesador emite el evento acumulado y reinicia su estado interno para la próxima ráfaga.
*   **`getStatistics()`**: Expone las métricas acumuladas para la monitorización del sistema.

### 4.5. Ventajas del Diseño
Esta capa de `SensorData` transforma el caos del mundo exterior en un flujo ordenado de objetos de conocimiento. Al encapsular la lógica en objetos especializados, el sistema gana en **mantenibilidad** (podemos añadir nuevas naturalezas de procesamiento sin tocar el servicio central) y en **robustez** (los errores en el procesamiento de un sensor no afectan la estabilidad de los demás). El SNA se convierte así en un sofisticado sistema de filtrado y preparación de datos que garantiza que el SNC siempre reciba información "lista para pensar".

## 5. El Modelo de Eventos: De la Construcción a la Entrega

En esta arquitectura, el **`SensorEvent`** es la "divisa informativa" del sistema. Representa el paquete de datos final que el Sistema Nervioso Autónomo (SNA) entrega al Sistema Nervioso Central (SNC). El diseño de este modelo es crítico, ya que debe equilibrar dos necesidades contrapuestas: la **eficiencia extrema** durante la fase de captura de datos (donde pueden producirse ráfagas de miles de eventos) y la **integridad cronológica** durante la fase de razonamiento.

### 5.1. Jerarquía de SensorEvent: Especialización Semántica
El sistema no utiliza una clase genérica de evento, sino una jerarquía tipada que refleja la naturaleza del estímulo original. Todas las implementaciones heredan de una clase base **`AbstractSensorEvent`**, que garantiza que todo paquete de información posea metadatos de identidad (sensor de origen, prioridad, canal) y traza temporal.

#### A. SensorEventDiscrete (La Instantánea Única)
Es un contenedor simple para un estímulo que no requiere procesamiento. Contiene el texto íntegro, el estado y la marca de tiempo exacta del momento en que ocurrió. Es inmutable desde su nacimiento.

#### B. SensorEventMergeable (El Paquete Narrativo)
Este modelo está diseñado para crecer. Internamente, utiliza estructuras eficientes (como `StringBuilder`) para acumular texto. Su característica principal es que gestiona un rango temporal: almacena tanto la marca de tiempo del **primer mensaje** recibido como la del **último**, permitiendo que el cerebro entienda cuánto tiempo duró la ráfaga de mensajes fusionados.

#### C. SensorEventAggregate (El Acumulador Ligero)
Es la implementación más optimizada a nivel de recursos. En lugar de almacenar cadenas de texto, gestiona un **contador numérico** (`long` o `int`). Su función es puramente cuantitativa: permite representar miles de estímulos idénticos en un objeto que ocupa apenas unos bytes de memoria, evitando la fragmentación del montón (*heap*) de Java y la sobrecarga del recolector de basura.

#### D. SensorEventState (La Foto de Actualidad)
Representa una condición de estado. Su estructura es similar al evento discreto, pero su lógica de gestión en la cola es de sustitución. Contiene la información más reciente de un canal, invalidando cualquier dato previo del mismo sensor que no haya sido procesado aún.

### 5.2. El Paradigma de Mutabilidad Controlada
Uno de los pilares técnicos de este diseño es la gestión del estado interno de los eventos. Se aplica un ciclo de vida dividido en dos fases claramente diferenciadas:

#### Fase 1: La Fase de Construcción (Mutable)
Mientras el evento reside dentro de su procesador `SensorData`, se considera que está en **"Estado de Crecimiento"**. En esta fase, el evento es **mutable**.
*   **Eficiencia Técnica**: El procesador de agregación incrementa un contador (`event.increment()`) y el de fusión concatena texto (`event.append()`). Esta mutabilidad evita la creación constante de nuevos objetos en memoria (evitando el *churn* de objetos), lo cual es vital cuando se reciben estímulos de alta frecuencia (como logs o telemetría).
*   **Acumulación de Señal**: El evento actúa como una esponja que va absorbiendo la "energía" de los estímulos entrantes mientras el sensor sea el `currentSensor` y no ocurra una interrupción.

#### Fase 2: La Fase de Entrega (Captura o Snapshot)
En el momento en que se produce un **"Flush"** (ya sea porque el agente pide los datos, el sensor cambia o llega un evento discreto), el evento en construcción es "sellado".
*   **Transmisión a la Cola**: El `SensorData` entrega el objeto actual a la **Cola de Entrega**.
*   **Reinicio de Ciclo**: Inmediatamente después de entregar el evento, el `SensorData` genera una **nueva instancia vacía** para seguir acumulando.
*   **Inmutabilidad Lógica**: Una vez que el evento entra en la `DeliveryQueue`, aunque técnicamente sus campos puedan seguir siendo accesibles, el sistema lo trata como una **"Instantánea Congelada"**. Esto garantiza que el Sistema Nervioso Central (el LLM) lea una versión coherente y terminada de la realidad, sin que los datos cambien bajo sus pies mientras está razonando.

### 5.3. Sincronización Temporal: El Pegamento Cronológico
Cada `SensorEvent` es responsable de preservar la línea de tiempo del agente. Para los eventos complejos (fusionados o agregados), el modelo registra:
1.  **`startTimestamp`**: El momento en que entró el primer estímulo de la ráfaga (define su posición en la cola).
2.  **`endTimestamp`**: El momento en que entró el último estímulo antes del cierre.
3.  **`deliveryTimestamp`**: El momento en que el SNA entregó el paquete al SNC.

Esta triple marca temporal permite que el agente no solo sepa *qué* pasó, sino con qué intensidad y durante cuánto tiempo, proporcionando una percepción del ritmo del entorno que es imposible de obtener con inyecciones de eventos individuales.

### 5.4. Empaquetado para la Consciencia
Finalmente, el `SensorEvent` dispone de métodos de serialización (normalmente a JSON o texto enriquecido) diseñados específicamente para ser consumidos por un LLM. El evento se encarga de presentarse de forma clara: *"Durante el periodo X a Y, el sensor Z ha detectado 45 ocurrencias de tipo [Error]"*. Este "empaquetado" final es el último paso en la transformación de la señal física en conocimiento agéntico.

## 6. Orquestación del Flujo: El SensorsService

El **`SensorsService`** actúa como el núcleo del Sistema Nervioso Autónomo (SNA), desempeñando el papel crítico de **coordinador sensorial y árbitro cronológico**. Su responsabilidad principal es gestionar la transición de los estímulos desde que son captados por los servicios de periferia (Sensores) hasta que son depositados en la "Cubeta de Contexto" para su consumo por el Sistema Nervioso Central (SNC). El servicio garantiza que, a pesar de la asincronía del mundo exterior, el agente perciba una realidad ordenada, digerida y coherente.

### 6.1. La Arquitectura del "Tálamo Digital"
Internamente, el `SensorsService` mantiene una estructura de datos compleja diseñada para la eficiencia y la fidelidad temporal. No es una simple cola; es un sistema de gestión de estados compuesto por:

1.  **Registro de Sensores**: Un mapa de `SensorData` indexado por el ID del canal, que contiene la lógica viva de cada sentido registrado.
2.  **Cola de Entrega (`DeliveryQueue`)**: Una cola de acceso concurrente donde se almacenan los `SensorEvent` ya procesados (Discretos, Fusionados o Agregados). Esta es la "bandeja de salida" hacia la consciencia.
3.  **Mapa de Estados (`StateMap`)**: Un almacén independiente para los eventos de naturaleza `STATE`. Dado que los estados no se acumulan cronológicamente sino que se sustituyen, residen aquí para ser intercalados dinámicamente en el momento de la entrega.
4.  **Puntero de Sensor Activo (`currentSensor`)**: Una referencia volátil que identifica cuál es el procesador que tiene actualmente una cubeta de acumulación abierta.

### 6.2. El Algoritmo de Ingesta: `putEvent`
Cuando un sensor externo (ej: el listener de Telegram) dispara una señal, el `SensorsService` ejecuta un protocolo de tres fases:

#### Fase 1: Identificación y Metacognición
El servicio localiza el `SensorData` correspondiente al canal. Antes de cualquier otra acción, se actualizan las `SensorStatistics`. El SNA registra que ha entrado un estímulo, calculando frecuencias y comprobando si el canal está en estado de silencio (Mute). Si el sensor está silenciado, la señal se contabiliza en las estadísticas de "eventos perdidos" y el proceso termina aquí, protegiendo al SNC de forma proactiva.

#### Fase 2: El Mecanismo de "Cierre por Interrupción"
Este es el componente más sofisticado de la orquestación. Para mantener la integridad del contexto, el sistema sigue la regla de **"Una sola cubeta activa"**. 
*   Si llega un estímulo y el sensor de origen es **diferente** al `currentSensor` que estaba acumulando datos, o si el evento es de naturaleza **`DISCRETE`**, el sistema detecta una ruptura en la continuidad.
*   **Acción de Flush**: El `SensorsService` ordena al sensor previo que cierre su buffer inmediatamente (`flushEvent()`). El evento acumulado (ej: el bloque de mensajes de Telegram de los últimos 2 minutos) se sella y se traslada a la `DeliveryQueue`.
*   Esta lógica garantiza que el agente nunca reciba mensajes mezclados de distintas fuentes en un mismo bloque, preservando la separación semántica de los eventos.

#### Fase 3: Disparo o Acumulación
Una vez gestionada la interrupción, el nuevo estímulo se procesa:
*   Si es `MERGEABLE` o `AGGREGATABLE`, el nuevo sensor se convierte en el `currentSensor` y empieza (o continúa) a llenar su propia cubeta.
*   Si es `STATE`, se actualiza el `StateMap`, reemplazando cualquier valor anterior de ese mismo sensor.

### 6.3. Sincronización Cronológica y el "Árbitro de Entrega"
Cuando el `ConversationService` solicita los eventos pendientes mediante la herramienta `pool_event`, el `SensorsService` no se limita a vaciar la cola. Realiza una **Fusión Maestra Cronológica**:

1.  **Cierre Forzado**: Primero, se obliga a realizar un *flush* de cualquier `currentSensor` que estuviera activo en ese milisegundo. Esto asegura que la consciencia reciba hasta el último bit de información disponible.
2.  **Lógica de Intercalado (The Peek Logic)**: El servicio compara el `timestamp` del primer evento en la `DeliveryQueue` con los `timestamps` de los eventos en el `StateMap`. 
3.  **Orden Real**: Si un evento de estado (ej: "Clima: Lluvia") ocurrió cronológicamente entre dos mensajes de texto que están en la cola, el servicio se encarga de entregarlos en ese orden exacto. Esto evita que el agente "viaje en el tiempo" o perciba efectos antes que sus causas.

### 6.4. Gestión de la Proactividad
El `SensorsService` monitoriza el estado del agente (`isBusy`). 
*   **Modo Vigilante**: Si el agente está ocioso y entra una señal, el `SensorsService` emite un aviso al orquestador para iniciar un nuevo turno de palabra. 
*   **Modo Acumulador**: Si el agente está ocupado, el servicio simplemente sigue orquestando el flujo en segundo plano, llenando la "Cubeta de Contexto" y esperando a que el SNC finalice su tarea actual para entregarle el informe de todo lo ocurrido durante su periodo de concentración.

### 6.5. Robustez y Concurrencia
Dado que los estímulos pueden llegar desde múltiples hilos simultáneos (red, sistema de archivos, temporizadores), el `SensorsService` está diseñado como un componente **Thread-Safe**. Utiliza bloqueos finos y estructuras concurrentes para asegurar que la orquestación del flujo no sufra condiciones de carrera, garantizando que el "sentido de la realidad" del agente sea siempre sólido y fiable.

En conclusión, el `SensorsService` transforma el orquestador de una simple pasarela a un **procesador de flujo inteligente**, asegurando que el cerebro del agente reciba una narrativa del mundo exterior perfectamente estructurada, optimizada y, sobre todo, cronológicamente veraz.


# 7. Interfaz de Proactividad y Atención Diferida

La **Interfaz de Proactividad** es el puente final entre el procesamiento fisiológico del Sistema Nervioso Autónomo (SNA) y el razonamiento estratégico del Sistema Nervioso Central (SNC). En esta arquitectura, la atención del cerebro (el LLM) se considera el recurso más escaso y valioso. Por tanto, el sistema no permite interrupciones caóticas o asíncronas que puedan fragmentar el contexto cognitivo. En su lugar, implementa un sofisticado mecanismo de **Atención Diferida**, asegurando que el agente siempre mantenga la soberanía sobre su propio flujo de pensamiento.

### 7.1. El Concepto de Atención Diferida
La atención diferida es la capacidad del sistema para registrar la realidad en tiempo real pero procesarla conscientemente solo cuando el motor de inferencia está preparado para un nuevo ciclo de razonamiento. El SNA actúa como un **"Buzón de Realidad"** que va acumulando señales digeridas (bloques de texto fusionados, contadores de eventos, cambios de estado) mientras el SNC está ocupado. 

Este diseño garantiza dos principios fundamentales:
1.  **Atomicidad del Razonamiento**: Un turno de conversación o la ejecución de una herramienta nunca se ven interrumpidos por la llegada de un estímulo externo. El agente termina lo que está haciendo antes de mirar qué ha pasado fuera.
2.  **Continuidad Contextual**: Al integrar los eventos entre turnos, se evita que el historial de la sesión se ensucie con inyecciones de datos que no guardan relación con el hilo actual del diálogo.

### 7.2. El Bucle de Revisión Sensorial (Post-Turn Check)
La comunicación entre el `SensorsService` y el `ConversationService` se rige por un estado compartido de disponibilidad (el flag `isBusy`). El momento crítico de la integración sensorial ocurre en la fase de **Post-Procesamiento del Turno**:

1.  **Finalización del Pensamiento**: El SNC termina su razonamiento y entrega la respuesta final al usuario.
2.  **Consulta al SNA**: Antes de liberar el control y volver al estado de reposo (*Idle*), el `ConversationService` ejecuta obligatoriamente un método de revisión: `checkSensoryMailbox()`.
3.  **Vaciado de la Cubeta**: Si el `SensorsService` indica que hay señales pendientes, el sistema **no permite que el usuario humano intervenga todavía**. En su lugar, el bucle de conversación se reinicia automáticamente utilizando los datos sensoriales acumulados como el nuevo "input" del agente.

Esta fase garantiza que el agente nunca esté "desactualizado". Antes de volver a escuchar al usuario, el agente se pone al día con todo lo que ha ocurrido en sus sensores durante el último minuto de trabajo.

### 7.3. La Herramienta `pool_event`: La "Mentira Necesaria"
Para que el Sistema Nervioso Central (el LLM) pueda digerir la información sensorial sin romper el protocolo estricto de los modelos de lenguaje (que esperan siempre un patrón *User -> AI -> Tool -> Result*), se utiliza el patrón de **Inversión de Control Simulada** mediante la herramienta `pool_event`.

*   **El Proceso de Inyección**: Cuando hay una señal consolidada en el SNA, el orquestador inyecta artificialmente en el historial un par de mensajes:
    1.  Un `AiMessage` que contiene una solicitud ficticia de la herramienta `pool_event()`.
    2.  Un `ToolExecutionResultMessage` que contiene el contenido del evento (el JSON con los datos del sensor).
*   **Efecto Cognitivo**: Al ver este par de mensajes, el LLM "cree" que él mismo ha decidido consultar sus sentidos. Esto permite que el modelo procese la información sensorial utilizando su lógica de razonamiento estándar, decidiendo si el evento requiere una respuesta inmediata, una anotación en memoria o si puede ser ignorado.

### 7.4. Escenarios de Disparo de Proactividad
El sistema gestiona la proactividad de forma diferente según el estado de carga del agente:

*   **Escenario A: Agente en Reposo (Idle)**: Si entra un estímulo y el agente no está haciendo nada, el `SensorsService` envía un "impulso eléctrico" al `ConversationService`. Este despierta, realiza la inyección de `pool_event` y el agente toma la iniciativa de hablarle al usuario (ej: *"Perdona que te interrumpa, pero acaba de llegar un correo urgente..."*).
*   **Escenario B: Agente Ocupado (Busy)**: Si el estímulo llega mientras el agente está, por ejemplo, leyendo un archivo grande, el SNA procesa y guarda la señal en la "Cubeta de Contexto". No hay interrupción. La señal espera pacientemente a que termine la tarea actual, momento en el cual se activará el Bucle de Revisión Sensorial descrito anteriormente.


### **Anexo I: Catálogo de Componentes Desacoplados**

*   **`SensorsService` (Interface)**: El punto de entrada único del sistema. Actúa como orquestador y factoría central, exponiendo métodos para crear `SensorInformation`, registrar sensores, inyectar estímulos (`putEvent`) y gestionar la disponibilidad sensorial.
    *   **`SensorsServiceImpl`**: Implementación interna que gestiona el estado del SNA, coordina los procesadores y ejecuta la lógica de "Fusión Maestra". Es la única que conoce las implementaciones concretas de los datos y eventos.

*   **`SensorInformation` (Interface)**: Contrato que define la identidad y naturaleza de un sentido. Permite al sistema y al modelo de lenguaje identificar el origen y la semántica de una observación sin depender de la lógica interna.
    *   **`SensorInformationImpl`**: Implementación de tipo POJO que almacena de forma privada el canal, la etiqueta, la naturaleza y la descripción del sensor.

*   **`SensorNature` (Enum)**: Definición de las estrategias de procesamiento de señal (`DISCRETE`, `MERGEABLE`, `AGGREGATABLE`, `STATE`).

*   **`SensorStatistics` (Interface)**: Define los métodos para consultar la salud y el rendimiento de un sensor, abstrayendo la forma en la que se calculan las frecuencias y los contadores de actividad.
    *   **`SensorStatisticsImpl`**: Implementación encargada de la lógica de acumuladores y ventanas temporales de actividad.

*   **`SensorData` (Interface)**: Lógica interna de procesamiento. Al ser un componente puramente del SNA (el "interior" del cuerpo), estas implementaciones no requieren interfaces públicas para tipos específicos, ya que no son consumidas por el cerebro (SNC).
    *   **`DiscreteSensorData`, `MergeableSensorData`, `AggregateSensorData`, `StateSensorData`**: Procesadores especializados que transforman estímulos en eventos según su naturaleza.

*   **`SensorEvent` (Interface)**: Contrato base para cualquier paquete de información que llegue a la consciencia. Define metadatos comunes como el canal, la prioridad y las marcas de tiempo.
    *   **`AbstractSensorEvent` (Clase Abstracta)**: Base común para las implementaciones internas de los eventos, facilitando la gestión de campos compartidos.
    *   **`SensorEventDiscrete` (Interface)**: Define el acceso al contenido íntegro de un evento atómico e independiente.
        *   **`SensorEventDiscreteImpl`**: Implementación interna inmutable.
    *   **`SensorEventMergeable` (Interface)**: Define el acceso al bloque de texto concatenado y al rango temporal que cubre una ráfaga narrativa.
        *   **`SensorEventMergeableImpl`**: Implementación interna que gestiona el buffer de texto.
    *   **`SensorEventAggregate` (Interface)**: Define el acceso al recuento numérico (contador) de las ocurrencias de una señal.
        *   **`SensorEventAggregateImpl`**: Implementación interna eficiente de bajo consumo de memoria.
    *   **`SensorEventState` (Interface)**: Garantiza que el evento representa la fotografía más actual y válida de una condición del entorno.
        *   **`SensorEventStateImpl`**: Implementación interna de sustitución.

*   **`PoolEventTool` (Clase)**: Herramienta de sistema que permite al LLM invocar la consulta de eventos pendientes, actuando como el activador de la proactividad sensorial.

### Reflexión sobre el flujo de creación:
Con este diseño, un servicio externo (ej: un plugin de monitorización de bolsa) haría esto:
1.  Pide al `SensorsService` un `SensorInformation` (usando el método factory).
2.  Registra ese objeto en el servicio.
3.  Empieza a disparar `putEvent(channelId, text, ...)` con datos en bruto.

El servicio externo **nunca crea el evento**. El `SensorsService` es quien, internamente y usando sus procesadores `SensorData`, decide cuándo y cómo instanciar las implementaciones de `SensorEvent` que luego la cola de entrega devolverá como interfaces.



# Anexo II: Especificaciones Técnicas de Implementación y Ciclo de Vida

Este anexo complementa la visión general de la arquitectura con las reglas de bajo nivel que gobiernan la concurrencia, la creación de componentes y la persistencia de la memoria sensorial del agente.

### 1. Estrategia de Concurrencia: El Modelo de Monitor
Dada la naturaleza multi-hilo de los sentidos (SNA) y la secuencialidad del razonamiento (SNC), la implementación del `SensorsService` adoptará el **patrón de Monitor de Java**. 

*   **Sincronización por Bloqueo Único**: Se utilizará la palabra clave `synchronized` en los métodos críticos de la implementación `SensorsServiceImpl`. Este enfoque garantiza la atomicidad de las operaciones de gestión de estado (como el cambio del `currentSensor`) y evita condiciones de carrera sin la complejidad de cerrojos granulares.
*   **Puntos Críticos de Bloqueo**:
    *   `putEvent()`: Asegura que la entrada de un estímulo y su posible efecto de "Flush" sobre el sensor previo ocurran como una única operación indivisible.
    *   `getPendingEvents()`: Garantiza que la recolección de señales para el cerebro se realice sobre una fotografía estática de la realidad, impidiendo que los hilos de los sensores modifiquen los buffers mientras se realiza la "Fusión Maestra".
*   **Compatibilidad con Hilos Virtuales (Java 21)**: Puesto que todas las operaciones dentro del `SensorsService` se realizan puramente en memoria (manipulación de mapas y punteros), no existe riesgo de bloqueo de hilos de plataforma (*thread pinning*). Esto permite que el sistema sensorial escale de forma transparente bajo la infraestructura de hilos virtuales de Noema.

### 2. Gestión de Buffers Estancados: El Protocolo de Cierre Forzado
Para evitar que la información de naturaleza acumulativa (`MERGEABLE` o `AGGREGATABLE`) quede atrapada indefinidamente en un buffer de trabajo a la espera de un nuevo estímulo que la "empuje", el sistema implementa la política de **Cierre Forzado por Consulta**.

*   **Eliminación de Temporizadores**: Se descarta el uso de hilos de limpieza (*reapers*) o *timeouts* internos para reducir la complejidad y el consumo de CPU.
*   **Garantía de Entrega**: El contrato del `SensorsService` dicta que cualquier solicitud de eventos proveniente del `ConversationService` (SNC) ejecutará automáticamente un `flush()` sobre el `currentSensor` activo en ese instante.
*   **Sincronización con el Turno**: Este mecanismo asegura que, al finalizar un turno de pensamiento, el agente reciba una actualización total de sus sentidos, capturando incluso ráfagas de mensajes que acaban de iniciarse. La realidad percibida por el cerebro siempre estará al día en el momento exacto de la consulta.

### 3. Contrato de Factoría y Desacoplamiento de Servicios
El `SensorsService` asume la responsabilidad de ser la única **Factoría Polimórfica** de componentes sensoriales, permitiendo que servicios externos se integren sin depender de las clases internas del orquestador.

*   **Firma del Método Factory**: El interface expondrá un método de creación de información:
    `SensorInformation createSensorInformation(String channel, String label, SensorNature nature, String description)`
*   **Registro de Naturalezas**: Internamente, el servicio mantendrá un mapa de registro de tipos: `Map<SensorNature, Supplier<SensorData>>`. Este mapa asocia cada `SensorNature` con el constructor de su implementación de procesamiento correspondiente.
*   **Extensibilidad "Open/Closed"**: Al añadir una nueva naturaleza sensorial, solo es necesario registrar su proveedor en el mapa interno durante el arranque del servicio. Los sensores externos simplemente solicitarán esa naturaleza, y el servicio instanciará la lógica de procesamiento adecuada de forma transparente.

### 4. Persistencia y Salud Sensorial: Ciclo de Vida del Servicio
El sistema sensorial no es volátil; posee "memoria de estado" para garantizar la continuidad de la atención entre reinicios de la aplicación.

*   **Ciclo de Vida Estandarizado**: Se añade el método `stop()` a la interfaz `AgentService`. El agente registrará un *Hook* de cierre en la JVM que invocará este método de forma coordinada al apagarse.
*   **Volcado a AgentSettings**: Durante la ejecución de `stop()`, el `SensorsService` persistirá en la configuración del agente (`settings.json`) el estado operativo de cada sensor (si estaba silenciado o activo) y sus estadísticas históricas (`totalEventsActive`, `totalEventsMuted`, `lastEventTimestamp`).
*   **Rehidratación Sensorial**: Al iniciarse el servicio (`start()`), este consultará la configuración persistida. Si un sensor se registra con un ID de canal ya conocido, sus estadísticas y su estado de silencio se restaurarán automáticamente, permitiendo que el agente mantenga sus preferencias de atención y su histórico de actividad de forma indefinida.

### 5. Formato de Intercambio de Información (JSON)
Para minimizar el impacto en el código existente y en los *prompts* del modelo de lenguaje, se mantendrá la estructura actual de la clase `Event`, delegando la especialización de los datos en el campo de contenido.

*   **Estructura del Paquete**: Cada observación inyectada mantendrá los campos: `channel`, `status`, `priority` y `contents`.
*   **Generación de Contenidos**: Cada implementación de `SensorEvent` será la encargada de poblar el campo `contents` con la representación textual o JSON adecuada a su naturaleza:
    *   **Mergeable**: Un bloque de texto narrativo con marcas de tiempo integradas.
    *   **Aggregate**: Un informe resumido de volumen e intensidad.
    *   **State**: El valor actual y único de la condición detectada.
*   **Consumo Uniforme**: Este enfoque garantiza que el SNC (el LLM) pueda seguir procesando las observaciones mediante la herramienta `pool_event` sin requerir una lógica de interpretación diferente para cada tipo de sensor, ya que la "digestión" técnica ha sido realizada previamente por el SNA.



# Anexo III: Elucubraciones sobre el Bucle de Consciencia Unificado

La refactorización hacia un sistema sensorial avanzado permite proyectar una evolución radical en la estructura del motor de diálogo: la transición desde un modelo de ejecución por llamada (Request-Response) hacia un **Bucle de Consciencia Unificado**. En esta visión, el agente deja de ser un programa que se ejecuta ante un comando y se convierte en un proceso autónomo y perpetuo.

### 1. El Usuario como Sensor Maestro
La distinción técnica entre "lo que dice el usuario" y "lo que reporta un sensor" desaparece. La interfaz de usuario (consola o UI) se registra en el `SensorsService` como un sensor de naturaleza `DISCRETE` (o `MERGEABLE` para ráfagas de texto). 
*   Cualquier texto introducido por el humano se trata como una **Señal de Entrada de Alta Prioridad**. 
*   Esto desacopla la interfaz: el usuario puede escribir en cualquier momento sin esperar a que el agente "termine", ya que sus mensajes se encolan y procesan de forma asíncrona junto al resto de la realidad.

### 2. De Función a Bucle Perpetuo: `executeReasoningLoop`
El motor de razonamiento (`ConversationService`) se simplifica al extremo, transformándose en un bucle `while(true)` ejecutado en un hilo independiente (o hilo virtual). Su única responsabilidad es consumir señales del SNA:

```java
// Representación lógica del bucle unificado
while (agent.isRunning()) {
    SensorEvent signal = sensorsService.takeNextSignal(); // Bloqueante hasta que haya señal
    processSignal(signal);
}
```

Este diseño elimina la necesidad de métodos complejos de "chequeo de buzón" al final de cada turno; el sistema simplemente vuelve al inicio del bucle y extrae la siguiente señal disponible en la cubeta sensorial (ya sea un log de error, un mensaje de Telegram o una nueva instrucción del usuario).

### 3. Enrutamiento de la Respuesta: El Papel de la Consola
En este modelo unificado, el agente no "retorna" un String como resultado de una función. La emisión de la respuesta se gestiona mediante un **Enrutamiento Basado en el Origen**:

*   **Si la señal proviene del Usuario**: El bucle de razonamiento identifica que el estímulo requiere una respuesta directa y emite su conclusión a través de `agent.getConsole().printModelResponse(texto)`.
*   **Si la señal proviene de un Sensor Asíncrono (Logs, Email, etc.)**: El agente puede decidir, basándose en el contenido, si debe notificar al usuario por consola o simplemente realizar una acción silenciosa (como actualizar un archivo o su propia memoria).

Esta arquitectura permite que el agente "hable solo" (proactividad) o responda al humano usando el mismo mecanismo interno, garantizando que el flujo de salida sea tan coherente como el de entrada.

### 4. Soberanía Cognitiva y Gestión de la Multitarea
Al tratar todo como eventos en una cola única, el agente gana una gestión de la atención mucho más natural. Si el agente está procesando una señal compleja y el usuario introduce tres mensajes seguidos, el `SensorsService` los agrupa (si son de naturaleza `MERGEABLE`). Cuando el agente queda libre, consume ese único bloque consolidado, permitiéndole entender todo el contexto de lo que el usuario ha dicho durante su periodo de "reflexión".

### 5. Conclusión: La Simplicidad como Estado Final
La unificación del flujo bajo el `SensorsService` y el Bucle Perpetuo elimina la deuda técnica derivada de gestionar dos mundos (síncrono/usuario y asíncrono/sensores) por separado. El sistema se convierte en un procesador de señales puro:
1.  **Ingesta (SNA)**: Todo estímulo entra por los sensores.
2.  **Procesamiento (SNC)**: Un bucle único extrae señales y decide.
3.  **Emisión (Consola/Tools)**: El agente actúa o habla según el contexto del origen.

Esta visión de futuro posiciona a la arquitectura de Noema no como un asistente de chat, sino como un sistema operativo cognitivo capaz de habitar un entorno dinámico de forma verdaderamente autónoma.


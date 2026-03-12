
# Diseño de Arquitectura: Sistema Sensorial de Agentes (SNA/SNC)

### 1. Visión General de la Arquitectura
Describe la división fundamental entre el **Sistema Nervioso Autónomo (SNA)**, encargado de la ingesta y procesamiento fisiológico de estímulos (Orquestador), y el **Sistema Nervioso Central (SNC)**, encargado del razonamiento consciente (LLM). Se explica cómo esta separación protege al agente de la saturación sensorial y optimiza el uso de la ventana de contexto.

### 2. Definiciones Base: Identidad y Naturaleza
Define la clase `SensorInformation` como el "DNI" de cada sensor (canal, etiqueta, descripción) y el enumerado `SensorNature`. Esta sección detalla los cuatro tipos de comportamiento sensorial (**Discrete, Mergeable, Aggregate, State**), estableciendo las reglas básicas de cómo el sistema interpreta cada fuente de datos.

### 3. Metacognición Sensorial: Estadísticas y Salud
Describe la clase `SensorStatistics` y su función de monitorear el rendimiento de los sentidos. Incluye la lógica para contabilizar eventos recibidos tanto en estado activo como inactivo (silenciado), frecuencias medias y marcas de tiempo de actividad, permitiendo que el agente "sepa" cuánto ruido está ignorando mediante la herramienta `sensor_status`.

### 4. Procesamiento Autónomo: La Capa de SensorData
Presenta la interfaz `SensorData` y sus implementaciones polimórficas. Se explica cómo cada instancia de `SensorData` actúa como un procesador de estado que encapsula su propia información y estadísticas, encargándose de la lógica de "digestión" de los estímulos (si debe juntar, contar o sustituir) antes de generar un evento para el cerebro.

### 5. El Modelo de Eventos: De la Construcción a la Entrega
Define la jerarquía de `SensorEvent` (Discrete, Mergeable, Aggregate, State). Se detalla el diseño de **mutabilidad controlada**, donde los eventos son mutables durante su fase de construcción dentro del `SensorData` para maximizar la eficiencia, pero se consideran "instantáneas" inalterables una vez que se trasladan a la cola de entrega definitiva.

### 6. Orquestación del Flujo: El SensorService
Describe el motor principal de gestión de señales. Introduce los conceptos de `currentSensor` (el sensor acumulador activo) y el mecanismo de **"Cierre por Interrupción"**, que dicta que los buffers activos deben sellarse y enviarse a la cola de entrega cuando llega un evento discreto o un estímulo de un sensor diferente, garantizando la coherencia cronológica y contextual.

### 7. Interfaz de Proactividad y Atención Diferida
Explica la comunicación entre el `SensorsService` y el `ConversationService`. Se detalla el mecanismo de **"atención diferida"**, donde el servicio sensorial notifica la presencia de señales pero respeta el estado de ocupado (*busy*) del agente, obligando a una revisión de la "cubeta de contexto" al finalizar cada turno antes de que el usuario vuelva a intervenir.


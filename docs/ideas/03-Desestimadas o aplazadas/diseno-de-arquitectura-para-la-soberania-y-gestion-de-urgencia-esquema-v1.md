
# Esquema: Diseño de Arquitectura para la Soberanía y Gestión de Urgencia

### 1. Filosofía de la Soberanía Cognitiva
Define el objetivo de pasar de un sistema reactivo FIFO (primero en entrar, primero en salir) a un organismo capaz de negociar su atención con el entorno. Se introduce la premisa de que el agente debe poseer "soberanía" para interrumpir procesos obsoletos en favor de estímulos más relevantes o mandatos directos del usuario.

### 2. El Mecanismo Atómico: `ReasoningService.cancelCurrentEvent()`
Describe la implementación técnica del "freno de mano" del cerebro del agente. Este método se limita exclusivamente a activar la señal de aborto que el streaming del LLM consulta periódicamente, provocando una excepción controlada que libera el hilo de ejecución sin gestionar colas ni estados externos.

### 3. El SNA como Gestor de Atención: `SensorsService.userCancelRequested()`
Presenta al sistema sensorial como el orquestador que decide qué sucede con la cola de eventos ante una interrupción. Detalla cómo este método específico para el usuario purga los eventos de naturaleza `USER` (arrepentimiento del dueño), empaqueta el resto de percepciones en un reporte de situación y finalmente invoca la cancelación en el servicio de razonamiento.

### 4. Lógica de Prioridad y "Golpe de Estado" Sensorial
Explica el protocolo automático de interrupción basado en la jerarquía de prioridades de los eventos entrantes. Describe cómo el SNA, al detectar un estímulo urgente, "prepara el futuro" (ordenando la cola y generando archivos de desbordamiento) antes de ejecutar el "golpe de estado" que silencia al cerebro para entregarle el nuevo escenario de forma inmediata.

### 5. Higiene de la Inundación: El Sándwich Sensorial
Detalla la estrategia para evitar el colapso de la ventana de contexto tras una interrupción masiva. Propone el uso del `SensorEventGroup` con estructura de "sándwich": una lista con los 10 eventos más antiguos (el origen), los 10 más nuevos (el precursor inmediato) y un contador del "gap" intermedio que ha sido desplazado.

### 6. Memoria de Trabajo Secundaria: Archivo por Lotes (`BatchID`)
Describe el sistema de persistencia temporal para los eventos que no cupieron en el sándwich sensorial. Introduce el concepto de `BatchID` como una etiqueta que vincula todos los estímulos de una ráfaga en la base de datos (`SENSORY_ARCHIVE`), permitiendo que el sistema mantenga la trazabilidad forense sin saturar el presente cognitivo.

### 7. Herramientas de Metacognición: `lookup_sensory_archive`
Define la interfaz que permite al agente recuperar voluntariamente la información que fue archivada durante una crisis de urgencia. Explica cómo el LLM puede usar el `BatchID` recibido para realizar investigaciones granulares (paginadas y filtradas) si considera que el backlog desplazado es relevante para resolver la situación actual.

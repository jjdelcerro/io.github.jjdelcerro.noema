# Tareas Pendientes: Integración del Subsistema Sensorial

Tras la implementación de la arquitectura base del Sistema Sensorial (SNA/SNC), las siguientes tareas son necesarias para su plena integración en el proyecto Noema.

## 1. Integración en el Ciclo de Vida del Agente

- [ ] **Añadir `stop()` a `AgentService`**: Modificar la interfaz `io.github.jjdelcerro.noema.lib.AgentService` para incluir el método `void stop()`. Asegurar que todas las implementaciones (especialmente `SensorsServiceImpl`) lo implementen para la persistencia al cierre.
- [ ] **Registrar `SensorsService` en `AgentLocator`**: Asegurar que el servicio esté disponible globalmente.
- [ ] **Actualizar `BootUtils` / `AgentManagerImpl`**: Inicializar el `SensorsService` durante el arranque y registrar las herramientas `PoolEventTool` y `SensorStatusTool` en el `AgentActions`.

## 2. Cognición y Atención Diferida (ConversationService)

- [ ] **Inyectar `SensorsService` en `ConversationService`**: El servicio de conversación debe ser consciente de los sentidos.
- [ ] **Implementar `checkSensoryMailbox()`**: Crear un método que se ejecute al finalizar cada turno de inferencia para vaciar la cubeta de eventos pendientes.
- [ ] **Mecanismo de Despertar Proactivo**: Implementar la lógica que, cuando el agente está `idle`, detecta nuevos eventos y dispara un turno artificial inyectando la llamada a `pool_event`.
- [ ] **Gestión del estado `isBusy`**: Asegurar que el flag de ocupado se gestione correctamente para evitar interrupciones durante el razonamiento.

## 3. Persistencia y Configuración

- [ ] **Soporte en `settings.json`**: Definir la estructura para guardar el estado de los sensores (muted/unmuted) y sus estadísticas históricas.
- [ ] **Lógica de Carga/Guardado**: En el `start()` y `stop()` de `SensorsServiceImpl`, implementar la rehidratación y el volcado de datos desde/hacia `AgentSettings`.

## 4. Sensores Concretos (Entradas de Datos)

- [ ] **Refactorizar `TelegramService`**: Adaptar el listener de Telegram para que use `SensorsService.putEvent()` en lugar de inyecciones directas o colas antiguas. Definir su naturaleza como `MERGEABLE`.
- [ ] **Refactorizar `EmailService`**: Adaptar la monitorización de correo. Naturaleza `DISCRETE` o `STATE` según el caso.
- [ ] **Monitor de Logs**: Crear un nuevo sensor para monitorizar logs críticos del sistema. Naturaleza `AGGREGATABLE`.

## 5. Interfaz de Usuario (UI)

- [ ] **Visualización en Swing/Consola**: Crear un panel o comando para que el usuario humano pueda ver el estado de los sensores y activarlos/desactivarlos manualmente.

---

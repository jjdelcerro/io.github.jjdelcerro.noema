
# Notas de Diseño: Asincronía Cognitiva y Prioridad de Desalojo

### 1. La Herramienta como Percepción Asíncrona
El modelo tradicional de agentes trata las herramientas como llamadas a funciones síncronas: el agente pregunta, el sistema se detiene, la función responde y el agente continúa. En la arquitectura avanzada de Noema, proponemos un cambio de paradigma: **Las herramientas son sensores que el propio agente activa.**

*   **Ejecución No Bloqueante**: Cuando el LLM emite un `tool_call`, el orquestador no bloquea el hilo principal. Lanza la ejecución en un hilo virtual independiente y el bucle de razonamiento vuelve inmediatamente a su estado de escucha en el `SensorsService`.
*   **Resultados como Eventos**: El resultado de una herramienta no es un "retorno de función", es un **Evento de tipo `TOOL_RESULT`** que entra en la cubeta sensorial cronológicamente.
*   **Gestión Discreta**: Si el LLM solicita tres herramientas a la vez, el agente procesará los resultados conforme vayan llegando al `SensorsService`. No hay necesidad de esperar a que todas terminen ("Ventanilla Única"). Esto permite al agente re-planificar su estrategia tras recibir el primer resultado, incluso antes de que los otros lleguen.

### 2. Intercalado de Contexto (La Interrupción en Tiempo Real)
Al tratar la entrada del usuario y el resultado de las herramientas como eventos en una cola única, eliminamos la "sordera temporal" del agente durante tareas largas.

*   **Inyección entre Herramientas**: Es posible que entre el resultado de la "Herramienta A" y la "Herramienta B", el usuario introduzca un mensaje.
*   **Flujo Semántico**: El agente leerá en su próximo ciclo: `[Resultado A] + [Mensaje Usuario]`. Esto le permite reaccionar a la interrupción del humano de forma inmediata, pudiendo incluso decidir ignorar el resultado de la "Herramienta B" (que aún no ha llegado o se está procesando) si el usuario ha cambiado las instrucciones.

### 3. El Usuario como Sensor de Prioridad Máxima (Preemption)
Para que el agente sea verdaderamente proactivo y reactivo a la vez, el usuario debe poseer la capacidad de **desalojo (preemption)** sobre los procesos en curso.

*   **Prioridad `USER_COMMAND`**: Los eventos provenientes de la consola de usuario se marcan con la prioridad más alta.
*   **Aborto de Tareas**: Si el usuario envía una señal de parada o cambio de dirección mientras una herramienta lenta (ej: `shell_execute` o `document_index`) está corriendo en su propio hilo, el orquestador (SNA) puede:
    1.  Detectar la prioridad máxima del mensaje de usuario.
    2.  Cancelar/Matar el hilo de la herramienta en curso.
    3.  Despertar al SNC (LLM) para que procese la nueva orden inmediatamente.
*   **Sensación de Control**: Esto garantiza que el usuario nunca sienta que ha perdido el mando del agente mientras este "piensa" o "ejecuta".

### 4. Desafíos Técnicos: La "Diplomacia" del Orquestador
Este modelo asíncrono choca con la rigidez de las APIs actuales de LLM (que exigen un orden estricto de *Assistant Call -> Tool Result*). El orquestador de Noema debe actuar como un diplomático:

*   **Coherencia de Historial**: Si un mensaje de usuario se cuela entre una petición de herramienta y su resultado, el orquestador debe reordenar o "envolver" los mensajes para que la API del LLM no devuelva un error de protocolo, manteniendo la ilusión de un diálogo legal mientras se gestiona una realidad asíncrona.
*   **Gestión de Estados Sucios**: Se requiere un sistema de seguimiento para saber qué herramientas se han cancelado y si han dejado rastros (ficheros a medias, etc.) que requieran una compensación posterior.

### 5. Conclusión: Hacia un Agente de Tiempo Real
Estas notas perfilan un agente que no solo es "inteligente" en sus respuestas, sino "vivo" en su interacción. Al desacoplar la ejecución de herramientas del bucle de pensamiento y priorizar la voz del usuario, Noema se convierte en un **Sistema Operativo Cognitivo de Tiempo Real**, capaz de habitar un mundo donde las cosas ocurren en paralelo y las prioridades cambian en milisegundos.

---

Este enfoque es el que permitirá que Noema pase de ser un "asistente de IA" a un "colaborador autónomo" que nunca te ignora. ¡Todo un reto para la fase de implementación!
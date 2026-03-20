# Noema: Implementación de Referencia para Memoria narrativa trazable

> ⚠️ **Estado del Proyecto: Prototipo de Investigación (Alpha)**
>
> Este repositorio contiene el código fuente que acompaña a mi serie de artículos sobre *Arquitectura de Agentes y Memoria Híbrida*.
>
> **No es un producto de consumo final.** Es una **Prueba de Concepto (PoC)** funcional diseñada para validar arquitecturas de IA locales, proactividad y gestión de memoria a largo plazo. Úsalo para estudiar la arquitectura, extraer patrones o como base para experimentos.

## Propósito del Proyecto

El objetivo de este software es demostrar que es posible construir un Agente Autónomo con persistencia real y capacidad de agencia sin depender de infraestructuras complejas en la nube o frameworks pesados.

Implementa los conceptos teóricos de:

*   **Memoria Híbrida Determinista:** Separación de roles entre un servicio de conversación
    (ReasoningService) y otro para la consolidación narrativa (MemoryService).
*   **Proactividad Simulada:** Implementación del patrón `pool_event` y gestion de
    eventos a través del SensorsService para permitir al agente reaccionar a estímulos 
    externos dentro del ciclo síncrono del LLM.
*   **Independencia:** Ejecución local (On-Premise) con soporte para LLMs remotos (OpenRouter) o locales (Ollama).

## Interfaz de Usuario

Noema no solo procesa texto; interactúa con su entorno. En la siguiente captura se observa cómo 
el agente, ante una consulta meteorológica, decide de forma autónoma localizar al usuario y consultar 
una API de clima externa antes de responder:

![Noema en acción](./screenshot.png)

*   **Razonamiento y Herramientas:** Visualización clara de la ejecución de `AgentTools`.
*   **Diseño Moderno:** Interfaz con la estética de los asistentes de IA actuales con soporte para Markdown.
*   **Control de Contexto:** Monitorización en tiempo real de los tokens utilizados (Herramientas + Conversación).

## Stack Tecnológico

La implementación prioriza la ligereza y el control explícito de recursos:

*   **Core:** Java 21 (Uso intensivo de *Virtual Threads*).
*   **Orquestación:** LangChain4j (sin integraciones de alto nivel, uso "bare-metal").
*   **Persistencia:** H2 Database embebida (Modo mixto: Relacional + BLOBs para vectores).
*   **Arquitectura:** Diseño modular basado en *Service Locator* e inyección manual. Sin Spring Boot ni frameworks de DI.
*   **UI:** Escritorio (Swing) y Consola (JLine 3, aunque este lo he tenido abandonada últimamente).

## Documentación y Arquitectura

La documentación técnica detallada de este proyecto reside en un archivo especial 
dentro de este mismo repositorio, aunque para variar esta incompleta:

📄 **[AGENT_CONTEXT.md](./AGENT_CONTEXT.md)**

> **Nota sobre este archivo:**
>
> Este proyecto se desarrolla utilizando una metodología de colaboración con IA. 
> El archivo `AGENT_CONTEXT.md` actúa como el **Contexto Vivo** que utiliza mi 
> asistente para entender el proyecto. Contiene el análisis de la arquitectura, 
> los patrones de diseño y las decisiones técnicas fundamentales.
>
> Si quieres entender cómo funciona este sistema "bajo el capó", ese es el 
> documento que debes leer.

📄 **[DEVELOPMENT_STATUS.md](./DEVELOPMENT_STATUS.md)**

Este proyecto es un organismo vivo que evoluciona junto con mis investigaciones. 
Para conocer el grado de completitud de cada bloque y la deuda técnica identificada, 
consultalo.

> **Nota:** Este informe es generado y actualizado de vez en cuando con ayuda 
> de mi asistente de IA tras cada hito relevante, actuando como un registro del 
> progreso y los desafíos pendientes.

📄 **Otra documentación relevante**

Puedes encontrar interesantes algunos de los artículos que he publicado y para 
los que este proyecto a sido el patio de pruebas.

*   [¿Qué es un agente?](https://jjdelcerro.github.io/es/blog/que-es-un-agente/)
*   [Agentes de IA y la inyección de observaciones proactivas en clientes de chat](https://jjdelcerro.github.io/es/blog/agentes-de-ia-y-la-inyeccion-de-observaciones-proactivas-en-clientes-de-chat/)
*   [Control proactivo de la percepción en agentes de IA](https://jjdelcerro.github.io/es/blog/como-gestionar-la-observacion-proactiva/)
*   [¿Memoria narrativa o resumen para un LLM?](https://jjdelcerro.github.io/es/blog/memoria-narrativa-o-resumen-para-un-llm/)
*   [Diseñando memoria narrativa trazable para agentes conversacionales](https://jjdelcerro.github.io/es/blog/disenando-memoria-narrativa-trazable-para-agentes-conversacionales/)

## Características Principales

1.  **Gestión de Memoria:**
    *   **Session:** Memoria de corto plazo.
    *   **Turns:** Persistencia inmutable de interacciones.
    *   **CheckPoints:** Resúmenes narrativos ("El Viaje") generados por IA para compactar la historia sin perder referencias.

2.  **Herramientas (Agency):**
    *   Sistema de archivos (Lectura, escritura, parches...).
    *   Navegación Web (Búsqueda y extracción con Apache Tika).
    *   Comunicación (Cliente de Email IMAP/SMTP, Bot de Telegram).
    *   Planificador de tareas (Scheduler en lenguaje natural).

3.  **Document Mapper (RAG Estructural):**
    *   Sistema de ingestión de documentos que genera una Tabla de Contenidos 
        estructural antes de vectorizar, permitiendo búsquedas más precisas 
        que el RAG tradicional.

## Inicio Rápido

### Requisitos
*   JDK 21 o superior.
*   Maven 3.8+.

### Construcción
El proyecto utiliza `maven-shade-plugin` para gestionar las dependencias y los 
SPIs de LangChain4j/Tika.

```bash
mvn clean package
```

### Configuración y primer arranque

El agente necesita configurar los proveedores de LLM. Al arrancar la aplicación
presenta un diálogo de inicio, que permite seleccionar la carpeta de trabajo, y 
nos muestra la configuración actual para los modelos básicos que precisa
para funcionar. Desde ahí mismo puede accederse al diálogo de configuración para
editar la lista de de proveedores, modelos o API-KEYS y configurar éstos en la 
aplicación.


1.  Ejecuta el JAR.
2.  Accede a la configuración, configura el proveedor, API-Keys y modelos y selecciona
    los que vayas a usar en cada parte de la aplicación.
3.  Una vez realizada la configuración cierra el dialogo de configuración para
    volver a la ventana de inicio y pulsa en continuar.
   
## Desarrollo y Depuración

Dado que este es un proyecto de investigación, es muy probable que quieras 
inspeccionar el estado interno del agente (ej: ver el contenido de `Session` o 
los vectores en memoria) mientras se ejecuta.

Recomiendo lanzar el agente exponiendo el puerto JDWP. Te dejo aquí un pequeño 
script de ejemplo (`noema`) para facilitar esto:

```bash
#!/bin/bash
cd $(dirname $0)
echo "Current folder $PWD"
if [ "$1" == "--debug" ] ; then
	suspend=y
else
	suspend=n
fi
exec java "-agentlib:jdwp=transport=dt_socket,address=8765,server=y,suspend=${suspend}" -jar noema.jar $@
```

Una vez iniciada la aplicación puedes acceder al modo debugger en el puerto 8765. 
Si precisas depurar el arranque inicia el script con "--debug" y luego conectate
desde el entorno de depuración.
    
## Estructura de Datos y Personalización (`./data`)

La aplicación crea en la carpeta de trabajo seleccionada una carpeta `.noema-agent`
en la que podemos encontrar la siguiente estructura:

* **home** (usada como home cuando se usa firejail para ejecutar comandos de shell).
* **var**

  * **cache**, carpeta donde guarda la cache de documentos que procesa, por ejemplo,
    datos de texto extraídos de pdf.
  * **config**, aquí esta la configuración del agente, en el fichero "settings.json",
    así como los prompts que utiliza en algunas de las tareas que realiza. Pueden
    editarse esos prompts en caliente para experimentar con ellos.
  * **identity**, aquí están los ficheros que conforman la identidad del agente.

    * **core**, estos se cargan automáticamente en el prompt del sistema.
    * **environ**, de estos solo se carga un indice para que el agente pueda cargarlos
      completos cuando los necesite.

  * **lib**, aquí se guardan las bases de datos de memoria y servicios del agente, 
    así como la configuración de sensores y la memoria *viva* de la sesión.
  * **log**, pues eso, los logs de la aplicación.
  * **skills**, aquí se guardan los skills que tiene el agente a su disposición.
  * **tmp**, aquí van a parar los ficheros temporales que genera el agente.


### 1. La Base de Conocimiento (Source of Truth)

*   **`var/lib/memory.mv.db`**: Base de Datos H2 empotrada. Contiene la tabla de 
    turnos inmutables, metadatos y los embeddings vectoriales. 

### 2. Memoria Narrativa (Checkpoints)

*   **`var/lib/checkpoints/checkpoint-XXX.md`**: Aquí es donde sucede la magia 
    de la consolidación. Son los archivos de "El Viaje" generados por el MemoryService.
    Leer estos archivos es la mejor forma de validar cómo el agente compacta la 
    historia sin perder el contexto cognitivo.

### 3. Sala de Máquinas (Prompts)

*   **`var/config/prompts/*.md`**: Al primer arranque, el agente despliega los 
    prompts del sistema desde el JAR a esta carpeta. 
    >
    > Puedes editar estos archivos en caliente para ajustar el comportamiento 
    > del agente sin necesidad de volver a compilar el código.
    >

### 4. Estado y Configuración

*   **`var/config/settings.json`**: Parámetros de conexión a los proveedores de LLM.
*   **`var/lib/active_session.json`**: Un snapshot memento de la sesión activa 
    para permitir la recuperación ante cierres inesperados.

### 5. Inspección de datos en vivo (H2 Console)

Para facilitar la auditoría de la memoria del agente, la aplicación inicia 
automáticamente un servidor web de H2. En el dialogo de configuración
hay posibilidad de configurar el puerto de este servicio asi como un botón
para iniciar la herramienta en el navegador.

Desde aquí puedes ejecutar consultas SQL para ver vectores, turnos consolidados 
y metadatos de las herramientas en tiempo real.

### 6. Estado

Me he centrado en tratar de tener funcionando la parte de:

* Conversación
* Consolidación de memoria
* La gestión básica de sensores
* La gestión de identidad
* La gestión basica de habilidades (skills)
* Que las opciones de configuración sean minimamente funcionales.
* Gestión básica de la seguridad.
* Herramientas básicas de acceso disco
* Herramientas básicas de acceso a internet
* Herramientas bñásicas de control de versiones.

Hay muchas cosas que no estan funcionales:

* El servicio de "documentos".
* Correo.
* Telegram.
* Ejecución de comandos de shell.
* Busqueda en internet.
* Muchas opciones de configuración, para asistir en la adición de archivos de 
  identidad, habilidades, configuracion en el home del usuario, limpieza de 
  temporales...

Estas partes las ire abordando poco a poco dependiendo de mi disponibilidad de
tiempo.


---
*Desarrollado por [Joaquin del Cerro](https://github.com/jjdelcerro) como parte de la investigación sobre arquitecturas de IA.*


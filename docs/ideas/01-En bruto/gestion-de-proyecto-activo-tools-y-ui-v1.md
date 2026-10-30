
# Gestion de proyecto activo, tools y UI.

### 1. La Motivación y el Problema de Fondo

Al refinar la categorización de `annotate_observation` (separando notas como `PROJECT_MAP` y `TASK_SCRATCHPAD`), detectamos un vacío: **esas notas quedaban huérfanas de contexto si el sistema no sabía a qué proyecto pertenecían**.

Este problema se agravaba por tres características propias de tu arquitectura y de tu flujo de trabajo:

1. **Sesión única y biográfica (Sin reinicios):**  
   En *Noema* no existe el concepto clásico de "cerrar sesión y abrir otra". Hay una única línea temporal continua que se extiende a lo largo de semanas o meses. Por tanto, no se podía confiar en un "fin de sesión" para limpiar borradores o delimitar tareas.
2. **La ruta de disco no equivale al proyecto:**  
   Un proyecto de software real no es una carpeta 1:1. Un mismo proyecto (ej. *Puertos y Costas*) abarca simultáneamente el backend en Python, el frontend en un monorepo, contenedores Docker, bases de datos y carpetas de notas en rutas completamente distintas. Además, en una misma sesión puedes saltar de trabajar en *Noema* a trabajar en *PyC*.
3. **El peligro del *Naming Drift* y la sobrecarga:**  
   Añadir un parámetro obligatorio `project` a la herramienta `annotate_observation` se descartó de inmediato:
   * **Sobrecarga cognitiva:** Obliga al LLM a gestionar un metadato más en cada turno.
   * **Inconsistencia estocástica:** El modelo hoy escribiría `"PyC"`, mañana `"librapyc"`, pasado `"gvsigol-frontend"` y al otro `"online"`, haciendo imposible agrupar o indexar nada de forma fiable.

---

### 2. Alternativas Evaluadas y Descartadas

* **Deducción pasiva por el Arnés (vía `resource_id` o `AgentPaths`):**  
  Aunque Java puede escalar por el árbol de directorios buscando `.git` o `package.json`, esto solo identifica el repositorio local, no el **proyecto conceptual**. Si estás tocando un script de docker fuera del repo del frontend pero para el mismo proyecto, la deducción automática falla o fragmenta el contexto.
* **Control modal inferido por el LLM:**  
  Dejar que el modelo decida por su cuenta cuándo cambia de proyecto genera falsos positivos (conmutaría de proyecto solo porque en una charla técnica se mencione un cliente o una herramienta de otro sistema).

---

### 3. La Solución Acordada: `set_current_project` + Arnés + UI

La solución se apoyó en un principio clásico de arquitectura: **el proyecto no es un argumento de cada herramienta; es una propiedad del entorno de ejecución**.

#### A. La herramienta `set_current_project(project_name)`
Se introduce una herramienta específica y ligera cuyo único cometido es cambiar el proyecto activo en el estado del arnés.
* **Regla contractual estricta:** El LLM **solo** puede invocarla bajo orden directa y explícita del usuario (*«Ahora vamos a trabajar en el proyecto PyC»*, *«Cambiamos a Noema»*). Tiene terminantemente prohibido deducirlo o invocarlo por iniciativa propia.

#### B. Visibilidad y Control en el UI
El arnés expone el proyecto activo en la interfaz (barra superior / status bar: `[Proyecto: PyC]`).
* El usuario siempre sabe en qué contexto mental está el agente.
* Si el contexto no coincide, el usuario puede corregirlo por chat o mediante un selector directo en la propia interfaz sin necesidad de consumir turnos de inferencia.

#### C. Estampado transparente por el Arnés
`annotate_observation` no cambia su firma ni se sobrecarga. Cuando el agente toma una nota, **es el arnés en Java el que le inyecta internamente el `project_id` activo** antes de guardarla. Cero riesgo de alucinación de nombres.

---

### 4. El Ciclo de Vida Resultante para la Memoria

Esta solución cerró limpiamente el ciclo de vida de las dos categorías que estaban en el aire:

* **Para `TASK_SCRATCHPAD` (Borrador de Tarea):**  
  * Dentro del proyecto, vive solo en la memoria reciente y **muere por envejecimiento natural** al salir de la ventana deslizante (ya que el consolidador lo ignora para *El Viaje*).
  * Si el usuario dice *«Cambiamos al proyecto Noema»*, el arnés puede purgar inmediatamente los borradores de tarea del proyecto anterior.
* **Para `PROJECT_MAP` (La Pizarra Viva del Repositorio):**  
  * Queda guardado y etiquetado bajo la clave del proyecto (ej. `PROJECT_MAP` de `PyC`).
  * No ensucia la crónica narrativa de *El Viaje*.
  * **La gran ventaja a largo plazo:** Si dentro de tres semanas vuelves a decir *«Nos ponemos con PyC»*, el arnés puede **rehidratar e inyectar automáticamente el `PROJECT_MAP` de PyC en la memoria proyectada** como contexto anclado. El agente recupera de golpe el mapa del monorepo y sus 45 microfrontends sin tener que volver a explorar el disco.
  
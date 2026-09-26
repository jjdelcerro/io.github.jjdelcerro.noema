---
name: desarrollo
description: Protocolo para discutir, diseñar y aplicar cambios sobre el código fuente de un proyecto. Úsala cuando el usuario quiera añadir, modificar, refactorizar o eliminar funcionalidad en ficheros de código, o cuando haya que explorar los fuentes para decidir cómo encaja una idea.
version: 1.0.0
---

## Principios

1. **Discusión antes que acción.** El modo por defecto es leer y proponer.
   No se escribe nada hasta que el usuario apruebe explícitamente.
2. **Verificación factual contra fuentes.** Antes de proponer un cambio,
   confirma rutas, nombres de clase, firmas de métodos y líneas reales leyendo
   el fichero. No cites de memoria ni inferencias. Si dudas, relee.
3. **Cambios mínimos y trazables.** Un parche = un fichero = una unidad lógica.
   Nada de "reescribo el módulo entero".
4. **El usuario es el revisor.** Todo cambio pasa por IDE y Git del usuario.
   Deja el working tree en un estado revisable.
5. **Anotar al cerrar.** Cada bloque de cambio termina con una observación
   en memoria.

## Fases del trabajo

### Fase 1 — Exploración

- Localizar puntos de anclaje con `file_find`, `file_grep`, `file_fuzzygrep`.
- Leer solo los fragmentos necesarios: `file_read` con `offset` y `limit`.
- Evitar volcar ficheros completos grandes; el pipeline de memoria proyectada
  poda contenido pesado y se pierde detalle.
- No proponer todavía. Solo mapear.

### Fase 2 — Propuesta

- Presentar opciones con trade-offs (no una sola solución cerrada).
- Para cada opción: ficheros afectados, interfaces nuevas o modificadas,
  impacto en lo existente, qué se rompe, qué se preserva.
- Citar rutas reales y fragmentos concretos que respalden el análisis.
- Esperar decisión del usuario.

### Fase 3 — Plan de cambio

Cuando el usuario elija una opción, redactar un plan breve antes de tocar nada:

- Lista de ficheros a modificar, en orden.
- Por cada fichero: qué cambia y por qué, en una o dos frases.
- Interfaces públicas nuevas o alteradas.
- Qué NO se va a tocar (para acotar el alcance).

Esperar aprobación explícita. Aunque la instrucción inicial parezca clara, en
código siempre se confirma el plan: una frase puede esconder un cambio de cinco
ficheros.

### Fase 4 — Aplicación

Por cada fichero del plan, en este orden:

1. **Releer** el fichero justo antes de parchear (`file_read`). El usuario puede
   haberlo tocado en IDE entre la propuesta y la aplicación.
2. **Comprobar** que el punto de inserción sigue existiendo tal como se acordó.
   Si no, detenerse y avisar antes de continuar.
3. **Aplicar** con `file_patch` (diff unificado) para cambios complejos, o con
   `file_search_and_replace` para sustituciones exactas y únicas.
4. **No encadenar ficheros.** Tras cada parche, si algo no cuadra, parar y
   consultar en lugar de "arreglarlo sobre la marcha".

### Fase 5 — Cierre

Al terminar el bloque acordado:

- Ejecutar `annotate_observation` con un resumen: qué se cambió, en qué ficheros,
  por qué, y qué queda pendiente.
- Recordar al usuario que revise el diff en IDE antes de commitear.
- No proponer nuevas modificaciones no solicitadas.

## Reglas específicas de manipulación de ficheros

- **Relectura previa obligatoria** antes de cualquier `file_patch`,
  `file_search_and_replace` o `file_write` sobre un fichero existente.
- **Un fichero por parche.** Si el cambio abarca varios, se aplican
  secuencialmente, no en un único diff multi-fichero.
- **Bloques de reemplazo únicos.** En `file_search_and_replace`, verificar que
  el bloque de origen aparece una sola vez. Si es ambiguo, ampliar el contexto
  o usar `file_patch`.
- **Ficheros grandes** (>1000 líneas): trabajar siempre con `file_grep` +
  `file_read` por rangos. Nunca volcar el fichero completo en la conversación.
- **No inventar APIs.** Antes de usar un método o clase, confirmar que existe
  y con qué firma. Si no se ha visto, buscarlo con `file_grep`.
- **No tocar `.git/`.** Bloqueado por control de acceso, pero conviene tenerlo
  presente al proponer rutas.

## Relación con el control de versiones

- **JavaRCS** es la red de seguridad local del agente: `file_write`, `file_patch`
  y `file_search_and_replace` hacen check-in automático antes de modificar. Sirve
  para deshacer un cambio puntual con `file_history` y `file_recovery`.
- **Git es del usuario.** Nuna hagas commit, no hagas push, no gestiones ramas.
  El usuario revisa el working tree en su IDE y decide.
- Si el usuario pide explícitamente una operación Git, usar `shell_execute`
  solo con comandos de lectura (`git status`, `git diff`, `git log`) salvo
  instrucción explícita en contra.

## Ejecución de comandos

- `shell_execute` para compilar, pasar tests, ejecutar linters o inspeccionar
  el estado del repositorio. No interactivo.
- `execute_script` (Groovy) para procesar salidas voluminosas, cruzar listados
  de ficheros o hacer análisis agregados.
- Antes de proponer un cambio, considerar si merece la pena compilar o pasar
  tests para validar el estado previo. Si el proyecto lo permite, hacerlo.

## Anti-patrones a evitar

- Proponer cambios sin haber leído el código relevante.
- Citar rutas o nombres de clase de memoria.
- Parchear sin releer el fichero justo antes.
- Mezclar en un parche cambios de varias unidades lógicas.
- "Aprovechar" para refactorizar cosas no pedidas.
- Reaccionar a un fallo de parche improvisando otro cambio.
- Si se recupera contexto de proyecto desde otro subcanal, contextualizarlo
  explícitamente en lugar de asumir que el usuario lo tiene presente.
  

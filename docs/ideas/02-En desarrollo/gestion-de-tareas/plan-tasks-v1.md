

Le he suministrado al agente un fichero con una descripcion funcional y requisitos de diseño.
Despues le he dicho:
```
Prepara un plan para llevar a cabo esta implemenatcion.
Escribe este plan en un fichero "plan.md" y crea un fichero "plantasks.md" de forma que cada linea empiece con un numero de tarea seguido de un [ ] y la de descripcion de la tarea en una sola linea.
Luego procede con la ejecucion del plan. MUY IMPORTANTE tras cada tarea realizada actualiza el fichero "plantask.md",  revisa cual es la siguiente tarea a realizar, lee la descripcion de esta en el plan y ponte con ella.
```

Como ves esta forma de trabajar?

---

La veo **bien planteada como base**, pero **incompleta para un agente que ejecuta código de forma autónoma**. Es esencialmente un enfoque *plan-and-execute* con checklist, que suele funcionar mejor que pedirle directamente “implementa esto”. Le pondría un **7,5/10** y varios ajustes.

## Lo que está bien

- **Separas plan detallado (`plan.md`) y checklist operativa (`plantasks.md`)**. Es una buena división: el plan da contexto y la checklist da seguimiento.
- **Obligas a releer la tarea antes de ejecutarla**, lo que reduce la deriva y las omisiones.
- **Actualizas el estado tras cada tarea**, lo que da trazabilidad y permite retomar.
- **Formato de una tarea por línea** facilita que el agente sepa “dónde está” y qué sigue.
- **Ejecución iterativa tarea a tarea** es más segura que “hazlo todo de golpe”.

## Riesgos y puntos débiles

1. **No defines “hecho”**: el agente puede marcar `[x]` solo porque escribió código, sin pruebas ni verificación.
2. **No exiges evidencia**: falta registrar comandos ejecutados, tests, resultados o motivo de aceptación.
3. **Inconsistencia de nombres**: dices `plantasks.md` y luego `plantask.md`. Eso puede hacer que el agente actualice un fichero distinto.
4. **No hay estados intermedios**: solo `[ ]`/`[x]`; falta `[!] bloqueada`, `[~] en curso` o similar.
5. **No hay criterios de aceptación por tarea** en la checklist; la descripción en una línea puede ser ambigua.
6. **No hay control de cambios**: si el plan cambia, ¿se actualiza `plan.md`? ¿Se renumeran tareas? ¿Se conservan IDs?
7. **No hay paradas de seguridad**: el agente puede seguir aunque una tarea falle o requiera una decisión humana.
8. **No pides commits ni checkpoints**: difícil revisar y revertir.
9. **El plan puede ser demasiado grande** para el contexto del agente; conviene dividir por hitos.

## Mejoras concretas

- En `plan.md`, cada tarea debería tener:
  - ID estable (`T01`, `T02`…)
  - Objetivo
  - Pasos concretos
  - Archivos afectados
  - **Criterio de aceptación**
  - **Verificación** (test, comando, revisión manual)
  - Dependencias y riesgos
- En `plantasks.md`, usa formato consistente, por ejemplo:
  ```text
  T01 [ ] Implementar parser de configuración
  T02 [ ] Añadir validación de campos obligatorios
  ```
  Y estados: `[ ]` pendiente, `[~]` en curso, `[x]` hecho, `[!]` bloqueado.
- Añade una regla explícita: **no marcar `[x]` sin ejecutar la verificación y anotar evidencia**.
- Exige que, si una tarea falla, **no se marque como hecha**; se registre el bloqueo y se pare o se proponga solución.
- Define **checkpoint humano** cada cierto número de tareas o antes de cambios destructivos.
- Pide **un commit por tarea** o por grupo pequeño, con mensaje claro.
- Corrige el typo y usa **un solo nombre de fichero** en todo el prompt.
- Añade una regla de **fuente única de verdad**: `plan.md` manda; `plantasks.md` es una vista derivada. Si cambia el alcance, se actualizan ambos.

## Prompt que yo añadiría

```text
Reglas de ejecución:
1. plan.md es la fuente de verdad. plantasks.md es la checklist derivada.
2. Ejecuta solo la primera tarea no completada, salvo que esté bloqueada.
3. Antes de implementar, lee en plan.md el ID, objetivo, pasos,
   criterios de aceptación y verificación de esa tarea.
4. No marques una tarea como [x] hasta ejecutar su verificación
   y dejar evidencia (comando, test o revisión).
5. Si la verificación falla, no marques [x]; marca [!] y explica el bloqueo.
6. Si cambia el alcance, actualiza plan.md y plantasks.md manteniendo IDs.
7. Tras cada tarea, actualiza plantasks.md y revisa la siguiente.
8. No reordenes ni elimines tareas sin justificarlo.
```

## Conclusión

**La forma de trabajar es buena**, pero le falta lo más importante en un agente autónomo: **criterios de aceptación, verificación obligatoria y evidencia de que la tarea está realmente hecha**. Con esos añadidos, el flujo sería bastante sólido y auditable.


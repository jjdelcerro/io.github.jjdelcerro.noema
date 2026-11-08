
# Tareas

Documento de trabajo. La especificacion esta en `specs.md`, en esta misma
carpeta. Cada tarea termina indicando el TITULO de las secciones de ese documento donde
esta la informacion que la gobierna; el numero entre parentesis es solo una ayuda de
navegacion.

## Procedimiento

- Trabaja las tareas en orden, una a una. No empieces una sin haber cerrado la anterior.
- Al empezar, marca `[~]`. Al terminar, marca `[x]`.
- Antes de tocar codigo, lee las secciones indicadas. La tarea dice QUE hay que
  conseguir; el documento dice POR QUE y COMO.
- No improvises. Si la seccion indicada no cubre lo que la tarea pide, o entra en
  conflicto con el codigo actual, para y pregunta.
- Una tarea solo pasa a `[x]` cuando has contrastado la especificacion de esta con la
  implementacion y se adecua a ella. No se compila ni se arranca la aplicacion tras cada
  tarea: la prueba de arranque se agrupa al cerrar cada fase.
- Cada tarea cierra con dos comprobaciones baratas, sin compilar: todos los imports
  relativos del directorio resuelven, y no queda ninguna referencia a los simbolos que la
  tarea ha eliminado.
- No mezcles tareas de fases distintas ni adelantes trabajo de una fase posterior.
- Si al ejecutar una tarea descubres que la especificacion es erronea, corrige
  `specs.md` en el mismo movimiento y dejando constancian de que se ha corregido en el mismo documento.

## Como leer la especificacion

NO leas `specs.md` completo en cada tarea. Localiza y extrae solo las
secciones que la tarea cita, con el script `gettaskdoc.groovy`, que esta
en esta misma carpeta y recibe los titulos como variables:

```
execute_script(
  scriptFile = "/home/jjdelcerro/datos/devel/io.github.jjdelcerro.noema/plans/gettaskdoc.groovy",
  variables  = {"titles": ["Persistencia del estado"]}
)
```

Reglas de uso:

- Cambia solo `titles`. Si la tarea cita varias secciones, ponlas todas en la lista y
  se leen en la misma llamada.
- El script delimita la seccion por el siguiente encabezado de nivel igual o superior.
  Por eso no hay que pasar tamanos ni limites: el corte es exacto.
- Sin `titles`, el script devuelve el indice completo de encabezados, con linea de
  inicio y presupuesto en tokens. Usalo cuando no conozcas el titulo exacto.
- Si el script falla con `Ninguna seccion encontrada`, NO significa que la seccion no
  exista. Significa que el titulo no coincide. Prueba con un titulo mas corto, o pide
  el indice, antes de concluir nada.
- Un encabezado de nivel 2 arrastra sus subsecciones: `Contrato de componentes` son
  ~1.550 tokens y `Addon` ~500. Pide el nivel mas bajo que cubra lo que necesitas.
- El parametro `file` es opcional y por defecto apunta a `specs.md`. Si el
  proyecto se mueve, pasalo o ajusta `DEFAULT_FILE` en el script.
- El documento `specs.md` completo puede ser pesado.
  Leerlo entero en cada tarea multiplica por diez el coste sin aportar nada.

## Estados

- `[ ]` pendiente
- `[~]` en curso
- `[x]` terminada y verificada

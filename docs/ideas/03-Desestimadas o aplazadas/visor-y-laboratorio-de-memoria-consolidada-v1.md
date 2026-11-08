
# Visor y laboratorio de memoria consolidada

## Motivación

Las consolidaciones no están produciendo lo que deberían, y los defectos no son de detalle: no se trata de una cita que falta ni de un dato omitido, sino de problemas **estructurales** en el documento generado. Ese tipo de fallo no se detecta leyendo el documento solo, ni comparándolo con la consolidación anterior. Se detecta contrastándolo con los turnos que le dieron origen: si el relato afirma algo que los turnos no sostienen, o jerarquiza mal lo que sí ocurrió, ahí está la grieta.

El trabajo real, por tanto, tiene dos fases distintas. Primero **inspeccionar**: leer una consolidación junto a su evidencia para decidir si está sana o rota. Después **experimentar**: retocar el prompt del consolidador y comprobar si la variante corrige el defecto, generando una nueva consolidación sobre los mismos turnos y leyéndola contra la misma evidencia.

Hoy el prompt vive en un fichero, el resultado de una consolidación no se puede releer de forma cómoda, y no hay manera de probar variantes sin consolidar de verdad, con lo que eso implica para la memoria del agente. La herramienta cubre exactamente ese hueco: **una mesa de disección, no un explorador**. Una vez identificada una consolidación rota, no se vuelve a ella: se anota el hallazgo y se pasa a la siguiente.

## Principios de diseño

**El addon no persiste nada.** No guarda prompt, no guarda consolidaciones, no guarda pensamiento. Es lectura más simulación. No puede corromper la memoria del agente ni dejarla en un estado del que haya que salir después. Esa propiedad es la que permite usarlo a lo bruto, lanzando tiradas sin miedo.

**El sujeto es la consolidación seleccionada.** El laboratorio no lo sustituye: produce una vista efímera sobre él. La consolidación seleccionada sigue siendo la guardada aunque en el laboratorio haya una versión recién generada.

**La comparación es contra la evidencia, no contra el histórico.** No interesa qué decía la consolidación anterior ni en qué se diferencia de la nueva. Interesa si la nueva se sostiene con los turnos.

**Los mensajes son el input real.** El panel de mensajes no es contexto ilustrativo: es exactamente el material con el que se alimenta la tirada. Por eso muestra el mismo conjunto en inspección y en laboratorio.

## Distribución

Tres columnas constantes. La geometría no cambia entre modos; solo cambia la asignatura de las dos de la derecha.

```
┌───────────┬────────────────────────┬────────────────────────┐
│  LISTA    │  Memoria consolidada   │  Mensajes              │
│  ~240px   │                        │  (turnos de origen)    │
│  #12      │                        │                        │
│  #13      │                        │                        │
└───────────┴────────────────────────┴────────────────────────┘
                    ▲ INSPECCIÓN

┌───────────┬────────────────────────┬────────────────────────┐
│  LISTA    │ ▸ Prompt consolidador  │ [Mensajes][Pensamiento]│
│  ~240px   │   [Recargar][Consolidar]├────────────────────────┤
│  #12      │────────────────────────┤                        │
│  #13      │  Memoria generada      │  Contraste             │
│           │                        │                        │
└───────────┴────────────────────────┴────────────────────────┘
                    ▲ LABORATORIO
```

La lista es el ancla y no se mueve nunca. Las pestañas **Inspección** y **Laboratorio** son el conmutador de modo de toda el área de trabajo, no de un panel concreto.

En laboratorio, el prompt ocupa una banda plegable en la parte superior, cerrada por defecto, con los botones en su cabecera. La razón es de frecuencia de uso: el documento generado se lee párrafo a párrafo; el prompt se toca de vez en cuando y luego se relee. El prompt no debe robar altura al documento.

El panel de contraste usa **pestañas** (mensajes / pensamiento) y no columnas adicionales: cuatro columnas dejarían cada documento en un ancho ilegible.

## Comportamiento al seleccionar una consolidación

Al pulsar una fila de la lista se llenan simultáneamente los paneles de ambos modos, no solo los del visible:

- `inspeccion-memoria` — el texto guardado.
- `inspeccion-mensajes` — los turnos de origen.
- `laboratorio-memoria` — el mismo contenido que `inspeccion-memoria`.
- `laboratorio-mensajes` — el mismo contenido que `inspeccion-mensajes`.
- `laboratorio-pensamiento` — vacío.
- `laboratorio-prompt` — solo la primera vez; después **se mantiene** lo que hubiera.

Cambiar de fila devuelve el área a inspección. La tirada pertenece a la consolidación anterior y no se arrastra: mantener el modo con otra selección dejaría un documento generado junto a los mensajes de otra memoria, que es una trampa silenciosa.

## El prompt

Se carga una sola vez, al abrir el laboratorio por primera vez, y **sobrevive a los cambios de selección**. La variable del experimento es el prompt; las memorias son las muestras. Si el retoque se perdiera en cada fila, sería imposible probar la misma variante contra varias memorias, que es justamente el caso de uso: si una variante arregla una consolidación rota, se prueba en otra para ver si el arreglo es general.

Junto al botón de consolidar hay un botón de **recargar**, que cumple dos funciones: descartar la edición en curso y traerse el prompt vigente en disco. Su propio estado es el indicador de situación: **activo significa que lo que hay delante ya no es el canónico**. No hace falta ninguna otra señal de "modificado": si el botón está disponible, hay divergencia; si está atenuado, estás en el original.

El prompt **nunca se guarda desde el addon**. La vía para promover una variante es copiarla, editar el fichero fuente y recargar. El addon propone, el fichero dispone.

## Indicadores

**Tic en la pestaña de inspección** cuando se ha lanzado una consolidación para la selección actual. Su significado no es el del editor: el panel de inspección no se modifica nunca, siempre muestra la guardada. Lo que indica es que *hay una tirada viva al otro lado*. Vive atado a la selección y muere con ella; sirve para no perder el hilo al conmutar de pestaña dentro de la misma memoria, no para llevar la cuenta entre memorias.

**Distinción entre las dos memorias**: basta con que cada una viva en su pestaña, con su cabecera. No hace falta nada más para no confundirlas.

## El pensamiento

Es efímero y de naturaleza distinta al documento: un chorro de razonamiento, no una pieza redactada. Se vacía al cambiar de selección y **se conserva al conmutar de pestaña** dentro de la misma, que es lo único que permite volver sobre él mientras se relee el resultado. No es información crítica: es una pista sobre por qué el documento no dice lo que se esperaba. Su ausencia —cuando el modelo configurado no emite razonamiento— debe presentarse como un estado normal, no como un error.

## La lista

Es el indice del addon y el ancla de todo lo demas: no se mueve nunca y gobierna la carga de los paneles.

Con 127 entradas hoy y unas 20 nuevas al dia, el volumen no es un problema de rendimiento —los metadatos de 127 consolidaciones son un punado de kilobytes— sino de **orientacion**. Lo que hace inutil una lista no es su tamano, es no poder reconocerla de un vistazo. Por eso:

- **Orden descendente por id.** Lo mas reciente arriba. Gratis, y es lo que se busca el 90% de las veces.
- **La fila muestra el tramo de esa pasada, no el rango acumulado.** El `first` se hereda de la consolidacion anterior, de modo que casi todas las filas acabarian diciendo "desde el turno 1". Lo que informa es que turnos entraron en esa consolidacion concreta: `#94 · turnos 1180-1224`.
- **Filtro por subcanal.** Aunque hoy solo haya un terminal de pruebas, la lista debe poder aislar un canal: mezclar conversaciones distintas sin aviso es una fuente de diagnosticos falsos.
- **Filtro por id en el cliente**, sobre las filas ya cargadas. "Quiero ver la consolidacion del turno 4100" es el caso de uso real y se resuelve comparando sobre un array en memoria, sin tocar el backend.
- **Sin paginacion, sin scroll infinito, sin virtualizacion.** No resuelven ningun problema actual y anaden estado (posicion de scroll, restauracion, medicion de alturas) que hay que mantener.

Aunque la UI no pagine, **el contrato del endpoint nace preparado para hacerlo**: acepta `offset`, `limit` y `order`, y la respuesta incluye `total`. Cuesta cero hoy y evita redisenar el contrato dentro de seis meses, cuando el default se baje y se anada un "cargar mas" sin tocar a los consumidores.

## Triaje

No vive en el addon. El estado de revision de cada consolidacion se deja constancia en la pestana de **notas** del workspace: se crea una nota y se anota que problema estructural se detecto. El addon no guarda estado, y no debe duplicar un mecanismo que ya existe.

De ahi se derivan dos requisitos. El primero, que **el identificador sea copiable de un solo gesto**, desde la lista o desde la cabecera: si hay que teclearlo, el cruce se degrada y la constancia se deja de escribir. El segundo, que el puente entre el addon y la nota sea un gesto y no un procedimiento manual; ver **Ir a nota** en Mejoras futuras.

## Flujo de trabajo

1. Recorrer la lista.
2. Abrir una consolidación, leer el documento y contrastarlo con los mensajes. Detectar si hay problema estructural.
3. Si lo hay, dejar constancia en notas y pasar al laboratorio.
4. Retocar el prompt, consolidar, leer la nueva contra **los mismos mensajes**. Consultar el pensamiento si el resultado no cuadra con lo esperado.
5. Si la variante funciona, probarla contra otra consolidación rota sin reeditarla.
6. Si se confirma que es mejor que la vigente, copiarla al fichero fuente y recargar.
7. Volver a la lista y seguir con la siguiente.

## Decisiones descartadas

- **Panel de estado de triaje dentro del addon**: las notas ya cubren esa función.
- **Diff contra la consolidación anterior**: la comparación es contra la evidencia, y la guardada solo sirve de disparadero.
- **Cuatro columnas**: el contraste va con pestañas.
- **Persistencia de cualquier tipo**: ni prompt, ni consolidaciones generadas.
- **Degradaciones por datos incompletos**: se asume que una consolidación tiene todos sus mensajes y su texto. Si la base de datos se ha manipulado y está rota, la herramienta fallará, y eso es aceptable.

## Endpoints

Cinco rutas, siguiendo el convenio del proyecto (`/api/<recurso>`, `{id}` en path). Los métodos de servicio que falten —listado completo de consolidaciones, cálculo del predecesor, consolidar con prompt de sistema por parámetro— se añaden a la capa de memoria episódica y al servicio de consolidación; aquí solo se fija el contrato de la superficie web.

| Método | Ruta | Devuelve |
|---|---|---|
| `GET` | `/api/checkpoints?subchannel=&offset=&limit=&order=` | Indice: `id`, `first`, `last`, `timestamp`, `subchannel` y `total`. Sin texto. Preparado para paginar aunque la UI cargue todo. |
| `GET` | `/api/checkpoints/{id}` | Detalle: metadatos, `text`, predecesor (o `null`) y rango de turnos de esa pasada. |
| `GET` | `/api/checkpoints/{id}/turns.xml?scope=new\|all` | Turnos en el mismo XML de conversacion que sirve la pestana Contexto del chat, listo para transformar con la hoja `conversation.xsl`. |
| `GET` | `/api/checkpoints/prompt` | Prompt de sistema vigente, leido de disco en el momento de la peticion, mas su ruta. |
| `POST` | `/api/checkpoints/{id}/consolidate` | Cuerpo: `systemPrompt` (opcional) y `scope`. Devuelve `thinking`, `text`, duracion y citas invalidas. **No persiste por contrato**: esta ruta nunca escribe en la memoria episodica. |

Por que XML y no JSON en los turnos: permite reutilizar sin duplicar el formateo de la pestana Contexto —hoja XSL cacheada y transformacion en cliente— y garantiza identidad visual con lo que ya se ve en el chat, en lugar de escribir un segundo render de turnos que con el tiempo divergiria del primero.

## Mejoras futuras

### Ir a nota

Un boton que convierta el puente entre el addon y las notas en un solo gesto, en lugar del procedimiento manual de copiar el identificador y crear la nota a mano.

Comportamiento: busca en las notas con la etiqueta `memory-checkpoint`; entre ellas, la que tenga el identificador de la consolidacion en el titulo. Si existe, navega a ella. Si no existe, la crea con una plantilla y navega.

Condiciones para que funcione y no genere ruido:

- **Mejorar el endpoint de busqueda de notas.** Hoy admite el modo `tags` y busquedas por texto, pero no la combinacion de ambos. Necesita poder filtrar por etiqueta y, dentro del resultado, resolver una coincidencia sobre el titulo.
- **Identificador con prefijo y delimitador en el titulo**, del tipo `CP-94`, con coincidencia exacta sobre el token. Buscar `94` a secas casa tambien con `194` y `940`, y el boton acabaria abriendo el expediente equivocado.
- **La nota nueva se crea con plantilla, no vacia.** Cabecera con identificador, tramo de turnos, fecha y canal, de modo que solo quede escribir el diagnostico. Un boton que crea notas en blanco produce basura a la tercera pulsacion.
- **El vinculo por titulo es fragil**: si la nota se renombra, el boton deja de encontrarla y creara una segunda. Es aceptable mientras se sepa, pero conviene valorar si el identificador debe vivir en el cuerpo de la nota con una marca reconocible en lugar de en el titulo.
- **Sin acoplamiento entre addons.** El addon de puntos de guardado no debe importar el editor de notas: el salto se resuelve publicando un evento que el workspace enrute, igual que el arbol del explorador ya no importa `openFile` y delega en su addon.


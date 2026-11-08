# Refinamiento de turnos en el consolidador

## Motivacion

La memoria consolidada no es un archivo del historial: es un relato. Su valor
esta en que alguien pueda leerla dentro de seis meses y reconstruir que se
decidio, por que y bajo que reglas. Todo lo que no contribuya a eso es ruido,
y el ruido tiene un coste doble: ocupa atencion del modelo que consolida y,
peor aun, fosiliza informacion que por naturaleza caduca.

Hoy el consolidador recibe, turno a turno, el contenido integro que llega desde
la memoria episodica. Eso incluye dos familias de material que no deberian
formar parte del punto de guardado:

- Notas de trabajo que describen el estado momentaneo de una tarea o el mapa
  del codigo en un instante concreto. Son utiles mientras se trabaja y dejan de
  serlo en cuanto el trabajo avanza. Si entran en la consolidacion, la
  siguiente consolidacion las leera como hechos vigentes cuando ya no lo son.
- Volcados de herramientas de exploracion. El resultado crudo de una busqueda o
  una lectura es materia prima, no conclusion. El consolidador no gana nada
  leyendo las coincidencias de un grep, y pierde foco al tejer el relato.

El objetivo es que a la memoria consolidada solo llegue lo que tiene valor
narrativo e historico, sin que eso suponga perder nada: el material descartado
sigue vivo, integro o recortado segun su propia politica, en las capas
anteriores de memoria y sigue siendo recuperable por las vias de siempre.

## Alcance

El cambio se aplica en un unico punto: la construccion del texto que se envia
al modelo consolidador a partir de los turnos que llegan desde la memoria
episodica.

Queda fuera de este trabajo:

- La memoria episodica. Lo que se guarda alli no cambia: las anotaciones viven
  completas y los resultados de herramientas de ingesta viven recortados segun
  la politica que ya tienen.
- La memoria reciente y la memoria proyectada, donde el agente ve los turnos
  completos.
- La ruta de calculo de embeddings, que tiene su propio problema de volumen y
  merece su propia decision.
- Cualquier forma de persistencia. Este refinamiento es efimero: no altera lo
  que esta guardado, solo lo que se le muestra al consolidador.

## Que se conserva y que se omite

### Anotaciones, por categoria

Cada anotacion declara una categoria que describe que clase de conocimiento
contiene. La regla es directa:

- Se omite el contenido de las notas de trabajo y del mapa del proyecto. Son
  descripciones del presente operativo, no historia.
- Pasan integras las que recogen reglas que no se pueden violar, las razones
  por las que se eligio un camino frente a otro, y las sintesis de documentos
  conceptuales. Eso es exactamente lo que hay que poder releer con el tiempo.

Una nota omitida no desaparece: sigue constando como existente, con su origen,
su categoria y el recurso al que apunta. Lo unico que se sustituye es su texto.
El consolidador debe poder distinguir entre "no hubo ninguna nota" y "hubo una
nota de este tipo y su contenido se omitio a proposito", porque en el primer
caso no hay nada que narrar y en el segundo si.

Las anotaciones anteriores a la existencia del campo de categoria pasan
integras. El descarte debe ser consecuencia de una clasificacion declarada, no
el resultado de un campo ausente; castigar retroactivamente un defecto de
esquema del pasado seria borrar contenido por la puerta de atras.

### Resultados de herramientas de ingesta

El resultado crudo de una busqueda, un listado o una lectura se sustituye por
una marca que indica que hubo contenido y que fue omitido. Se conserva la
invocacion, es decir, que se busco y donde, porque eso describe la intencion
del agente y si tiene valor narrativo. Lo que se descarta es el volcado.

El recorte lo decide la propia herramienta. Es la unica pieza que sabe
distinguir su cabecera de su cuerpo y la unica que puede hacerlo de forma
coherente cuando manana aparezca una herramienta nueva de la misma familia. El
consolidador no debe replicar ese conocimiento ni mantener una lista paralela
de casos.

Cuando la herramienta conserva su cabecera, la marca hereda las coordenadas
del recurso: que se leyo, cuantas lineas tenia y donde quedo el bloque. Es mas
util para el consolidador que un hueco, porque le permite citar sin inventar.

## Comportamiento esperado

1. Una nota de trabajo llega al consolidador como nota existente pero sin
   contenido, conservando su origen, su categoria y su recurso.
2. Una nota de regla, decision o sintesis llega integra, sin transformacion.
3. El resultado de una herramienta de ingesta llega como marca de contenido
   omitido, conservando la invocacion que lo genero.
4. Ningun turno desaparece. Se conserva la existencia y el identificador de
   cada uno, porque las citas que emita el consolidador deben seguir siendo
   validas y porque el turno completo sigue estando disponible en la memoria
   episodica para quien quiera profundizar.
5. Lo que dijo el agente no se toca. El refinamiento actua sobre el resultado
   de la herramienta y sobre el texto de la nota, no sobre el razonamiento ni
   sobre la respuesta.
6. Si un contenido ya llega marcado como omitido, no se vuelve a marcar. La
   operacion no debe acumular marcas ni degradar el texto en pasadas sucesivas.
7. El consolidador tiene que poder interpretar la marca. Una marca que no
   entiende es tan inutil como el ruido al que sustituye, y en el peor caso la
   tomara por contenido.

## Efectos esperados

- Menos ruido en el punto de guardado y mas atencion dedicada a lo que de
  verdad construye el relato.
- Puntos de guardado mas estables entre consolidaciones: el mismo hecho no se
  reescribe en cada pasada porque ya no arrastra consigo el estado momentaneo
  de la tarea.
- Ninguna perdida de informacion respecto a hoy, porque el material descartado
  permanece accesible en las capas anteriores.

## Decisiones cerradas

1. Criterio de recorte: no hay umbral de tamano. Se recorta por naturaleza de
   la herramienta. Si declara tipo operativo, su resultado pasa por el recorte
   que ella misma define; las herramientas de memoria y las anotaciones quedan
   fuera de esta via.

   Consecuencia que conviene tener presente: el tipo por defecto de una
   herramienta es operativo. Toda herramienta que no declare tipo de forma
   explicita sera tratada como podable. Es el comportamiento deseable para la
   familia de ingesta y evita mantener una lista de nombres que se quedaria
   corta a la primera herramienta nueva.

2. La poda interna de cada herramienta es responsabilidad suya. El consolidador
   delega y no valida el resultado: si la herramienta no puede recortar, el
   texto pasa tal cual.

3. La proteccion contra el doble recorte vive en el recortador, no en el
   llamante. La pieza que poda comprueba si el contenido ya lleva la marca y,
   si la lleva, no hace nada. Asi la operacion es segura ante llamadas
   repetidas sin que quien la invoca tenga que saber nada.

4. El consolidador resuelve la herramienta por nombre a traves del servicio de
   razonamiento del agente. Si no la encuentra, o si el servicio no esta
   disponible, no se recorta.

5. La marca de la nota omitida es un texto fijo, sin la categoria. La categoria
   ya viaja en su propio campo y repetirla solo anade ruido.

## Diseno

Esta seccion no pertenece al analisis: recoge las decisiones de construccion
acordadas una vez cerrado el comportamiento. Lo anterior describe que se quiere;
esto describe como se va a organizar.

### Punto de intervencion

Un metodo nuevo en el servicio de consolidacion, invocado desde `consolide()`
con la lista de turnos que llegan de la memoria episodica. Devuelve una lista
nueva.

### Contrato de la lista devuelta

- Misma longitud y mismo orden. El punto de guardado toma el primer y el ultimo
  turno para fijar su rango; una lista mas corta lo rompe en silencio.
- Los turnos que no se modifican se devuelven tal cual, sin copiarlos.
- Los turnos que se modifican se devuelven como instancias nuevas.
- Ningun turno procedente de la memoria episodica se muta. Son objetos de otra
  capa y pueden volver a escribirse.

### Que se modifica en cada rama

Las dos ramas no tocan el mismo campo, y confundirlas deja la poda sin efecto:

- Anotaciones: el contenido vive en el `tool_call`, en el argumento de la nota.
  El `tool_result` solo contiene el acuse. La poda actua sobre el `tool_call`.
- Herramientas de ingesta: el contenido vive en el `tool_result`. El `tool_call`
  lleva la invocacion y se conserva, porque describe la intencion del agente.

### Delegacion del recorte

El consolidador no replica la logica de recorte. Resuelve la herramienta por
nombre a traves del servicio de razonamiento del agente y le delega la poda. Si
no la encuentra, o si el servicio no esta disponible, el texto pasa intacto.

La proteccion contra el doble recorte vive en la propia pieza que recorta:
antes de podar comprueba si el contenido ya lleva la marca y, si la lleva, no
hace nada. Quien invoca no necesita saberlo.

### Creacion de los turnos modificados

La interfaz de turno expone dos operaciones de copia con sustitucion:

```java
Turn withToolCall(String toolCall);
Turn withToolResult(String toolResult);
```

Cada una devuelve una instancia nueva con todos los campos del original y solo
el indicado sustituido. Se descartaron dos alternativas:

- `clone`: obliga a ampliar la visibilidad de un metodo de `Object`, exige una
  interfaz marcadora vacia, no pasa por el constructor y sugiere una copia
  exacta, que no es lo que se necesita.
- `from(Turn)`: declarado en la interfaz introduce una dependencia de la API
  hacia la implementacion, y sin campos parametrizables no permite cambiar nada.

La copia vive junto al tipo, de modo que un campo nuevo en el turno obligue a
revisar un unico sitio.

### Identidad de los turnos

Ambas variantes conservan el identificador del turno original. Se abandona la
idea de dejar sin identidad a la nota omitida: no aporta nada que la copia fiel
no de ya, y elimina la dependencia con el calculo del rango del punto de
guardado.

### Pendiente de cerrar antes de escribir codigo

- La redaccion del prompt del consolidador para que interprete las marcas de
  contenido omitido. Pospuesto de forma explicita: tocar ese prompt arrastra al
  resto de cambios pendientes y se aborda al final.

## Calculo del rango sobre la lista refinada

Se usa la lista nueva para calcular tanto el rango del punto de guardado como el
conjunto de identificadores citables. Es seguro porque el refinamiento conserva
los identificadores y no altera ni el numero ni el orden de los turnos.

Queda una dependencia implicita que conviene tener escrita: las herramientas que
emiten citas dentro de su resultado son de tipo memoria y, por tanto, no se
recortan. Si alguna vez una herramienta operativa emitiera citas, sus
referencias dejarian de contarse al calcular sobre la lista refinada.

## Fases de implementacion

### Fase 1. Contrato de copia en el turno

Declarar en la interfaz de turno las dos operaciones de copia con sustitucion y
llevarlas a la implementacion, copiando todos los campos del original.

Verificacion: un turno con todos sus campos poblados, copiado con cada uno de los
dos metodos, conserva identidad, marca de tiempo, subcanal y demas campos, y solo
cambia el que se indica. No depende de nada externo.

### Fase 2. Idempotencia del recorte

Incorporar en el recortador de la herramienta paginada la comprobacion de la
marca de contenido omitido, de modo que una segunda pasada no haga nada.

Verificacion: aplicar la operacion dos veces sobre el mismo texto produce
exactamente el mismo resultado que aplicarla una. Independiente de las fases de
consolidacion; se puede cerrar antes que estas.

### Fase 3. Rama de anotaciones

Que el refinador reconozca los turnos de anotacion, lea la categoria del
`tool_call` y sustituya el contenido de la nota por la marca cuando proceda.

Verificacion: las notas de trabajo y de mapa del proyecto salen vaciadas
conservando origen, categoria y recurso; las de regla, decision y sintesis salen
intactas; las anotaciones sin categoria salen intactas.

### Fase 4. Rama de herramientas

Que el refinador reconozca los resultados de herramientas operativas, resuelva la
herramienta por nombre y le delegue el recorte.

Verificacion: el resultado de una busqueda llega recortado con su marca; los
resultados de herramientas de memoria llegan intactos; si la herramienta no se
resuelve o el servicio de razonamiento no esta disponible, el texto pasa sin
tocar.

### Fase 5. Integracion en la consolidacion

Invocar el refinador desde el punto de entrada de la consolidacion y construir el
prompt con la lista devuelta.

Verificacion: la consolidacion resultante no contiene notas de trabajo ni
volcados de herramientas, y el rango del punto de guardado sigue siendo correcto.

### Fase 6. Prompt del consolidador

Que el modelo interprete las marcas de contenido omitido en lugar de tomarlas por
contenido o ignorarlas. POSPUESTA: se aborda al final, junto al resto de cambios
pendientes en los prompts.

Sin esta fase las anteriores son funcionales pero opacas: el contenido deja de
llegar, pero el modelo no sabe por que.

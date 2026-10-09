# Descarga de contexto bajo demanda del LLM



## Nota preliminar

Este documento no describe un mecanismo ya implementado ni una decisión cerrada. Recoge el razonamiento seguido a partir de una observación externa —un vídeo sobre consumo de tokens en agentes tácticos— y las conclusiones a las que llegamos al contrastarla con la arquitectura de este agente. El objetivo es dejar constancia del análisis, de por qué se descartó la solución que el vídeo propone, y de qué mecanismo alternativo emerge como coherente con el diseño existente. Queda aparcada su implementación, no su formulación.

---

## I. Antecedentes

### 1.1 El origen de la discusión

Un vídeo divulgativo sobre Claude Code describe siete "fugas" por las que se consume el presupuesto de tokens de una sesión. La más llamativa, y la que motivó esta discusión, es la séptima: la primera respuesta después de una pausa larga. La caché de prompt de los proveedores expira tras un intervalo (típicamente una hora). Si el usuario se ausenta más tiempo y vuelve a la misma sesión, el sistema debe reprocesar la conversación entera desde cero y volver a almacenarla. En una sesión de horas, eso puede suponer reprocesar cientos de miles de tokens de golpe, con un coste equivalente a decenas de respuestas normales.

La recomendación del vídeo es sencilla: antes de irse, cerrar la tarea. Pedir al agente que anote lo hecho y lo pendiente, y vaciar el contexto. Al volver, arrancar una sesión limpia que lee esas notas. El coste se reduce de una relectura masiva a la "mochila" inicial más unas pocas líneas.

### 1.2 El contexto en el que se propone

Esa recomendación tiene sentido en el paradigma del **ejecutor táctico**, que es el que el vídeo describe. En un ejecutor táctico, la sesión coincide con la tarea. El usuario abre, pide algo acotado, lo resuelve, cierra. La pausa larga coincide con el fin de la tarea, porque no hay tarea que sobreviva a la sesión. Limpiar al terminar es, literalmente, terminar.

### 1.3 La pregunta que se plantea

Trasladada a este agente, la recomendación no encaja sin más. Aquí la sesión no coincide con la tarea. La pausa es solo tiempo transcurrido, y el tiempo no dice nada sobre si el hilo operativo sigue vivo. El usuario puede volver al día siguiente para continuar exactamente lo que estaba haciendo, o para empezar algo completamente distinto. Y puede que ni él lo sepa hasta que intercambie un par de mensajes. Consolidar o vaciar en la pausa es decidir por él con información que no se tiene.

La pregunta que se abre, entonces, no es si hay que limpiar en la pausa. Es si conviene **consolidar cuando el LLM percibe un cambio de tema**, con independencia del tiempo transcurrido.

---

## II. Por qué se descarta la consolidación precipitada

### 2.1 El coste real de consolidar

Consolidar no es barato. Implica tomar la conversación acumulada y la crónica vigente, enviarlas a un modelo especializado con un prompt de consolidación, y producir una nueva crónica. Es una llamada completa sobre el contexto entero, más la generación del texto narrativo resultante.

### 2.2 El coste de no consolidar

No consolidar también tiene coste: los turnos siguen en la memoria reciente y se reprocesan en cada inferencia. Pero ese reprocesamiento se hace contra la caché de prompt, no contra el precio completo.

### 2.3 La asimetría de precios

Los precios oficiales de la API de DeepSeek ilustran la magnitud de la diferencia. A modo de referencia off-peak:

| Concepto | Precio por millón de tokens |
|---|---|
| Entrada con cache hit | ~$0.003 |
| Entrada con cache miss | ~$0.15 |
| Salida | ~$0.60 |

La lectura desde caché cuesta aproximadamente **cincuenta veces menos** que la lectura sin caché. En horario peak los valores se duplican, pero la relación se mantiene.

Esto significa que, una vez que el contexto está en caché, el coste marginal de mantenerlo ahí es prácticamente despreciable. Consolidar para reducirlo no ahorra nada relevante: la propia operación de consolidar implica leer ese mismo contexto a precio de cache miss o completo, más el coste de generar la crónica.

### 2.4 La conclusión económica

Con caché de prompt barata, la consolidación no se justifica como estrategia de ahorro de tokens. Solo se paga si la reducción del tamaño de la proyección, multiplicada por los turnos futuros esperados, supera su coste. Eso exige del orden de quince a veinte turnos futuros con la proyección reducida para amortizarla. Un cambio de tema no garantiza esos turnos: el usuario puede no volver a ese hilo nunca, o terminar la sesión ahí.

Por tanto: **casi nunca conviene consolidar por cambio de conversación**. El criterio económico no es semántico, es estructural. Consolidar merece la pena cuando la proyección ha crecido lo suficiente como para que incluso las lecturas de caché pesen, y se esperan muchos turnos por delante. Eso ya lo mide la cadencia periódica del arnés. La percepción semántica del LLM no añade información económica.

---

## III. Por qué no se puede simplemente podar la proyección

### 3.1 La tentación de la solución barata

Si consolidar es caro, la alternativa evidente parece ser podar la proyección sin consolidar: dejar de proyectar turnos antiguos, sin tocar la crónica. El coste sería nulo y el efecto el mismo: menos tokens en la proyección.

### 3.2 El problema de coherencia entre estratos

Esa solución rompe la propiedad que sostiene el sistema. Las cuatro memorias son **vistas de la misma información con distinto nivel de detalle**. La memoria consolidada es el mapa de lo que ya no está en la reciente. Si la proyección elimina turnos que la consolidada no narra, el mapa miente.

Las consecuencias son tres:

1. **Inconsciencia del hueco.** El LLM no sabe que perdió acceso a algo. La crónica no lo menciona porque la consolidación aún no ocurrió. Opera como si su pasado fuera continuo, y no lo es.
2. **Reaparición diferida.** En la siguiente consolidación, la crónica narra lo que había desaparecido. El LLM recibe de golpe conocimiento que no intervino en los turnos intermedios.
3. **Pérdida de trazabilidad.** Las referencias de la crónica apuntan a turnos que la proyección ya no tiene. El ciclo de rehidratación se rompe.

Un agente cuyo mapa miente está disociado. No es una optimización: es un trastorno.

### 3.3 La distinción que salva el problema

Hay una diferencia crucial entre **podar turnos** y **podar cuerpos de herramientas dentro de turnos**.

- Podar un turno elimina el mensaje del usuario, la respuesta del agente, la llamada y su resultado. El hilo narrativo desaparece.
- Podar el cuerpo de una herramienta sustituye el volcado masivo por una marca de metadatos. El turno sigue existiendo: se ve qué se llamó, con qué argumentos, y que el resultado fue truncado.

La amnesia controlada de datos crudos ya opera así. No borra turnos: trunca cuerpos. La conversación permanece íntegra; solo desaparece el andamio.

Eso es seguro porque **la respuesta al usuario ya contiene las conclusiones**. Si el agente respondió la pregunta y cambió de tema, el dato crudo que la produjo es andamio, no estructura. Lo que el agente concluyó ya lo transmitió. La anotación nunca fue para preservar todo, sino para lo que no está en la respuesta y no es re-obtenible. Y eso suele ser vacío cuando el tema ha cerrado.

La conclusión es que **se puede adelantar la poda de cuerpos sin romper el mapa**, siempre que no se toquen los turnos. Lo que no se puede es adelantar la consolidación.

---

## IV. El mecanismo propuesto: herramienta de descarga

### 4.1 Formulación

Se introduce una herramienta que el LLM puede invocar para señalar que ya no necesita los datos crudos de las herramientas presentes en su contexto. La herramienta no ejecuta la poda: **registra una frontera**. La poda se materializa en la siguiente reproyección, que es donde ya ocurren todas las transformaciones de la memoria proyectada.

La herramienta no acepta argumentos en su forma básica. El LLM la invoca cuando considera que el tema ha cerrado y los cuerpos crudos anteriores ya no le hacen falta. El arnés registra el identificador absoluto del turno actual como **watermark**, y devuelve una confirmación de éxito.

### 4.2 La operación de poda

El pipeline de proyección incorpora una operación adicional que actúa sobre los turnos cuyo id es anterior al watermark vigente. Para cada uno, si contiene resultados de herramientas con cuerpo masivo, sustituye ese cuerpo por una marca de metadatos. Los turnos no se eliminan. Las cabeceras de las llamadas permanecen.

La operación es:

- **Monótona.** Cada invocación solo puede avanzar la frontera. Un watermark posterior nunca retrocede a uno anterior.
- **Idempotente.** Podar dos veces el mismo cuerpo no tiene efecto la segunda vez.
- **Compatible con la poda por edad.** La zona de seguridad por envejecimiento sigue existiendo. La frontera del watermark se suma a ella: se poda lo que quede fuera de cualquiera de las dos.
- **Sobrevive a la consolidación.** El watermark es un id absoluto de turno, no una posición en la memoria reciente. La consolidación reescribe la crónica, no los identificadores. La frontera sigue siendo válida después de cada ciclo.

### 4.3 Interacción con la rehidratación

La recuperación de un turno pasado no inserta un turno nuevo en la memoria reciente. Es una llamada a herramienta cuyo valor de retorno es el turno completo recuperado, y ese contenido vive como salida de herramienta dentro del turno actual. No tiene id propio que pueda quedar por detrás del watermark. Por tanto, no hay riesgo de que la rehidratación se pode inmediatamente al entrar en la proyección.

### 4.4 Ámbito multi-subcanal

Cada subcanal —terminal, TUI, sesión web, vía de mensajería— mantiene su propia memoria reciente, consolidada y proyectada. Solo la memoria episódica es compartida. El watermark vive en la memoria reciente del subcanal donde se invocó, y la poda se aplica solo a la proyección de ese subcanal. No hay contaminación cruzada entre terminales.

### 4.5 Lo que no cambia

La poda por edad sigue existiendo como red de seguridad por si el LLM nunca invoca la herramienta. Si no la usa, el sistema funciona exactamente como hasta ahora. Si la usa, adelanta lo que iba a ocurrir igual. Eso la hace segura por construcción: el peor caso es que no se use.

---

## V. Cuestiones abiertas

### 5.1 La instrucción en el prompt del sistema

La dificultad principal no está en el mecanismo, sino en cómo instruir al LLM para que lo use. El problema es epistémico: **si necesitas algo, lo sabes; si no lo necesitas, no sabes si lo vas a necesitar en dos turnos**. La instrucción no puede ser "llama a la herramienta cuando cambies de tema", porque eso invita a liberar datos irreversibles que quizá se necesiten, o a no invocarla nunca por aversión al riesgo.

La instrucción tendría que anclarse en algo más concreto que "cambio de tema", y conviene que diferencie por reversibilidad: los ficheros se releen, las salidas de terminal no. El sesgo debe ser hacia no liberar, porque el coste de no hacerlo son tokens de caché, y el de hacerlo de más es pérdida de datos.

Este punto queda aparcado. Se documenta el mecanismo, no su pedagogía.

### 5.2 Confirmación del protocolo

Si el LLM emite la llamada junto a su respuesta al usuario en el mismo turno, no hay inferencia extra. El arnés ejecuta la operación, inyecta la confirmación y espera al siguiente mensaje del usuario. Si la API exige un turno de asistente después de un resultado de herramienta, el arnés inyecta una confirmación mínima —el mismo patrón que ya se usa en el engaño protocolario— y no invoca al LLM hasta que el usuario hable. La poda se aplica a la proyección del turno siguiente, no a la respuesta que se está generando.

---

## Anexo: poda selectiva por identificador de llamada

### A.1 Motivación

La herramienta de descarga descrita opera por barrido: todo lo anterior al watermark deja de proyectarse con cuerpo. Es útil cuando el LLM cierra un tema entero, pero no permite liberar una lectura pesada concreta sin comprometerse con todo lo que vino antes.

### A.2 Extensión

Se contempla la posibilidad de que la herramienta acepte, como parámetro opcional, una lista de identificadores de llamada a herramienta. En ese caso, la poda se aplica solo a esas llamadas, con independencia de su posición relativa al watermark.

### A.3 Propiedades

- **No compite con el watermark.** El watermark es monótono y acumulativo. La lista es puntual y no avanza ninguna frontera.
- **Alcanza dentro de la zona de seguridad.** Permite podar una llamada específica en medio de la memoria reciente sin podar las que la rodean. Aporta granularidad donde antes solo había un mecanismo de todo-o-nada.
- **Idempotente.** Podar dos veces la misma llamada no tiene efecto la segunda vez.
- **Redundante pero inofensiva.** Si el LLM invoca la lista con ids de turnos ya cubiertos por el watermark, la operación no produce error.
- **Interacción con la consolidación sin cambios.** La crónica narra lo que sobrevive en la reciente. Si el LLM podó un cuerpo sin anotar, esa información no llega a la crónica, igual que ya ocurría con la poda por edad.

### A.4 Consecuencia de diseño

Con esta extensión, el LLM dispone de tres formas de liberar contexto:

1. Dejar que envejezca y salga de la zona de seguridad.
2. Avanzar el watermark para podar todo lo anterior.
3. Podar llamadas concretas por identificador.

Las tres respetan la misma invariante: **se truncan cuerpos, nunca turnos**. Y las tres dejan la decisión de qué es prescindible en el único que puede saberlo, sin que el arnés tenga que interpretar nada.

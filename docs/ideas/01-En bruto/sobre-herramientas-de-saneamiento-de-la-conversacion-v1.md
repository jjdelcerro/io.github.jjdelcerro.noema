
# Sobre herramientas de saneamiento de la conversacion

**User**

Anoto el cuarto caso, respuesta a tool sin llamada.
Sobre donde ponerlo, tienes razon, parece que pueda no ser de la memoria proyectada, pero no deberia ponerse despues de esta, si no antes. Errores como estos pueden causar que las operaciones de la memoria proyectada den resultados validos. Probablemente sea una "herramienta" que usa la memoria proyectada antes de ponerse ha hacer nada, pero como bien comentas no incrustandola en la memoria proyectada si no dandole entidad propia, con su clase y sus operaciones.

---

**Model**

Tienes razón en el «antes», y el motivo es más fuerte de lo que yo había considerado: **el daño de un historial inválido no es solo el 400**. Es que todo lo que se construye encima trabaja sobre material roto. Una destilación que se encuentra un `tool call` sin respuesta no falla: o lo ignora, o lo narra mal, y el resultado **se consolida y se persiste**. Un hueco de protocolo se convierte en una incoherencia semántica. Exactamente lo que vimos ayer con el fallo de red convertido en frase del modelo, pero un piso más abajo.

Así que: sanear **antes** de la proyección. De acuerdo.

Pero no en lugar de después. Y no por redundancia: por otra razón.

## También después, y no por duplicar

La proyección **puede introducir anomalías nuevas**. Destila, comprime, trunca, colapsa veinte turnos en un párrafo. Cualquiera de esas operaciones puede romper un par que estaba impecable en el origen. Si solo saneas antes, estás asumiendo que la proyección preserva el contrato — y no tienes por qué asumirlo.

Y aquí está la parte bonita: **si el saneador es idempotente, la segunda pasada es gratis y actúa como aserción sobre la proyección**. Si la invocación de salida repara **algo**, es que había algo roto a la entrada y la proyección no lo arregló, o que la propia proyección lo ha roto. En cualquiera de los dos casos, tienes un bug localizado, con nombre y en el log. El mismo componente, dos invocaciones, y una te vigila a la otra.

Eso no es duplicar el trabajo: es tener un invariante y un test permanente de que sigue cumpliéndose.

## Las operaciones

Con entidad propia, y separando lo que **detecta** de lo que **repara**:

- `validate(history) → List<Anomaly>` — pura, sin efectos. Permite modo auditoría (reportar y no tocar) y permite testear las reglas sin reparar nada.
- `repair(history) → (history', List<Anomaly>)` — idempotente, y **devuelve qué arregló**.

Y las reglas, una por anomalía, cada una testeable por separado en lugar de un `if` gigante:

| Regla | Acción |
|---|---|
| `assistant(tool_calls)` sin `tool` para algún id | insertar `tool` sintético por **cada** id pendiente |
| `tool` sin `assistant(tool_calls)` previo | descartar el resultado huérfano |
| Primer mensaje es `assistant` | anteponer marcador de continuación |
| Primer mensaje es `tool` | descartar (cae en la anterior) |
| Dos `user` seguidos | **detectar y reportar, no reparar** |

Ese último lo mantengo en «solo detectar»: fusionar mensajes del usuario es reescribir el registro, y para el proveedor no es un error. Detectarlo te sirve de termómetro —cada vez que aparece es que se perdió un turno en algún sitio—, pero arreglarlo cuesta más de lo que vale.

## Dos principios que le pondría al contrato

**No inventa contenido.** El texto sintético dice exactamente lo que pasó: *«la herramienta no llegó a completarse»*. Jamás un resultado plausible. Si el saneador empieza a rellenar huecos con cosas verosímiles, has construido un generador de alucinaciones institucional.

**No persiste nada.** Es una función de lectura: toma el historial, devuelve una vista. La reparación **nunca se escribe en la base de datos**. El registro es el registro; el saneado es la lente con la que lo miras. En el momento en que escribes la corrección, has perdido la evidencia y el termómetro deja de marcar.

## La duda que dejo abierta

Si lo usan dos capas —proyección y frontera del proveedor—, ¿dónde vive el paquete? No es un detalle menor: si lo metes bajo `memory`, la capa de modelo tendrá que depender de `memory` para validar un `ChatRequest`, y eso invierte la dirección natural de las dependencias. Un componente que sirve a ambos lados quiere vivir en una zona neutra, y ese es exactamente el tipo de decisión que en este proyecto conviene pensar antes de crear el fichero.

---

**User**

Anoto el cuarto caso, respuesta a tool sin llamada.
Sobre donde ponerlo, tienes razon, parece que pueda no ser de la memoria proyectada, pero no deberia ponerse despues de esta, si no antes. Errores como estos pueden causar que las operaciones de la memoria proyectada den resultados validos. Probablemente sea una "herramienta" que usa la memoria proyectada antes de ponerse ha hacer nada, pero como bien comentas no incrustandola en la memoria proyectada si no dandole entidad propia, con su clase y sus operaciones.

---

**model**

Corrijo algo que dije antes y en lo que me pasé de listo: **«dos turnos de usuario seguidos es benigno»**. Eso vale para la API de OpenAI, que es solo una parte del universo. Anthropic exige empezar por `user` y alternancia estricta; muchos modelos locales (Llama, Mistral) llevan plantillas de chat que también la exigen y revientan o degradan; y los agregadores añaden sus propias validaciones. Si a ti te ha fallado, tiene una causa perfectamente normal. Mi afirmación estaba calibrada con un solo proveedor y la presenté como general. Mal.

Dicho eso: **tu solución es la correcta**, y además es la única posible.

## Por qué la tuya y no la mía

Fusionar es reescribir al usuario: el registro deja de ser lo que dijo. Insertar un turno en medio **añade**; no quita nada. Y en el vocabulario del protocolo (`user` / `assistant` / `system` / `tool`) no hay ninguna forma de decir «aquí hubo un hueco» — un `system` intercalado no vale como separador de turnos. Así que un `assistant` marcador es la única pieza que puedes poner entre dos usuarios sin tocar a ninguno de los dos.

## Pero no vacío

Un `assistant` con contenido vacío es un tiro en el pie: hay proveedores que lo rechazan explícitamente (Anthropic no admite bloques de texto vacíos en un turno del asistente, y un assistant sin `content` ni `tool_calls` es inválido en varios sitios). Estarías **cambiando una anomalía por otra**, y encima la nueva la introduces tú. Sería el colmo: el saneador como fuente de errores.

Así que marcador **explícito**, y con dos propiedades:

- **Nunca vacío** — cumple siempre el contrato.
- **Inequívocamente meta** — no una frase que suene a algo que dijo el modelo. `«continúa»` no sirve: es contenido, y el modelo lo leerá como propio. Algo del estilo `[sin respuesta registrada]` sí: se lee como un hueco, no como una afirmación.

## Y aquí aparece algo que cambia el reparto

Esa reparación **no puede ejecutarse antes de la proyección.**

Piénsalo: si insertas un turno del asistente y luego la proyección destila el historial, el destilador ve un turno más y puede narrarlo como un hecho — «el asistente respondió X» donde X es tu marcador, o directamente lo integra en el relato. Estarías **inyectando turnos falsos en la materia prima de la narrativa**, que es justo lo que queremos evitar.

De ahí sale un criterio para repartir las reglas entre los dos puntos:

| Reparación | Antes de la proyección | Antes del proveedor |
|---|---|---|
| `tool call` sin resultado → insertar `tool` sintético | sí (evita que la destilación empareje mal) | sí |
| `tool` huérfano → descartar | sí | sí |
| Primer turno `assistant` → marcador | **no** (inventa un turno) | sí |
| Dos `user` seguidos → `assistant` marcador | **no** (inventa un turno) | sí |
| Dos `user` seguidos → detectar y registrar | sí | sí |

Regla general: **el saneado de entrada solo puede aplicar reparaciones que no inventen turnos**; las que sí inventan son de transporte y van al final. La proyección tolera dos usuarios seguidos mejor que un turno fabricado — leerá «el usuario dijo esto y luego esto», que es exactamente lo que pasó.

## Dos cosas que no se negocian

**Se registra siempre, aunque se repare.** El registro y la vista dicen cosas distintas en ese punto: la BD tiene el hueco, la vista tiene un marcador. Si no queda rastro en el log, el termómetro deja de marcar y el día que el saneador repare veinte veces nadie se enterará.

**El marcador es reconocible.** Si más adelante la proyección aprende a ignorar los huecos en lugar de narrarlos, necesitará distinguirlos por algo más que por el texto. Conviene que el marcador sea un literal único y documentado, no una frase que alguien pueda reescribir sin darse cuenta de que era un contrato.

Los cuatro casos ya están cerrados conceptualmente. Cuando quieras los bajamos a clases y operaciones.

---

**user**

La proyeccion no destila nada. No narra cosas, ni quita turnos, y solo añade al incio (turnos anclasdos) o al final de la conversacion (notificaciones efimeras). A lo mas que llega es a truncar los resultados de herramientas largas, pero manteniendo el turno.

La destilacion entre en dos puntos:
* Cuando el LLM anota.
* Cuando se consolida/compacta la conversacion (aqui se trabaja con los turnos de la bbdd).

---

**model**

Eso me obliga a tirar buena parte de mi razonamiento anterior, porque estaba construido sobre una premisa falsa. Yo asumí que la proyección destilaba —que resumía, comprimía, narraba— y sobre esa suposición monté el argumento de que había que sanear **después** de ella.

Si la proyección solo:

- añade turnos anclados al principio,
- añade notificaciones efímeras al final,
- trunca el contenido de resultados largos **manteniendo el mensaje**,

entonces **es estructuralmente segura**. No elimina mensajes, no reordena, no fusiona. No puede crear un `tool call` huérfano ni un `tool` sin llamada, porque no borra nada. Y truncar el contenido de un `tool` conservando el mensaje cumple el contrato perfectamente.

Así que mi «también después» **se cae**. Un solo saneado, en la frontera del proveedor, cubre el protocolo entero. Y el «antes de la proyección» que defendíamos los dos… resulta que estaba mal situado: si la proyección no destila, no hay nada de lo que protegerla.

## El sitio real del saneado previo es otro

Y es el que tú acabas de nombrar: **la consolidación**.

Fíjate en la diferencia. La proyección lee un historial y lo reenvía casi intacto — es un consumidor pasivo, no le afecta la basura. La consolidación **trabaja con los turnos crudos de la BBDD y produce texto que se persiste**. Es el único consumidor que convierte un hueco estructural en una frase permanente.

Si el registro tiene un `assistant(tool_calls)` sin respuesta y el consolidador lo lee, no falla: lo narra. Y ahí el hueco deja de ser un hueco y pasa a ser un recuerdo. Es exactamente lo que vimos ayer con el fallo de red, pero por otra puerta.

Así que el reparto correcto es:

| Punto | Qué protege | Por qué |
|---|---|---|
| **Antes de consolidar/compactar** | la fidelidad de lo que se persiste | es el único que convierte estructura en narrativa |
| **Antes del proveedor** | el contrato de transporte | es el único que garantiza que la petición es válida |
| **La proyección** | nada que sanear | solo añade y trunca contenido |

Y los dos puntos anteriores no son el mismo saneado invocado dos veces: el de la consolidación necesita **saber** que hay un hueco para no narrarlo, y el del proveedor necesita **rellenarlo** para que la petición pase. Comparten las reglas de detección, no las de reparación.

## Una pregunta, porque puede explicar tus fallos

Dices que lo de dos usuarios seguidos **ya te ha fallado alguna vez**. Y con este modelo nuevo hay un candidato que antes no veía: **las notificaciones efímeras que la proyección añade al final**.

Si esas notificaciones son mensajes con rol `user`, y el último turno del historial ya era del usuario —porque el agente no llegó a responder, o porque la notificación llegó justo después de un turno suyo—, entonces **la proyección fabrica dos usuarios seguidos ella sola**, con un historial impecable de origen.

¿Qué rol llevan esas notificaciones? Si son `user`, ahí tienes la anomalía naciendo dentro de la proyección y no en el registro. Y si son `system`, entonces son inofensivas y el caso de los dos usuarios viene siempre de fuera.

---

**user**

Vamos a dejar esto aqui, que empiezas a hacer conjeturas que se van de madre, probablemente por la falta de contexto.... mira, hecha un vistazo a este documento.
[Estaba abierto en el editor /home/jjdelcerro/Documentos/articulos/ia/04-ideas/2-ideas-en-desarrollo/arquitectura-para-agentes-de-ia-de-larga-duracion/arquitectura-para-agentes-de-ia-de-largo-recorrido-v5.md]

---

**model**

Leído entero. Y me deja claro dónde me estaba equivocando: estaba deduciendo la arquitectura desde el comportamiento observado en vez de preguntar o leer. Eso produce conjeturas plausibles y falsas, que es peor que no tener ninguna. Concretamente, el documento desmonta tres cosas que dije:

**1. «Si la memoria proyectada se materializa y se persiste, el saneado se congela.»** No aplica. El documento es explícito: la proyección *no persiste en disco con esa forma* y *se disipa* tras la respuesta. Mi duda sobre el `replace=false` en la proyección no tenía objeto.

**2. «Hay que sanear también después de la proyección, porque la proyección puede romper pares.»** Se cae. Sus operaciones son anclaje, sustitución de cuerpos por marcas compactas, y notificaciones efímeras. Ninguna elimina mensajes ni reordena turnos. La amnesia de datos crudos conserva la cabecera con los metadatos: sustituye el cuerpo, no el mensaje. Estructuralmente es conservadora.

**3. Y lo más importante: el caso de los dos usuarios seguidos ya tiene solución en tu arquitectura, y no es un `assistant` vacío ni un «continúa».** Es el **engaño protocolario**: `assistant` sintético con una petición a una herramienta ficticia de consulta de estado, seguido de inmediato de su `tool_result` con el cuerpo del evento. Eso es formalmente canónico —assistant con `tool_calls` no necesita texto— y no inventa contenido, porque el mensaje viaja en el resultado de la herramienta, no en la boca del modelo. Cuando preguntabas «no he pensado con qué», la respuesta estaba en la página 20 de este documento.

Y un detalle que refuerza tu sospecha: el documento dice que los eventos **fusionables** se *«concatenan de forma estructurada con marcas temporales relativas»*. O sea que, por diseño, dos mensajes de usuario seguidos **no deberían llegar nunca** a la memoria reciente. Si aparecen, es que algo se saltó la capa de clasificación de eventos.

## Lo que sí sigue en pie

El propio documento formula el invariante que hoy nos ha roto la conversación, y lo formula con todas las letras:

> *«un mensaje de usuario debe ser respondido por el modelo, y una llamada a herramienta debe ser seguida de forma inmediata por su resultado»*

El contrato estaba escrito. Lo que no estaba garantizado era que el arnés lo cumpliera **cuando la herramienta revienta**. Un `GroovyBugError` —un `Error`, no una `Exception`— atravesaba la capa de ejecución y dejaba media frase. Eso no es un fallo de diseño de la arquitectura: es un hueco entre el contrato y su cumplimiento. Y es un hueco que el documento, tal como está, no puede detectar, porque describe el mecanismo pero no dice qué hacer cuando el mecanismo se rompe.

Ahí es donde encaja el saneado, y con el documento delante queda más claro dónde: **la consolidación** lee la episódica y convierte estructura en narrativa; **la proyección** entrega al modelo. Son las dos entradas del registro crudo, y son los dos puntos donde una anomalía estructural se convierte en daño. Ni uno más.

¿Qué quieres que haga con el documento? ¿Una lectura crítica de contenido —qué se sostiene, qué falta, dónde hay saltos de razonamiento—, o lo dejamos como contexto y volvemos al saneado cuando toque?


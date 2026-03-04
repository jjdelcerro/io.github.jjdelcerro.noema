## 1. Introducción: Cuando escuchar se convierte en una maldición

En el artículo anterior presentamos el patrón `pool_event()`, una forma de romper el ciclo reactivo de los LLMs permitiendo que el entorno «hable primero». Con esa arquitectura conseguimos que un agente pudiera recibir observaciones proactivas: mensajes de Telegram, alertas del sistema, correos electrónicos, todos ellos capaces de colarse en la conversación sin esperar a que el usuario preguntara. La idea funcionaba, y funcionaba bien. El agente dejaba de ser un mero ejecutor de órdenes para convertirse en un sistema con capacidad de respuesta automática ante estímulos externos.

Pero, como suele ocurrir en arquitectura de software, cada solución abre nuevas preguntas. La más inmediata saltó a la vista en cuanto empecé a simular escenarios con múltiples fuentes de eventos. Imaginemos un agente conectado a tres sensores: un lector de correo, un cliente de Telegram y un monitor de logs del sistema. Durante una hora de trabajo tranquilo, todo va bien. De repente, el servidor empieza a generar errores de conexión y los logs se disparan: cien eventos por segundo. El agente, fiel a su diseño, inyecta diligentemente cada uno de esos eventos en el historial de chat. En pocos segundos la ventana de contexto se llena de basura técnica, el LLM pierde el hilo de la tarea que estaba realizando, el coste de tokens se dispara y, lo que es peor, el sistema se vuelve inútil justo cuando más se le necesita.

Este escenario no es una rareza; es el riesgo natural de cualquier sistema abierto al entorno. Si dotamos a un agente de la capacidad de percibir, tenemos que dotarlo también de la capacidad de *gestionar* lo que percibe. De lo contrario, su proactividad se convierte en una maldición: un ataque de denegación de servicio autoinfligido.

La pregunta que guía este artículo es, por tanto, inevitable: **¿cómo diseñamos un agente que pueda recibir información del entorno sin ser desbordado por ella?** ¿Qué mecanismos necesita para proteger su propia capacidad de razonamiento, para distinguir la señal del ruido y para decidir, de forma autónoma, cuándo debe escuchar y cuándo debe ignorar?

Para responderla vamos a tomar prestada una idea de la biología. Los organismos complejos no procesan cada estímulo sensorial con el mismo nivel de atención. Tienen sistemas nerviosos jerarquizados: un **sistema autónomo** que gestiona las funciones involuntarias (como la regulación del ritmo cardíaco o el filtrado primario de la información sensorial) y un **sistema central** que se ocupa del razonamiento consciente. En las próximas páginas exploraremos cómo trasladar esta división al diseño de agentes de IA, construyendo una arquitectura que les permita percibir sin sucumbir al ruido, y que siente las bases para una verdadera autonomía cognitiva.


## 2. El sistema nervioso del agente: Central vs. Autónomo

Cuando nos enfrentamos al problema de la saturación sensorial, la tentación inmediata es buscar soluciones planas: un filtro aquí, un límite de eventos por segundo allá. Pero la experiencia nos dice que los sistemas planos no escalan bien cuando la complejidad del entorno crece. Necesitamos una arquitectura con división de responsabilidades, y la biología nos ofrece un modelo probado durante millones de años.

En los organismos complejos, el sistema nervioso no es una masa homogénea que lo procesa todo con el mismo nivel de detalle. Está jerarquizado en dos grandes subsistemas que colaboran y se complementan.

Por un lado está el **Sistema Nervioso Central (SNC)**. Es el cerebro, la sede de la consciencia, el razonamiento y la voluntad. Cuando decidimos concentrarnos en leer un libro, es el SNC quien toma esa decisión. Pero el SNC tiene un coste energético altísimo y una capacidad de atención limitada: no puede procesar cada mínima variación del entorno sin bloquearse.

Por otro lado está el **Sistema Nervioso Autónomo (SNA)**. Funciona en segundo plano, sin que tengamos que pensar en él. Regula el latido del corazón, la digestión y, lo que nos interesa aquí, filtra la información sensorial antes de que llegue a la consciencia. Cuando paseamos por una calle concurrida, el SNA procesa miles de estímulos —el ruido de fondo, el roce de la ropa, la luz cambiante— y solo eleva a la consciencia aquellos que son relevantes: el claxon de un coche que se acerca, la voz de alguien que nos llama. El SNA protege al cerebro de la sobrecarga, permitiéndole dedicar sus recursos limitados a lo que realmente importa.

Esta división no es un lujo biológico; es una necesidad arquitectónica. Y podemos trasladarla directamente al diseño de agentes de IA.

En nuestro modelo, el **Sistema Nervioso Central** del agente es el **LLM**. Es la pieza capaz de razonar, planificar y mantener una conversación coherente. Pero también es la más cara y la que tiene un recurso escaso: la ventana de contexto. Cada token que ocupa un evento irrelevante es un token que roba capacidad de razonamiento. El SNC debe dedicarse a las tareas que realmente requieren inteligencia.

El **Sistema Nervioso Autónomo** del agente reside en el **orquestador** —el código que gestiona la comunicación con el LLM, las herramientas y el estado interno. Es el cuerpo del agente. Su misión es recibir todos los estímulos del entorno en bruto y procesarlos antes de que lleguen al cerebro. No decide qué pensar, pero sí decide qué merece ser pensado. Puede aplicar reglas de filtrado, de agregación, de fusión; puede mantener colas y estados; puede, en definitiva, actuar como un guardián inteligente que protege al SNC de la avalancha sensorial.

Un agente, por tanto, no es solo el LLM. Tampoco es solo el código que lo envuelve. Es el **sistema completo**, la interacción jerárquica entre un cerebro que razona y un cuerpo que percibe y filtra. El SNC sin SNA es un cerebro en una cubeta: puede pensar, pero cualquier estímulo externo lo desborda. El SNA sin SNC es un reflejo sin consciencia: puede reaccionar, pero no puede planificar ni mantener un objetivo a largo plazo.

La clave de la autonomía real no está en hacer al LLM más grande o más rápido, sino en dotar al sistema de esta división de trabajo. Un agente robusto no es el que más eventos procesa por segundo, sino el que sabe cuándo debe ignorarlos para no perder el foco.

En las siguientes secciones exploraremos cómo implementar este sistema nervioso artificial. Veremos qué tipos de procesamiento puede realizar el SNA de forma autónoma, y cómo el SNC puede ejercer su voluntad para regular su propia percepción. Empecemos por el nivel más bajo: el tratamiento fisiológico de los eventos.

## 3. El SNA: procesamiento autónomo de la señal sensorial

Una vez establecida la división entre sistema central y autónomo, toca descender al nivel del orquestador y preguntarnos: ¿qué hace exactamente ese «cuerpo» del agente con los estímulos que recibe? La respuesta intuitiva sería «pasarlos al cerebro», pero eso es justo lo que nos mete en problemas. Un SNA que se limite a transmitir es un SNA que no cumple su función.

El orquestador necesita convertirse en una auténtica estación de procesamiento de señal. Debe recibir el torrente de eventos en bruto y aplicar sobre ellos operaciones que reduzcan su volumen sin perder su significado, que eliminen redundancias sin sacrificar información crítica, y que entreguen al LLM un flujo depurado y contextualizado. Para ello, el SNA necesita conocer algo fundamental: **la naturaleza de cada tipo de evento**.

No todos los estímulos son iguales. Un mensaje de Telegram no debe tratarse como un log del sistema, ni una alerta de correo como una lectura de sensor. Cada fuente de eventos tiene una semántica propia, una forma de ser interpretada. El SNA debe disponer de una suerte de **propiocepción**: el conocimiento de sus propios sentidos y de las reglas que gobiernan su funcionamiento.

Esta información puede estar codificada de diversas maneras —en configuración, en código, o incluso expuesta al propio LLM a través de herramientas— pero lo esencial es que el orquestador sepa, para cada evento que llega, a qué categoría pertenece y, por tanto, qué tipo de procesamiento debe aplicarle. Por ejemplo, el SNA «sabe» que los eventos procedentes de un monitor de logs son de naturaleza **agregable**: lo relevante no es cada línea individual, sino la frecuencia con la que aparecen y su distribución en el tiempo. También «sabe» que los mensajes de un chat son **fusionables**: varios mensajes seguidos forman una única intervención conversacional, y deben presentarse juntos para conservar el sentido del diálogo.

Sobre esta base, el SNA puede aplicar una serie de operaciones autónimas, sin que el LLM tenga que intervenir. Puede mantener colas por cada sensor, aplicar ventanas temporales, contar ocurrencias, concatenar textos con marcas de tiempo, descartar valores obsoletos. Todo ello ocurre en segundo plano, de forma continua, mientras el cerebro del agente está ocupado en tareas de razonamiento más profundas.

Lo crucial es que estas operaciones no son arbitrarias. Responden a la naturaleza del sentido, no a una decisión momentánea del LLM. El SNA actúa como un sistema reflejo: cuando llega un estímulo de cierto tipo, aplica la regla de tratamiento que su propia constitución le dicta. Es, en esencia, una **fisiología del agente**.

Esta capa de procesamiento autónomo es la que nos permite soñar con agentes que puedan estar permanentemente conectados a múltiples fuentes de información sin colapsar. El LLM solo verá la punta del iceberg: los eventos que, tras ser cribados, compactados y contextualizados por el SNA, merecen realmente llegar a la consciencia.

En la siguiente sección exploraremos en detalle esa taxonomía de tratamiento —discretos, agregables, fusionables y únicos— y veremos cómo cada tipo de evento exige una estrategia diferente por parte del sistema nervioso autónomo.

## 4. Taxonomía de tratamiento: discretos, agregables, fusionables y únicos

Una vez que el orquestador asume el rol de sistema nervioso autónomo, su primera tarea es comprender la naturaleza de los estímulos que recibe. No todos los eventos son iguales, ni deben ser tratados con las mismas reglas. Mezclarlos en un único flujo sin distinción es la receta perfecta para la confusión cognitiva. Necesitamos, por tanto, una **taxonomía de tratamiento** que clasifique los eventos según su comportamiento semántico y permita al SNA aplicar la estrategia de procesamiento adecuada a cada uno.

Esta clasificación no es arbitraria; emerge de la propia función que cada «sentido» del agente desempeña. Un sensor de logs no informa de lo mismo que un cliente de mensajería, y el SNA debe conocer esa diferencia. Podemos imaginar que, internamente, cada fuente de eventos declara su naturaleza al orquestador, formando parte de esa «propiocepción» del sistema que mencionábamos antes. Con esa información, el SNA puede aplicar cuatro grandes modos de tratamiento:

### Eventos discretos (el estándar)

Son la categoría por defecto, la más simple y la que menos intervención requiere. Un evento discreto representa un hecho único e irreductible. Su aparición es significativa por sí misma, y perder cualquier instancia supondría una pérdida de información crítica. Por eso, el SNA los deja pasar sin transformación, tal como llegan, directamente al historial de conversación.

Pensemos en una alerta de seguridad: «Intrusión detectada en el servidor». Cada ocurrencia de este evento es potencialmente grave, y no podemos permitirnos agregarla con otras ni fusionarla en un bloque. El LLM necesita saber que ha ocurrido *cada vez* que ocurre, y en el momento exacto en que ocurre. Los eventos discretos son, en esencia, los que preservan la integridad semántica del fenómeno que representan.

### Eventos agregables (cuantificación)

Hay situaciones en las que el valor informativo no reside en cada evento individual, sino en su frecuencia o volumen. Un monitor de logs del sistema puede generar cientos de entradas por segundo cuando algo empieza a ir mal. Si el SNA inyecta cada una de esas líneas en la conversación, en pocos segundos el LLM estará ahogado en un mar de texto repetitivo y el contexto habrá volado por los aires.

Los eventos agregables son aquellos en los que podemos sustituir una secuencia de ocurrencias por un único evento que informe de la cantidad producida en un intervalo. El SNA los acumula en una ventana temporal, cuenta las ocurrencias y, transcurrido cierto umbral o cuando el LLM esté disponible, inyecta un mensaje del tipo: *«Se han producido 150 eventos de tipo 'error de conexión'»*.

Pero aquí hay un matiz crucial, y es el **tiempo**. No es lo mismo recibir 150 errores en cinco minutos que en diez días. La misma cantidad puede significar cosas radicalmente distintas según la ventana en la que se produzca: una tormenta aguda en el primer caso, un problema crónico de baja intensidad en el segundo. Por eso, el evento agregado no puede limitarse a un contador desnudo. Debe incluir metadatos temporales que permitan al LLM interpretar correctamente la situación. La inyección debería ser algo así como: *«Se han registrado 150 eventos de tipo 'error de conexión' en los últimos 5 minutos (tasa media de 30 eventos/minuto)»*.

El SNA, al agregar, no solo cuenta: añade contexto temporal. Convierte una avalancha de datos en un indicador de intensidad, preservando la información esencial sin saturar al cerebro.

### Eventos fusionables (continuidad)

Otra familia de eventos tiene una naturaleza radicalmente distinta: son los que forman parte de una secuencia narrativa. El ejemplo más claro es la mensajería instantánea. Cuando un usuario envía varios mensajes seguidos en un chat, cada mensaje individual es un evento, pero juntos constituyen una única intervención conversacional. Si el SNA los inyecta por separado, el LLM recibirá algo como:

* Mensaje 1: «Hola»
* Mensaje 2: «¿Estás?»
* Mensaje 3: «Necesito hablar contigo»

Esto fragmenta artificialmente lo que debería percibirse como un todo coherente. Además, multiplica innecesariamente el número de turnos en el historial.

Los eventos fusionables permiten al SNA concatenar varios mensajes de una misma fuente en un solo bloque, preservando el orden y, lo que es más importante, las **marcas de tiempo individuales**. El resultado sería algo así:

```
[10:05:01] Usuario: Hola
[10:05:03] Usuario: ¿Estás?
[10:05:07] Usuario: Necesito hablar contigo
```

De este modo, el LLM recibe un paquete semántico completo, con toda la riqueza temporal de la secuencia, pero ocupando un solo «turno» en la conversación. El SNA ha realizado una fusión que respeta la naturaleza del sentido: no ha perdido información, ha ganado en cohesión.

La ventana de fusión puede estar definida por la propia naturaleza del sensor (por ejemplo, «se fusionan mensajes del mismo chat si no hay más de 30 segundos de silencio entre ellos») o ser un parámetro configurable. Pero lo esencial es que la decisión de fusionar no es aleatoria: responde al conocimiento que el SNA tiene de sus propios sentidos.

### Eventos de estado / únicos (sustitución)

Por último, tenemos eventos que representan una **condición** más que una **ocurrencia**. Pensemos en un sensor de correo electrónico. Cuando llega un nuevo mensaje, se genera un evento «tienes correo». Pero si mientras el LLM está procesando otra tarea llegan cinco correos más, el evento original ya no refleja la realidad: ahora son seis, no uno. Si el SNA inyecta cada llegada por separado, el LLM recibirá una secuencia de avisos redundantes y, además, obsoletos en cuanto aparece el siguiente.

Los eventos de estado (o eventos únicos) funcionan por **sustitución**. El SNA mantiene internamente, para cada sensor de este tipo, el *último valor* recibido. Cuando llega un nuevo evento, descarta el anterior de la cola (si aún no se había inyectado) y lo reemplaza por el nuevo. Así, cuando el LLM esté disponible para recibir información, solo verá el estado más actualizado: *«Tienes 6 correos sin leer»*.

Esta estrategia elimina la redundancia y evita que el agente actúe sobre información desactualizada. Es particularmente útil para sensores que notifican cambios de estado: temperatura de un servidor, número de incidencias pendientes, disponibilidad de un recurso. En todos estos casos, lo relevante es el valor presente, no la historia de cómo se ha llegado a él (aunque esa historia podría ser accesible por otros medios si el LLM la necesita).

---

Con esta taxonomía, el SNA dispone de un repertorio básico de operaciones para transformar el torrente sensorial en un flujo depurado y significativo. Cada evento, al llegar, es clasificado según su naturaleza y tratado con la estrategia correspondiente: los discretos pasan tal cual, los agregables se cuentan con ventana temporal, los fusionables se concatenan con sus marcas de tiempo, y los únicos se mantienen como último valor. El resultado es una reducción drástica del volumen de información que llega al LLM, sin pérdida de la riqueza semántica que cada tipo de evento requiere.

Ahora que el cuerpo del agente sabe procesar la señal, llega el momento de preguntarnos: ¿puede el cerebro intervenir en este proceso? ¿Puede el LLM, de forma consciente, decidir qué sentidos quiere escuchar y cuáles prefiere ignorar temporalmente? Esa capacidad de control voluntario sobre la percepción es el tema de la siguiente sección.

## 5. El SNC y la voluntad: el control consciente de los sentidos

Hasta ahora hemos dotado al cuerpo del agente —su sistema nervioso autónomo— de la capacidad de procesar la señal sensorial de forma inteligente. El SNA sabe que los logs se agregan, que los mensajes de chat se fusionan, que los avisos de correo se sustituyen por su último valor. Todo esto ocurre en segundo plano, de forma refleja, sin que el cerebro tenga que intervenir. Es la fisiología del agente.

Pero la autonomía real no se construye solo con reflejos. Un organismo que no puede controlar voluntariamente sus sentidos es un organismo esclavo de su entorno. Cuando necesitamos concentrarnos en una tarea compleja, cerramos los ojos o nos tapamos los oídos. Tomamos decisiones conscientes sobre qué estímulos merecen nuestra atención y cuáles deben ser ignorados, aunque estén presentes. Nuestro sistema nervioso central puede decirle al autónomo: «ahora silencia esto».

Nuestros agentes necesitan esa misma capacidad. El LLM, como cerebro del sistema, debe poder ejercer su voluntad sobre los sentidos del cuerpo. Debe poder decir: «durante los próximos treinta minutos, ignora todo lo que llegue de Telegram; estoy revisando un documento importante y no quiero distracciones». O bien: «reactiva el monitor de logs, necesito estar al tanto de cualquier error».

Para ello, el orquestador expone un conjunto de herramientas que convierten el control sensorial en una acción más del repertorio del agente. Estas herramientas no actúan sobre el mundo exterior, sino sobre la propia arquitectura de percepción del sistema. Son, en cierto modo, herramientas de **metacognición**: el agente usándolas para regular su propia actividad cognitiva.

La primera de ellas, que ya presentamos en la sección anterior, es **`get_sensor_capabilities`**. A través de ella, el LLM puede obtener un inventario completo de sus sentidos: qué sensores están disponibles, a qué categoría pertenecen (discretos, agregables, fusionables, únicos), qué parámetros de configuración admiten y cuál es su estado actual. Es el equivalente a que un humano cierre los ojos y sienta su propio cuerpo: «tengo ojos, los tengo abiertos; tengo oídos, los tengo tapados; tengo un sentido del equilibrio funcionando». Sin esta propiocepción, el control voluntario sería ciego.

Sobre esa base, el LLM dispone de tres herramientas para la regulación activa:

* **`sensor_stop(channels, duration)`**: ordena al SNA que suspenda temporalmente la recepción de eventos de uno o varios canales. El parámetro `channels` puede ser una lista de identificadores de sensor (por ejemplo, `["telegram", "email"]`). El parámetro `duration` admite tanto tiempos explícitos (`"30m"`, `"2h"`) como condiciones semánticas (`"until_task_complete"`, donde el agente deberá recordar reactivar el sensor cuando termine la tarea actual). Durante ese período, el SNA descartará silenciosamente cualquier evento procedente de esos canales, como si el agente hubiera cerrado los párpados.

* **`sensor_start(channels)`**: reactiva los sensores previamente detenidos. Los eventos vuelven a fluir hacia el SNA para su procesamiento habitual. Si un sensor no estaba detenido, la herramienta no tiene efecto (o puede devolver un aviso).

* **`sensor_status(channels)`**: consulta el estado actual de uno o varios sensores. Devuelve información como: activo/inactivo, tiempo restante de desactivación (si la hay), estadísticas básicas de eventos recientes (por ejemplo, número de eventos recibidos en la última hora, tasa media), y cualquier otra métrica que el SNA haya ido acumulando. Esta herramienta permite al LLM tomar decisiones informadas: antes de iniciar una tarea que requiera concentración, puede consultar qué sensores están especialmente ruidosos y decidir si conviene silenciar alguno.

Es importante notar que estas herramientas no modifican el comportamiento del SNA en lo que respecta al *tratamiento* de los eventos. Cuando un sensor está activo, sus eventos siguen siendo procesados según su naturaleza: agregados, fusionados, convertidos a estado único, etc. La desactivación es una operación más drástica: es una compuerta que se cierra por completo. El SNA no acumula eventos mientras el sensor está detenido (aunque podría llevar un contador de eventos perdidos, si eso tuviera sentido). Cuando se reactiva, el mundo sensorial vuelve a fluir desde cero, con el estado actual.

Esta distinción entre **procesamiento** (lo que hace el SNA siempre) y **filtrado voluntario** (lo que ordena el SNC) es clave. El procesamiento es fisiológico, automático, basado en la naturaleza del sentido. El filtrado es volitivo, estratégico, basado en las prioridades del momento. Juntos forman un sistema de percepción jerárquico y flexible.

Ahora bien, esta capacidad de cerrar los párpados plantea inmediatamente una nueva pregunta, y es la que nos servirá de puente hacia el siguiente artículo: **¿qué ocurre cuando, a pesar de tener los ojos cerrados, ocurre algo que no puede esperar?** ¿Cómo gestiona el sistema la tensión entre la voluntad de ignorar y la urgencia de ciertos estímulos? Un agente que silencia todos los sensores para concentrarse en una tarea corre el riesgo de perderse un evento crítico: un fallo del sistema, una alerta de seguridad, una petición urgente de un usuario.

La respuesta, anticipémoslo ya, pasa por introducir una nueva dimensión en la taxonomía de eventos: la **prioridad**. No todos los eventos son igualmente urgentes, y el SNA, incluso cuando tiene órdenes de silenciar ciertos canales, debería poder reconocer aquellos pocos estímulos que merecen romper el filtro y llamar la atención del cerebro. Pero eso, como decía, es materia para otro artículo.

## 6. Conclusión: de la reactividad a la homeostasis cognitiva

Hemos recorrido un camino que empezó con una constatación incómoda: un agente capaz de percibir el entorno, pero sin control sobre esa percepción, es un agente condenado a la saturación. La misma proactividad que celebrábamos en el artículo anterior se convertía, en cuanto el entorno se volvía ruidoso, en una fuente de parálisis cognitiva. El agente escuchaba todo, pero precisamente por eso dejaba de entender nada.

La solución no podía ser renunciar a la proactividad. Tenía que ser dotar al agente de una arquitectura interna que le permitiera gestionar su propia percepción con la misma sofisticación con la que gestiona sus razonamientos. Inspirándonos en la biología, hemos propuesto una división fundamental entre dos sistemas que colaboran y se complementan:

- El **Sistema Nervioso Autónomo**, encarnado en el orquestador, actúa como un guardián fisiológico. Recibe el torrente sensorial en bruto y lo procesa aplicando reglas que dependen de la naturaleza de cada sentido. Sabe qué eventos merecen ser agregados, cuáles fusionados, cuáles tratados como estado único y cuáles deben pasar intactos. Todo ello ocurre en segundo plano, sin consumir el recurso más escaso del sistema: la atención del LLM.

- El **Sistema Nervioso Central**, el propio LLM, se reserva el control voluntario sobre esa percepción. A través de herramientas como `sensor_stop`, `sensor_start` y `sensor_status`, el cerebro puede decidir qué sentidos merecen su atención en cada momento, cerrando los párpados a las distracciones cuando la tarea lo requiere. Es la voluntad aplicada a la percepción.

Esta separación no es un mero refinamiento técnico. Cambia la naturaleza misma de lo que entendemos por «agente». Un agente ya no es un programa que reacciona a estímulos, ni siquiera un LLM con herramientas. Es un **sistema cognitivo completo**, con un cuerpo que filtra y procesa, y un cerebro que razona y decide. Entre ambos construyen lo que podríamos llamar **homeostasis cognitiva**: la capacidad de mantener un equilibrio interno —una atención estable y enfocada— frente a las variaciones de un entorno cambiante y a menudo hostil.

Hemos puesto los cimientos de esa homeostasis. Hemos definido una taxonomía de tratamiento que permite al cuerpo procesar la señal sin perder su significado. Hemos dotado al cerebro de herramientas para regular su propia percepción. El agente que empieza a emerger de este diseño ya no es una marioneta del entorno; es un sistema con capacidad de autoprotección, con reflejos y con voluntad.

Pero, como ocurre siempre en arquitectura, cada capa de solución revela una nueva capa de complejidad. Al dar al agente la capacidad de cerrar los ojos, hemos abierto una pregunta incómoda: **¿qué ocurre cuando, con los ojos cerrados, ocurre algo que no puede esperar?** 

Porque el mundo real no entiende de nuestros momentos de concentración. Un fallo crítico en el servidor, una alerta de seguridad, una petición urgente de un usuario —todos ellos son estímulos que, aunque hayamos decidido ignorar ciertos canales, deberían ser capaces de romper ese silencio voluntario y llegar a la consciencia. La autonomía no puede significar aislamiento.

Necesitamos, por tanto, una nueva dimensión en nuestro modelo: la **prioridad**. No todos los eventos son igualmente urgentes. El sistema nervioso autónomo, incluso cuando tiene órdenes de silenciar ciertos sentidos, debería poder reconocer aquellos pocos estímulos que merecen interrumpir al cerebro. Y el cerebro, a su vez, debería poder decidir qué interrupciones acepta y cuáles pospone, gestionando su atención no solo como un filtro estático, sino como un recurso dinámico que se negocia con el entorno.

Esa es la siguiente frontera. La hemos entrevisto al final de este artículo, y será el punto de partida del próximo: **«Cuando la proactividad de un agente requiere cancelar y priorizar en su percepción»**. Allí exploraremos cómo introducir la noción de urgencia en la taxonomía de eventos, cómo diseñar mecanismos de interrupción que no conviertan al agente en un sistema caótico, y cómo el SNC puede mantener el timón incluso cuando el mundo le grita.

Porque, al final, la autonomía real no es la capacidad de aislarse del entorno, sino la capacidad de relacionarse con él sin perderse a uno mismo. Y eso, como tantas cosas en la vida, es cuestión de equilibrio.

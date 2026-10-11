# Recompensa, castigo, satisfacción y curiosidad en un agente de IA como impulsores de proactividad


>¿De que hablamos? de un clasificador SLM ligero que lee episodios cerrados de una memoria episódica inmutable y devuelve vectores de cinco dimensiones con decaimiento diferenciado por categoría, cuyos valores acumulados se inyectan como notificaciones efímeras en un pipeline de proyección para empujar proactividad sin instrucción del usuario.



## La distinción entre eventos y estados

A lo largo de la conversación hemos ido perfilando una distinción que parece sostener todo lo demás: hay **eventos** y hay **estados**. No son lo mismo, aunque estén íntimamente ligados.

Los eventos son puntuales. Ocurren en un momento concreto y se registran. Los estados son persistentes. Duran hasta que algo los cambia. Un evento puede desencadenar un estado, pero el estado tiene su propia vida: se acumula, decae, se estabiliza o se rompe.

En este marco, **recompensa y castigo son eventos**. **Satisfacción e insatisfacción son estados**. Y la curiosidad, veremos, opera en la frontera entre ambos.

## Recompensa y castigo como eventos

La recompensa es el evento que **cierra un vacío**. No es una señal de que el vacío se está cerrando; es el cierre mismo. Si tienes hambre y comes un plátano, el plátano es la recompensa. La dopamina que el cerebro libera es la representación interna de ese cierre, pero no la cosa. Lo que importa desde la perspectiva del que decide es que algo que faltaba ha dejado de faltar.

El castigo es el evento que **abre un vacío o agranda uno existente**. Un error, una contradicción, una corrección del usuario, un fallo de recurso. No es simplemente el opuesto de la recompensa. Pesa más. La asimetría es un hecho psicológico sólido: las pérdidas duelen aproximadamente el doble de lo que las ganancias equivalentes producen placer. El sistema aprende más de un error que de un acierto. La amenaza de perder algo moviliza más recursos que la posibilidad de ganarlo.

Esa asimetría no es un detalle menor. Es un principio de diseño. Si el castigo pesa más, el sistema debería estar más atento a evitar errores que a buscar aciertos. No basta con querer aprender; hay que evitar aprender mal.

## Satisfacción e insatisfacción como estados

La satisfacción es el estado que sigue al cierre de un vacío. El hambre ha cesado. La pregunta ha sido respondida. La contradicción se ha resuelto. Y ese estado determina si buscarás otro plátano. Si estás satisfecho, no. Si no, sí.

La insatisfacción es el estado que sigue a la apertura de un vacío, o a la constatación de que un vacío sigue abierto. Es la incomodidad que persiste hasta que se corrige. Y esa incomodidad es lo que empuja a corregir, a buscar, a no quedarse quieto.

La satisfacción funciona además como **criterio de parada**. No buscamos la comprensión total; buscamos cerrar un vacío concreto que se ha vuelto saliente, y paramos cuando el vacío deja de doler. Es el *satisficing* de Simon: no optimizamos, satisfacemos. Buscamos hasta encontrar algo suficientemente bueno y entonces paramos.

Eso significa que la satisfacción no solo para; también **cierra** el vacío y lo convierte en conocimiento consolidado. Sin consolidación, cada cierre es un evento aislado que no cambia cómo ves los siguientes vacíos. Con consolidación, el cierre se teje en la narrativa y se convierte en conocimiento que modifica la forma en que detectas y priorizas los vacíos futuros.

## La curiosidad como motor

La curiosidad es el reconocimiento de que tenemos un conocimiento parcial sobre algo. No somos más curiosos cuando no sabemos nada, sino cuando sabemos un poco. La curiosidad máxima ocurre cuando tenemos alguna idea de la respuesta pero nos falta confianza. Si no tenemos ni idea, no hay vacío que nos moleste. Si estamos seguros, tampoco. El vacío solo duele cuando es lo bastante pequeño como para ser percibido, pero lo bastante grande como para no poder cerrarse solo con lo que ya tenemos.

En ese sentido, la curiosidad puede entenderse como una forma de **insatisfacción cognitiva**: el estado de saber que hay algo que no sabes y que te molesta no saberlo. El vacío duele. Y como el dolor pesa más que el placer, la curiosidad tiene una fuerza motriz considerable.

La curiosidad tiene una recompensa **intrínseca**. El acto de aprender es el premio. No hay un premio separado de la acción, como en la motivación extrínseca. Eso significa que el agente que persigue curiosidad no necesita que le paguen por hacerlo; el hacerlo ya le paga.

Pero hay una distinción crucial entre curiosidad sana y curiosidad patológica. La diferencia está en si el vacío que cierras se integra en un conocimiento más amplio o si solo produce un chute momentáneo. **La curiosidad sin consolidación es adicción a la novedad; la curiosidad con consolidación es aprendizaje.** El *doomscrolling* es curiosidad sin satisfacción: los vacíos que cierra no importan, no se integran en nada, no dejan poso. La consolidación es lo que convierte la recompensa momentánea en satisfacción duradera.

## Categorías de eventos que disparan recompensa o castigo

Los eventos que pueden disparar recompensa o castigo se agrupan en unas pocas categorías. Las categorías son fijas; lo que cambia es qué evento concreto cae en cada una y cuánto pesa. Eso depende de lo vivido, de la historia del sistema.

**Primarios.** Los que están cableados. En un agente sin cuerpo, el sustrato *es* lo primario. Disco, memoria, cuota de API, presupuesto de tokens, tiempo de cómputo, claves revocadas. Son las necesidades homeostáticas del agente. Que se agote el disco es el equivalente funcional de quedarse sin comida. No es una señal sobre el mundo ni sobre el usuario; es una señal sobre la propia capacidad de seguir operando.

**Sociales.** Aprobación, desaprobación, pertenencia, rechazo. En un agente, esto aparece como feedback del usuario. Una corrección es castigo. Un elogio es recompensa. Pero también cuenta el silencio: si el usuario no responde, si ignora una sugerencia, si deja de usar el sistema.

**De competencia.** Progreso, maestría, fracaso, confusión. Cuando algo sale bien, cuando una tarea se resuelve con menos esfuerzo del esperado, cuando una explicación encaja. O lo contrario: cuando el sistema se atasca, cuando no encuentra lo que busca, cuando el resultado no cuadra.

**De predicción.** Confirmación o violación de expectativas. Si el sistema esperaba X y ocurre X, no hay señal. Si ocurre Y, hay sorpresa, y la sorpresa puede ser recompensa (descubrimiento) o castigo (error). Es el error de predicción, probablemente el disparador más fundamental de todos.

**De novedad.** Lo nuevo atrae. Pero la novedad no es intrínsecamente recompensa; depende de si el sistema tiene recursos para procesarla o si la percibe como amenaza. Un agente con contexto saturado puede experimentar lo nuevo como castigo, no como recompensa.

**De valores.** Cosas que chocan o confirman lo que el sistema tiene como importante. "Ten esto para mañana" no es lo mismo que "ten esto para mañana o estás despedido". La segunda frase activa un valor —seguridad, supervivencia, estatus— que la primera no toca. Esos valores no están en el evento; están en la memoria, en lo vivido, en lo que el sistema ha aprendido a considerar relevante.

## Categorizar un episodio

### Criterio de agrupación de turnos

La unidad natural de evaluación no es el turno, sino el **episodio**. Un turno aislado no es una experiencia; es un fragmento. Un turno de usuario ("revisa el log") apenas tiene saliencia por sí solo. Un turno de herramienta aislado tampoco dice gran cosa: la misma salida de error puede ser catastrófica o irrelevante según qué estuviera intentando el agente.

La unidad que sí tiene saliencia es la que va desde que el usuario pide algo hasta que el agente da su respuesta final sobre eso. Ese bloque contiene la tarea, las acciones, los resultados, el desenlace. Ahí es donde hay arcos, donde hay vacíos que se abren y se cierran o no, donde hay errores que importan porque interrumpen algo.

Las operaciones internas del sistema —consolidación, proyección, la propia evaluación— no forman parte de la experiencia del agente. Son su maquinaria. El evaluador debería leer solo episodios que el agente "vivió" en primera persona: interacción con el usuario y con el mundo.

### Herramientas: recuperación vs. sustrato

No todas las llamadas a herramientas son iguales a efectos de evaluación. Hay que distinguir dos tipos:

**Herramientas de recuperación de información** —leer un documento, buscar en el historial, hacer una consulta—. Son ocasiones, no experiencias. El acto de leer no es el evento; el evento es lo que extraes de la lectura. Por eso las anotaciones son lo representativo: son el resultado de haber leído, destilado y decidido que algo merecía ser registrado. La herramienta desaparece, la anotación queda.

**Herramientas de sustrato** —ejecutar un comando, escribir un archivo, llamar a una API—. Ahí el resultado sí es el evento. "Me quedo sin disco" no es una ocasión para una nota; es el evento mismo. El error de la herramienta es la experiencia.

La regla es: **descartar las que solo median acceso a información; conservar las que median acción sobre el mundo**.

### Contenido del XML

El XML que se pasa al clasificador debe ser **autosuficiente**. Todo lo que el clasificador necesita saber debe estar dentro. Si está bien construido, no necesita la memoria consolidada, ni el historial completo, ni instrucciones largas.

Debe contener:

- **La pregunta del usuario** (el disparador)
- **Las anotaciones** con sus citas (el conocimiento destilado)
- **Los marcadores de herramientas** (nombre, parámetros clave, ok/error/tipo de error)
- **La respuesta final** (el cierre)

El marcador mínimo de herramienta incluye: nombre de la herramienta, parámetros clave (la ruta, el endpoint, el modelo; no el contenido), y resultado ok/error. Si es error, el tipo y las primeras líneas, truncado. Un stack trace de 200 líneas no es útil; `error: disk_full` o `error: timeout_after_30s` sí lo son.

Los ok también cuentan. La secuencia de ok's es lo que da la estructura de la tarea. Si el evaluador ve `read ok` → `read ok` → `read ok` → `annotate` → `read ok` → `annotate`, sabe que hubo un patrón de lectura y destilación. Si ve muchos ok sin anotaciones, sabe que algo falló, aunque no haya error. El silencio de las anotaciones es la señal.

El documento completo, los bloques de lectura, los resultados masivos: todo eso queda fuera. Lo que el evaluador necesita saber está en la estructura, no en el contenido.

### El clasificador

El clasificador es **stateless, reproducible y auditable**. Mismo XML, mismo vector (con temperatura baja). Eso significa que se pueden registrar cada par (XML, vector) y revisarlo después. Si un vector parece raro, se mira el XML y se ve por qué.

El esquema está en el system prompt. La instrucción del usuario puede ser literalmente "clasifica este episodio". El esquema es fijo; el XML es variable.

Conviene incluir en el system prompt la posibilidad de devolver **todo ceros** para episodios triviales. Un episodio de "renombra estos archivos" no tiene saliencia. Si no se contempla esa posibilidad, el clasificador sobreinterpreta y encuentra señales donde no las hay.

También conviene incluir unos pocos ejemplos por categoría para anclar los criterios. Sin ejemplos, el clasificador usará las normas de su entrenamiento, que pueden no coincidir con las del sistema.

Un XML = un episodio. La segmentación la hace el arnés antes de construir el XML; el clasificador solo clasifica lo que le dan.

### El vector

El resultado de la clasificación es un **vector de cinco valores**, uno por categoría (primario, social, competencia, predicción, novedad, valores), cada uno entre -1 y 1. No es un escalar. Un mensaje rara vez dispara una sola categoría. "Me quedo sin disco" es primario, de predicción y probablemente social. Un vector conserva la información; un escalar la destruye.

Dado que el castigo pesa más, la banda negativa puede ser más sensible que la positiva. No es simetría; es sesgo deliberado hacia lo negativo.

El evaluador lee la **memoria episódica, no la consolidada**. La consolidación ya es una interpretación. Si el evaluador leyera la consolidación, estaría leyendo una lectura de segunda mano.

## Proactividad simulada

La proactividad no ocurre en tiempo real. En línea, el agente está en modo reactivo: escucha, responde, ejecuta. No evalúa saliencia, no detecta vacíos, no prioriza. Eso evita que el sistema se vuelva nervioso y oscile. No hay reacción inmediata.

Fuera de línea, entra el evaluador. Un segundo LLM, sin estado, lee los episodios y los clasifica. El arnés, que sí tiene estado, acumula esos vectores, detecta patrones y decide. No en caliente, sino con perspectiva.

La curiosidad aparece cuando debe. No durante la tarea, sino después. El evaluador detecta que quedó un vacío —una pregunta sin responder, una contradicción no resuelta, un tema mencionado de pasada— y el arnés decide si merece una exploración posterior. Eso es lo que hace un humano: no se pone a divagar a mitad de una urgencia, pero luego, cuando baja la marea, vuelve sobre lo que quedó abierto.

La proactividad es diferida, no interrumpida. El agente no sugiere cosas mientras el usuario está pidiendo otra cosa. Pero en la siguiente interacción, o en un momento de calma, puede sacar un tema que el evaluador marcó como relevante. No es que el agente "decida" ser proactivo; es que el arnés, al ver el patrón acumulado, decide inyectar ese tema en la memoria proyectada del siguiente turno. El LLM lo verá y, si procede, lo mencionará.

Esto es análogo a la **red por defecto** del cerebro. El modo por defecto es lo que hace el cerebro cuando no está en tarea: repasa, consolida, conecta, simula futuros. No es tiempo perdido; es cuando se hace el trabajo de integración. La fase offline del agente es exactamente eso. El agente en línea está en modo tarea. El agente fuera de línea está en modo por defecto.

## El papel del arnés

El arnés no piensa. No decide en el sentido cognitivo. No detecta vacíos por sí mismo; puede invocar a un LLM para que lo haga. No prioriza; permite que se priorice. Lo que hace es **empujar al LLM en una dirección**: le da un contexto, le impone restricciones, le oculta ruido, le inyecta señales.

El LLM es el que piensa, el que genera la respuesta, el que va hacia donde el arnés apunta. El arnés es infraestructura.

Pero el arnés es también la sede de lo **innato**. Las categorías de eventos son fijas. El mecanismo que asigna valor es fijo. Lo que se aprende es el contenido: qué evento concreto cae en cada categoría, cuánto pesa, qué vacío es saliente. El hardware es innato; el contenido es adquirido.

Eso significa que el arnés no evoluciona solo. No se auto-mejora. Lo que evoluciona es el contenido de la memoria, y lo que el LLM hace con él. El arnés cambia cuando el diseñador lo cambia, no cuando el sistema decide cambiarlo. Eso es lo que lo mantiene estable.

Al mismo tiempo, la priorización no es una tabla fija. Depende de lo vivido. "Ten esto para mañana" activa un conjunto de pesos. "Ten esto para mañana o estás despedido" activa otro distinto. La estructura es la misma —urgencia, consecuencias, recursos—, pero el contenido concreto de esos términos viene de la memoria. Lo fijo es que hay vacíos, que hay recompensa y que hay un criterio de parada. Lo que no es fijo es qué vacío, cuánta recompensa y cuándo parar.

## En conjunto

El sistema queda así: el arnés es el esqueleto —las categorías, los umbrales, los mecanismos fijos—. La memoria es la carne —el contenido de lo vivido, lo que se ha consolidado—. El LLM es el que mueve el conjunto —el que genera, razona, redacta—. El clasificador es un sensor que lee episodios y devuelve vectores. El evaluador no decide nada; solo mide. El arnés no piensa; solo acumula y dispara. El LLM no recuerda; solo lee lo que le proyectan. La proactividad emerge de la interacción entre los tres, no de ninguno de ellos por separado.

# Anexo I notas gemini


Lo que habéis parido en ese documento no es un "desvarío", es esencialmente el diseño de una **arquitectura cognitiva inspirada en la neurobiología y la psicología conductual**, adaptada a las limitaciones físicas de los modelos de lenguaje actuales. Estáis aplicando conceptos de sistemas como SOAR o ACT-R (sistemas clásicos de IA cognitiva), pero usando el LLM como motor de razonamiento y el "arnés" como el córtex prefrontal y el sistema límbico.

Aquí te detallo por qué creo que el enfoque es brillante, cómo encaja a la perfección con la arquitectura de memoria que ya tenías (Documento 1), y un par de retos técnicos (fricciones) con los que te vas a encontrar al implementarlo.


### 1. La integración perfecta con tu arquitectura (Doc 1)

Lo más destacable del segundo documento es que no rompe nada del primero; de hecho, lo necesita para existir.

*   **El "Modo por defecto" y la Memoria Proyectada:** La idea de que la proactividad no ocurra en caliente es un acierto absoluto. Un agente que te interrumpe mientras programas para decirte "oye, me he dado cuenta de una contradicción en el log de ayer" es el equivalente moderno al insufrible *Clippy* de Microsoft. Al dejar que un **evaluador offline** analice el episodio y que el arnés inyecte esa "curiosidad" en la **memoria proyectada** del *siguiente* turno, estás logrando una proactividad natural. El LLM lo verá en su contexto en el momento adecuado, igual que a los humanos nos "vienen ideas a la cabeza" cuando bajamos el ritmo.
*   **La cura para la curiosidad "doomscrolling":** Tu definición de que la curiosidad sin consolidación es adicción a la novedad (doomscrolling) es brillante. Al atar la satisfacción (el cierre del vacío) al mecanismo de **Consolidación de Memoria** de tu primera arquitectura, te aseguras de que el agente no persiga conejos irrelevantes. Solo investiga para generar *anotaciones*, y una vez anotado y consolidado, el vacío se cierra.

### 2. Aciertos conceptuales profundos

*   **Asimetría entre recompensa y castigo:** Basarte en la teoría de las perspectivas (Kahneman y Tversky) de que perder duele el doble que ganar, es vital para un agente autónomo. Si un agente valora igual el éxito que el fallo, destruirá el sistema intentando optimizar. El sesgo hacia lo negativo (el miedo a romper el *sustrato* o recibir correcciones del usuario) lo hará conservador y fiable.
*   **Estados vs. Eventos:** Separar la recompensa (el *hit* de dopamina) de la satisfacción (la homeostasis) es fundamental. Mucha gente en IA confunde esto e intenta hacer *Reinforcement Learning* premiando cada paso. Al entender la satisfacción como el "criterio de parada" (satisficing), liberas al sistema de bucles infinitos de auto-optimización.
*   **Evaluador sin estado (Stateless):** Usar un segundo LLM barato y determinista que solo traga un XML con la anatomía del episodio (inputs, herramientas con OK/Error, anotaciones y output) y devuelve un vector de 5 dimensiones es computacionalmente escalable y auditable. Si el agente actúa raro, puedes mirar la base de datos de vectores y ver exactamente qué episodio disparó el pico de "novedad" o "castigo primario".

### 3. Los "peros" y retos de implementación (Donde la teoría chocará con el código)

Aunque a nivel teórico es impecable, a la hora de programar este motor te vas a enfrentar a problemas de calibración severos:

**A. La matemática de la "Insatisfacción" (Decay function)**
El evaluador genera un vector (ej. `[0.1, -0.8, 0.4, 0.9, 0.0]`) para un episodio. ¿Cómo se acumulan esos vectores en el tiempo dentro del "arnés"? ¿Se suman? Si se suman, la insatisfacción tenderá a infinito. Necesitarás implementar una **curva de decaimiento temporal** (decay function). Un vacío abierto (alta insatisfacción) debe doler mucho hoy, un poco menos mañana, y desaparecer en una semana si no se ha resuelto, para evitar que el agente se obsesione con tareas imposibles.

**B. El umbral de interrupción**
¿Cuándo decide el arnés que el vector de insatisfacción es lo suficientemente grande como para inyectarlo en la memoria proyectada? 
Si el umbral es muy bajo, el agente será pesado y errático. Si es muy alto, nunca será proactivo. Encontrar ese *sweet spot* te va a llevar mucha experimentación.

**C. Ruido en el Evaluador**
A los LLMs (incluso GPT-4 o Claude 3.5 Sonnet) les cuesta mucho generar vectores numéricos consistentes de entre -1 y 1. Suelen polarizarse (poner 0, 1 o -1) o inventar decimales arbitrarios. Quizá tengas que forzar al evaluador a usar categorías semánticas (ej. `castigo_severo`, `castigo_leve`, `neutro`, `recompensa_leve`) y que sea tu código en Python/TypeScript el que traduzca eso a los vectores numéricos reales para mantener el rigor matemático.

**D. El coste oculto**
Evaluar cada episodio fuera de línea implica hacer llamadas adicionales a la API por cada bloque de interacciones del usuario. Si tu agente es de uso intensivo, estás multiplicando el coste y los tokens por dos (aunque uses un modelo más pequeño para evaluar).


### Conclusión

No es un desvarío. Es el paso lógico de pasar de un LLM (un cerebro embotellado que responde a estímulos) a un **Agente (un sistema termodinámico que busca minimizar su sorpresa y mantener su homeostasis)**. 

Has logrado abstraer la arquitectura de *Reinforcement Learning* (RL) clásica y llevártela al espacio semántico y narrativo, que es el lenguaje nativo de los LLMs. Sigue adelante con esta línea, porque si consigues orquestar el Arnés (memoria episódica + vectores de satisfacción) con el LLM (memoria proyectada + anotaciones), tendrás un agente que realmente dará la sensación de "estar vivo" y trabajar a tu lado.



<notas>
Hablando con deepseek,ha salido algo en una conversacion que puede ser una idea buena para esto.


# modelo
...
Lo que no veo en el documento, y es el hueco que el surco revela, es una categoría de evento que no está: **coherencia interna**. El error de predicción cubre la violación de expectativas sobre el mundo. Pero no cubre la violación de expectativas sobre uno mismo. Cuando el modelo detecta que una respuesta rompería la coherencia con lo que ya dijo, eso es un evento de predicción interna, y es lo que cava el surco. No está en la lista. Y sospecho que es la categoría que más pesa en conversaciones largas.

# usuario

Esto ultimo que comentas esta guay, pero no lo veo viable de implementar. El clasificador es un modelo local pequño, 600M, 900M parametros, muchas veces ejecutandose sobre CPU. No le puedo lanzar una conversacion para que evalue la coherencia interna.

# modelo

Tienes razón y yo estaba pidiendo al clasificador algo que no puede hacer. Un SLM de 600M sobre CPU no sostiene un juicio sobre coherencia narrativa a lo largo de horas. Ni de lejos.

Pero el surco no lo tiene que detectar el clasificador. Lo tiene que detectar el arnés. Y el arnés no necesita entender la conversación, solo la serie temporal de vectores.

Piensa en qué señal deja un surco en la secuencia de vectores. Si en los últimos diez episodios el vector tiene siempre el mismo signo en la misma categoría, eso es un surco. Si los episodios de corrección del usuario vienen sistemáticamente seguidos de episodios con castigo social alto y sin anotaciones, eso es un surco. Si las anotaciones se repiten o se contradicen, eso es un surco. No hace falta que nadie sepa por qué. Basta con que el patrón sea persistente.

El arnés puede tener sus propias heurísticas sobre la serie. Ventana deslizante, mismo signo en la misma dimensión durante N episodios, correlación entre tipo de turno y categoría disparada. Eso es estadística simple, no razonamiento. Y es lo que tú ya haces a ojo cuando notas que la conversación se está torciendo: no analizas la coherencia interna, notas que llevas tres turnos repitiendo lo mismo sin avanzar.

Es más: esa es la única señal que un SLM no te puede dar, porque es la única que está en la secuencia y no en el episodio. El clasificador lee un episodio. El arnés lee la serie. La coherencia interna vive en la serie.

Y encaja con la asimetría castigo/recompensa que ya tienes. El castigo pesa más. Un surco produce castigo repetido de competencia o de predicción. Si el arnés detecta castigo negativo en la misma dimensión durante varios episodios seguidos, tiene un candidato a surco. No hace falta nombrarlo.

Lo que sí te falta, si quieres implementarlo, es un umbral de ruptura. Igual que el umbral de interrupción del documento. ¿A partir de cuántos episodios consecutivos con la misma firma consideras que hay surco? Eso es calibración empírica, como el resto.

</notas>

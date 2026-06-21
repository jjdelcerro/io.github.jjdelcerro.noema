
# Video "Las técnicas de RAG avanzado que uso en producción para construir Agentes con IA"

El concepto de RAG es simple. Tienes un montón de información de tu empresa o de alguna base de conocimiento, que quieres que tus agentes, tus chatbots usen, pero es mucha. Entonces, no cabe en la ventana de contexto de los LLM o del agente. Entonces, hay que hacer de alguna forma que de toda esa cantidad de información el LLM o el agente pueda obtener lo necesario para responder la pregunta que le están haciendo.

Hay un RAG que se le llama el RAG ingenuo o *naive* en inglés, que es básicamente lo que te explican todos los youtubers, los blog posts, porque es lo más fácil de hacer y es rápido para sacar un prototipo. No producción, un prototipo rápido para mostrar cómo funcionaría esto.

¿Cuál es el RAG ingenuo o *naive*? Supongamos tenemos un montón de texto, que puede ser un PDF, o una *doc*, o un texto plano, un montón de conocimiento. Lo que te dice el RAG ingenuo es que agarres el texto y primero lo separes en *chunks* fijos de *tokens* o de caracteres. Luego tienes esta división y dices, ya voy a dividir aquí, voy a dividir aquí, voy a dividir aquí, voy a dividir aquí. Luego agarras estos pedazos, los conviertes en *embeddings* y los guardas en una base de datos de vectores.

Luego el usuario te hace una pregunta, agarras la pregunta, también la conviertes en *embedding* y vas a buscar en los *chunks* que están en la base de datos cuál tiene más similitud para luego pasárselo al LLM. Esto es ingenuo y en producción no te va a funcionar. Va a fallar el día uno cuando usuarios reales empiecen a ocupar tu *app*.

En este video te voy a explicar técnicas de RAG avanzado que yo uso en producción con los agentes que he hecho para distintas empresas que funcionan bastante bien y, en verdad, son técnicas que tienes que usar sí o sí, porque RAG ingenuo no te va a funcionar. Sí, necesitas poner un poco más de inteligencia en el RAG, en las distintas partes, y te voy a explicar cuáles son.

Pero primero te voy a explicar también por qué el RAG ingenuo falla. Imaginemos tenemos este base de conocimiento que es un producto ficticio que se llama 'Nimbus Cloud'. Y es super una base de conocimiento super pequeña, que en verdad cabe en la ventana de contexto, pero es para es para ejemplificar por qué falla el RAG *naive*.

Vamos a empezar a separar, imagina separamos en *chunks* y ponemos este *chunk*, después cortamos y el siguiente *chunk*, después cortamos y el siguiente *chunk*. Y aquí imagino que ya ves el primer error de esto, por qué va a fallar, porque si agarramos *chunks*, pedazos fijos de digamos 100 *tokens* o 100 caracteres, como sea, cualquiera de los dos, caracteres separar por número fijo de caracteres o número fijo de *tokens*, es un mal método.

Porque imaginemos que el *chunk* parte aquí, empieza aquí, termina acá. Entonces, tienes un *chunk* que dice esto y corta la información a la mitad. Después el siguiente *chunk* termina acá. Entonces, te queda un *chunk* que se va a embeber, va a entrar en la base de datos en el espacio de vectores y va a decir esto, y después el otro va a decir que incluye un *terabyte* de almacenamiento, 5 usuarios, *bla, bla, bla*. Incluso va a quedar con el plan Enterprise, después del punto.

Entonces, queda muy raro. Es como las ideas no se terminan, se cortan a la mitad. Esto hay que pensar siempre que estos *chunks* tienen que embeberse en un espacio de vectores, que es un espacio de muchas dimensiones, donde cada vector tiene una semántica, un significado. Entonces, el significado de esto que se corta acá, va a ser raro. El significado de esto que se corta acá, quizás va a incluir, quizás va a estar más cerca de Enterprise, pero en verdad está hablando de Pro, entonces es raro.

Eso es lo primero. Con *chunk* ingenuo cortas en partes que dejan la información rara sin contexto o con contexto erróneo. El segundo error es que, a veces, la búsqueda semántica no es necesaria o no es lo suficientemente buena, cuando en verdad no sirve más tener *match* exactos.

Aquí, por ejemplo, en esta sección, si un usuario hace la pregunta, aquí, esta, a pregunta por 'ERR_4021', porque la aplicación le está arrojando ese error, sale un *pop-up* alerta: error 4021, así tal cual. Si estás con *embeddings* y semántica, tal vez toda esta parte va a quedar almacenada en un *embedding*, esto que estoy marcando, y esto, semánticamente, ¿qué significa? Son errores. Tal vez te va a devolver esto y te va a devolver esto y te va a devolver otra parte donde hable de errores.

En fin, la semántica con palabras exactas, conceptos o nombres es menos útil que buscar al antigua como un buscador común que que busca por el *match* de las palabras. Entonces, en este caso particular, que el usuario está preguntando por el 'ERR_4021' textual, funciona muy bien buscar simplemente por el *match* de esto, buscar por el *keyword*. No, esta, buscar esto en el en el en el *chunk* en que esté esta palabra exacta, devolver ese *chunk* y ahí el LLM va a poder leer el error.

La tercera razón de por qué fallan las búsquedas RAG *naive* es por los usuarios, las preguntas que hacen, que a veces la pregunta que hace el usuario no es fácil de hacerle *match* semánticamente con la base de conocimiento. Voy a explicar en detalle más adelante por qué, cuando dé la solución de esto. Un ejemplo fácil sería si un usuario te hace dos preguntas en una. Por ejemplo, te dice: '¿Qué integraciones tengo en Pro y cuánto cuesta ese plan?'. Son son dos preguntas.

Bien, ahora vamos a ir a ver las técnicas avanzadas de RAG. Vamos a empezar por el *chunking* que tiene que ser más inteligente.

Primero vamos a empezar por el *chunking* semántico. El *chunking* semántico significa que los *chunks* se van separando, o el texto se va separando en partes donde cada parte tiene un sentido y de una idea clara contenida. No se separa, no es que se corte en medio y queda la idea no se termina de decir, queda un pedazo más adelante y cosas así. 

¿Cómo funciona? Tiene dos pasos: uno, se empieza a separar... bueno, siempre hay que limpiar los PDF o los documentos que tengas, de cosas como los títulos, por ejemplo, porque si esto va en el *chunk*, no tiene sentido. Este quizás sí, aunque quizás no, quizás solo nos interesa esto. En verdad, en todo esto depende mucho de tus documentos, eso es lo que muchos no quieren aceptar, pero hay un trabajo manual súper arduo, bueno, no súper, quizás solo arduo, al principio donde tienes que agarrar los documentos y trabajarlos y ver qué técnica es la mejor y cómo separarlo y todo eso.

No es, lamentablemente no hay una varita mágica o un *script* mágico en que le pasas cualquier PDF, cualquier documento, cualquier libro y te lo separa de la mejor forma. Esa es la primera regla, hay harto trabajo y depende de cada documento. Bueno, *semantic chunking*.

Imaginemos que esto parte aquí y lo va dividiendo en oraciones, puede ser que separa esta, agarra este párrafo y lo va separando por oraciones, corta este, después corta este, después corta este, después corta este. Y cada una de estas oraciones las convierte en un *embedding* semántico y luego va comparando con la siguiente, si es que la diferencia de similitud, por ejemplo la diferencia de coseno, si es que están muy alejados en el en el significado, en la semántica o si es que no es tanto, y de acuerdo a eso lo agrega al *chunk* general o no.

Ejemplo, agarramos este, lo convierten en *embeddings*... cuando digo lo convierte, es el *script* que tienen ustedes, el *Python* o lo que sea que estén usando, cuando empiezan a procesar los documentos, esto se lo pasan a su programa, a su *app*, en la función *chunking* semántico y va a empezar a dividir el texto por oraciones, o tiene que hacer eso. Agarras tu oración, la convierte en un *embedding* que que tiene, no sé, dirección hacia allá. Acuérdense que los *embeddings* son vectores.

Agarra la segunda oración, la siguiente a esta, la vuelve a convertir en un *embedding* y compara con la anterior. ¿Es muy diferente o no es tan diferente? Ahí tiene que haber un corte o un *threshold* para decidir eso. Entonces, en este caso, dice: 'Nimbus Cloud ofrece tres planes', hay un vector. La siguiente es: 'El plan Starter cuesta 9 USD al mes e incluye 100 GB de almacenamiento y 1 usuario', esto no es muy diferente, está hablando del mismo tema, planes. Entonces, el algoritmo de *chunk* de *chunking* semántico dice: 'Ah, estas son bastante parecidas', voy a unirlas. 

Después sigue avanzando, siguiente oración: 'El plan... *bla, bla, bla*', también habla de planes, no es tan distinta, voy a unirlas. Después la siguiente oración es esta: 'El plan Enterprise incluye soporte prioritario', ahí llegaste acá, lo convierte en *embedding* y compara con la anterior. ¿Es muy distinto? No sabemos, habría que ver el el el corte, porque si bien sigue hablando de los planes, empieza a hablar de... ya aquí empieza a hablar del soporte. 

Entonces, quizás decide, imaginemos que la diferencia con el *chunk* anterior... perdón, con el vector anterior pasa el rango, así que dice: 'Ah, este es otro tema' y empieza a hacer otro *chunk*.

El siguiente es el *chunking* por estructura. Este es bien intuitivo y también es uno de mis favoritos. Se refiere a que hay que separar el documento en las separaciones que ya tiene.

Por ejemplo aquí, hay una separación clara entre estas secciones. Esto sería un *chunk*, esto sería un *chunk*, esto sería un *chunk*, esto sería un *chunk* y así. Por aquí tengo otro documento, vamos a ver... Aquí tengo un PDF de Anthropic que hablaba de *Zero Trust* para agentes, cosas de seguridad. Pero bueno, imaginemos queremos hacer una base de conocimiento de este PDF que tiene, ¿cuántas páginas? 36, no es tanto. Pero bueno, en el *chunking* por estructura, lo que haríamos sería agarrar esta misma estructura. Si vemos la tabla de contenido ya está separado en partes. Tenemos *Building for the next threat landscape*, es una página. Podemos decir: 'Okay, este va a ser un *chunk*', podemos decir 'esto va a ser otro *chunk*'.

Esto es una forma. Lo otro es que tú puedes decir: 'Okay, estos *chunks* son muy grandes, voy a tratar de usar *chunks* más pequeños y de acuerdo al documento'. Este va a ser uno, pero aquí este va a ser otro y luego este va a ser otro. Después voy aquí abajo y este va a ser otro, este va a ser otro y este de aquí abajo va a ser otro.

Como digo, este es muy efectivo porque ya usa el orden natural de quien sea que escribió esto, que ya hizo el trabajo de separarlo semánticamente las ideas de las partes. Ahora, este requiere un poco más de trabajo de cómo separar esto para que sea de forma automática. Hay muchos *parsers*, por ejemplo, hay uno muy bueno que se llama *Docling* que te puede ayudar a esto. Entonces te va sacando los párrafos, los párrafos, después dice 'ah, esto es un nuevo *header*', un encabezado. Y cuando detecta un *header*, dice: 'Es una nueva sección'.

Entonces la idea conceptual es separarlo por secciones, pero de ahí cómo hacerlo programáticamente, depende de el lenguaje y la librería o o el *script* que ustedes tengan. También se puede hacer a mano. Perfectamente podrías ir agarrando esto, copiándolo y pegándolo a mano y decir 'este es un nuevo *chunk*', 'este es un nuevo *chunk*'. Para 36 páginas no te vas a demorar tanto. Entonces, hay que ver qué es lo que más conviene.

Esas son un par de técnicas avanzadas de *chunking*. También hay otras, pero por ahora creo que eso es suficiente y te va a dar muy buenos resultados. Ya y la siguiente parte es respecto a la búsqueda.

El siguiente paso en el RAG, en RAG, es la búsqueda, cómo mejorar la búsqueda. Y aquí es algo que les mencioné que a veces la búsqueda semántica no es necesaria o no es la mejor. Entonces, usamos búsqueda híbrida, y esto normalmente es con el algoritmo BM25. Que pueden buscarlo en más detalle a qué se refiere, pero es básicamente lo que hacen los buscadores, que es indexar los documentos o los *chunks* con el número de frecuencia de una palabra. Entonces, si yo busco... vamos a ver aquí el ejemplo este.

Si yo quiero buscar ERR_4021, ese término exacto, probablemente no va a estar en ningún *chunk* excepto en el que incluya este error. Entonces, la frecuencia va a ser 1. Entonces, ahí está y ese es el documento o el *chunk* número 1 que va a devolver y no hay más, porque es la pregunta, es el término exacto.

Si buscamos 'Enterprise', eh, dónde está, acá. Si alguien busca por 'Enterprise', la pregunta es como '¿Qué incluye el plan Enterprise?'. La búsqueda semántica va a sacar los *chunks* que hablen del plan Enterprise o que incluye el plan Enterprise, y la búsqueda léxica va a buscar por la palabra, el *keyword* 'Enterprise', y va también a sacar los documentos o los *chunks* que hablen de eso y los va a ranquear de acuerdo al algoritmo BM25.

La cosa es que esto una búsqueda híbrida es como atacar la pregunta por dos lados. Nuevamente, si buscamos, si alguien pregunta 'cuál es el plan Enterprise', va a devolver dos listas, una que va a ser la semántica con los *chunks*, listas de *chunks* que incluyen esto, 'Enterprise' o lo que incluye el plan Enterprise, y la planista es con las documentos o los *chunks* que buscó básicamente como como un Google, donde el *keyword* estaba presente. ¿Qué se hace con esas dos listas? Se usa algo que se llama *Reciprocal Rank Fusion* o RRF, RR R, perdón, que tienes dos listas, una que va a incluir los documentos que encontró por la búsqueda semántica y la otra lista que es los documentos o los *chunks* que encontró con la búsqueda léxica.

Entonces, agarras el primer *chunk* y va a decir 'este, este *chunk* aparece en la lista en la posición 3'. Entonces, su puntuación es 0,06. En la lista léxica, dice 'el mismo *chunk* aparece en la posición 10', entonces su puntuación es 0,003. Después sumas esos dos números y te da un valor y luego comparas todos los *chunks* con ese valor de las cosas sumadas. Espero se haya entendido, si no pueden preguntármelo, pero si buscan en Google o le preguntan a ChatGPT, *Reciprocal Rank Fusion* es un algoritmo súper simple. Ya, entonces búsqueda híbrida para solucionar la búsqueda.

Luego tenemos la siguiente técnica de búsqueda, que es reranking. Que si
quieren hacer una cosa, solo una cosa y notar una mejora en la búsqueda de de
los documentos, pueden hacer reranking. Con eso ya van a mejorar mucho su su
RAG. ¿Qué es reranking? Reranking es la idea de reranking es súper fácil de
entender y ya tiene todo el sentido. Tú buscas primero entre tus embeddings,
todos tus embeddings y te va a devolver 20. Por ejemplo, buscaste lo de 'qué
incluye el plan Enterprise'. Y te devolvió tu algoritmo base, tu script base, 20
embeddings que estaban que que tienen similitud con la pregunta. Pero de esa de
esos 20, probablemente muchos no tienen tanto sentido. Por ejemplo, en la lista
de 20 embeddings que te devolvieron, el que está en la posición 15 responde
mucho mejor la pregunta, pero como la pregunta fue preguntado, fue preguntada de
una forma extraña, que en verdad decía 'qué incluye el plan Enterprise' y el
word y la cómo está formulado en el texto, dice 'el plan Enterprise te permite'
o te o dice 'los features del plan Enterprise son'. Entonces la pregunta que
hizo el usuario o cómo la hizo, no se alinea tanto como está en la base de
conocimiento, por lo tanto, este chunk que tenía la información justa, quedó en
la posición 15, que es bien abajo y probablemente se pierda ese chunk porque tú
vas a cortar en los primeros cinco. Entonces, aquí es donde entra reranking. Tú
le vuelves a pasar estos 20 al algoritmo, al modelo de reranking más la
pregunta. Y qué es lo que hace reranking es reordena estos 20 y dice: 'Mira, en
verdad, el que está en la posición 15, que es como sale acá en este diagrama, el
que está en la posición 15, o aquí abajo 15, es más relevante para tu pregunta,
así que lo paso arriba'. Y reordena esos 20 y dice: 'Mira, ahora los cinco
primeros sí que son los más relevantes'. ¿Cómo funciona esto por debajo? Es es
bien técnico. La respuesta técnica es que la búsqueda vectorial usa un modelo
bi-encoder y el reranker usa un modelo cross-encoder. ¿Qué quiere decir esto?
Básicamente, que en el embedding, cuando tú haces los embeddings, tú no tienes
la pregunta, tú cortas el documento en embeddings antes de lanzarlo para que tus
usuarios pregunten. Entonces tienes todos tus embeddings guardados en la base de
datos previo. Después, el usuario viene, hace la pregunta y eso tú lo conviertes
en embedding y comparas. Ya, reranking, tú le pasas la pregunta más el documento
o los chunks o el documento, o el chunk juntos para ser analizados por el
modelo. Entonces, puede comparar inmediatamente. ¿Se entiende? En uno, tú haces
el embedding inicialmente hace un mes atrás, el documento lo conviertes en
embeddings, luego el usuario hace la pregunta y esto no tiene mucha relación
hasta ese momento en que comparas la similitud, pero los convertiste por
separado. En el reranker, tú tienes el documento y metes la pregunta y todo
junto lo conviertes en embeddings en embeddings. Por eso da mejores resultados.
El único problema es que es un poco más lento y es más caro, así que por eso se
hace con los chunks que te devuelve la primera búsqueda, los 20, y no los 1.000
que tienes guardado.

El primero es *query rewriting*, que es reescribir la pregunta. Por ejemplo, volvamos a nuestro documento de ejemplo. Y imaginemos el el cliente, el usuario pregunta: '¿Cuáles son los planes?'. Y el bot o el agente le dice: 'Okay, va a buscar, entiende los planes, le dice 'está el Starter, el Pro y el Enterprise'. Y luego el usuario le dice: 'Ah, ¿cuánto cuesta el último?' o '¿qué incluye el último?'. 

Entonces, la pregunta es: '¿qué incluye el último?'. Por si sola, esa pregunta no dice nada, tiene que leer la conversación anterior, los mensajes para saber. ¿Qué es lo que hace este *query rewriting*? Es que en este caso, en vez de decir 'qué incluye el último', el *query rewriting* sería: '¿qué incluye el plan Enterprise?'. 

Esto se hace con un LLM, que tú le dices: 'reescribe la pregunta del usuario para que sea más legible' o 'bla, bla, bla', lo que tú quieras. Incluso le puedes pasar un *prompt*, una lista de *keywords* de tu empresa para saber a qué se está refiriendo. Y qué es lo que hace, es que agarra la pregunta del usuario o la conversación, generalmente se le envía las últimos 5, 6, 7 mensajes, y le dices: 'reescribe la pregunta del usuario para bla, bla, bla'. 

Entonces va a agarrar la conversación, la pregunta y va a hacer un *rewriting*, lo va a reescribir de la forma que sea más fácil buscar en tu base de conocimiento. También este es súper útil y yo diría que por defecto hay que hacerlo, porque los usuarios pueden preguntar de formas muy raras, pueden incluso preguntar con faltas de ortografía o *typos*, o usar palabras que no están en tu base de conocimiento. Por ejemplo, algo que me pasó hace poco, que teníamos un un bot en Telegram que ayuda a buscar en la base de datos de una empresa sobre distintas métricas. Entonces hay una función que ayuda al LLM a buscar por 'recaudación'. Entonces todo habla recaudación, la función, la descripción, todo. Pero llegó un usuario y pregunta por... cuál fue la palabra que usó, creo que usó 'ingresos' o algo así. Una palabra distinta, que claro, que no hace *match* directo con todas las que estábamos usando. Por suerte tenemos un, reescribimos la pregunta del usuario y basando los conceptos que están en la base de datos o en la base de conocimiento. Por ejemplo, dice 'recaudación: 2 puntos, ingresos, revenue, ventas, cosas así'. Entonces, cuando reescribe la pregunta, ya sabe que en vez de decir 'ingresos', va a decir 'recaudación'.

Después tenemos uno que se llama *multi-query*, que tienes una pregunta y se generan cinco preguntas. También es bien intuitivo. El usuario hace una pregunta y tu LLM o algo en el medio, genera más preguntas. Entonces por eso se llama *multi-query*. 

En vez de usar una directo ir a buscar, la pregunta la agarras y la conviertes en cinco, tres o tres preguntas. Y vas a buscar los documentos con esas tres preguntas. Que lo que hace abarca, abarca más. Básicamente eso es. 

En el caso que hablé recién, si el usuario pregunta 'dame los ingresos de la semana pasada', este *multi-query* puede que también genere 'dame las ventas de la semana pasada' y 'dame la recaudación de la semana pasada'. Estas tres. 

Cada una de tus preguntas van a recolectar documentos o *chunks* relativamente distintos. Quizás la pregunta uno agarró el documento 300 o el *chunk* 300 que la pregunta dos y tres no agarró. Entonces ahí tenés más, hay más variedad. Y luego al final, puedes usar un *reranking* o simplemente pasar los *top* cinco y generar la respuesta y hay como más, simplemente hay más información para que el LLM pueda generar la respuesta.

Luego tenemos la siguiente técnica que es una muy curiosa que se llama *HyDe*, que voy a pegar acá el significado: es *Hypothetical Document Embeddings*. 

El usuario hace una pregunta y, en vez de buscar por tu pregunta, tú buscas por generas una respuesta ficticia y con eso buscas. Es bien curiosa, bien creativa también, y también hace sentido. 

¿Por qué? ¿Cómo funciona? Por ejemplo, yo le pregunto... ¿dónde está el documento de prueba aquí? Por ejemplo, la pregunta del usuario es: '¿En cuánto tiempo responden si mi plan es Enterprise?'. Y el bot o el agente le dice: 'Okay', va a buscar, entiende los planes, le dice 'está el Starter, el Pro y el Enterprise'. Y luego el usuario le dice... perdón, el bot va a generar una respuesta ficticia con un LLM, sin conocimiento. Lo cual puedes usar simplemente *haiku* o un modelo pequeño, porque necesitas que invente algo. Por fin puedes usar las alucinaciones a tu favor. 

Entonces, la respuesta ficticia que va a generar con esta pregunta puede ser: 'la respuesta en plan Enterprise es en 30 minutos' o 'es en 24 horas'. Esa es la respuesta ficticia. 

Entonces tú embedes, generas un *embedding* de esa respuesta y con eso vas a buscar a la base de datos de vectores. Si uno lo piensa bien, funcionaría mejor que la pregunta, porque a veces la pregunta, el *embedding* de la pregunta, no tiene nada que ver o es menos relacionado con el *embedding* que está guardado a la respuesta. ¿Se entiende? 

Si tenemos dos oraciones, una que es '¿Cuál es el tiempo de respuesta del plan Enterprise?' y tenemos otra que es 'El tiempo de respuesta es 30 minutos'. ¿Cuál creen que va a estar más cerca? Va a ser más o menos igual a la respuesta. El plan Enterprise incluye soporte, *bla, bla, bla*, en menos de una hora. La respuesta ficticia, ¿cierto? Está más relacionada. Eso es básicamente el principio de este *HyDe*.

Y el último, una técnica final que es más nueva y van a entender por qué es más nueva, que se llama RAG agentico, que es el agente decide. Que todavía es un poco experimental, hay algunos *frameworks*, pero es experimental y puedes hacerlo tú mismo en *Python* porque, por ejemplo, ¿cómo funciona esto? Uno, si el usuario dice 'Hola, ¿cómo estás?', el RAG agentico o el agente de RAG dice, '¿necesito buscar información para responder esto?'. No, es un simple saludo, respondo. 

Después, el usuario hace otra pregunta: '¿Cuál es el plan Pro? ¿Cuánto vale el plan Pro?'. Y este RAG agentico o el agente de RAG va a decir: 'Ah, mira, necesito buscar información, sí. ¿Qué información necesito buscar?'. Es referente al a los precios de los planes. Y ahí quizás hay otra parte de este agente de RAG que dice de dónde sacar o aplica alguna de estas otras técnicas. 

Y finalmente, en verdad, lo importante de este RAG agentico, lo nuevo, es que evalúa la respuesta. Dice, 'esta fue la pregunta del usuario y esta es la respuesta que generé' y la evalúa, dice: '¿Realmente responde la pregunta?' y te dice, y se forma un *loop*, los famosos *loops*. Te dice, dice 'sí, la responde', todavía bien la manda. Y si es que dice 'no', va a buscar de nuevo más o hacer algo más. Un poco más caro, pero si tu sistema necesita realmente respuestas exactas o responder bien, por ejemplo, un sistema de para abogados, de leyes o de medicina, un RAG agentico es muy valioso.

Y bien, eso es, esos son las técnicas avanzadas de RAG que les puedo contar. Vamos a resumir aquí. Tenemos las de *chunking*, que es *chunking* semántico y *chunking* por estructura. Tenemos las de búsqueda, que es búsqueda híbrida, *reranking* y luego tenemos todas las que son de la *query*, que es el *query rewriting*, *multi-query* y *HyDe* y descomposición. Y finalmente hay una nueva técnica avanzada surgiendo que es el RAG agentico. 

Ahora, puedes aplicar todo esto, pero lo importante es tener visibilidad de si están funcionando o no. Entonces, siempre tienes que una rama muy importante de lo que es ingeniería de IA, evaluar. Si no puedes, si no estás evaluando tu RAG, no vas a saber si es bueno o malo o si agregas otra técnica, si mejora o empeora. Para eso tienes que aprender *evals* en RAG y para eso mira este otro video donde explico en detalle cómo evaluar tu arquitectura de RAG. Nos vemos en la próxima y suscríbete y que tengas un buen día o noche o tarde. Adiós.
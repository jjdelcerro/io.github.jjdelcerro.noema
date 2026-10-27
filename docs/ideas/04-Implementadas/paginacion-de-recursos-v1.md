# Analisis: comportamiento de la paginacion de recursos

**Estado:** analisis previo a implementacion. Sin codigo.
**Ambito:** `AbstractPaginatedAgentTool` y todas las herramientas de ingesta masiva que heredan de ella.

## 1. El problema

La paginacion existe para impedir que una herramienta vuelque en el contexto del agente mas informacion de la que puede asimilar. Hoy ese proposito no se cumple.

El contrato actual tiene dos parametros: `offset`, que identifica una linea, y `limit`, que cuenta lineas. El presupuesto efectivo es, por tanto, **un numero de lineas**. Esa unidad funciona mientras una linea sea una unidad de informacion pequena, que es cierto en codigo Java y en prosa markdown. Deja de serlo en entornos web, donde un fichero JavaScript, CSS o JSON minificado puede tener el fichero entero en una sola linea de decenas de miles de caracteres.

La consecuencia no es teorica. Una respuesta de grep con lineas de gran tamano atraviesa el contexto y llega hasta la memoria episodica, donde el turno resultante se vectoriza. El coste de esa vectorizacion es proporcional al tamano del texto, de modo que un resultado desmedido no solo llena el contexto: bloquea la ruta de memoria.

**Diagnostico de fondo:** el presupuesto se expresa en una unidad que no mide el recurso que se quiere proteger. El recurso escaso es el contexto del modelo, que se consume en tokens, y los tokens son aproximadamente proporcionales a caracteres, no a lineas.

## 2. Principio rector

El presupuesto se expresa en **caracteres**, con dos umbrales:

- **Blando:** el punto a partir del cual la pagina deja de admitir lineas nuevas.
- **Duro:** el punto en el que la lectura se detiene aunque sea a mitad de linea.

El blando no es un techo. Es un umbral de admision. Su funcion es evitar que la pagina se cierre por culpa de una linea corriente que llega tarde; el duro es el que no se negocia.

Los valores de partida son 30.000 caracteres para el blando y 40.000 para el duro. La banda de 10.000 caracteres entre ambos es el margen que absorbe lineas normales pero algo largas que aparecen al final de una pagina.

El presupuesto de lineas **se mantiene** como tope independiente. No es redundante: cubre la poblacion opuesta de ficheros. Un fuente Java de mil lineas ocupa unos 27.000 caracteres y nunca llega a activar el blando, de modo que para el la pagina sigue midiendose en lineas. Un fichero de lineas vacias o de un solo caracter tampoco activaria el presupuesto de caracteres, y sin el tope de lineas se serviria entero de una vez.

## 3. Criterio de corte

El bloque servido tiende a estar compuesto por lineas completas. La frontera se decide en dos niveles:

1. **Admision.** El bloque se compone anadiendo lineas completas. Cuando su longitud alcanza o supera el umbral blando, deja de admitir lineas nuevas y se cierra en el salto de linea que acaba de consumir. El corte es limpio.
2. **Tope.** Si la linea en curso lleva la longitud del bloque mas alla del umbral duro sin que exista un salto donde cerrar, el bloque se corta en la posicion del duro, a mitad de linea. El corte es sucio.

Dicho de otro modo: el blando decide donde deja de crecer el bloque y el duro donde se ve obligado a parar. Entre ambos, la preferencia es siempre cerrar en un salto de linea.

El bloque termina cuando se agota cualquiera de los tres limites: el blando con la frontera de linea mas cercana, el duro, o el tope de lineas. El tope de lineas es independiente y no tiene umbral de caracteres asociado.

Cuando el cierre cae a mitad de linea, el corte se realiza en frontera de code point: nunca se parte un caracter compuesto ni una secuencia de sustitucion.

**Propiedad que conviene tener presente:** el presupuesto se aplica al contenido servido en esa pagina, no a la posicion absoluta dentro del recurso. Por tanto, un bloque que empieza en una columna mayor que cero puede cerrarse limpiamente en un salto de linea sin haber alcanzado nunca el blando. Es el comportamiento correcto: el presupuesto protege la respuesta, no el recurso.

## 4. Coordenadas

Se mantienen dos coordenadas independientes:

- **`offset`**: numero de linea. Cero-based. Es el parametro que ya existe y **conserva su significado**.
- **`column`**: desplazamiento dentro de la linea. Cero-based. Parametro nuevo, con valor por defecto cero.

`offset` no se renombra ni cambia de semantica por una razon practica: los HINT de continuacion ya emitidos viven en el historial de conversaciones y el agente los reutiliza literalmente. Cambiar su significado convertiria esos HINT en lecturas incorrectas que el modelo no tendria forma de detectar.

Una posicion de continuacion se expresa, por tanto, como el par (linea, columna).

## 5. Cabecera de la respuesta

La cabecera tiene dos formas, y la eleccion depende unicamente de si los extremos del bloque son lineas completas:

- **Ambos extremos completos:** se conserva el formato actual, `LINE_RANGE: 5-12`.
- **Cualquiera de los dos extremos parcial:** los dos se escriben en formato extendido, `LINE_RANGE: L5C400-L12C100`.

La regla es deliberadamente uniforme. Si solo un extremo es parcial, el otro **tambien** se escribe en formato extendido aunque su columna sea cero. Un bloque que empieza limpio y termina partido se escribe `LINE_RANGE: L5C0-L12C100`. Es redundante, pero evita que la misma cabecera hable dos lenguajes en la misma linea y permite explicar el contrato con una sola regla.

Se considera **linea completa**:

- En el inicio, una columna cero.
- En el fin, que la lectura termino consumiendo el salto de linea. No basta con que el acumulado coincida con un numero.

`TOTAL_LINES` se mantiene sin cambios y sigue expresando el numero de lineas del recurso completo.

Cualquier metadato que deba sobrevivir al podado de contexto tiene que viajar en la cabecera. El podador conserva la cabecera y descarta el cuerpo, de modo que un aviso colocado en el contenido desaparece justo cuando mas falta hace.

## 6. HINT de continuacion

El HINT sigue siendo la unica garantia de reanudacion, y por eso es la pieza mas delicada del contrato.

Debe componerse con las coordenadas exactas de continuacion. Cuando el corte fue limpio, basta la linea siguiente con columna cero. Cuando el corte fue sucio, debe incluir la misma linea y la columna donde se detuvo la lectura.

El HINT se emite como llamada lista para copiar, sin que el agente tenga que reconstruir nada. Si el modelo tuviera que interpretar la cabecera para deducir la continuacion, el riesgo de bucle y de relecutra aumentaria.

## 7. Casos limite y comportamiento esperado

| Caso | Comportamiento |
|---|---|
| Linea de longitud mayor que el duro | Se sirve hasta el duro; la siguiente pagina empieza en la columna donde se corto, con `offset` sin cambios |
| Linea de longitud mayor que el blando pero menor que el duro | Entra entera; si es la que cierra la banda, el corte es limpio |
| Pagina que empieza en columna mayor que cero | Se sirve por acumulado de esa pagina; puede terminar limpia sin alcanzar el blando |
| Fichero de lineas vacias | Corta el tope de lineas; el presupuesto de caracteres no interviene |
| Fuente Java de mil lineas y 27 KB | Corta el tope de lineas; el presupuesto de caracteres no interviene |
| Fin exacto en un salto de linea | No es un corte parcial; la cabecera usa el formato simple |
| `column` mayor o igual que la longitud de la linea de `offset` | Es un parametro invalido y debe rechazarse de forma explicita |
| `offset` mas alla del final del recurso | Se conserva el comportamiento actual de error |
| Recurso vacio | Documento vacio valido, sin cambios |

## 8. Lo que no cambia

- El protocolo general de respuesta: estado, identificador de recurso, marca de vacio, rango, total de lineas, pista y separador.
- El caracter de uso restringido: la herramienta solo se invoca con un HINT previo.
- El podado de contexto y su comportamiento sobre la cabecera.
- La semantica de `offset` y del tope de lineas.
- El identificador de recurso y su ciclo de vida.

## 9. Efectos colaterales esperados

**Mas paginas por recurso.** Con lineas de longitud corriente, el presupuesto de caracteres se agota alrededor de la linea 350 o 400, muy antes que el tope de mil. Un fichero que antes se leia en una llamada pasara a necesitar tres. No es un defecto, pero cambia la ergonomia y debe estar previsto.

**La instruccion del sistema de la herramienta hay que reescribirla.** El texto que explica el protocolo documenta hoy una sola forma de `LINE_RANGE`. Si la cabecera adopta dos y la instruccion solo describe una, el modelo interpretara la nueva como una anomalia. La instruccion debe incluir las dos formas con un ejemplo de cada una y la semantica de `column`.

**El tamano objetivo en tokens.** Cuarenta mil caracteres son del orden de diez mil tokens: el rango alto de lo razonable para una pagina. Los umbrales deberian ser constantes con nombre, no literales dispersos, porque son el parametro que se ajusta cuando cambia la ventana del modelo o el coste por token.

## 10. Fuera de alcance

Este cambio **no resuelve** dos problemas con los que comparte sintoma:

- **El pico de memoria de la busqueda.** Ocurre antes de la paginacion, al acumular los resultados de la busqueda y al volcarlos al fichero temporal. Para ese camino la estrategia adecuada no es partir lineas sino truncar con ventana alrededor de la coincidencia.
- **La ruta de memoria.** Cualquier texto grande que entre por otra via sigue llegando intacto al calculo del embedding. La paginacion protege el contexto; no protege la memoria.

Tampoco entra en este cambio:

- Un chunker que devuelva bloques en lugar de lineas.
- Paginar por caracteres de forma nativa, descartando la nocion de linea.
- Cambiar el formato del separador entre cabecera y contenido.

## 11. Decisiones

**Cerradas:**

- Presupuesto blando de 30.000 caracteres y duro de 40.000.
- La decision de parada se evalua al terminar cada linea.
- El tope de mil lineas se mantiene como guarda independiente.
- `offset` conserva su significado de linea; se anade `column` como desplazamiento dentro de la linea.
- Cabecera en formato simple cuando ambos extremos son lineas completas; formato extendido uniforme en cuanto uno de los dos es parcial.
- El separador entre inicio y fin de rango es un guion.

**Abiertas:**

- Si el umbral duro debe poder fijarse por llamada o queda como constante.
- Si el umbral blando se queda fijo siempre.
- El texto definitivo de la instruccion del sistema de la herramienta.

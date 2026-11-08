# Gestion de tareas y continuidad del agente

> Documento de trabajo. Recoge las ideas discutidas el 25-oct-2026 sobre como mantener
> un plan de tareas anclado durante la ejecucion y como evitar que el agente lo
> abandone a mitad. No es una especificacion cerrada: hay decisiones tomadas, riesgos
> identificados y cuestiones abiertas.

## 1. Motivacion

El procedimiento analisis -> especificacion -> tareas -> ejecucion, materializado en
`ui-architecture.md` y `ui-architecture-tasks.md`, funciona. Pero descansa en que el
agente recuerde que esta dentro de un plan, y eso falla: termina una tarea, pierde el
hilo y se para sin coger la siguiente.

La conclusion de partida es que el problema no se arregla con mas instrucciones en el
prompt. Se arregla con un mecanismo que reinyecte el estado desde el propio fichero.

## 2. Estados de la tarea

Actuales: `[ ]` pendiente, `[~]` en curso, `[x]` terminada.

Propuesto: aniadir `[-]` abortada o cancelada.

Sin ese estado, una tarea irrealizable queda pendiente para siempre: N2 nunca llega a
cero y el aviso de estado no desaparece nunca. Es la valvula de escape del sistema.

Condiciones:

- El motivo NO va en la linea de la tarea. Va en un fichero de incidencias aparte, en
  modo append: "tarea X cancelada por ...".
- Pero el ESTADO si va en la linea. El contador se deriva de la lista de tareas; si la
  cancelacion solo se anota en el fichero de incidencias, el contador no se entera y
  miente.
- Cancelar y registrar deben ser una sola operacion. Dos escrituras encadenadas por el
  modelo es precisamente la disciplina que ya sabemos que falla.

## 3. Aviso de estado

Forma: "Tienes N1 tareas en curso y N2 pendientes, tareas en /x/y/tasks.md".

Propiedades:

- Determinista: se recalcula desde el fichero en cada turno. No hay estado que
  mantener ni que persistir. Si el agente se reinicia, reaparece correcto.
- Efimero: se inyecta como ultimo mensaje y no se propaga por el historial. No pasa por
  el consolidador, asi que no se degrada ni se recorta.
- Autolimitado: desaparece cuando N1 = N2 = 0. Su coste total es proporcional a la
  duracion del plan, no a la del agente.
- No rompe la cache de prefijo: al ser el ultimo mensaje, todo lo anterior queda
  estable. Solo hay dos roturas reales por plan: cuando aparece y cuando desaparece.

Punto debil: el aviso es tan veraz como el fichero. Si el agente olvida marcar `[x]`,
el contador no decrementa y el aviso miente para siempre. Amplifica los fallos de
disciplina, no los corrige.

Caso util: N1 = 0 con N2 > 0 seniala que cerro una tarea y no abrio la siguiente.

## 4. Empujes: ligeros y pesados

Dos niveles, mismo origen, distinto destino:

- Ligero: notificacion efimera, del orden de 25 tokens. Informa del estado. No arranca
  turno.
- Pesado: evento en la cola, un turno completo. Levanta al agente.

Son complementarios, no alternativos: el ligero informa, el pesado mueve. Un aviso
efimero no saca al agente del paron porque el paron es, precisamente, la ausencia de
turno.

Solo los pesados necesitan limite: contador por tarea y corte tras K intentos sin que
cambie el fichero. Cada pesado cuesta un turno con todo el contexto.

## 5. El bucle de eventos

Noema funciona a base de eventos: un bucle infinito lee eventos y se los pasa al LLM.
El teclado inyecta un evento con el mensaje del usuario. Por tanto, empujar es
simplemente encolar un evento, y eso se puede hacer desde la propia operacion.

Con eso, el problema deja de ser tecnico y pasa a ser politica de encolado:

- Prioridad minima y solo con la cola vacia. Asi no compite con el usuario ni aparece
  en mitad de una conversacion ajena.
- Caso peligroso: el agente pregunta "procedo?", el usuario se levanta, la cola se
  vacia y el empuje le dice que coja la siguiente tarea. El evento deberia llevar la
  condicion, o el agente declarar la tarea como "esperando confirmacion" para que quien
  encola lo respete.
- Interrupcion: si el usuario escribe mientras el bucle procesa un empuje, su evento
  espera detras de una respuesta que nadie pidio. Hace falta cancelacion de
  razonamiento, ya prevista en la arquitectura de resiliencia sensorial.

## 6. Entrada en modo plan

Idea: una tool que el LLM invoca para entrar en "sigue este plan de tareas", pidiendo
confirmacion al usuario si o si.

Restriccion: una tool no espera respuesta humana, se ejecuta y devuelve. La garantia
tiene que ser mecanica, no una convencion.

Alternativa de dos pasos con invariante de turno:

- `request_plan_mode(file)`: escribe el estado "pendiente de confirmacion" y no activa
  nada.
- `confirm_plan_mode()`: solo tiene efecto si existe una peticion pendiente creada en
  un turno ANTERIOR. Si se creo en el mismo turno, falla.

Ese invariante impide que el agente pida y confirme en la misma respiracion: para que
`confirm` funcione tiene que haber pasado un turno de usuario entre medias.

Nota: algunas tools ya piden confirmacion al usuario directamente. Si ese mecanismo
existe y es reutilizable, es la via preferente y este apartado se simplifica.

Ciclo de vida del modo: fichero asociado, estado activo, revocacion explicita y
caducidad.

## 7. Responsabilidad y revocacion

Si el usuario carga una lista y dice "ponte con ello", es su decision que el agente
encadene tareas y se autoconteste. Si quiere control, dira "haz la siguiente" y estara
para contestar. No es un fallo del sistema: es un modo.

Unica reserva: el modo debe ser un estado explicito y revocable, no una inferencia de
algo dicho cuarenta turnos atras. Si vive solo en el historial, un "hazlas todas" de
hace una hora sigue gobernando aunque el usuario haya cambiado de tema.

Revocacion: al cerrar sesion, al decir "para", o al recibir cualquier instruccion que
no sea de continuidad.

## 8. Avisos que sugieren accion

Un aviso que solo informa se ignora. El que sugiere la herramienta concreta es el que
mueve. Ejemplo: "hay recursos pendientes de anotar que se van a podar, usa la
herramienta de anotar".

Condiciones:

- Emisor de confianza. Solo el sistema. Nunca texto construido a partir de contenido
  externo (ficheros, webs, mensajes de usuario): seria inyeccion de instrucciones por
  la puerta de atras.
- El aviso no debe desaparecer solo si se obedece. Si la unica forma de silenciarlo es
  ejecutar la herramienta, el agente la ejecutara con cualquier cosa para quitarselo de
  encima. La condicion de desaparicion es la del hecho subyacente (quedan 0), no la de
  haber invocado la tool.
- Frecuencia acotada para los accionables. Los de estado pueden ir en cada turno; los
  de accion, no.

## 9. Cuestiones abiertas

- Como detectar el paron de forma fiable. Candidato: hay `[~]`, el fichero no cambio en
  K turnos y el ultimo mensaje no contiene pregunta al usuario. El tercer punto es el
  mas fragil; por eso conviene que el agente declare el estado del turno ("esperando
  confirmacion" / "sin actividad") en lugar de que el servidor lo adivine.
- Donde vive el estado del modo plan: settings, estado de sesion o el propio fichero.
- Formato y ubicacion del fichero de incidencias.
- Valor de K y comportamiento al agotar los empujes.
- Si el aviso de estado es una operacion de memoria proyectada, con el mismo patron que
  la percepcion de filesystem pendiente, o va por otro canal.
- Varios planes activos a la vez.

## 10. Relacion con deudas existentes

- Memoria episodica: el fallo era la falta de disparador mecanico, no de instruccion.
  Este mecanismo es la respuesta a ese diagnostico para el caso concreto de un plan.
- Consolidador: lo efimero escapa a la sintesis progresiva. Es la unica via que no se
  degrada con el tiempo.
- Memoria proyectada: la operacion de percepcion de filesystem sigue pendiente y
  comparte patron (operacion autonoma, texto generado cada turno, prioridad fija).

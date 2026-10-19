

1. [x] (alta) grep/find que ignora las rutas prohibidas
2. [x] (media) Parece que el streaming solo va en el thinking y no en la respuesta del LLM.
3. [ ] (baja) grep con lineas inmensas.
4. [ ] (baja) intencion del viaje ¿Deberia estar en primera persona? 
5. [*] (**media**) Soporte para imagenes.No olvidarse de ImagePruningOperation.
6. [ ] (baja) Tendria que plantearme si merece la pena hacer algo para sanear una conversacioon cosas como: 
   - Una llamada a herramienta sin respuesta antes del turno del usuario.
   - Una respuesta de herramienta sin llamada 
   - Dos turnos de usuario seguidos.
   - Un turno de LLM como primero de una conversacion.
  
   Y habria que ver quien hace eso, tal vez deberia encargarse la memoria proyectada.
   La conversacion es la que es por las razones que sea pero en el momento de enviarla al LLM 
   debe estar "bien".
  
7. [ ] Consolidacion en segundo plano.
8. [x] (alta) Que se acuerde de las pestañas abiertas en el editor (es muy incomodo 
   perderlas en cada reinicio.
9. [ ] (media) repasar por que esta creciendo la memoria consolidada mas de lo esperado.
10. [ ] (**alta**) repasar el que la memoria proyectada este oscilando entre 200kb y 500kb.
    He obserbado que quizas tenga que ver con la herramienta search_full_history, mirar 
    el log "context-default-2026-10-17 14:33:21.253637356.json"
    Tal vez se podria mitigar añadiendo un resumen/titulo de 200 caracteres generados
    por un modelo local pequeño y rapido de los turnos, y metido en un campo mas de la
    tabla de la memoria episodica, y devolver eso en lugar del turno completo, o barajar
    devolver menos turnos, ahora esta devolviendo 15, y ademas no se podan de la memoria
    proyectada. Valorar si los turnos de esta herramienta los podamos de la proyectada y
    los mantenemos en la episodica, y se usaran en la consolidada. A la hora de podarlos
    en lugar de solo incluir el mensaje de que se han podado los resultados, habria que
    poner un resumen de lo que se ha encontrado, algo como era X resultados y estas son
    las citas que se habian encontrado. Por ejemplo:
    ```
    [búsqueda "aparcar para luego": 4 resultados — {cite:720} {cite:704} {cite:707} {cite:710}]
    ```

    Hemos quedado subir el umbral por defecto a 0.5 y mentener el maximos de turnos.
    **No es preciso implementar una operacion solo el trimResults de la herramienta.**

11. [x] Que las busquedas web funcionen.
12. [ ] herramienta para el LLM para que pueda abrir una pestaña nueva y cargar un texto arbitrario.
13. [ ] Posibilidad de poder presentar en el editor (depende de 12) el git diff de un fichero.
    Modulo de scriptint git.
14. [ ] que en la entrada del char cambiemos el compartamiento de enter/ctrl+enter.
    * enter, que solo haga un enter en la caja de texto.
    * ctrl+enter, envie los datos.
    
    En el uso normal, acabo escribiendo mas entradas de varias lineas
    que de una sola, y en cuento me despisto le doy enter y se envia 
    a mitad.

15. [ ] Un boton junto al de enviar que abra una ventana en el navegador
    para que pueda editar de forma mas facil y un par de botones enviar y cancelar.
    La caja de texto de la ventana que sea un ace editor configurado
    para editar markldown.
    Aqui podriamos mantener la secuencia ctrl+enter como acelerador
    de enviar.
16. [ ] mientras trabajo es normal que acabe abriendome un bloc de 
    notas, en mi caso el kate, donde voy escribiendo ideas que 
    van surgiendome pero que aun no he materializado. Voy tomando
    notas para mas tarde. Me toca ir saltando entre el kate y el 
    navegador. Tal vez seria interesante que ademas de las pestañas de conversacion y contexto proyectado, tuviese una mas con un ace 
    editor que dijese notas. Estas notas podrian tener un boton de 
    guardar que automaticamente se guardasen en un fichero en var/lib

    Pensandolo bien, lo ideal seria que el workspace (tree+editores)
    estuviese en una pestaña "workspace" y añadir ahi la de notas.
    Con eso el salto entre tomar notas de la conversacion y la 
    conversacion seria mas fluido.
    
17. [ ] poner en el agente el metodo walkfs y extraer una clase AbstractAgent 
    
18. [ ] Modificar la memoria proyectada para que esta tenga los limites de las zonas de poda 
    y las operaciones los consulten con un metodo. Seria ideal que esa consulta llevase como
    parametro la herramienta de alguna manera aunque no se bien como por que se consultan
    para establecer las zonas y luego se recorren los turnos.

19. [ ] hay que repasar las especificaciones de las herramientas y las funciones de fsModule para comprobar
    que se indica que las rutas tienen que ser absolutas.





1. [x] (alta) grep/find que ignora las rutas prohibidas
2. [x] (media) Parece que el streaming solo va en el thinking y no en la respuesta del LLM.
3. [ ] (baja) grep con lineas inmensas.
4. [ ] (baja) intencion del viaje ¿Deberia estar en primera persona? 
5. [*] (media) Soporte para imagenes 
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
10. [ ] (media) repasar el que la memoria proyectada este oscilando entre 200kb y 500kb.
    He obserbado que quizas tenga que ver con la herramienta search_full_history, mirar 
    el log "context-default-2026-10-17 14:33:21.253637356.json"
    Tal vez se podria mitigar añadiendo un resumen/titulo de 200 caracteres generados
    por un modelo local pequeño y rapido de los turnos, y metido en un campo mas de la
    tabla de la memoria episodica, y devolver eso en lugar del turno completo, o barajar
    devolver menos turnos, ahora esta devolviendo 15, y ademas no se podan de la memoria
    proyectada. Valorar si los turnos de esta herramienta los podamos de la proyectada y
    los mantenemos en la episodica, y se usaran en la consolidada.

11. [x] Que las busquedas web funcionen.
12. [ ] herramienta para el LLM para que pueda abrir una pestaña nueva y cargar un texto arbitrario.
13. [ ] Posibilidad de poder presentar en el editor (depende de 12) el git diff de un fichero.
    Modulo de scriptint git.



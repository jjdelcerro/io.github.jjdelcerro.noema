
Habria que ver de abordar la refactorizacion de la gestion de eventos para llevarlo todo a un paquete y acabar disponiendo de una clase cola de eventos que la tenga el conversation service (o el agente), moviendo la logica que hay en el conversarion sercice aqui.

Esta cola tendria que tener en cuenta:

* Posibilidad de recuperar un agregador de eventos.
  Cuando se haga un get de la cola no devolverte un solo evento sino una lista de eventos hasta un maximo de X.
  
* Posibilidad de compactar eventos.
  En funcion del tipo de evento ver si se pueden hacer "compactaciones" del tipo de juntar en un solo evento "Y se han recivido 20 eventos de este tipo".
  
* Posibilidad de unir eventos.
  En funcion del tipo de evento poder unir en uno solo varios eventos. Por ejemplo, si llegan tres eventos seguidos de mensajes de telegram se podrian meter en un solo evento los tres mensajes indicando la hora de cada uno.
  
* Posibilidad de tener tipos de eventos de algo asi como "evento unico".
  Solo puede haber un evento de este tipo en la cola. Si se añade uno nuevo se eliminan de la cola el que hubiese antes.
  Ejemplo: ha llegado correo.
  
  
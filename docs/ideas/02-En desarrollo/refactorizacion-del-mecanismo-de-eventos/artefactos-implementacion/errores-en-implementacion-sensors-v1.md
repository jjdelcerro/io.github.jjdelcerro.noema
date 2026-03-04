

1.  Lee la clase Event (io.github.jjdelcerro.noema.lib.impl.services.conversation).
    ¿Ves los metodos toJson, getAiMessage y getResponseMessage?
    La clase AbstractSensorEvent deberia tener metodos similares.
    ¿Lo ves viable?
    ¿Que le falta al SensorEvent que tenia el Event?

2.  ¿Deberiamos añadir una atributo text (getText) en la clase AbstractSensorEvent?

3.  La clase SensorEventAggregateImpl pierde el mensaje asociado al evento. El valor de getContent deberia ser algo como `text+". Se han recivido XXX eventos de este tipo."`.

4.  Las herramientas pool_event y persisten objetos en el json ¿Esta registrada y configurada la persistencia de estos en el gson?
    ¿Deberiamos añadirle a las clases que toque un metodo getJSonFactory o algo asi?
 
5.  La clase SensorEventMergeableImpl deberia incluir tambien en el contentsBuilder la fecha al hacer el append (piensa en mensajes de telegram)

6.  No me encaja la existencia de un metodo getPendingEvents en SensorsServiceImpl. Deberia tener un metodo para recuperar un evento, un getEvent o tal vez incluso un peekEvent. Quien consume los eventos decidira cuantos saca y empaqueta donde tenga que hacerlo. De hecho proablemente deberia comportarse como una cola con get/peek/isEmpty.




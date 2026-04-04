package io.github.jjdelcerro.noema.lib.impl.services.memory.tools;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;

public class AnnotateObservationTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "annotate_observation";

  public AnnotateObservationTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description(
"""
Utiliza esta herramienta para guardar una nota o resumen sobre información que acabas de leer o procesar (ej. tras usar file_read o web_search).
Utilizala para guardar:
* Insights, resúmenes o relaciones que infieres entre múltiples fuentes.
* Patrones arquitectónicos o de diseño que identificas al leer ficheros.
* Conexiones que establezcas entre archivos
* Comprensión del flujos que infieras

Recuerda que la herramienta está pensada para retener valor cognitivo, no para duplicar datos,
no la uses para duplicar contenido que puedes recuperar de nuevo con file_read o web_search.
""")
            .addParameter("source", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("El origen de la información (ej: nombre del archivo, URL o 'instrucción del usuario')."))
            .addParameter("note", JsonSchemaProperty.STRING,
                    JsonSchemaProperty.description("Los hechos clave que deseas fijar en tu memoria episódica."))
            .build();
  }

  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public int getType() {
    /*
    ATENCION!! Aqui tenemos un problema.
    Si quiero ser "purista" esta deberia ser una herramienta de tipo "memoria", pero
    al hacerlo ahora mismo se etiquetaria como un "lookup_turn", y eso forzaria ha hacer
    cambios en el reasoning y en el memory service si no queremos que el LLM que va ha 
    generar el punto de guardado se lie.
    De momento, siendo menos purista, la voy a dejar de tipo "operativo", y aprovendo que
    la llamada a una herramienta se guarda tal cual en el turno de la BBDD, ya que solo
    se "truncan" las salidas de las herramientas. Vamos que aprovechamos un efecto 
    secundario de la arquitectura para conseguir lo que queremos. Pero hay que tener
    mucho cuidado con esto ya que si en el futuro decidimos recortar las llamadas a las
    herramientas esto dejaria de funcionar.
    Segun como vaya esto habria que abordar los cambios para tratar la herramienta
    como "memory".
    */
    return TYPE_OPERATIONAL;
//    return TYPE_MEMORY;
  }

  @Override
  public String execute(String jsonArguments) {
    // La herramienta en sí no hace nada funcional, el valor real 
    // está en que el LLM formula los argumentos y el SourceOfTruth los guarda.
    return "{\"status\": \"success\", \"message\": \"Anotación fijada correctamente. Se incluirá en la próxima consolidación de memoria.\"}";
  }
}

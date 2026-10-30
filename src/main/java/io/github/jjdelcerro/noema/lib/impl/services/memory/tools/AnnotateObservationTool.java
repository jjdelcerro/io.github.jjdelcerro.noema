package io.github.jjdelcerro.noema.lib.impl.services.memory.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.AbstractAgentTool;
import io.github.jjdelcerro.noema.lib.impl.tools.ToolSpecificationBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("UseSpecificCatch")
public class AnnotateObservationTool extends AbstractAgentTool {

  public static final String TOOL_NAME = "annotate_observation";

  public AnnotateObservationTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecificationBuilder getSpecification() {
    return ToolSpecificationBuilder.create()
            .name(TOOL_NAME)
            .description(
                    """
Utiliza esta herramienta para guardar una nota o resumen sobre información que acabas de leer o procesar (ej. tras usar file_read o web_search).
Utilizala para guardar:
* Insights, resúmenes o relaciones que infieres entre múltiples fuentes.
* Patrones arquitectónicos o de diseño que identificas al leer ficheros.
* Conexiones que establezcas entre archivos
* Comprensión del flujos que infieras

Recuerda que la herramienta está pensada para retener lo que vive en recursos efímeros.
No la uses para registrar lo que se ha dicho en la conversación: tus mensajes, los del usuario
y tu propio razonamiento no se podan nunca y son recuperables. Anotarlos solo duplica.

**Parámetro opcional resource_id:**
Si estás anotando información extraída de un recurso paginado (por ejemplo, tras una llamada a file_read o web_get_content),
DEBES incluir el campo "resource_id" con el valor exacto que apareció en el campo RESOURCE_ID de la respuesta de esa herramienta.
Esto permite vincular la anotación al recurso original y evita que el sistema te lo recuerde más tarde.
                    
**Uso del parámetro `type`:**
Este campo está reservado para flujos especializados. Por defecto DEBES omitirlo. 
Solo debes incluirlo cuando recibas una instrucción explícita que te indique bajo qué tipo clasificar la información. 

**Criterio de categorización (según la PROCEDENCIA de la información):**
            
1. Si nace de LEER y SINTETIZAR un documento concreto (archivo .md, PDF, especificación, manual, RFC, página web o guía)
   -> **DOCUMENT_SUMMARY**.
   *REGLA DE ORO:* Da igual de qué trate el documento. Si estás destilando un texto o 
    especificación técnica que has leído con file_read o web_get_content, ES SIEMPRE `DOCUMENT_SUMMARY` 
    (incluso si el documento describe la arquitectura, las clases o el diseño de un sistema).

2. Si nace de EXPLORAR DIRECTAMENTE el repositorio/código (árbol de ficheros, estructura de 
   carpetas, pom.xml, rastreo de imports o comandos como file_find/file_grep)
   -> **PROJECT_MAP**.
   *REGLA DE ORO:* Es la cartografía del código o orchivos obtenida por exploración 
   empírica del workspace (qué módulos existen físicamente en disco, qué paquetes 
   cuelgan de dónde, cómo se distribuyen las fuentes). NUNCA uses PROJECT_MAP 
   para resumir un archivo de documentación.

3. Si nace de una regla permanente que ningún cambio futuro debe violar
   -> **INVARIANT**.

4. Si nace de justificar por qué se eligió una solución frente a otra o un compromiso técnico
   -> **DESIGN_DECISION**.

5. Si es un checklist temporal, pasos pendientes o notas inmediatas de trabajo sobre un proyecto
   -> **TASK_SCRATCHPAD**.

""")
            .addStringParameter("source", false, "El origen de la información (ej: nombre del archivo, URL, o 'instrucción del usuario' cuando te haya pedido explícitamente anotar o recordar algo).")
            .addStringParameter("note", false, "Los hechos clave que deseas fijar en tu memoria episódica.")
            .addStringParameter("resource_id", false, "Debes copiar exactamente el RESOURCE_ID proporcionado por la herramienta de lectura (ej. file_read, web_get_content) que genero esta anotación.")
            .addStringParameter("category", false, 
"""
Clasificación ontológica estricta:
- DOCUMENT_SUMMARY: Síntesis de un recurso documental leído (especificaciones técnicas,
  archivos markdown/PDF, RFCs, páginas web, actas). Si la información procede de resumir
  un documento que leíste, usa SIEMPRE esta categoría sin importar su temática técnica.
- PROJECT_MAP: Cartografía del repositorio real obtenida explorando el sistema de 
  archivos (árbol de carpetas, módulos existentes en disco, mapa de paquetes, estructura
  del monorepo). Solo para la radiografía viva del código en el workspace, NUNCA 
  para resúmenes de documentos.
- INVARIANT: Contratos arquitectónicos y reglas inviolables del sistema.
- DESIGN_DECISION: Razones de diseño, alternativas descartadas y compromisos técnicos.
- TASK_SCRATCHPAD: Checklist inmediata o notas de usar y tirar de la tarea en curso.

"""
            )
            .addStringParameter(
                "type", 
                true, 
                "USO RESTRINGIDO. Omitir o dejar vacío por defecto. "
                + "Rellenar ÚNICAMENTE si el usuario, una directiva explícita de tu rol/subagente "
                + "o una notificación del sistema (SYSTEMNOTIFICATION) te ordenan fijar un tipo específico "
                + "para esta anotación. No inventes categorías por iniciativa propia."
            )    
            ;
  }
  
  @Override
  public int getMode() {
    return AgentTool.MODE_READ;
  }

  @Override
  public int getType() {
    return TYPE_ANNOTATION; // Las anotaciones tienen entidad propia.
  }

  @Override
  public ToolResult execute(String jsonArguments) {
    // La herramienta en sí no hace nada funcional, el valor real 
    // está en que el LLM formula los argumentos y el EpisodicMemory los guarda.

    // Parsear argumentos para extraer el resource_id si está presente
    Map<String, Object> args = gson.fromJson(jsonArguments, Map.class);
    String resourceId = args != null ? (String) args.get("resource_id") : null;

    // Construir respuesta incluyendo resource_id si se proporcionó
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "success");
    result.put("message", "Anotación fijada correctamente. Se incluirá en la próxima consolidación de memoria.");
    if (resourceId != null && !resourceId.isBlank()) {
      result.put("resource_id", resourceId);
    }

    return result(gson.toJson(result));
  }

  public String getResourceIdFromResultMessage(ToolExecutionResultMessage message) {
    String text = message.text();
    if (text == null) {
      return null;
    }
    try {
      JsonObject json = JsonParser.parseString(text).getAsJsonObject();
      if (json.has("resource_id")) {
        return json.get("resource_id").getAsString();
      }
    } catch (Exception e) {
      // ignore parse errors
    }
    return null;
  }

  /**
   * Extrae de forma segura el parametro 'type' a partir del texto de la llamada
   * a la herramienta. Soporta tanto JSON directo de argumentos como
   * representaciones textuales envolventes.
   *
   * @param toolCall Cadena con los argumentos o la llamada a la herramienta.
   * @return El tipo de anotacion normalizado, o null si no esta presente o no
   * es valido.
   */
  public static String getAnnotationTypeFromToolCall(String toolCall) {
    if (toolCall == null || toolCall.isBlank()) {
      return null;
    }

    try {
      JsonObject json = JsonParser.parseString(toolCall.trim()).getAsJsonObject();

      // The tool call is stored as { id, name, arguments }. Fall back to the
      // root object so that a bare arguments document is still accepted.
      JsonObject arguments = json.has("arguments") && json.get("arguments").isJsonObject()
              ? json.getAsJsonObject("arguments")
              : json;

      if (!arguments.has("type") || arguments.get("type").isJsonNull()) {
        return null;
      }

      String type = arguments.get("type").getAsString().trim();
      return type.isEmpty() ? null : type;
    } catch (Exception e) {
      // Ignorar errores de parseo y retornar null de forma segura
      return null;
    }
  }
}

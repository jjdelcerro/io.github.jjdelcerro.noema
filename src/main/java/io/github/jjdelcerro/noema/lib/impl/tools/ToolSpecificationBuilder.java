package io.github.jjdelcerro.noema.lib.impl.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 *
 * @author jjdelcerro
 */
public class ToolSpecificationBuilder {

  private JsonObjectSchema.Builder parameters;
  private String name;
  private String description;
  private JsonObjectSchema rawParameters;
  private final Set<String> requiredParameters;

  public ToolSpecificationBuilder() {
    this.requiredParameters = new LinkedHashSet<>();
  }

  /**
   * Establece un esquema ya construido, preservando tipos complejos (arrays
   * anidados, enums, anyOf, etc.) tal cual vienen del servidor MCP.
   * @param rawParameters
   * @return 
   */
  public ToolSpecificationBuilder parameters(JsonObjectSchema rawParameters) {
    this.rawParameters = rawParameters;
    return this;
  }

  private JsonObjectSchema.Builder parameters() {
    if (this.parameters == null) {
      this.parameters = JsonObjectSchema.builder();
    }
    return this.parameters;
  }

  public String name() {
    return this.name;
  }

  public String description() {
    return this.description;
  }

  public ToolSpecificationBuilder name(String name) {
    this.name = name;
    return this;
  }

  public ToolSpecificationBuilder description(String description) {
    this.description = description;
    return this;
  }

  public ToolSpecificationBuilder addStringParameter(String name, boolean optional, String description) {
    this.parameters().addStringProperty(name, description);
    this.registerRequired(name, optional);
    return this;
  }

  public ToolSpecificationBuilder addStringParameter(String name, String description) {
    this.parameters().addStringProperty(name, description);
    this.registerRequired(name, false);
    return this;
  }

  public ToolSpecificationBuilder addIntegerParameter(String name, boolean optional, String description) {
    this.parameters().addIntegerProperty(name, description);
    this.registerRequired(name, optional);
    return this;
  }

  public ToolSpecificationBuilder addIntegerParameter(String name, String description) {
    this.parameters().addIntegerProperty(name, description);
    this.registerRequired(name, false);
    return this;
  }

  public ToolSpecificationBuilder addNumberParameter(String name, boolean optional, String description) {
    this.parameters().addNumberProperty(name, description);
    this.registerRequired(name, optional);
    return this;
  }

  public ToolSpecificationBuilder addNumberParameter(String name, String description) {
    this.parameters().addNumberProperty(name, description);
    this.registerRequired(name, false);
    return this;
  }

  public ToolSpecificationBuilder addStringArrayParameter(String name, boolean optional, String description) {
    JsonArraySchema array = JsonArraySchema.builder().description(description).items(new JsonStringSchema()).build();
    this.parameters().addProperty(name, array);
    this.registerRequired(name, optional);
    return this;
  }

  private void registerRequired(String name, boolean optional) {
    if (!optional) {
      this.requiredParameters.add(name);
    }
  }

  public ToolSpecification build() {
    ToolSpecification.Builder toolSpecification = ToolSpecification.builder();
    toolSpecification.name(name);
    toolSpecification.description(description);
    if (this.rawParameters != null) {
      toolSpecification.parameters(this.rawParameters);
    } else if (this.parameters != null) {
      if (!this.requiredParameters.isEmpty()) {
        this.parameters.required(this.requiredParameters.toArray(new String[0]));
      }
      toolSpecification.parameters(this.parameters.build());
    }
    return toolSpecification.build();
  }

  public static ToolSpecificationBuilder create() {
    return new ToolSpecificationBuilder();
  }
}

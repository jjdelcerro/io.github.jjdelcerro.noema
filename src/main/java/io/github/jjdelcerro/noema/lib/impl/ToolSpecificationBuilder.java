package io.github.jjdelcerro.noema.lib.impl;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

/**
 *
 * @author jjdelcerro
 */
public class ToolSpecificationBuilder {

  private JsonObjectSchema.Builder parameters;
  private String name;
  private String description;

  public ToolSpecificationBuilder() {
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
    return this;
  }

  public ToolSpecificationBuilder addStringParameter(String name, String description) {
    this.parameters().addStringProperty(name, description);
    return this;
  }

  public ToolSpecificationBuilder addIntegerParameter(String name, boolean optional, String description) {
    this.parameters().addIntegerProperty(name, description);
    return this;
  }

  public ToolSpecificationBuilder addIntegerParameter(String name, String description) {
    this.parameters().addIntegerProperty(name, description);
    return this;
  }

  public ToolSpecificationBuilder addNumberParameter(String name, boolean optional, String description) {
    this.parameters().addNumberProperty(name, description);
    return this;
  }

  public ToolSpecificationBuilder addNumberParameter(String name, String description) {
    this.parameters().addNumberProperty(name, description);
    return this;
  }

  public ToolSpecificationBuilder addStringArrayParameter(String name, boolean optional, String description) {
    JsonArraySchema array = JsonArraySchema.builder().description(description).items(new JsonStringSchema()).build();
    this.parameters().addProperty(name, array);
    return this;
  }

  public ToolSpecification build() {
  ToolSpecification.Builder toolSpecification = ToolSpecification.builder();
  toolSpecification.name(name);
  toolSpecification.description(description);
    if (this.parameters != null) {
      toolSpecification.parameters(this.parameters.build());
    }
    return toolSpecification.build();
  }
  
  public static ToolSpecificationBuilder create() {
    return new ToolSpecificationBuilder();
  }
}

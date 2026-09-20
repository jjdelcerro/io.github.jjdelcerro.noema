package io.github.jjdelcerro.noema.lib;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Contract defining the declarative recipe and configuration of a subagent.
 */
public interface SubagentDefinition {

  /**
   * Supported parameter types for declarative subagent inputs.
   */
  enum SubagentParamType {
    STRING,
    PATH,
    FILE,
    DIRECTORY,
    INTEGER,
    BOOLEAN;

    public static SubagentParamType fromString(String val) {
      if (val == null) {
        return STRING;
      }
      return switch (val.trim().toLowerCase()) {
        case "path" ->
          PATH;
        case "file" ->
          FILE;
        case "dir", "directory" ->
          DIRECTORY;
        case "int", "integer" ->
          INTEGER;
        case "bool", "boolean" ->
          BOOLEAN;
        default ->
          STRING;
      };
    }
  }

  /**
   * Supported access control operations for declarative subagent inputs.
   */
  enum SubagentAccessControlOperation {
    NOP,
    EXTENDS_PARENT,
    ADD_ALLOWED_PATH,
    ADD_NONWRITABLE_PATH,
    ADD_NONREADABLE_PATH;

    public static SubagentAccessControlOperation fromString(String val) {
      if (val == null) {
        return NOP;
      }
      return switch (val.trim().toLowerCase()) {
        case "EXTENDS_PARENT", "extendsParent" ->
          EXTENDS_PARENT;
        case "ADD_ALLOWED_PATH", "addAllowedPath" ->
          ADD_ALLOWED_PATH;
        case "ADD_NONWRITABLE_PATH", "addNonWritablePath" ->
          ADD_NONWRITABLE_PATH;
        case "ADD_NONREADABLE_PATH", "addNonReadablePath" ->
          ADD_NONREADABLE_PATH;
        default ->
          NOP;
      };
    }
  }

  /**
   * Immutable representation of a parameter definition expected by the
   * subagent.
   */
  record SubagentParam(String name, SubagentParamType type, String description) {

    public SubagentParam {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Parameter name cannot be null or blank");
      }
      type = (type != null) ? type : SubagentParamType.STRING;
      description = (description != null) ? description.trim() : "";
    }
  }

  /**
   * Immutable representation of a AccessControl operation defined for the
   * subagent.
   */
  record SubagentAccessControl(SubagentAccessControlOperation operation, String path) {

    public SubagentAccessControl {
      operation = (operation != null) ? operation : SubagentAccessControlOperation.NOP;
    }
  }

  /**
   * Returns the unique technical identifier of the subagent recipe.
   * @return 
   */
  String getName();

  /**
   * Returns the functional description of the subagent.
   * @return 
   */
  String getDescription();

  /**
   * Returns the list of declared parameters for this subagent.
   * @return 
   */
  List<SubagentParam> getParams();
  
  List<SubagentAccessControl> getAccessControl();

  String resolvePlaceholders(String template, Map<String, ?> parameters);
  
  /**
   * Returns the declared parameter matching the given name, or null if not
   * found.
   * @return 
   */
  SubagentParam getParam(String name);

  /**
   * Returns the strict whitelist of tool names authorized for this subagent.
   * @return 
   */
  List<String> getTools();

  /**
   * Returns the specific model identifier assigned to this subagent, or null to
   * inherit from parent.
   * @return 
   */
  String getModelId();

  /**
   * Returns the maximum execution timeout in seconds.
   * @return 
   */
  int getTimeoutSeconds();

  /**
   * Returns the system prompt template defining the worker's role and rules.
   * @return 
   */
  String getSystemPrompt();

  /**
   * Returns the custom memory compaction prompt template, or null for standard
   * fallback.
   * @return 
   */
  String getMemoryPrompt();

  /**
   * Returns the Phase 1 initial task prompt template.
   * @return 
   */
  String getPromptIni();

  /**
   * Returns the Phase 2 synthesis prompt template, or null if single-phase.
   * @return 
   */
  String getPromptFin();

  /**
   * Returns true if a Phase 2 synthesis prompt is defined.
   * @return 
   */
  boolean hasPromptFin();

  /**
   * Returns true if a custom system prompt is defined.
   * @return 
   */
  boolean hasSystemPrompt();

  /**
   * Returns true if a custom memory prompt is defined.
   * @return 
   */
  boolean hasMemoryPrompt();

  /**
   * Resolves placeholders in the initial prompt using provided variables.
   * @return 
   */
  String resolvePromptIni(Map<String, ?> params);

  /**
   * Resolves placeholders in the final prompt using provided variables.
   * @return 
   */
  String resolvePromptFin(Map<String, ?> params);
}

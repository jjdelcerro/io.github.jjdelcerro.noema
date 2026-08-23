package io.github.jjdelcerro.noema.lib.impl;

import io.github.jjdelcerro.noema.lib.SubagentDefinition;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable implementation of SubagentDefinition with XML DOM parsing
 * factories.
 */
public class SubagentDefinitionImpl implements SubagentDefinition {

  public static final int DEFAULT_TIMEOUT_SECONDS = 300;

  private final String name;
  private final String description;
  private final List<SubagentParam> params;
  private final List<String> tools;
  private final String modelId;
  private final int timeoutSeconds;
  private final String systemPrompt;
  private final String memoryPrompt;
  private final String promptIni;
  private final String promptFin;

  public SubagentDefinitionImpl(
          String name,
          String description,
          List<SubagentParam> params,
          List<String> tools,
          String modelId,
          int timeoutSeconds,
          String systemPrompt,
          String memoryPrompt,
          String promptIni,
          String promptFin
  ) {
    this.name = Objects.requireNonNull(name, "Subagent name cannot be null");
    this.description = description != null ? description.trim() : "";
    this.params = params != null ? Collections.unmodifiableList(new ArrayList<>(params)) : Collections.emptyList();
    this.tools = tools != null ? Collections.unmodifiableList(new ArrayList<>(tools)) : Collections.emptyList();
    this.modelId = StringUtils.trimToNull(modelId);
    this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    this.systemPrompt = StringUtils.trimToNull(systemPrompt);
    this.memoryPrompt = StringUtils.trimToNull(memoryPrompt);
    this.promptIni = promptIni != null ? promptIni.trim() : "";
    this.promptFin = StringUtils.trimToNull(promptFin);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public List<SubagentParam> getParams() {
    return params;
  }

  @Override
  public SubagentParam getParam(String paramName) {
    if (paramName == null) {
      return null;
    }
    for (SubagentParam p : this.params) {
      if (p.name().equalsIgnoreCase(paramName.trim())) {
        return p;
      }
    }
    return null;
  }

  @Override
  public List<String> getTools() {
    return tools;
  }

  @Override
  public String getModelId() {
    return modelId;
  }

  @Override
  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  @Override
  public String getSystemPrompt() {
    return systemPrompt;
  }

  @Override
  public String getMemoryPrompt() {
    return memoryPrompt;
  }

  @Override
  public String getPromptIni() {
    return promptIni;
  }

  @Override
  public String getPromptFin() {
    return promptFin;
  }

  @Override
  public boolean hasPromptFin() {
    return promptFin != null && !promptFin.isBlank();
  }

  @Override
  public boolean hasSystemPrompt() {
    return systemPrompt != null && !systemPrompt.isBlank();
  }

  @Override
  public boolean hasMemoryPrompt() {
    return memoryPrompt != null && !memoryPrompt.isBlank();
  }

  @Override
  public String resolvePromptIni(Map<String, ?> parameters) {
    return resolvePlaceholders(this.promptIni, parameters);
  }

  @Override
  public String resolvePromptFin(Map<String, ?> parameters) {
    if (this.promptFin == null) {
      return null;
    }
    return resolvePlaceholders(this.promptFin, parameters);
  }

  /**
   * Replaces all occurrences of '{KEY}' with the corresponding value from the
   * params map.
   */
  public static String resolvePlaceholders(String template, Map<String, ?> parameters) {
    if (StringUtils.isBlank(template) || parameters == null || parameters.isEmpty()) {
      return template != null ? template : "";
    }
    String result = template;
    for (Map.Entry<String, ?> entry : parameters.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
      result = StringUtils.replace(result, "{" + key + "}", value);
    }
    return result;
  }

  // =========================================================================
  // STATIC XML PARSING FACTORIES
  // =========================================================================
  public static SubagentDefinition from(Path xmlPath) throws IOException {
    if (xmlPath == null || !Files.exists(xmlPath)) {
      throw new IllegalArgumentException("XML descriptor path does not exist: " + xmlPath);
    }
    String fallbackName = FilenameUtils.getBaseName(xmlPath.getFileName().toString());
    try (InputStream in = Files.newInputStream(xmlPath)) {
      return from(in, fallbackName);
    }
  }

  public static SubagentDefinition from(String xmlContent, String fallbackName) throws IOException {
    if (StringUtils.isBlank(xmlContent)) {
      throw new IllegalArgumentException("XML content cannot be empty");
    }
    try (StringReader reader = new StringReader(xmlContent)) {
      return parseDocument(new InputSource(reader), fallbackName);
    }
  }

  public static SubagentDefinition from(InputStream inputStream, String fallbackName) throws IOException {
    if (inputStream == null) {
      throw new IllegalArgumentException("InputStream cannot be null");
    }
    return parseDocument(new InputSource(inputStream), fallbackName);
  }

  private static SubagentDefinition parseDocument(InputSource inputSource, String fallbackName) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      try {
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      } catch (Exception ignored) {
        // Ignore unsupported parser features
      }

      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(inputSource);
      Element root = doc.getDocumentElement();

      if (!"subagent".equalsIgnoreCase(root.getTagName())) {
        throw new IllegalArgumentException("Root element must be <subagent>, found: <" + root.getTagName() + ">");
      }

      String name = root.getAttribute("name");
      if (StringUtils.isBlank(name)) {
        name = getChildText(root, "name");
      }
      if (StringUtils.isBlank(name)) {
        name = fallbackName;
      }
      if (StringUtils.isBlank(name)) {
        name = "unnamed_subagent";
      }

      String description = getChildText(root, "description");
      String modelId = getChildText(root, "model_id");

      int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
      String timeoutStr = getChildText(root, "timeout");
      if (StringUtils.isBlank(timeoutStr)) {
        timeoutStr = getChildText(root, "timeout_seconds");
      }
      if (StringUtils.isNotBlank(timeoutStr)) {
        try {
          timeoutSeconds = Integer.parseInt(timeoutStr.trim());
        } catch (NumberFormatException e) {
          timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }
      }

      List<SubagentParam> params = parseParams(root);
      List<String> tools = parseTools(root);
      String systemPrompt = getChildText(root, "system_prompt");
      String memoryPrompt = getChildText(root, "memory_prompt");
      String promptIni = getChildText(root, "prompt_ini");
      String promptFin = getChildText(root, "prompt_fin");

      return new SubagentDefinitionImpl(
              name.trim(),
              description,
              params,
              tools,
              modelId,
              timeoutSeconds,
              systemPrompt,
              memoryPrompt,
              promptIni,
              promptFin
      );

    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("Failed to parse subagent XML definition: " + e.getMessage(), e);
    }
  }

  private static List<SubagentParam> parseParams(Element root) {
    List<SubagentParam> paramsList = new ArrayList<>();
    NodeList paramsContainers = root.getElementsByTagName("params");

    if (paramsContainers.getLength() > 0) {
      Element paramsEl = (Element) paramsContainers.item(0);
      NodeList paramNodes = paramsEl.getElementsByTagName("param");

      for (int i = 0; i < paramNodes.getLength(); i++) {
        Node node = paramNodes.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
          Element paramEl = (Element) node;
          String paramName = paramEl.getAttribute("name");
          if (StringUtils.isBlank(paramName)) {
            continue;
          }

          String typeStr = paramEl.getAttribute("type");
          SubagentParamType type = SubagentParamType.fromString(typeStr);

          String desc = paramEl.getAttribute("description");
          if (StringUtils.isBlank(desc)) {
            desc = paramEl.getTextContent();
          }

          paramsList.add(new SubagentParam(paramName.trim(), type, desc));
        }
      }
    }
    return paramsList;
  }

  private static List<String> parseTools(Element root) {
    List<String> tools = new ArrayList<>();
    NodeList toolsContainers = root.getElementsByTagName("tools");

    if (toolsContainers.getLength() > 0) {
      Element toolsEl = (Element) toolsContainers.item(0);
      NodeList toolNodes = toolsEl.getElementsByTagName("tool");

      for (int i = 0; i < toolNodes.getLength(); i++) {
        Node node = toolNodes.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
          Element toolEl = (Element) node;
          String toolName = toolEl.getAttribute("name");
          if (StringUtils.isBlank(toolName)) {
            toolName = toolEl.getTextContent();
          }
          if (StringUtils.isNotBlank(toolName)) {
            tools.add(toolName.trim());
          }
        }
      }
    }
    return tools;
  }

  private static String getChildText(Element parent, String tagName) {
    NodeList list = parent.getElementsByTagName(tagName);
    if (list.getLength() == 0) {
      return null;
    }
    Node node = list.item(0);
    return node.getTextContent();
  }
}

package io.github.jjdelcerro.noema.lib.impl.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/**
 * Utility to convert raw conversation JSON dumps to structured XML documents.
 * Transcodes nested JSON payloads into real XML nodes and wraps multiline text
 * inside verbatim CDATA blocks.
 *
 * @author jjdelcerro
 */
public final class ConversationJsonToXml {

  private static final String DEFAULT_XSL_HREF = "conversation.xsl";

  private ConversationJsonToXml() {
    // Utility class, no instances allowed
  }

  /**
   * Core streaming method: reads JSON from reader and writes XML to writer.
   *
   * @param in Source reader containing conversation JSON
   * @param out Target writer for the resulting XML
   * @throws XMLStreamException If XML generation fails
   * @throws IOException If reading or writing fails
   */
  public static void json2xml(Reader in, Writer out) throws XMLStreamException, IOException {
    json2xml(in, out, DEFAULT_XSL_HREF);
  }

  /**
   * Core streaming method with custom XSLT stylesheet reference.
   *
   * @param in Source reader containing conversation JSON
   * @param out Target writer for the resulting XML
   * @param xslHref Path or URL to the XSL stylesheet (null to omit)
   * @throws XMLStreamException If XML generation fails
   * @throws IOException If reading or writing fails
   */
  public static void json2xml(Reader in, Writer out, String xslHref) throws XMLStreamException, IOException {
    JsonElement root = JsonParser.parseReader(in);
    if (root == null || root.isJsonNull()) {
      return;
    }

    XMLOutputFactory factory = XMLOutputFactory.newInstance();
    XMLStreamWriter writer = factory.createXMLStreamWriter(out);

    try {
      writer.writeStartDocument("UTF-8", "1.0");
      writer.writeCharacters("\n");

      if (xslHref != null && !xslHref.isBlank()) {
        writer.writeProcessingInstruction("xml-stylesheet", "type=\"text/xsl\" href=\"" + xslHref + "\"");
        writer.writeCharacters("\n");
      }

      writeStartTag(writer, "conversation", 0);

      if (root.isJsonArray()) {
        JsonArray array = root.getAsJsonArray();
        int turnCounter = 1;
        for (JsonElement item : array) {
          if (item.isJsonObject()) {
            processConversationNode(writer, item.getAsJsonObject(), turnCounter);
            turnCounter++;
          }
        }
      } else if (root.isJsonObject()) {
        processConversationNode(writer, root.getAsJsonObject(), 1);
      }

      writeEndTag(writer, "conversation", 0);
      writer.writeCharacters("\n");
      writer.writeEndDocument();
      writer.flush();
    } finally {
      writer.close();
    }
  }

  /**
   * Convenience method for String in / String out.
   */
  public static String json2xml(String json) throws XMLStreamException, IOException {
    StringWriter out = new StringWriter();
    try (Reader in = new StringReader(json)) {
      json2xml(in, out);
    }
    return out.toString();
  }

  /**
   * Convenience method for file-to-file transformation.
   */
  public static void json2xml(Path jsonPath, Path xmlPath) throws XMLStreamException, IOException {
    try (Reader in = new FileReader(jsonPath.toFile(), StandardCharsets.UTF_8); Writer out = new FileWriter(xmlPath.toFile(), StandardCharsets.UTF_8)) {
      json2xml(in, out);
    }
  }

  // --- Private Processing Logic ---
  private static void processConversationNode(XMLStreamWriter writer, JsonObject node, int index) throws XMLStreamException {
    // Case 1: Tool execution result
    if (node.has("toolName")) {
      writeToolResultNode(writer, node);
      return;
    }

    // Case 2: User turn (has contents array with TEXT type)
    if (node.has("contents") && !node.has("toolName")) {
      writeUserTurnNode(writer, node, index);
      return;
    }

    // Case 3: Assistant turn (has thinking, toolExecutionRequests, or text with attributes)
    if (node.has("thinking") || node.has("toolExecutionRequests") || (node.has("text") && node.has("attributes"))) {
      writeAssistantTurnNode(writer, node, index);
      return;
    }

    // Case 4: System prompt or Context block (plain text object without interaction metadata)
    if (node.has("text")) {
      writeSystemContextNode(writer, node);
    }
  }

  private static void writeSystemContextNode(XMLStreamWriter writer, JsonObject node) throws XMLStreamException {
    writeStartTag(writer, "system_context", 1);
    String text = node.get("text").getAsString();
    writeTextElementWithCData(writer, "content", text, 2);
    writeEndTag(writer, "system_context", 1);
  }

  private static void writeUserTurnNode(XMLStreamWriter writer, JsonObject node, int index) throws XMLStreamException {
    indent(writer, 1);
    writer.writeStartElement("turn");
    writer.writeAttribute("index", String.valueOf(index));
    writer.writeAttribute("role", "user");

    JsonArray contents = node.getAsJsonArray("contents");
    if (contents != null) {
      for (JsonElement elem : contents) {
        if (elem.isJsonObject()) {
          JsonObject item = elem.getAsJsonObject();
          if (item.has("data") && item.getAsJsonObject("data").has("text")) {
            String userText = item.getAsJsonObject("data").get("text").getAsString();
            writeTextElementWithCData(writer, "content", userText, 2);
          }
        }
      }
    }
    writeEndTag(writer, "turn", 1);
  }

  private static void writeAssistantTurnNode(XMLStreamWriter writer, JsonObject node, int index) throws XMLStreamException {
    indent(writer, 1);
    writer.writeStartElement("turn");
    writer.writeAttribute("index", String.valueOf(index));
    writer.writeAttribute("role", "assistant");

    // 1. Thinking block
    if (node.has("thinking") && !node.get("thinking").isJsonNull()) {
      String thinking = node.get("thinking").getAsString();
      if (!thinking.isBlank()) {
        writeTextElementWithCData(writer, "thinking", thinking, 2);
      }
    }

    // 2. Tool Execution Requests
    if (node.has("toolExecutionRequests") && node.get("toolExecutionRequests").isJsonArray()) {
      JsonArray requests = node.getAsJsonArray("toolExecutionRequests");
      for (JsonElement reqElem : requests) {
        if (reqElem.isJsonObject()) {
          writeToolCallNode(writer, reqElem.getAsJsonObject(), 2);
        }
      }
    }

    // 3. Main response text
    if (node.has("text") && !node.get("text").isJsonNull()) {
      String text = node.get("text").getAsString();
      if (!text.isBlank()) {
        writeTextElementWithCData(writer, "content", text, 2);
      }
    }

    writeEndTag(writer, "turn", 1);
  }

  private static void writeToolCallNode(XMLStreamWriter writer, JsonObject req, int depth) throws XMLStreamException {
    indent(writer, depth);
    writer.writeStartElement("tool_call");

    if (req.has("id")) {
      writer.writeAttribute("id", req.get("id").getAsString());
    }
    if (req.has("name")) {
      writer.writeAttribute("name", req.get("name").getAsString());
    }

    if (req.has("arguments")) {
      writeStartTag(writer, "arguments", depth + 1);
      String rawArgs = req.get("arguments").getAsString();
      renderStructuredOrCData(writer, "payload", rawArgs, depth + 2);
      writeEndTag(writer, "arguments", depth + 1);
    }

    writeEndTag(writer, "tool_call", depth);
  }

  private static void writeToolResultNode(XMLStreamWriter writer, JsonObject node) throws XMLStreamException {
    indent(writer, 1);
    writer.writeStartElement("tool_result");

    if (node.has("id")) {
      writer.writeAttribute("id", node.get("id").getAsString());
    }
    if (node.has("toolName")) {
      writer.writeAttribute("name", node.get("toolName").getAsString());
    }

    if (node.has("contents") && node.get("contents").isJsonArray()) {
      JsonArray contents = node.getAsJsonArray("contents");
      for (JsonElement c : contents) {
        if (c.isJsonObject()) {
          JsonObject item = c.getAsJsonObject();
          if (item.has("data") && item.getAsJsonObject("data").has("text")) {
            String rawResult = item.getAsJsonObject("data").get("text").getAsString();
            renderStructuredOrCData(writer, "output", rawResult, 2);
          }
        }
      }
    }

    writeEndTag(writer, "tool_result", 1);
  }

  /**
   * Determines if a text string is a serialized JSON object/array. If so,
   * recursively transcodes it to XML tags. Otherwise, writes as CDATA.
   */
  private static void renderStructuredOrCData(XMLStreamWriter writer, String defaultTag, String rawText, int depth) throws XMLStreamException {
    if (rawText == null || rawText.isBlank()) {
      return;
    }

    String trimmed = rawText.trim();
    if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
      try {
        JsonElement parsed = JsonParser.parseString(trimmed);
        if (parsed.isJsonObject() || parsed.isJsonArray()) {
          writeJsonElement(writer, defaultTag, parsed, depth);
          return;
        }
      } catch (Exception ignored) {
        // Not valid JSON, fallback to plain CDATA
      }
    }

    // Fallback for non-JSON text
    writeTextElementWithCData(writer, defaultTag, rawText, depth);
  }

  /**
   * Recursive mapper from JsonElement tree to XML elements.
   */
  private static void writeJsonElement(XMLStreamWriter writer, String tagName, JsonElement element, int depth) throws XMLStreamException {
    String sanitizedTag = sanitizeXmlName(tagName);

    if (element.isJsonObject()) {
      writeStartTag(writer, sanitizedTag, depth);
      JsonObject obj = element.getAsJsonObject();
      for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
        writeJsonElement(writer, entry.getKey(), entry.getValue(), depth + 1);
      }
      writeEndTag(writer, sanitizedTag, depth);
    } else if (element.isJsonArray()) {
      writeStartTag(writer, sanitizedTag, depth);
      JsonArray arr = element.getAsJsonArray();
      for (JsonElement item : arr) {
        writeJsonElement(writer, "item", item, depth + 1);
      }
      writeEndTag(writer, sanitizedTag, depth);
    } else if (element.isJsonPrimitive()) {
      JsonPrimitive prim = element.getAsJsonPrimitive();
      if (prim.isString()) {
        String strValue = prim.getAsString();
        // Check if string contains another nested JSON string (recursive unwrapping)
        String trimmed = strValue.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
          try {
            JsonElement nested = JsonParser.parseString(trimmed);
            if (nested.isJsonObject() || nested.isJsonArray()) {
              writeJsonElement(writer, sanitizedTag, nested, depth);
              return;
            }
          } catch (Exception ignored) {
            // Not JSON, continue as text
          }
        }

        if (strValue.contains("\n") || strValue.length() > 80) {
          writeTextElementWithCData(writer, sanitizedTag, strValue, depth);
        } else {
          writeSimpleElement(writer, sanitizedTag, strValue, depth);
        }
      } else {
        writeSimpleElement(writer, sanitizedTag, prim.getAsString(), depth);
      }
    }
  }

  // --- StAX Formatting and CDATA Helpers ---
  private static void writeTextElementWithCData(XMLStreamWriter writer, String tagName, String text, int depth) throws XMLStreamException {
    indent(writer, depth);
    writer.writeStartElement(sanitizeXmlName(tagName));
    writeCDataSafe(writer, text);
    writer.writeEndElement();
  }

  private static void writeSimpleElement(XMLStreamWriter writer, String tagName, String text, int depth) throws XMLStreamException {
    indent(writer, depth);
    writer.writeStartElement(sanitizeXmlName(tagName));
    writer.writeCharacters(text != null ? text : "");
    writer.writeEndElement();
  }

  private static void writeStartTag(XMLStreamWriter writer, String tagName, int depth) throws XMLStreamException {
    indent(writer, depth);
    writer.writeStartElement(sanitizeXmlName(tagName));
  }

  private static void writeEndTag(XMLStreamWriter writer, String tagName, int depth) throws XMLStreamException {
    indent(writer, depth);
    writer.writeEndElement();
  }

  private static void indent(XMLStreamWriter writer, int depth) throws XMLStreamException {
    writer.writeCharacters("\n" + "  ".repeat(Math.max(0, depth)));
  }

  /**
   * Prevents invalid CDATA termination if the content contains ']]>'.
   */
  private static void writeCDataSafe(XMLStreamWriter writer, String text) throws XMLStreamException {
    if (text == null) {
      return;
    }
    if (text.contains("]]>")) {
      // Split into safe chunks
      String safe = text.replace("]]>", "]]]]><![CDATA[>");
      writer.writeCData(safe);
    } else {
      writer.writeCData(text);
    }
  }

  /**
   * Ensures tag names are valid XML identifiers.
   */
  private static String sanitizeXmlName(String name) {
    if (name == null || name.isBlank()) {
      return "item";
    }
    StringBuilder sb = new StringBuilder();
    char first = name.charAt(0);
    if (Character.isLetter(first) || first == '_') {
      sb.append(first);
    } else {
      sb.append('_').append(first);
    }
    for (int i = 1; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
        sb.append(c);
      } else {
        sb.append('_');
      }
    }
    return sb.toString();
  }
}

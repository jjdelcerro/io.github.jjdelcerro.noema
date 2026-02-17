package io.github.jjdelcerro.chatagent.lib.impl.services.documents;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Representa la estructura jerárquica de un documento. Se persiste en disco
 * como JSON (lista plana de entradas) y se renderiza como XML plano para la
 * comunicación con el LLM.
 *
 * @author jjdelcerro
 */
@SuppressWarnings("UseSpecificCatch")
public class DocumentStructure implements Iterable<DocumentStructure.DocumentStructureEntry> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentStructure.class);

  public static class DocumentStructureEntry {

    private String title;
    private String parentTitle; // Cambiado a parentTitle para consistencia
    private int level;
    private int startLineNumber;
    private int endLineNumber; // Calculado
    private long byteOffset;   // Calculado
    private long byteLength;   // Calculado
    private String summary;
    private List<String> categories;

    // Campo transitorio (solo en memoria, no se persiste en JSON)
    private transient String fulltext;
    private transient boolean dirtyEntry; // Para marcar si la entrada individual ha cambiado

    // Constructor vacío para GSON
    public DocumentStructureEntry() {
      this.categories = new ArrayList<>();
      this.endLineNumber = -1; // -1 indica no calculado
      this.byteOffset = -1;
      this.byteLength = -1;
      this.dirtyEntry = false;
    }

    public DocumentStructureEntry(String title, String parentTitle, int level, int startLineNumber) {
      this();
      this.title = title;
      this.parentTitle = parentTitle;
      this.level = level;
      this.startLineNumber = startLineNumber;
    }

    // --- Getters ---
    public String getTitle() {
      return title;
    }

    public String getParentTitle() {
      return parentTitle;
    }

    public int getLevel() {
      return level;
    }

    public int getStartLineNumber() {
      return startLineNumber;
    }

    public int getEndLineNumber() {
      return endLineNumber;
    }

    public long getByteOffset() {
      return byteOffset;
    }

    public long getByteLength() {
      return byteLength;
    }

    public String getSummary() {
      return summary;
    }

    public List<String> getCategories() {
      return categories;
    }

    public String getFulltext() {
      return fulltext;
    } // Solo en memoria

    public String getId() {
      return "SECTION-" + this.startLineNumber;
    }

    // --- Setters (marcan dirty) ---
    public void setTitle(String title) {
      if (!Objects.equals(this.title, title)) {
        this.title = title;
        this.dirtyEntry = true;
      }
    }

    public void setParentTitle(String parentTitle) {
      if (!Objects.equals(this.parentTitle, parentTitle)) {
        this.parentTitle = parentTitle;
        this.dirtyEntry = true;
      }
    }

    public void setLevel(int level) {
      if (this.level != level) {
        this.level = level;
        this.dirtyEntry = true;
      }
    }

    public void setStartLineNumber(int startLineNumber) {
      if (this.startLineNumber != startLineNumber) {
        this.startLineNumber = startLineNumber;
        this.dirtyEntry = true;
      }
    }

    public void setEndLineNumber(int endLineNumber) {
      if (this.endLineNumber != endLineNumber) {
        this.endLineNumber = endLineNumber;
        this.dirtyEntry = true;
      }
    }

    public void setByteOffset(long byteOffset) {
      if (this.byteOffset != byteOffset) {
        this.byteOffset = byteOffset;
        this.dirtyEntry = true;
      }
    }

    public void setByteLength(long byteLength) {
      if (this.byteLength != byteLength) {
        this.byteLength = byteLength;
        this.dirtyEntry = true;
      }
    }

    public void setSummary(String summary) {
      if (!Objects.equals(this.summary, summary)) {
        this.summary = summary;
        this.dirtyEntry = true;
      }
    }

    public void setCategories(List<String> categories) {
      if (!Objects.equals(this.categories, categories)) {
        this.categories = new ArrayList<>(categories);
        this.dirtyEntry = true;
      }
    }

    public void setFullText(String fulltext) { // No marca dirtyEntry porque es transient
      this.fulltext = fulltext;
    }

    public String getContents(Path path) throws IOException {
      if (this.byteOffset < 0 || this.byteLength <= 0) {
        LOGGER.warn("Sección '" + this.title + " no indexada.");
        return "Sección '" + this.title + " no indexada.";
      }

      try (InputStream fis = Files.newInputStream(path); BufferedInputStream bis = new BufferedInputStream(fis)) {

        // Salto seguro al offset
        long goal = this.byteOffset;
        while (goal > 0) {
          long skipped = bis.skip(goal);
          if (skipped <= 0) {
            break; // Fin de archivo inesperado
          }
          goal -= skipped;
        }

        // Envoltura limitada
        BoundedInputStream limitedStream = new BoundedInputStream(bis, this.byteLength);

        // Decodificación a String
        try (InputStreamReader isr = new InputStreamReader(limitedStream, StandardCharsets.UTF_8)) {
          StringBuilder sb = new StringBuilder((int) Math.min(this.byteLength, 8192));
          char[] buffer = new char[4096];
          int charsRead;
          while ((charsRead = isr.read(buffer)) != -1) {
            sb.append(buffer, 0, charsRead);
          }
          return sb.toString();
        }
      }
    }

    public boolean isDirty() {
      return dirtyEntry;
    }

    public void clearDirty() {
      this.dirtyEntry = false;
    }

    @Override
    public String toString() {
      return String.format("[%s] %s (L%d-%d)", getId(), title, startLineNumber, endLineNumber);
    }

    boolean isEmpty() {
      return StringUtils.isBlank(this.summary);
    }
  }

  private List<DocumentStructureEntry> entries;
  private String title; // Título del documento
  private String summary; // Resumen global del documento
  private int id; // ID numérico para DocumentServices
  private List<String> categories;

  private transient boolean dirty; // Para marcar si el DocStructure como un todo ha cambiado

  public DocumentStructure() {
    this.entries = new ArrayList<>();
    this.dirty = false;
    this.id = -1; // -1 indica ID no asignado
  }

  // --- Getters & Setters ---
  public int getId() {
    return id;
  }

  public void setId(int id) {
    if (this.id != id) {
      this.id = id;
      this.dirty = true;
    }
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    if (!Objects.equals(this.title, title)) {
      this.title = title;
      this.dirty = true;
    }
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    if (!Objects.equals(this.summary, summary)) {
      this.summary = summary;
      this.dirty = true;
    }
  }

  public boolean isDirty() {
    return dirty || entries.stream().anyMatch(DocumentStructureEntry::isDirty);
  }

  public void clearDirty() {
    this.dirty = false;
    this.entries.forEach(DocumentStructureEntry::clearDirty);
  }

  public int size() {
    return this.entries.size();
  }

  public boolean isEmpty() {
    return this.entries.isEmpty();
  }

  public DocumentStructureEntry get(int n) {
    return this.entries.get(n);
  }

  @Override
  public Iterator<DocumentStructureEntry> iterator() {
    return this.entries.iterator();
  }

  /**
   * Añade una nueva entrada a la estructura.Actualiza el endLineNumber de la
   * entrada anterior.
   *
   * @param title
   * @param parentTitle
   * @param startLineNumber
   * @param level
   */
  public void add(String title, String parentTitle, int level, int startLineNumber) {
    if (!this.entries.isEmpty()) {
      DocumentStructureEntry lastEntry = this.entries.getLast();
      if (lastEntry.getEndLineNumber() == -1) { // Si aún no se ha calculado
        lastEntry.setEndLineNumber(startLineNumber - 1);
      }
    }
    this.entries.add(new DocumentStructureEntry(title, parentTitle, level, startLineNumber));
    this.dirty = true;
  }

  /**
   * Recupera una entrada por su ID (ej."SECTION-120").
   *
   * @param id
   * @return
   */
  public DocumentStructureEntry get(String id) {
    return this.entries.stream()
            .filter(entry -> entry.getId().equalsIgnoreCase(id))
            .findFirst()
            .orElse(null);
  }

  /**
   * Consolida la estructura calculando endLineNumber, byteOffset y byteLength.
   * Debe llamarse antes de guardar o de generar XML con contenido.
   */
  public void consolide(Path path) throws IOException {
    int countLines = this.calculateOffsets(path);
    for (int i = 0; i < this.entries.size(); i++) {
      DocumentStructureEntry currentEntry = this.entries.get(i);

      // Calcular endLineNumber
      if (i < this.entries.size() - 1) {
        currentEntry.setEndLineNumber(this.entries.get(i + 1).getStartLineNumber() - 1);
      } else {
        // Última entrada: hasta el final del documento
        currentEntry.setEndLineNumber(countLines - 1);
      }
      if (currentEntry.getEndLineNumber() < currentEntry.getStartLineNumber()) {
        currentEntry.setEndLineNumber(currentEntry.getStartLineNumber()); // Asegurar un mínimo
      }
      currentEntry.clearDirty(); // Reset dirty flag after consolidation
    }
    this.dirty = true; // DocStructure ha sido modificado
  }

  /**
   * Elimina los resúmenes de todas las entradas.
   */
  public void removeSummaries() {
    this.entries.forEach(entry -> entry.setSummary(null));
    this.dirty = true;
  }

  /**
   * Concatena títulos y resúmenes de todas las entradas.
   */
  public String joinAll() {
    return this.entries.stream()
            .map(entry -> {
              StringBuilder sb = new StringBuilder();
              sb.append(entry.getTitle());
              if (entry.getSummary() != null && !entry.getSummary().isBlank()) {
                sb.append(": ").append(entry.getSummary());
              }
              return sb.toString();
            })
            .collect(Collectors.joining("\n"));
  }

  /**
   * Carga la estructura del documento desde un archivo JSON.
   *
   * @param documentPath
   * @return
   */
  public static DocumentStructure from(Path documentPath) {
    Path structFile = getStructFilePath(documentPath);
    if (!Files.exists(structFile)) {
      return null;
    }
    Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    try (Reader reader = new FileReader(structFile.toFile(), StandardCharsets.UTF_8)) {
      DocumentStructure loaded = gson.fromJson(reader, DocumentStructure.class);
      if (loaded != null) {
        loaded.clearDirty();
      }
      return loaded;
    } catch (Exception e) {
      LOGGER.warn("Can't create document from '" + Objects.toString(documentPath), e);
      return null;
    }
  }

  /**
   * Guarda la estructura del documento en un archivo JSON.
   *
   * @param documentPath
   */
  public void save(Path documentPath) {
    if (!isDirty()) { // Solo guardar si ha habido cambios
      return;
    }
    Path structFile = getStructFilePath(documentPath);
    try {
      Files.createDirectories(structFile.getParent());
      Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();
      try (Writer writer = new FileWriter(structFile.toFile(), StandardCharsets.UTF_8)) {
        gson.toJson(this, writer);
      }
      clearDirty();
    } catch (Exception e) {
      LOGGER.warn("Can't save document to '" + Objects.toString(documentPath), e);
    }
  }

  private static Path getStructFilePath(Path documentPath) {
    String fileName = documentPath.getFileName().toString();
    // Cambiar la extensión a .struct
    String structFileName = fileName.substring(0, fileName.lastIndexOf('.')) + ".struct";
    return documentPath.getParent().resolve(structFileName);
  }

  /**
   * Genera la representación XML plana de la estructura del documento para el
   * LLM.
   *
   * @param expandedIds Lista de IDs de secciones cuyo contenido debe expandirse
   * (incluir fulltext).
   * @param includeSummaries Indica si se deben incluir los resúmenes.
   * @return String XML.
   */
  public String toXML(List<String> expandedIds, boolean includeSummaries) {
    StringBuilder sb = new StringBuilder();
    sb.append("<document id=\"").append(escapeXmlAttribute(String.valueOf(this.id)))
            .append("\" title=\"").append(escapeXmlAttribute(this.title)).append("\">\n");

    if (this.summary != null && !this.summary.isBlank() && includeSummaries) {
      sb.append("  <doc_summary>").append(escapeXmlContent(this.summary)).append("</doc_summary>\n");
    }

    for (DocumentStructureEntry entry : this.entries) {
      // Indentación visual basada en el nivel
      sb.append("  ".repeat(entry.getLevel())).append("<section");
      sb.append(" id=\"").append(escapeXmlAttribute(entry.getId()));
      sb.append("\" level=\"").append(entry.getLevel());
      sb.append("\" title=\"").append(escapeXmlAttribute(entry.getTitle()));
      sb.append("\">\n");

      if (includeSummaries && entry.getSummary() != null && !entry.getSummary().isBlank()) {
        sb.append("  ".repeat(entry.getLevel() + 1)).append("<summary>");
        sb.append(escapeXmlContent(entry.getSummary()));
        sb.append("</summary>\n");
      }

      if (expandedIds != null && expandedIds.contains(entry.getId()) && entry.getFulltext() != null) {
        sb.append("  ".repeat(entry.getLevel() + 1)).append("<content_start/>\n");
        // Contenido aquí no se escapa, se asume que el LLM lo lee tal cual hasta content_end
        sb.append(entry.getFulltext()).append("\n");
        sb.append("  ".repeat(entry.getLevel() + 1)).append("<content_end/>\n");
      } else if (expandedIds != null && expandedIds.contains(entry.getId()) && entry.getFulltext() == null) {
        sb.append("  ".repeat(entry.getLevel() + 1)).append("<content_error>Error: Contenido no disponible o no cargado.</content_error>\n");
      } else {
        sb.append("  ".repeat(entry.getLevel() + 1)).append("<metadata status=\"collapsed\"/>\n");
      }
      sb.append("  ".repeat(entry.getLevel())).append("</section>\n");
    }
    sb.append("</document>");
    return sb.toString();
  }

  /**
   * Genera la representación XML plana sin resúmenes.
   *
   * @param expandedIds
   * @return
   */
  public String toXMLWithoutSummaries(List<String> expandedIds) {
    return toXML(expandedIds, false);
  }

  /**
   * Genera la representación XML plana con resúmenes, sin contenido expandido
   * por defecto.
   *
   * @return
   */
  public String toXML() {
    return toXML(Collections.emptyList(), true);
  }

  /**
   * Escapa caracteres especiales para ser usados como atributos XML.
   */
  private String escapeXmlAttribute(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;"); // Esto es importante para atributos
  }

  /**
   * Escapa caracteres especiales para ser usados como contenido XML.
   */
  private String escapeXmlContent(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
  }

  private int calculateOffsets(Path path) throws IOException {
    int currentLine = 1;
    try (InputStream is = new BufferedInputStream(new FileInputStream(path.toFile()))) {
      long currentByte = 0;
      int b;
      for (DocumentStructureEntry entry : this) {
        while (currentLine < entry.getStartLineNumber() && (b = is.read()) != -1) {
          currentByte++;
          if (b == '\n') {
            currentLine++;
          }
        }
        entry.setByteOffset(currentByte);
        // La longitud la calcularemos luego por diferencia con el siguiente offset
      }
    }
    return currentLine;
  }

  /**
   * Reconstruye la estructura a partir de un CSV.Basado en la optimización de
   * seguridad: Solo procesamos líneas que empiezan por dígito.Orden esperado:
   * linestart, level, title, parentTitle
   *
   * @param csvstruct
   * @return
   */
  public static DocumentStructure from(String csvstruct) {
    DocumentStructure ds = new DocumentStructure();
    if (csvstruct == null || csvstruct.isBlank()) {
      return ds;
    }

    String[] lines = csvstruct.split("\\R");

    for (String line : lines) {
      String trimmed = line.trim();

      // Filtro: Debe empezar por número
      if (trimmed.isEmpty() || !Character.isDigit(trimmed.charAt(0))) {
        continue;
      }

      List<String> columns = parseSimpleCsvLine(trimmed);

      // Mínimo 3 columnas: linestart, level, title
      if (columns.size() >= 3) {
        try {
          int lineStart = Integer.parseInt(columns.get(0).trim());
          int level = Integer.parseInt(columns.get(1).trim());
          String title = columns.get(2).trim();

          // Manejo elástico del parentTitle
          String parent = null;
          if (columns.size() >= 4) {
            parent = columns.get(3).trim();
            // Si viene como "", "null", o solo espacios, lo normalizamos a null real
            if (parent.isEmpty() || parent.equalsIgnoreCase("null")) {
              parent = null;
            }
          }

          ds.add(title, parent, level, lineStart);
        } catch (NumberFormatException e) {
          continue;
        }
      }
    }
    ds.dirty = true;
    return ds;
  }

  /**
   * Parser de CSV que respeta comillas para títulos complejos.
   */
  private static List<String> parseSimpleCsvLine(String line) {
    List<String> values = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '\"') {
        inQuotes = !inQuotes;
      } else if (c == ',' && !inQuotes) {
        values.add(sb.toString());
        sb.setLength(0);
      } else {
        sb.append(c);
      }
    }
    values.add(sb.toString()); // Esto captura la columna vacía tras la última coma
    return values.stream()
            .map(v -> v.trim().replaceAll("^\"|\"$", ""))
            .collect(Collectors.toList());
  }

  void setCategories(List<String> categories) {
    this.categories = categories;
  }

  public List<String> getCategories() {
    return categories;
  }

}

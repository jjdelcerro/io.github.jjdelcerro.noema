package io.github.jjdelcerro.noema.ui.webclient.workspace.notes;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Almacen de notas en disco.
 *
 * Cada nota vive en un fichero XML propio dentro de la carpeta de notas, en
 * una jerarquia de anyo y mes. El contenido se guarda en CDATA para que el
 * Markdown no tenga que escaparse.
 *
 * Esta clase es la unica puerta de acceso al arbol de notas: ningun otro
 * componente debe construir rutas a mano.
 *
 * @author jjdelcerro
 */
public class NoteStore {

  private static final String TAG_NOTE = "note";
  private static final String TAG_ID = "id";
  private static final String TAG_TITLE = "title";
  private static final String TAG_TAGS = "tags";
  private static final String TAG_TAG = "tag";
  private static final String TAG_CREATED = "created";
  private static final String TAG_UPDATED = "updated";
  private static final String TAG_CONTENT = "content";

  /**
   * Un identificador valido es un instante con precision de segundo. Se valida
   * antes de tocar disco para que un id manipulado no pueda escapar de la
   * carpeta de notas.
   */
  private static final Pattern ID_PATTERN = Pattern.compile("^[0-9]{8}-[0-9]{6}$");

  /**
   * Formato de los identificadores de nota: fecha y hora con precision de
   * segundo en la zona horaria local.
   */
  private static final DateTimeFormatter ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final Path notesFolder;

  public NoteStore(Path notesFolder) {
    this.notesFolder = notesFolder.toAbsolutePath().normalize();
  }

  public Path getNotesFolder() {
    return this.notesFolder;
  }

  /**
   * Indica si un identificador tiene el formato esperado.
   *
   * @param id identificador a validar
   * @return true si es valido
   */
  public static boolean isValidId(String id) {
    if (id == null) {
      return false;
    }
    return ID_PATTERN.matcher(id).matches();
  }

  /**
   * Construye un documento XML nuevo a partir de una nota. Se usa cuando la
   * nota no existe todavia en disco.
   *
   * @param note datos de la nota
   * @return documento listo para serializar
   */
  private Document newDocument(Note note) {
    Document document = this.newDocumentBuilder().newDocument();
    Element root = document.createElement(TAG_NOTE);
    document.appendChild(root);
    this.appendTextElement(document, root, TAG_ID, note.getId());
    this.appendTextElement(document, root, TAG_TITLE, note.getTitle());
    Element tags = document.createElement(TAG_TAGS);
    root.appendChild(tags);
    List<String> tagList = note.getTags();
    for (String tag : tagList) {
      this.appendTextElement(document, tags, TAG_TAG, tag);
    }
    this.appendTextElement(document, root, TAG_CREATED, note.getCreated());
    this.appendTextElement(document, root, TAG_UPDATED, note.getUpdated());
    Element content = document.createElement(TAG_CONTENT);
    root.appendChild(content);
    this.appendCData(content, note.getContent());
    return document;
  }

  /**
   * Crea un elemento con texto simple, tratando el null como cadena vacia.
   *
   * @param document documento propietario
   * @param parent elemento padre
   * @param name nombre de la etiqueta
   * @param value texto del elemento
   */
  private void appendTextElement(Document document, Element parent, String name, String value) {
    Element element = document.createElement(name);
    if (value != null) {
      element.setTextContent(value);
    }
    parent.appendChild(element);
  }

  /**
   * Anade el contenido como CDATA.
   *
   * La secuencia ]]> no puede aparecer dentro de una seccion CDATA, asi que el
   * texto se parte en varias secciones adyacentes: la primera termina en ]] y
   * la siguiente empieza por >. Al serializar, el resultado sigue siendo XML
   * valido y el contenido leido es identico al original.
   *
   * @param content elemento content
   * @param text texto a guardar
   */
  private void appendCData(Element content, String text) {
    Document document = content.getOwnerDocument();
    String remaining;
    if (text == null) {
      remaining = "";
    } else {
      remaining = text;
    }
    while (true) {
      int index = remaining.indexOf("]]>");
      if (index < 0) {
        this.appendCDataSection(document, content, remaining);
        break;
      }
      this.appendCDataSection(document, content, remaining.substring(0, index + 2));
      remaining = remaining.substring(index + 2);
    }
  }

  /**
   * Anade una seccion CDATA evitando crear nodos vacios consecutivos.
   *
   * @param document documento propietario
   * @param content elemento content
   * @param text texto de la seccion
   */
  private void appendCDataSection(Document document, Element content, String text) {
    if (text.isEmpty() && content.hasChildNodes()) {
      return;
    }
    CDATASection section = document.createCDATASection(text);
    content.appendChild(section);
  }

  /**
   * Construye un parser DOM sin acceso a recursos externos. El sistema de
   * notas no procesa DTD ni entidades, asi que se desactivan por completo.
   *
   * @return constructor de documentos
   */
  private DocumentBuilder newDocumentBuilder() {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setExpandEntityReferences(false);
      factory.setNamespaceAware(false);
      return factory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("No se pudo configurar el parser XML de notas", e);
    }
  }

  /**
   * Serializa un documento a texto XML con indentacion de dos espacios.
   *
   * @param document documento a serializar
   * @return XML como cadena
   */
  private String toXml(Document document) {
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      Transformer transformer = factory.newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      StringWriter writer = new StringWriter();
      transformer.transform(new DOMSource(document), new StreamResult(writer));
      return writer.toString();
    } catch (TransformerException e) {
      throw new IllegalStateException("No se pudo serializar el XML de la nota", e);
    }
  }

  /**
   * Escribe un documento en disco, creando las carpetas necesarias.
   *
   * @param document documento a escribir
   * @param target ruta de destino
   * @throws IOException si falla la escritura
   */
  private void writeDocument(Document document, Path target) throws IOException {
    Path parent = target.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    String xml = this.toXml(document);
    Files.writeString(target, xml, StandardCharsets.UTF_8);
  }

  /**
   * Lee una nota desde disco.
   *
   * @param path ruta del fichero XML
   * @return nota con los campos conocidos
   * @throws IOException si falla la lectura o el XML no es valido
   */
  public Note read(Path path) throws IOException {
    Document document = this.parseDocument(path);
    return this.toNote(path, document);
  }

  /**
   * Lee una nota desde un flujo de caracteres ya abierto.
   *
   * No conoce el nombre del fichero de origen, de modo que si el XML no trae
   * el id de la nota el resultado se queda sin id. El llamador decide si eso
   * es aceptable.
   *
   * @param reader flujo del que leer el XML
   * @return nota con los campos conocidos
   * @throws IOException si falla la lectura o el XML no es valido
   */
  public Note read(Reader reader) throws IOException {
    if (reader == null) {
      throw new IllegalArgumentException("reader no puede ser null");
    }
    try {
      Document document = this.newDocumentBuilder().parse(new InputSource(reader));
      return this.toNote(document);
    } catch (SAXException e) {
      throw new IOException("XML de nota invalido", e);
    }
  }

  /**
   * Parsea un fichero XML de nota.
   *
   * @param path ruta del fichero
   * @return documento DOM
   * @throws IOException si falla la lectura o el XML no es valido
   */
  public Document parseDocument(Path path) throws IOException {
    try {
      DocumentBuilder builder = this.newDocumentBuilder();
      return builder.parse(path.toFile());
    } catch (SAXException e) {
      throw new IOException("XML de nota invalido: " + path, e);
    }
  }

  /**
   * Extrae los campos conocidos de un documento. Los nodos desconocidos se
   * ignoran al leer, pero siguen en el documento para el guardado.
   *
   * @param path ruta de origen, usada para derivar el id si falta en el XML
   * @param document documento origen
   * @return nota
   */
  private Note toNote(Path path, Document document) {
    Note note = this.toNote(document);
    if (note.getId() == null || note.getId().isEmpty()) {
      note.setId(this.idFromFileName(path));
    }
    return note;
  }

  /**
   * Extrae los campos conocidos de un documento sin derivar nada del nombre
   * del fichero, que aqui no se conoce.
   *
   * @param document documento origen
   * @return nota, con el id vacio si el XML no lo trae
   */
  private Note toNote(Document document) {
    Note note = new Note();
    Element root = document.getDocumentElement();
    note.setId(this.textOf(root, TAG_ID));
    note.setTitle(this.textOf(root, TAG_TITLE));
    note.setTags(this.tagsOf(root));
    note.setCreated(this.textOf(root, TAG_CREATED));
    note.setUpdated(this.textOf(root, TAG_UPDATED));
    Element content = this.findChildElement(root, TAG_CONTENT);
    if (content == null) {
      note.setContent("");
    } else {
      note.setContent(content.getTextContent());
    }
    return note;
  }

  /**
   * Aplica los campos editables sobre un documento ya existente.
   *
   * Solo se sustituyen title, updated, tags y content. El id, el created y
   * cualquier nodo que no sea del dominio de la aplicacion se dejan intactos,
   * de modo que un fichero enriquecido a mano no pierde informacion al
   * guardarse desde la WebUI.
   *
   * @param note datos nuevos
   * @param document documento a mutar
   */
  public void apply(Note note, Document document) {
    Element root = document.getDocumentElement();
    this.putText(document, root, TAG_TITLE, note.getTitle());
    this.putText(document, root, TAG_UPDATED, note.getUpdated());
    Element tags = this.ensureChild(document, root, TAG_TAGS);
    this.clearChildren(tags);
    List<String> tagList = note.getTags();
    for (String tag : tagList) {
      this.appendTextElement(document, tags, TAG_TAG, tag);
    }
    Element content = this.ensureChild(document, root, TAG_CONTENT);
    this.clearChildren(content);
    this.appendCData(content, note.getContent());
  }

  /**
   * Escribe un texto en un elemento, creandolo si no existe.
   *
   * @param document documento propietario
   * @param parent elemento padre
   * @param name nombre del elemento
   * @param value texto a escribir
   */
  private void putText(Document document, Element parent, String name, String value) {
    Element element = this.findChildElement(parent, name);
    if (element == null) {
      this.appendTextElement(document, parent, name, value);
    } else {
      if (value == null) {
        element.setTextContent("");
      } else {
        element.setTextContent(value);
      }
    }
  }

  /**
   * Devuelve el hijo con el nombre indicado, creandolo si no existe.
   *
   * @param document documento propietario
   * @param parent elemento padre
   * @param name nombre del elemento
   * @return elemento existente o nuevo
   */
  private Element ensureChild(Document document, Element parent, String name) {
    Element element = this.findChildElement(parent, name);
    if (element == null) {
      element = document.createElement(name);
      parent.appendChild(element);
    }
    return element;
  }

  /**
   * Elimina todos los hijos de un elemento.
   *
   * @param element elemento a vaciar
   */
  private void clearChildren(Element element) {
    while (element.hasChildNodes()) {
      element.removeChild(element.getFirstChild());
    }
  }

  /**
   * Busca un hijo por nombre de etiqueta.
   *
   * @param parent elemento padre
   * @param name nombre de la etiqueta
   * @return el elemento o null si no existe
   */
  private Element findChildElement(Element parent, String name) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
        return (Element) child;
      }
    }
    return null;
  }

  /**
   * Devuelve el texto de un hijo, o null si no existe.
   *
   * @param parent elemento padre
   * @param name nombre de la etiqueta
   * @return texto del elemento
   */
  private String textOf(Element parent, String name) {
    Element element = this.findChildElement(parent, name);
    if (element == null) {
      return null;
    }
    return element.getTextContent();
  }

  /**
   * Extrae las etiquetas del elemento tags.
   *
   * @param root raiz del documento
   * @return lista de etiquetas, nunca null
   */
  private List<String> tagsOf(Element root) {
    List<String> tags = new ArrayList<>();
    Element tagsElement = this.findChildElement(root, TAG_TAGS);
    if (tagsElement == null) {
      return tags;
    }
    NodeList children = tagsElement.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && TAG_TAG.equals(child.getNodeName())) {
        String value = child.getTextContent();
        if (value != null && !value.isBlank()) {
          tags.add(value.trim());
        }
      }
    }
    return tags;
  }

  /**
   * Deriva el id a partir del nombre del fichero, sin extension.
   *
   * @param path ruta del fichero
   * @return identificador deducido
   */
  private String idFromFileName(Path path) {
    String name = path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      return name.substring(0, dot);
    }
    return name;
  }

  /**
   * Localiza el fichero de una nota por su identificador.
   *
   * El identificador no incluye la jerarquia de carpetas, porque la nota pudo
   * crearse en un mes y moverse despues a otro. Por eso la busqueda recorre el
   * arbol completo en lugar de calcular una ruta.
   *
   * @param id identificador de la nota
   * @return ruta del fichero, o null si no existe
   * @throws IOException si falla el recorrido
   */
  public Path find(String id) throws IOException {
    if (!isValidId(id)) {
      return null;
    }
    if (!Files.isDirectory(this.notesFolder)) {
      return null;
    }
    String fileName = id + ".xml";
    try (Stream<Path> stream = Files.walk(this.notesFolder)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(candidate -> fileName.equals(candidate.getFileName().toString()))
          .findFirst()
          .orElse(null);
    }
  }

  /**
   * Crea una nota nueva en disco.
   *
   * Si la nota no trae identificador se genera uno a partir del instante
   * actual. Si no trae fecha de creacion se usa el mismo instante. La fecha de
   * modificacion siempre se sobrescribe.
   *
   * @param note datos de la nota
   * @return la misma instancia, completada con id, created y updated
   * @throws IOException si falla la escritura
   */
  public Note create(Note note) throws IOException {
    String now = this.nowIso();
    if (note.getId() == null || note.getId().isEmpty()) {
      note.setId(this.generateId());
    }
    if (!isValidId(note.getId())) {
      throw new IllegalArgumentException("Identificador de nota invalido: " + note.getId());
    }
    if (note.getCreated() == null || note.getCreated().isEmpty()) {
      note.setCreated(now);
    }
    note.setUpdated(now);
    Path target = this.notePathFor(note);
    this.writeDocument(this.newDocument(note), target);
    return note;
  }

  /**
   * Genera un identificador libre.
   *
   * Si el instante actual ya esta ocupado se avanza un segundo, de modo que el
   * formato se mantiene y dos notas creadas dentro del mismo segundo no
   * colisionan.
   *
   * @return identificador con formato YYYYMMDD-HHmmss
   * @throws IOException si no se encuentra un identificador libre
   */
  public String generateId() throws IOException {
    Instant instant = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    for (int attempt = 0; attempt < 3600; attempt++) {
      String id = ID_FORMATTER.format(instant.atZone(ZoneId.systemDefault()));
      if (this.find(id) == null) {
        return id;
      }
      instant = instant.plusSeconds(1);
    }
    throw new IOException("No se pudo generar un identificador libre para la nota");
  }

  /**
   * Calcula la ruta canonica de una nota a partir de su fecha de creacion.
   *
   * @param note nota con id y created informados
   * @return ruta del fichero dentro de la jerarquia anyo/mes
   */
  private Path notePathFor(Note note) {
    LocalDate date = this.dateOf(note.getCreated());
    String year = String.format("%04d", date.getYear());
    String month = String.format("%02d", date.getMonthValue());
    return this.notesFolder
        .resolve(year)
        .resolve(month)
        .resolve(note.getId() + ".xml");
  }

  /**
   * Convierte una fecha ISO-8601 en fecha local. Si el valor falta o no se
   * puede interpretar se usa la fecha actual.
   *
   * @param iso fecha en texto
   * @return fecha local
   */
  private LocalDate dateOf(String iso) {
    if (iso == null || iso.isEmpty()) {
      return LocalDate.now();
    }
    try {
      return Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate();
    } catch (RuntimeException e) {
      return LocalDate.now();
    }
  }

  /**
   * Devuelve el instante actual como texto ISO-8601, truncado a segundos.
   *
   * @return marca temporal
   */
  private String nowIso() {
    return DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS));
  }

  /**
   * Guarda una nota.
   *
   * Si la nota no existe en disco se crea. Si existe, se muta el documento
   * original y se traslada al mes corriente sin cambiar el nombre: el id es
   * inmutable y no depende de la carpeta en la que viva el fichero.
   *
   * @param note datos de la nota
   * @return la misma instancia, con la fecha de modificacion actualizada
   * @throws IOException si falla la lectura, la escritura o el borrado
   */
  public Note save(Note note) throws IOException {
    if (note == null || !isValidId(note.getId())) {
      throw new IllegalArgumentException("Identificador de nota invalido");
    }
    note.setUpdated(this.nowIso());
    Path existing = this.find(note.getId());
    if (existing == null) {
      return this.create(note);
    }
    Document document = this.parseDocument(existing);
    this.apply(note, document);
    Path target = this.currentMonthPath(note.getId());
    this.writeDocument(document, target);
    if (!target.equals(existing)) {
      Files.deleteIfExists(existing);
      this.pruneEmptyParents(existing);
    }
    return note;
  }

  /**
   * Construye la ruta que corresponde al mes corriente.
   *
   * @param id identificador de la nota
   * @return ruta de destino
   */
  private Path currentMonthPath(String id) {
    LocalDate today = LocalDate.now();
    String year = String.format("%04d", today.getYear());
    String month = String.format("%02d", today.getMonthValue());
    return this.notesFolder
        .resolve(year)
        .resolve(month)
        .resolve(id + ".xml");
  }

  /**
   * Elimina las carpetas de anyo y mes que hayan quedado vacias tras mover una
   * nota. El recorrido se detiene en la carpeta de notas.
   *
   * @param movedPath ruta del fichero que ya no existe en su ubicacion antigua
   * @throws IOException si falla el borrado
   */
  private void pruneEmptyParents(Path movedPath) throws IOException {
    Path folder = movedPath.getParent();
    while (folder != null && !folder.equals(this.notesFolder) && folder.startsWith(this.notesFolder)) {
      boolean empty;
      try (Stream<Path> children = Files.list(folder)) {
        empty = children.findAny().isEmpty();
      }
      if (!empty) {
        return;
      }
      Files.deleteIfExists(folder);
      folder = folder.getParent();
    }
  }

  /**
   * Devuelve los hijos directos de una carpeta de notas.
   *
   * No se recorre en profundidad: el arbol de la WebUI carga cada nivel bajo
   * demanda. Las carpetas van antes que los ficheros y ambos en orden
   * descendente, para que lo mas reciente quede arriba.
   *
   * @param relativeFolder ruta relativa a la carpeta de notas, o null para la raiz
   * @return hijos directos
   * @throws IOException si falla el listado
   */
  public List<NoteEntry> list(String relativeFolder) throws IOException {
    Path folder = this.resolveFolder(relativeFolder);
    List<NoteEntry> entries = new ArrayList<>();
    if (!Files.isDirectory(folder)) {
      return entries;
    }
    List<Path> folders = new ArrayList<>();
    List<Path> files = new ArrayList<>();
    try (Stream<Path> stream = Files.list(folder)) {
      stream.forEach(child -> {
        String name = child.getFileName().toString();
        if (Files.isDirectory(child)) {
          folders.add(child);
        } else if (name.endsWith(".xml")) {
          files.add(child);
        }
      });
    }
    Comparator<Path> byName = Comparator.comparing(candidate -> candidate.getFileName().toString());
    folders.sort(byName.reversed());
    files.sort(byName.reversed());
    for (Path child : folders) {
      String name = child.getFileName().toString();
      entries.add(NoteEntry.folder(name, this.relativePath(child)));
    }
    for (Path child : files) {
      String id = this.idFromFileName(child);
      entries.add(NoteEntry.file(id, this.relativePath(child), id, this.titleOf(child, id)));
    }
    return entries;
  }

  /**
   * Devuelve todos los ficheros de nota del subarbol indicado.
   *
   * A diferencia de list(), que solo baja un nivel, este recorrido entra en
   * todas las carpetas. Lo usan los modos de busqueda, que necesitan ver el
   * corpus completo.
   *
   * @param relativeFolder subarbol donde buscar, o null para todo el arbol
   * @return rutas absolutas de los XML encontrados
   * @throws IOException si falla el recorrido
   */
  public List<Path> findAll(String relativeFolder) throws IOException {
    Path folder = this.resolveFolder(relativeFolder);
    List<Path> files = new ArrayList<>();
    if (!Files.isDirectory(folder)) {
      return files;
    }
    try (Stream<Path> stream = Files.walk(folder)) {
      stream.filter(Files::isRegularFile)
              .filter(candidate -> candidate.getFileName().toString().endsWith(".xml"))
              .forEach(files::add);
    }
    return files;
  }

  /**
   * Localiza la nota modificada mas recientemente.
   *
   * Recorre el arbol completo porque la nota mas reciente puede estar en
   * cualquier carpeta: el guardado mueve el fichero al mes corriente, pero
   * una nota antigua puede seguir en un mes anterior hasta que se edite.
   *
   * @return la nota con mayor fecha de modificacion, o null si no hay notas
   * @throws IOException si falla el recorrido
   */
  public Note findLatest() throws IOException {
    List<Path> files = this.findAll(null);
    Note latest = null;
    for (Path file : files) {
      Note candidate;
      try {
        candidate = this.read(file);
      } catch (IOException e) {
        continue;
      }
      if (latest == null || this.isNewer(candidate, latest)) {
        latest = candidate;
      }
    }
    return latest;
  }

  /**
   * Compara dos notas por fecha de modificacion.
   *
   * Las fechas se guardan en ISO-8601 instantaneo, asi que el orden
   * lexicografico coincide con el cronologico y no hace falta convertirlas.
   *
   * @param candidate nota candidata
   * @param current nota que se mantiene por ahora
   * @return true si la candidata es mas reciente
   */
  private boolean isNewer(Note candidate, Note current) {
    String left = candidate.getUpdated();
    String right = current.getUpdated();
    if (left == null || left.isEmpty()) {
      return false;
    }
    if (right == null || right.isEmpty()) {
      return true;
    }
    return left.compareTo(right) > 0;
  }

  /**
   * Resuelve una ruta relativa contra la carpeta de notas y comprueba que no
   * se sale del arbol.
   *
   * @param relativeFolder ruta relativa
   * @return ruta absoluta normalizada
   */
  private Path resolveFolder(String relativeFolder) {
    if (relativeFolder == null || relativeFolder.isEmpty()) {
      return this.notesFolder;
    }
    Path candidate = this.notesFolder.resolve(relativeFolder).normalize();
    if (!candidate.startsWith(this.notesFolder)) {
      throw new IllegalArgumentException("Ruta de notas fuera del arbol: " + relativeFolder);
    }
    return candidate;
  }

  /**
   * Devuelve la ruta relativa a la carpeta de notas, con separador de barra
   * para que el cliente web la reciba igual sea cual sea el sistema.
   *
   * @param path ruta absoluta dentro del arbol
   * @return ruta relativa
   */
  public String relativePath(Path path) {
    return this.notesFolder.relativize(path).toString().replace('\\', '/');
  }

  /**
   * Obtiene el titulo de una nota para mostrarlo en el arbol. Si el fichero no
   * se puede leer se usa el identificador como respaldo.
   *
   * @param path ruta del fichero
   * @param fallback texto a usar si no hay titulo
   * @return titulo de la nota
   */
  private String titleOf(Path path, String fallback) {
    try {
      Note note = this.read(path);
      String title = note.getTitle();
      if (title == null || title.isBlank()) {
        return fallback;
      }
      return title;
    } catch (IOException e) {
      return fallback;
    }
  }

  /**
   * Elimina una nota del disco.
   *
   * @param id identificador de la nota
   * @return true si la nota existia y se ha borrado
   * @throws IOException si falla el borrado
   */
  public boolean delete(String id) throws IOException {
    Path path = this.find(id);
    if (path == null) {
      return false;
    }
    Files.deleteIfExists(path);
    this.pruneEmptyParents(path);
    return true;
  }
}

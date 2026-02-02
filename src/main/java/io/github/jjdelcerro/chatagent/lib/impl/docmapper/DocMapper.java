package io.github.jjdelcerro.chatagent.lib.impl.docmapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Esta clase esta EN CONSTRUCCION.
 * 
 * El DocMapper implementa el "Lector de documentos". Es un proceso desacoplado 
 * del `ConversationManager` cuya misión es transformar un documento extenso y 
 * no estructurado en un "Mapa de Conocimiento Jerárquico" persistido en un 
 * formato binario de acceso aleatorio.
 * 
 * Para superar las limitaciones de contexto y garantizar la coherencia, el Lector 
 * opera en tres fases secuenciales:
 * - Pasada 1: Descubrimiento de Estructura (Serial)
 * - Pasada 2: Digestión de Contenido (Paralelizable)
 * - Pasada 3: Categorización y Síntesis Global
 * 
 * El resultado no se guarda como un JSON masivo, sino como un fichero 
 * optimizado para el acceso aleatorio desde el `ConversationManager`.
 * 
 * El `ConversationManager` interactúa con el documento mediante un 
 * modelo de "percepción y herramientas":
 * - Notificación Proactiva: Al terminar la lectura, el Lector inyecta 
 *   un evento en el sistema (`putEvent`). El Agente percibe: 
 *   *"Nuevo documento disponible: 'Manual de Topografía' [ID:DOC-001]"*.
 * 
 * - Navegación Determinista: El Agente dispone de herramientas para 
 *   "tocar" el libro sin leerlo entero:
 *   - `doc_explorer`: Para ver el esquema y resúmenes de nivel superior.
 *   - `doc_search`: Para buscar por significado en la sección de embeddings del fichero binario.
 *   - `doc_read_leaf`: Para recuperar el texto bruto de una sección específica usando los punteros del binario.
 * 
 * @author jjdelcerro
 */
public class DocMapper {

    /**
     * Lee un fichero de texto y devuelve una representación JSON mapeando
     * número de línea con su contenido.
     * 
     * @param filePath Ruta absoluta al fichero.
     * @return String con el JSON resultante.
     * @throws IOException Si hay problemas de lectura.
     */
    public String mapDocumentToJson(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<Map<String, Object>> resultList = new ArrayList<>();

        int lineNumber = 1;
        for (String line : lines) {
            Map<String, Object> lineMap = new LinkedHashMap<>();
            lineMap.put("lineNumber", lineNumber++);
            lineMap.put("text", line);
            resultList.add(lineMap);
        }

        // Usamos PrettyPrinting para facilitar la lectura humana del resultado
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(resultList);
    }

    /**
     * Lee un fichero de texto y devuelve una representación CSV mapeando
     * número de línea con su contenido.
     * 
     * @param filePath Ruta absoluta al fichero.
     * @return String con el contenido CSV.
     * @throws IOException Si hay problemas de lectura.
     */
    public String mapDocumentToCSV(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        
        // Cabecera del CSV
        sb.append("lineNumber,text\n");

        int lineNumber = 1;
        for (String line : lines) {
            sb.append(lineNumber++);
            sb.append(",");
            
            // Escapamos las comillas dobles duplicándolas según el estándar CSV
            // Nota: Files.readAllLines elimina los retornos de carro de la línea,
            // por lo que solo nos preocupamos de las comillas en el contenido.
            String escapedLine = line.replace("\"", "\"\"");
            
            sb.append("\"").append(escapedLine).append("\"\n");
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        String inputPath = "/home/jjdelcerro/datos/devel/io.github.jjdelcerro.chatagent/tmp/testdoc1.txt";
        // Calculamos las rutas de salida
        String jsonOutputPath = inputPath.replace(".md", ".json");
        String csvOutputPath = inputPath.replace(".md", ".csv");
        
        // Prevención básica por si el fichero no termina en .md
        if (jsonOutputPath.equals(inputPath)) jsonOutputPath += ".json";
        if (csvOutputPath.equals(inputPath)) csvOutputPath += ".csv";

        DocMapper mapper = new DocMapper();
        try {
            System.out.println("Iniciando mapeo del documento...");
            System.out.println("Entrada: " + inputPath);
            
            // Generar JSON
            String jsonOutput = mapper.mapDocumentToJson(inputPath);
            Path jsonDestPath = Paths.get(jsonOutputPath);
            Files.write(jsonDestPath, jsonOutput.getBytes(StandardCharsets.UTF_8));
            System.out.println("Salida JSON generada exitosamente en: " + jsonOutputPath);
            
            // Generar CSV
            String csvOutput = mapper.mapDocumentToCSV(inputPath);
            Path csvDestPath = Paths.get(csvOutputPath);
            Files.write(csvDestPath, csvOutput.getBytes(StandardCharsets.UTF_8));
            System.out.println("Salida CSV generada exitosamente en: " + csvOutputPath);
            
        } catch (IOException e) {
            System.err.println("Error crítico procesando el documento.");
            System.err.println("Mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

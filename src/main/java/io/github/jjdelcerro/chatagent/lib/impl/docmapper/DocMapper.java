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

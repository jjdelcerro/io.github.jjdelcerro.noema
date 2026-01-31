package io.github.jjdelcerro.chatagent.lib.impl.tools.file;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class FileFindTool implements AgenteTool {
/*
    TODO: Seria interesante que pasase a usar tika para detectar el mimetype.
    */
    private final Path rootPath = Paths.get(".").toAbsolutePath().normalize();
    private final Gson gson = new Gson();
    private final Agent agent;
    
    public FileFindTool(Agent agent) {
      this.agent = agent;
    }
    
    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("file_find")
                .description("Busca archivos por patrón glob y devuelve sus metadatos (tamaño, tipo, etc).")
                .addParameter("pattern", JsonSchemaProperty.STRING, 
                        JsonSchemaProperty.description("Patrón glob (ej: 'src/**/*.java' o 'pom.xml')"))
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String pattern = args.get("pattern");
            if (pattern == null) return error("Patrón no proporcionado.");

            final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            List<Map<String, Object>> results = new ArrayList<>();

            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = rootPath.relativize(file);
                    if (matcher.matches(relative)) {
                        Map<String, Object> fileInfo = new LinkedHashMap<>();
                        fileInfo.put("path", relative.toString());
                        fileInfo.put("size_bytes", attrs.size());
                        fileInfo.put("last_modified", attrs.lastModifiedTime().toString());
                        
                        // Intentar obtener el MIME Type
                        String contentType = Files.probeContentType(file);
                        fileInfo.put("mime_type", contentType != null ? contentType : "unknown");
                        
                        results.add(fileInfo);
                    }
                    // Límite de seguridad: no devolver miles de entradas
                    return results.size() < 100 ? FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName().toString();
                    if (name.equals("target") || name.equals(".git") || name.equals(".idea")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            return gson.toJson(Map.of("status", "success", "matches", results));

        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private String error(String m) { return gson.toJson(Map.of("status", "error", "message", m)); }
}

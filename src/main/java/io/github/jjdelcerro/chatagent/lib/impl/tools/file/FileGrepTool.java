package io.github.jjdelcerro.chatagent.lib.impl.tools.file;

import com.google.gson.Gson;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.description;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.type;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class FileGrepTool implements AgenteTool {
    private final Path rootPath = Paths.get(".").toAbsolutePath().normalize();
    private final Gson gson = new Gson();

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("file_grep")
                .description("Busca un texto dentro de los archivos del proyecto.")
                .addParameter("query", type("string"), description("Texto o Regex a buscar"))
                .addParameter("filePattern", type("string"), description("Opcional: glob para filtrar archivos (ej: '*.java')"))
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
            String query = args.get("query");
            String filePattern = args.getOrDefault("filePattern", "**/*");
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);

            List<Map<String, String>> results = new ArrayList<>();
            Files.walk(rootPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(rootPath.relativize(p)))
                    .forEach(path -> {
                        try (Stream<String> lines = Files.lines(path)) {
                            int[] lineNum = {1};
                            lines.forEach(line -> {
                                if (line.contains(query)) {
                                    results.add(Map.of(
                                        "file", rootPath.relativize(path).toString(),
                                        "line", String.valueOf(lineNum[0]),
                                        "content", line.trim()
                                    ));
                                }
                                lineNum[0]++;
                            });
                        } catch (Exception ignored) {}
                    });

            return gson.toJson(Map.of("status", "success", "matches", results.subList(0, Math.min(results.size(), 50))));
        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}

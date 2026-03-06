package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.file;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import io.github.jjdelcerro.noema.lib.impl.AbstractAgentTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.noema.lib.Agent;
import static io.github.jjdelcerro.noema.lib.AgentAccessControl.AccessMode.PATH_ACCESS_READ;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@SuppressWarnings("UseSpecificCatch")
public class FileGrepTool extends AbstractAgentTool {

  public FileGrepTool(Agent agent) {
    super(agent);
  }

  @Override
  public ToolSpecification getSpecification() {
    return ToolSpecification.builder()
            .name("file_grep")
            .description("Busca un texto dentro de los archivos del proyecto.")
            // --- LÍNEAS CORREGIDAS ---
            .addParameter("query", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Texto o Regex a buscar"))
            .addParameter("filePattern", JsonSchemaProperty.STRING, JsonSchemaProperty.description("Opcional: glob para filtrar archivos (ej: '*.java')"))
            .build();
  }

  @Override
  public String execute(String jsonArguments) {
    try {
      Map<String, String> args = gson.fromJson(jsonArguments, Map.class);
      String query = args.get("query");
      String filePattern = args.getOrDefault("filePattern", "**/*");
      PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);

      Path rootPath = this.agent.getAccessControl().resolvePathOrNull(".", PATH_ACCESS_READ);

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
                } catch (Exception ignored) {
                }
              });

      return gson.toJson(Map.of("status", "success", "matches", results.subList(0, Math.min(results.size(), 50))));
    } catch (Exception e) {
      LOGGER.warn("Error searching in files, args=" + jsonArguments, e);
      return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
    }
  }
}

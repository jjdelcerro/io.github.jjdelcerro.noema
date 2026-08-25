package io.github.jjdelcerro.noema.lib.impl.skills;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.memory.proyected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.memory.proyected.operations.PinnedTurnsOperation;
import io.github.jjdelcerro.noema.lib.memory.proyected.operations.PinnedTurnsOperation.PinnedTurnState;
import io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Skill {

  private final Agent agent;
  private final Path rootPath;
  private final String name;
  private final String description;
  private final String version;
  private final String content;

  public Skill(Agent agent, Path rootPath, String name, String description, String version, String content) {
    this.agent = Objects.requireNonNull(agent, "agent cannot be null");
    this.rootPath = Objects.requireNonNull(rootPath, "rootPath cannot be null").toAbsolutePath().normalize();
    this.name = Objects.requireNonNull(name, "name cannot be null").trim();
    this.description = description != null ? description.trim() : "";
    this.version = version != null ? version.trim() : "1.0.0";
    this.content = content != null ? content : "";
  }

  public Agent getAgent() {
    return agent;
  }

  public Path getRootPath() {
    return rootPath;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getVersion() {
    return version;
  }

  public String getContents() {
    return content;
  }

  public Path resolveResource(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      return null;
    }
    Path resolved = rootPath.resolve(relativePath).normalize();
    if (!resolved.startsWith(rootPath)) {
      throw new SecurityException("Access Denied: Path traversal detected outside skill root: " + relativePath);
    }
    return resolved;
  }

  public ProcessBuilder createScriptProcess(String scriptName, List<String> args) {
    if (scriptName == null || scriptName.isBlank()) {
      throw new IllegalArgumentException("Script name cannot be null or blank");
    }

    Path scriptsDir = rootPath.resolve("scripts").normalize();
    Path scriptFile = scriptsDir.resolve(scriptName).normalize();

    if (!scriptFile.startsWith(scriptsDir)) {
      throw new SecurityException("Access Denied: Script path outside scripts directory: " + scriptName);
    }

    if (!Files.exists(scriptFile) || !Files.isRegularFile(scriptFile)) {
      throw new IllegalArgumentException("Script file not found or is not a regular file: " + scriptName);
    }

    List<String> command = new ArrayList<>();
    command.add("bash");
    command.add(scriptFile.toString());
    if (args != null && !args.isEmpty()) {
      command.addAll(args);
    }

    ProcessBuilder pb = new ProcessBuilder(command);
    if (agent.getPaths() != null && agent.getPaths().getWorkspaceFolder() != null) {
      pb.directory(agent.getPaths().getWorkspaceFolder().toFile());
    }
    return pb;
  }

  public void deactivate(String subchannel) {
    ReasoningService reasoning = (ReasoningService) agent.getService(ReasoningService.NAME);
    if (reasoning == null) {
      return;
    }

    ProjectedMemory projectedMemory = reasoning.getProjectedMemory(subchannel);
    if (projectedMemory == null) {
      return;
    }
    PinnedTurnsOperation operation = (PinnedTurnsOperation) projectedMemory.getOperation(PinnedTurnsOperation.OPERATION_NAME);
    operation.removePinnedTurn(this::isMatchingPinnedTurn);
  }

  private boolean isMatchingPinnedTurn(PinnedTurnState state) {
    ToolExecutionResultMessage result = state.getResultMessage();
    if (result != null && "activate_skill".equals(result.toolName())) {
      AiMessage req = state.getRequestMessage();
      if (req != null && req.hasToolExecutionRequests()) {
        for (ToolExecutionRequest r : req.toolExecutionRequests()) {
          if (isMatchingSkillRequest(r)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean isMatchingSkillRequest(ToolExecutionRequest request) {
    if (request == null || request.arguments() == null) {
      return false;
    }
    try {
      JsonObject args = JsonParser.parseString(request.arguments()).getAsJsonObject();
      if (args.has("name")) {
        return this.name.equalsIgnoreCase(args.get("name").getAsString().trim());
      }
    } catch (Exception e) {
      return request.arguments().contains("\"" + this.name + "\"");
    }
    return false;
  }
}

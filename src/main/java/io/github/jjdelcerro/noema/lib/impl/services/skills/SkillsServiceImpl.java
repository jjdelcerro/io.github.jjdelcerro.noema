package io.github.jjdelcerro.noema.lib.impl.services.skills;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.impl.services.skills.tools.ActivateSkillTool;
import io.github.jjdelcerro.noema.lib.impl.services.skills.tools.DeactivateSkillTool;
import io.github.jjdelcerro.noema.lib.impl.services.skills.tools.ListSkillsTool;
import io.github.jjdelcerro.noema.lib.impl.services.skills.tools.ReadSkillResourceTool;
import io.github.jjdelcerro.noema.lib.impl.services.skills.tools.RunSkillScriptTool;
import io.github.jjdelcerro.noema.lib.spi.AbstractAgentService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class SkillsServiceImpl 
        extends AbstractAgentService
        implements SkillsService
  {

  private static final Logger LOGGER = LoggerFactory.getLogger(SkillsServiceImpl.class);

  private static final String SKILLS_DIR0 = ".claude/skills";
  private static final String SKILLS_DIR1 = "var/skills";
  private static final String SKILL_FILE_NAME = "SKILL.md";

  public SkillsServiceImpl(AgentServiceFactory factory, Agent agent) {
    super(factory, agent);
  }

  private List<Path> getSkillsFolders(Agent agent) {
    if (agent == null || agent.getPaths() == null || agent.getPaths().getWorkspaceFolder() == null) {
      return null;
    }
    List<Path> r = new ArrayList();
    r.add(agent.getPaths().getWorkspaceFolder().resolve(SKILLS_DIR0).toAbsolutePath().normalize());
    r.add(agent.getPaths().getAgentPath(SKILLS_DIR1).toAbsolutePath().normalize());
    return r;
  }

  @Override
  public List<Skill> listSkills(Agent agent) {
    List<Path> skillsFolders = getSkillsFolders(agent);
    if (skillsFolders == null || skillsFolders.isEmpty()) {
      return Collections.emptyList();
    }

    List<Skill> skills = new ArrayList<>();
    for (Path skillsFolder : skillsFolders) {
      if (skillsFolder == null || !Files.exists(skillsFolder) || !Files.isDirectory(skillsFolder)) {
        continue;
      }
      try (Stream<Path> stream = Files.list(skillsFolder)) {
        List<Path> subdirs = stream.filter(Files::isDirectory).toList();
        for (Path dir : subdirs) {
          Path skillFile = dir.resolve(SKILL_FILE_NAME);
          if (Files.exists(skillFile) && Files.isRegularFile(skillFile)) {
            Skill skill = parseSkillFile(agent, dir, skillFile);
            if (skill != null) {
              skills.add(skill);
            }
          }
        }
      } catch (IOException e) {
        LOGGER.warn("Error listing skills in folder: {}", skillsFolder, e);
        return Collections.emptyList();
      }
    }

    skills.sort(Comparator.comparing(Skill::getName, String.CASE_INSENSITIVE_ORDER));
    return skills;
  }

  @Override
  public Skill getSkill(Agent agent, String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }

    List<Path> skillsFolders = getSkillsFolders(agent);
    if (skillsFolders == null || skillsFolders.isEmpty()) {
      return null;
    }
    for (Path skillsFolder : skillsFolders) {
      if (skillsFolder == null || !Files.exists(skillsFolder)) {
        continue;
      }
      Path skillDir = skillsFolder.resolve(name.trim()).normalize();
      if (!skillDir.startsWith(skillsFolder)) {
        LOGGER.warn("Path traversal attempt detected looking for skill: {}", name);
        continue;
      }

      if (!Files.exists(skillDir) || !Files.isDirectory(skillDir)) {
        continue;
      }

      Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
      if (!Files.exists(skillFile) || !Files.isRegularFile(skillFile)) {
        continue;
      }

      return parseSkillFile(agent, skillDir, skillFile);
    }
    return null;
  }

  private Skill parseSkillFile(Agent agent, Path skillDir, Path skillFile) {
    String fallbackName = skillDir.getFileName().toString();
    String name = fallbackName;
    String description = "";
    String version = "1.0.0";
    StringBuilder contentBuilder = new StringBuilder();

    try (BufferedReader reader = Files.newBufferedReader(skillFile, StandardCharsets.UTF_8)) {
      String line = reader.readLine();

      // Skip leading empty lines
      while (line != null && line.trim().isEmpty()) {
        line = reader.readLine();
      }

      if (line != null && line.trim().equals("---")) {
        // Parse YAML frontmatter
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (trimmed.equals("---")) {
            break; // End of frontmatter
          }
          if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            continue;
          }

          String[] parts = line.split(":", 2);
          if (parts.length == 2) {
            String key = parts[0].trim().toLowerCase();
            String value = cleanYamlValue(parts[1]);

            switch (key) {
              case "name":
                if (StringUtils.isNotBlank(value)) {
                  name = value;
                }
                break;
              case "description":
                description = value;
                break;
              case "version":
                if (StringUtils.isNotBlank(value)) {
                  version = value;
                }
                break;
              default:
                break;
            }
          }
        }

        // Read remaining lines as skill body instructions
        while ((line = reader.readLine()) != null) {
          contentBuilder.append(line).append("\n");
        }
      } else {
        // No frontmatter found, whole file is content
        if (line != null) {
          contentBuilder.append(line).append("\n");
        }
        while ((line = reader.readLine()) != null) {
          contentBuilder.append(line).append("\n");
        }
      }
    } catch (IOException e) {
      LOGGER.warn("Error reading skill file: {}", skillFile, e);
      return null;
    }

    String content = contentBuilder.toString().trim();
    return new Skill(agent, skillDir, name, description, version, content);
  }

  private String cleanYamlValue(String raw) {
    if (raw == null) {
      return "";
    }
    String val = raw.trim();
    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
      if (val.length() >= 2) {
        val = val.substring(1, val.length() - 1).trim();
      }
    }
    return val;
  }

  @Override
  public void start() {
    String[] resources = new String[]{
        "var/skills/software_developmet/SKILL.md"
    };
    for (String resPath : resources) {
      this.agent.installResource(resPath);
    }
  }

  @Override
  public List<AgentTool> getTools() {
    AgentTool[] tools0 = new AgentTool[]{
      new ListSkillsTool(this.agent),
      new ActivateSkillTool(this.agent),
      new DeactivateSkillTool(this.agent),
      new ReadSkillResourceTool(this.agent),
      new RunSkillScriptTool(this.agent)
    };
    List<AgentTool> tools = new ArrayList<>(Arrays.asList(tools0));
    return tools;
  }

}

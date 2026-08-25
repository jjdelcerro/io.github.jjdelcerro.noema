package io.github.jjdelcerro.noema.lib.impl.skills;

import io.github.jjdelcerro.noema.lib.Agent;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class SkillUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(SkillUtils.class);
  public static final String SKILLS_DIR = ".claude/skills";
  public static final String SKILL_FILE_NAME = "SKILL.md";

  private SkillUtils() {
  }

  public static Path getSkillsFolder(Agent agent) {
    if (agent == null || agent.getPaths() == null || agent.getPaths().getWorkspaceFolder() == null) {
      return null;
    }
    return agent.getPaths().getWorkspaceFolder().resolve(SKILLS_DIR).toAbsolutePath().normalize();
  }

  public static List<Skill> listSkills(Agent agent) {
    Path skillsFolder = getSkillsFolder(agent);
    if (skillsFolder == null || !Files.exists(skillsFolder) || !Files.isDirectory(skillsFolder)) {
      return Collections.emptyList();
    }

    List<Skill> skills = new ArrayList<>();
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

    skills.sort(Comparator.comparing(Skill::getName, String.CASE_INSENSITIVE_ORDER));
    return skills;
  }

  public static Skill getSkill(Agent agent, String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }

    Path skillsFolder = getSkillsFolder(agent);
    if (skillsFolder == null || !Files.exists(skillsFolder)) {
      return null;
    }

    Path skillDir = skillsFolder.resolve(name.trim()).normalize();
    if (!skillDir.startsWith(skillsFolder)) {
      LOGGER.warn("Path traversal attempt detected looking for skill: {}", name);
      return null;
    }

    if (!Files.exists(skillDir) || !Files.isDirectory(skillDir)) {
      return null;
    }

    Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
    if (!Files.exists(skillFile) || !Files.isRegularFile(skillFile)) {
      return null;
    }

    return parseSkillFile(agent, skillDir, skillFile);
  }

  private static Skill parseSkillFile(Agent agent, Path skillDir, Path skillFile) {
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

  private static String cleanYamlValue(String raw) {
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
}

package io.github.jjdelcerro.noema.lib.impl.services.skills;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentService;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public interface SkillsService extends AgentService {

  public static String NAME = "skills";

  public List<Skill> listSkills(Agent agent);

  public Skill getSkill(Agent agent, String name);

}

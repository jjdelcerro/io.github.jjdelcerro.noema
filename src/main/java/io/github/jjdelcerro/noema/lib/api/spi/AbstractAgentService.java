/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.github.jjdelcerro.noema.lib.api.spi;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.AgentService;
import io.github.jjdelcerro.noema.lib.api.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.api.AgentTool;
import io.github.jjdelcerro.noema.lib.api.model.ModelParameters;
import static io.github.jjdelcerro.noema.lib.impl.services.email.EmailService.NAME;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractAgentService implements AgentService {

  protected final Agent agent;
  protected boolean enabled;
  protected boolean running;
  protected final AgentServiceFactory factory;

  protected AbstractAgentService(AgentServiceFactory factory, Agent agent) {
    this.agent = agent;
    this.factory = factory;
    this.enabled = true;
    this.running = false;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public boolean isRunning() {
    return this.running;
  }

  @Override
  public AgentServiceFactory getFactory() {
    return factory;
  }

  @Override
  public String getName() {
    return this.factory.getName();
  }

  @Override
  public ModelParameters getModelParameters(String name) {
    return null;
  }

  @Override
  public boolean canStart() {
    return true;
  }

  @Override
  public void stop() {
    this.running = false;
  }

  @Override
  public List<AgentTool> getTools() {
    return null;
  }

}

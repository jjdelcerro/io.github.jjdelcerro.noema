/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.github.jjdelcerro.noema.lib.spi;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentService;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.AgentTool;
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
  public Agent.ModelParameters getModelParameters(String name) {
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

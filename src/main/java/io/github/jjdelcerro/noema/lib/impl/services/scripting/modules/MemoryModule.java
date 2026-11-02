package io.github.jjdelcerro.noema.lib.impl.services.scripting.modules;

import dev.langchain4j.data.message.ChatMessage;
import groovy.lang.Closure;
import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.memory.projected.ProjectedMemory;
import io.github.jjdelcerro.noema.lib.api.memory.projected.SimplifiedProjectedMemory;
import io.github.jjdelcerro.noema.lib.api.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptContext;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptModule;
import io.github.jjdelcerro.noema.lib.spi.services.scripting.AbstractScriptModule;
import io.github.jjdelcerro.noema.lib.spi.memory.projected.AbstractProjectedMemoryOperation;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public class MemoryModule extends AbstractScriptModule {

  public static class MemoryScriptModuleFactory extends AbstractScriptModuleFactory {

    public MemoryScriptModuleFactory() {
      super("MemoryModule");
    }

    @Override
    public ScriptModule createModule(Agent agent, ScriptContext context) {
      return new MemoryModule(context, agent);
    }
  }

  public static class SimpleScriptProjectedMemoryOperation extends AbstractProjectedMemoryOperation {

    private final Closure<?> closure;

    public SimpleScriptProjectedMemoryOperation(Agent agent, String name, int priority, groovy.lang.Closure<?> closure) {
      super(agent, name, priority);
      this.closure = closure;
    }

    @Override
    public void process(ProjectedMemory memory, List<ChatMessage> projectedMessages, List<String> notifications) {
      try {
        closure.call(memory, notifications);
      } catch (Throwable t) {
        LOGGER.warn("Error calling closure", t);
      }
    }
  }

  public MemoryModule(ScriptContext context, Agent agent) {
    super(context, agent, "memory", "modulo de acceso a los estratos de la memoria del agente");
  }

  @Override
  public String help() {
    return """
[agent.memory API] 
• registerProjectedOperation(name, priority, closure): boolean
  Registra una regla/monitor dinámico que se ejecuta en CADA turno antes de que el LLM reciba el contexto.
  - name: Identificador único de la operación (permite sustituirla o retirarla después).
  - priority: Orden en el pipeline (40 recomendado para avisos; menor número = antes en el pipeline).
  - closure: Bloque { SimpleProjectedMemory memory, List<String> notifications -> ... } 
  -> agent.memory.registerProjectedOperation('recordatorio_plan', 40) { mem, notifications ->
         notifications.add('[PLAN ACTIVO: "mi-plan"] Continúa con la siguiente tarea pendiente.')
     }
• removeProjectedOperation(name): boolean
  Retira una operación dinámica previamente registrada por su nombre para detener los avisos.
  -> agent.memory.removeProjectedOperation('recordatorio_plan')
""";
  }

  @Override
  public List<Class> getImports() {
    return List.of(SimplifiedProjectedMemory.class);
  }

  public boolean registerProjectedOperation(String name, int priority, groovy.lang.Closure<?> closure) {
    SimpleScriptProjectedMemoryOperation operation = new SimpleScriptProjectedMemoryOperation(agent, name, priority, closure);
    ReasoningService reasoning = (ReasoningService) this.agent.getService(ReasoningService.NAME);
    ProjectedMemory memory = reasoning.getProjectedMemory(reasoning.getCurrentSubchannel());
    memory.addOperation(operation);
    return true;
  }
  
  public boolean removeOperation(String name) {
    ReasoningService reasoning = (ReasoningService) this.agent.getService(ReasoningService.NAME);
    ProjectedMemory memory = reasoning.getProjectedMemory(reasoning.getCurrentSubchannel());
    return memory.removeOperation(name);    
  }

}

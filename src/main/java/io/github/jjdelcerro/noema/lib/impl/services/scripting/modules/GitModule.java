package io.github.jjdelcerro.noema.lib.impl.services.scripting.modules;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.services.scripting.AbstractScriptModule;
import io.github.jjdelcerro.noema.lib.impl.services.scripting.ScriptContextImpl;

/**
 *
 * @author jjdelcerro
 */
public class GitModule extends AbstractScriptModule {


  public GitModule(ScriptContextImpl context, Agent agent) {
    super(context, agent, "git", "modulo de acceso a git");
    // Usaria JGit
  }

  @Override
  public String help() {
    return """
[agent.git API] (En construccion)
• diff(pathname)
""";
  }

  public String diff(String pathname) {
//// Asumiendo que ya tienes una instancia de Repository llamada 'repository'
//ObjectReader reader = repository.newObjectReader();
//CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
//ObjectId headTree = repository.resolve("HEAD^{tree}");
//oldTreeIter.reset(reader, headTree);
//
//AbstractTreeIterator newTreeIter = new FileTreeIterator(repository);
//
//DiffFormatter formatter = new DiffFormatter(System.out);
//formatter.setRepository(repository);
//formatter.format(oldTreeIter, newTreeIter); // Imprime el diff en consola
    return "";
  }


}

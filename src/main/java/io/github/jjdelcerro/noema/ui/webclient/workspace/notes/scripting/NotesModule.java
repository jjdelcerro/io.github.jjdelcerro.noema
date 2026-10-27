package io.github.jjdelcerro.noema.ui.webclient.workspace.notes.scripting;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptContext;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptModule;
import io.github.jjdelcerro.noema.lib.spi.services.scripting.AbstractScriptModule;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.Note;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NoteStore;
import io.github.jjdelcerro.noema.ui.webclient.workspace.notes.NotesSupport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public class NotesModule extends AbstractScriptModule {

  public static class NotesScriptModuleFactory extends AbstractScriptModuleFactory {

    public NotesScriptModuleFactory() {
      super("NotesModule");
    }
    
    @Override
    public ScriptModule createModule(Agent agent, ScriptContext context) {
      return new NotesModule(context, agent);
    }    
  }
  
  public NotesModule(ScriptContext context, Agent agent) {
    super(context, agent, "notes", "modulo de acceso a las notas");
  }

  @Override
  public List<Class> getImports() {
    return List.of(Note.class);
  }
  
  @Override
  public String help() {
    return """
[agent.notes API] 
• read(relativePath)
""";
  }

  public Note read(Path relpath) throws IOException {
    NoteStore store = NotesSupport.store(agent);
    Path path = store.getNotesFolder().resolve(relpath);
    Note note = store.read(path);
    return note;
  }

}

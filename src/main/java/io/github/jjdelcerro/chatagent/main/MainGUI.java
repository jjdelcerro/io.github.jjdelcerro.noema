package io.github.jjdelcerro.chatagent.main;

import com.formdev.flatlaf.FlatDarkLaf;
import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentActions.AgentAction;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import io.github.jjdelcerro.chatagent.ui.swing.AgentSwingInitializer;
import io.github.jjdelcerro.chatagent.ui.swing.MainChatPanel;
import io.github.jjdelcerro.chatagent.ui.swing.SimpleTextEditor;
import javax.swing.*;
import java.io.File;

public class MainGUI {

  public static void main(String[] args) {
    FlatDarkLaf.setup(); // Estética moderna desde el inicio
    UIManager.put("Component.arc", 12); 
    UIManager.put("TextComponent.arc", 12);

    SwingUtilities.invokeLater(() -> {
      JFrame frame = new JFrame("ChatAgent v1.0");
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setSize(900, 700);

      MainChatPanel chatPanel = new MainChatPanel();
      frame.add(chatPanel);
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
      
      AgentSwingInitializer.init(chatPanel.getController());
      
      // Carga asíncrona del agente
      Thread.ofVirtual().start(() -> {
        try {
          Agent agent = AgentUtils.init(new File("./data"));
          agent.getActions().addAction("OPEN_MODELS_EDITOR", new OpenModelsEditorAction(agent));
          agent.getActions().addAction("OPEN_PROVIDERS_URL_EDITOR", new OpenProvidersUrlsEditorAction(agent));
          agent.getActions().addAction("OPEN_PROVIDERS_APIKEY_EDITOR", new OpenProvidersAPILKeysEditorAction(agent));
          agent.start();
          SwingUtilities.invokeLater(() -> chatPanel.setAgent(agent));
        } catch (Exception e) {
          chatPanel.getController().printSystemError("Error fatal: " + e.getMessage());
        }
      });
    });
  }
  
  private static class OpenEditorAction implements AgentAction {

    private final Agent agent;
    private final String fname;

    public OpenEditorAction(Agent agent, String fname) {
      this.agent = agent;
      this.fname = fname;
    }
    
    @Override
    public boolean perform(AgentSettings settings) {
      File target = new File(agent.getDataFolder(), fname);
      SwingUtilities.invokeLater(() -> {
        SimpleTextEditor editor = new SimpleTextEditor();
        editor.load(target);
        editor.showWindow("Editor");
      });
      return true;
    }
  }
  
  private static class OpenModelsEditorAction extends OpenEditorAction {
    public OpenModelsEditorAction(Agent agent) {
      super(agent, "models.properties");
    }    
  }
  private static class OpenProvidersUrlsEditorAction extends OpenEditorAction {
    public OpenProvidersUrlsEditorAction(Agent agent) {
      super(agent, "providers_urls.properties");
    }    
  }
  private static class OpenProvidersAPILKeysEditorAction extends OpenEditorAction {
    public OpenProvidersAPILKeysEditorAction(Agent agent) {
      super(agent, "providers_urls.properties");
    }    
  }
}

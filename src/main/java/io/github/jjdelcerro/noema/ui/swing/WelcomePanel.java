package io.github.jjdelcerro.noema.ui.swing;

import com.formdev.flatlaf.extras.FlatSVGUtils;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import static io.github.jjdelcerro.noema.lib.services.conversarion.ConversationService.CONVERSATION_MODEL_ID;
import static io.github.jjdelcerro.noema.lib.services.conversarion.ConversationService.CONVERSATION_PROVIDER_API_KEY;
import static io.github.jjdelcerro.noema.lib.services.conversarion.ConversationService.CONVERSATION_PROVIDER_URL;
import static io.github.jjdelcerro.noema.lib.services.memory.MemoryService.MEMORY_MODEL_ID;
import static io.github.jjdelcerro.noema.lib.services.memory.MemoryService.MEMORY_PROVIDER_API_KEY;
import static io.github.jjdelcerro.noema.lib.services.memory.MemoryService.MEMORY_PROVIDER_URL;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.main.BootUtils;
import io.github.jjdelcerro.noema.main.MainGUI;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Rectangle;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import static java.lang.System.exit;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import org.apache.commons.lang3.StringUtils;

/**
 * Panel de bienvenida para la selección de workspace y validación de
 * configuración.
 */
public class WelcomePanel extends WelcomePanelView {

  private AgentSettings settings;

  private SecondaryLoop loop;
  private JFrame window;
  private boolean returnValue;

  public WelcomePanel(AgentSettings workspaceSettings) {
    this.returnValue = true;
    this.settings = workspaceSettings;
    this.initUI();
    loadRecentWorkspaces();
    refreshWorkspaceConfig();
  }

  private void initUI() {
    comboWorkspace.addActionListener(e -> refreshWorkspaceConfig());
    btnBrowse.addActionListener(e -> handleBrowse());
    btnCancelar.addActionListener(e -> handleCancel());

    txtConfigSummary.setContentType("text/html");
    txtDisclaimer.setContentType("text/html");
    txtDisclaimer.setText(
            """
Noema es un agente <b>experimental</b> aut\u00f3nomo con capacidad de modificar archivos y ejecutar comandos.<br>
El uso de esta herramienta implica la aceptaci\u00f3n de los riesgos asociados.<br>
Aseg\u00farese de ejecutar el agente en un entorno controlado o con backups actualizados.<br>
""");
//    txtDisclaimer.setLineWrap(true);
//    txtDisclaimer.setWrapStyleWord(true);

    btnConfigure.addActionListener(e -> handleConfigure());
    btnContinue.addActionListener(e -> handleContinue());

    btnContinue.setEnabled(false);

    this.setPreferredSize(new Dimension(800, 700));
  }

  private void handleContinue() {
    this.closeWindow(true);
    this.settings.setLastWorkspacePath(this.settings.getPaths().getWorkspaceFolder().toString());
  }

  private void handleCancel() {
    closeWindow(false);
  }

  /**
   * Carga la lista de recientes desde los settings globales
   */
  private void loadRecentWorkspaces() {
    AgentManager manager = AgentLocator.getAgentManager();
    AgentPaths paths = manager.createAgentPaths(null);
    AgentSettings settings = manager.createSettings(paths);

    List<String> recents = settings.getLastWorkspacesPaths();
    if (recents != null) {
      recents.forEach(comboWorkspace::addItem);
    }
    String last = settings.getLastWorkspacePath();
    if (last != null) {
      comboWorkspace.setSelectedItem(last);
    }
  }

  /**
   * Se dispara cada vez que cambia el texto en el combo o se selecciona uno
   */
  private void refreshWorkspaceConfig() {
    AgentPaths paths = getSelectedPaths();
    if (paths == null) {
      showSummary("<i>Por favor, seleccione una carpeta de trabajo.</i>", false, false);
      btnContinue.setEnabled(false);
      return;
    }
    AgentManager manager = AgentLocator.getAgentManager();
    settings.setupSettings(paths);
    settings.load();

    // Validamos servicios
    AgentServiceFactory convFactory = manager.getServiceFactory("Conversation");
    AgentServiceFactory memFactory = manager.getServiceFactory("Memory");

    boolean convOk = convFactory != null && convFactory.canStart(settings);
    boolean memOk = memFactory != null && memFactory.canStart(settings);

    updateConfigSummaryUI(convOk, memOk);

    // Habilitar continuar solo si ambos motores están listos
    boolean canContinue = BootUtils.areSettingsValid(settings);
    btnContinue.setEnabled(canContinue);
    if (this.window != null && this.window.getRootPane() != null) {
      if (canContinue) {
        this.window.getRootPane().setDefaultButton(btnContinue);
      } else {
        this.window.getRootPane().setDefaultButton(null);
      }
    }
    this.repaint();
  }

  private void updateConfigSummaryUI(boolean convOk, boolean memOk) {
    StringBuilder sb = new StringBuilder("<html><body>");

    sb.append("<b>Model de conversación:</b> ").append(getStatusIcon(convOk)).append("<br>");
    sb.append("&nbsp;&nbsp;- Proveedor: ").append(getProperty(CONVERSATION_PROVIDER_URL)).append("<br>");
    sb.append("&nbsp;&nbsp;- Modelo: ").append(getProperty(CONVERSATION_MODEL_ID)).append("<br>");
    sb.append("&nbsp;&nbsp;- API Key: ").append(getMaskedApiKey(CONVERSATION_PROVIDER_API_KEY)).append("<br><br>");

    sb.append("<b>Modelo de compactación de memoria:</b> ").append(getStatusIcon(memOk)).append("<br>");
    sb.append("&nbsp;&nbsp;- Proveedor: ").append(getProperty(MEMORY_PROVIDER_URL)).append("<br>");
    sb.append("&nbsp;&nbsp;- Modelo: ").append(getProperty(MEMORY_MODEL_ID)).append("<br>");
    sb.append("&nbsp;&nbsp;- API Key: ").append(getMaskedApiKey(MEMORY_PROVIDER_API_KEY)).append("<br>");

    sb.append("</body></html>");
    txtConfigSummary.setText(sb.toString());
  }

  private String getStatusIcon(boolean ok) {
    return ok ? "<font color='#50fa7b'>🟢&#128994;</font>" : "<font color='#ff5555'>&#128308;</font>";
  }

  private String getMaskedApiKey(String key) {
    String val = settings.getPropertyAsString(key);
    if (val == null || val.isBlank()) {
      return "<font color='#ffb86c'>no configurada</font>";
    }
    return "********" + (val.length() > 4 ? val.substring(val.length() - 4) : "");
  }

  private String getProperty(String key) {
    String val = settings.getPropertyAsString(key);
    return val != null ? val : "no definido";
  }

  private void showSummary(String msg, boolean conv, boolean mem) {
    txtConfigSummary.setText("<html><body>" + msg + "</body></html>");
  }

  private void handleBrowse() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      String path = chooser.getSelectedFile().getAbsolutePath();
      comboWorkspace.setSelectedItem(path);
      refreshWorkspaceConfig();
    }
  }

  private void handleConfigure() {
    AgentPaths paths = getSelectedPaths();
    if (paths == null) {
      JOptionPane.showMessageDialog(this,
              "Debe indicar primero el Workspace (carpeta de trabajo).",
              "Workspace Requerido",
              JOptionPane.WARNING_MESSAGE);
      return;
    }
    this.settings.setupSettings(paths);
//    AgentUISettings settingsUI = AgentUILocator.getAgentUIManager().createSettings(this.settings); FIXME Por que no va?
    AgentSwingSettingsImpl settingsUI = new AgentSwingSettingsImpl(null, settings);
    settingsUI.showWindow(this.window);

    // Al volver, refrescamos los datos para ver si ya podemos habilitar "Continuar"
    refreshWorkspaceConfig();
  }

  public AgentPaths getSelectedPaths() {
    Object item = comboWorkspace.getSelectedItem();
    if (item == null || StringUtils.isBlank(item.toString())) {
      return null;
    }
    AgentManager manager = AgentLocator.getAgentManager();
    AgentPaths paths = manager.createAgentPaths(Path.of(item.toString().trim()));
    return paths;
  }

  public boolean showWindow() {
    AgentManager manager = AgentLocator.getAgentManager();

    GraphicsConfiguration config = MouseInfo.getPointerInfo().getDevice().getDefaultConfiguration();
    Rectangle bounds = config.getBounds();

    JFrame frame = new JFrame(manager.getName() + " v" + manager.getVersion());
    frame.getContentPane().add(this);
    frame.pack(); // Importante para que el frame tenga tamaño antes de calcular la posición
    int x = bounds.x + (bounds.width - frame.getWidth()) / 2;
    int y = bounds.y + (bounds.height - frame.getHeight()) / 2;
    frame.setLocation(x, y);
    try {
      List<Image> icons = FlatSVGUtils.createWindowIconImages(
              MainGUI.class.getResource("/io/github/jjdelcerro/noema/ui/swing/app_icon.svg")
      );
      frame.setIconImages(icons);
    } catch (Exception e) {
      // FIXME: log error
      System.err.println("No se pudo cargar el icono de la aplicación: " + e.getMessage());
    }

    this.loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent e) {
        loop.exit();
      }
    });
    frame.setVisible(true);
    this.window = frame;

    boolean canContinue = BootUtils.areSettingsValid(settings);
    if (canContinue) {
      this.window.getRootPane().setDefaultButton(btnContinue);
    }
    if (!this.loop.enter()) {
      // FIXME: log error
      System.err.println("No se pudo iniciar la aplicación");
      exit(2);
    }
    this.window.setVisible(false);
    return this.returnValue;
  }

  private void closeWindow(boolean returnValue) {
    this.loop.exit();
    this.returnValue = returnValue;
  }

}

package io.github.jjdelcerro.noema.ui.swing;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGUtils;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.AgentPaths;
import io.github.jjdelcerro.noema.lib.AgentServiceFactory;
import io.github.jjdelcerro.noema.lib.AgentSettings;
import io.github.jjdelcerro.noema.main.BootUtils;
import io.github.jjdelcerro.noema.main.MainGUI;
import io.github.jjdelcerro.noema.ui.AgentUILocator;
import io.github.jjdelcerro.noema.ui.AgentUISettings;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Image;
import java.nio.file.Path;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;

/**
 * Panel de bienvenida para la selección de workspace y validación de
 * configuración.
 */
public class WelcomePanel extends JPanel {

  private AgentSettings settings;

  private JComboBox<String> comboWorkspace;
  private JTextPane txtConfigSummary;
  private JTextArea txtDisclaimer;
  private JButton btnConfigure;
  private JButton btnContinue;
  private JDialog dialog;

  public WelcomePanel(AgentSettings workspaceSettings) {
    this.settings = workspaceSettings;
    initUI();
    loadRecentWorkspaces();
    refreshWorkspaceConfig();
  }

  private void initUI() {
    setLayout(new MigLayout("fillx, ins 20, wrap 1", "[grow]", "[]10[]15[]15[grow]20[]"));

    // --- SECCIÓN 1: Seleccion de Workspace ---
    add(new JLabel("Seleccione la carpeta de trabajo:"), "left");

    JPanel pnlWorkspace = new JPanel(new MigLayout("fillx, ins 0", "[grow]10[]"));
    pnlWorkspace.setOpaque(false);

    comboWorkspace = new JComboBox<>();
    comboWorkspace.setEditable(true);
    // Permitir que el usuario escriba o elija de recientes
    comboWorkspace.addActionListener(e -> refreshWorkspaceConfig());

    JButton btnBrowse = new JButton("Seleccionar");
    btnBrowse.addActionListener(e -> handleBrowse());

    pnlWorkspace.add(comboWorkspace, "growx");
    pnlWorkspace.add(btnBrowse, "right");
    add(pnlWorkspace, "growx");

    // --- SECCIÓN 2: Configuración Básica ---
    add(new JLabel("Configuración del motor:"), "left, gaptop 10");

    txtConfigSummary = new JTextPane();
    txtConfigSummary.setContentType("text/html");
    txtConfigSummary.setEditable(false);
    txtConfigSummary.setBackground(new Color(40, 40, 40));
    txtConfigSummary.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));

    // Aplicar el estilo redondeado de FlatLaf
    txtConfigSummary.putClientProperty(FlatClientProperties.STYLE, "arc: 10");

    add(new JScrollPane(txtConfigSummary), "growx, h 120!");

    // --- SECCIÓN 3: Disclaimer ---
    add(new JLabel("Aviso legal y condiciones:"), "left, gaptop 10");

    txtDisclaimer = new JTextArea(
            """
Noema es un agente aut\u00f3nomo con capacidad de modificar archivos y ejecutar comandos.
El uso de esta herramienta implica la aceptaci\u00f3n de los riesgos asociados.
Aseg\u00farese de ejecutar el agente en un entorno controlado o con backups actualizados.
""");
    txtDisclaimer.setEditable(false);
    txtDisclaimer.setLineWrap(true);
    txtDisclaimer.setWrapStyleWord(true);
    txtDisclaimer.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
    txtDisclaimer.setForeground(Color.LIGHT_GRAY);
    txtDisclaimer.setOpaque(false);

    add(new JScrollPane(txtDisclaimer), "grow, h 80!");

    // --- SECCIÓN 4: Botones de Acción ---
    JPanel pnlActions = new JPanel(new MigLayout("fillx, ins 0", "[]push[]"));
    pnlActions.setOpaque(false);

    btnConfigure = new JButton("Configurar");
    btnConfigure.addActionListener(e -> handleConfigure());

    btnContinue = new JButton("Continuar");
    btnContinue.putClientProperty(FlatClientProperties.STYLE, "background: $Component.accentColor; foreground: #ffffff");
    btnContinue.setEnabled(false);
    btnContinue.addActionListener(e -> doContinue());

    pnlActions.add(btnConfigure);
    pnlActions.add(btnContinue, "right");
    add(pnlActions, "growx, gaptop 10");
  }

  private void doContinue() {
     this.dialog.setVisible(false);
     this.settings.setLastWorkspacePath(this.settings.getPaths().getAgentFolder().toString());
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
    btnContinue.setEnabled(BootUtils.areSettingsValid(settings));
  }

  private void updateConfigSummaryUI(boolean convOk, boolean memOk) {
    StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif; font-size:10pt; color:white;'>");

    sb.append("<b>LLM de Conversación:</b> ").append(getStatusIcon(convOk)).append("<br>");
    sb.append("&nbsp;&nbsp;- Modelo: ").append(getProperty("CONVERSATION_MODEL_ID")).append("<br>");
    sb.append("&nbsp;&nbsp;- API Key: ").append(getMaskedApiKey("CONVERSATION_PROVIDER_API_KEY")).append("<br><br>");

    sb.append("<b>LLM de Memoria:</b> ").append(getStatusIcon(memOk)).append("<br>");
    sb.append("&nbsp;&nbsp;- Modelo: ").append(getProperty("MEMORY_MODEL_ID")).append("<br>");
    sb.append("&nbsp;&nbsp;- API Key: ").append(getMaskedApiKey("MEMORY_PROVIDER_API_KEY")).append("<br>");

    sb.append("</body></html>");
    txtConfigSummary.setText(sb.toString());
  }

  private String getStatusIcon(boolean ok) {
    return ok ? "<font color='#50fa7b'>🟢&#128994;</font>" : "<font color='#ff5555'>&#128308;</font>";
  }

  private String getMaskedApiKey(String key) {
    String val = settings.getProperty(key);
    if (val == null || val.isBlank()) {
      return "<font color='#ffb86c'>no configurada</font>";
    }
    return "********" + (val.length() > 4 ? val.substring(val.length() - 4) : "");
  }

  private String getProperty(String key) {
    String val = settings.getProperty(key);
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
    AgentUISettings settingsUI = AgentUILocator.getAgentUIManager().createSettings(this.settings);
    settingsUI.showWindow();

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
  
  public void showWindow() {
    this.dialog = new JDialog((Frame)null, "Noema v0.1.0", true);
    dialog.getContentPane().add(this);
    dialog.setSize(1024, 600);
    try {
      List<Image> icons = FlatSVGUtils.createWindowIconImages(
              MainGUI.class.getResource("/io/github/jjdelcerro/noema/ui/swing/app_icon.svg")
      );
      dialog.setIconImages(icons);
    } catch (Exception e) {
      // FIXME: log error
      System.err.println("No se pudo cargar el icono de la aplicación: " + e.getMessage());
    }
    dialog.setVisible(true);
  }
  
}

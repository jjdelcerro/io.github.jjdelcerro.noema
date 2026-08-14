package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.ActionListDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;
import com.googlecode.lanterna.gui2.Window.Hint;
import com.googlecode.lanterna.gui2.dialogs.ActionListDialog;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.terminal.Terminal;

import io.github.jjdelcerro.noema.lib.Agent;
import static io.github.jjdelcerro.noema.lib.Agent.DEFAULT_SUBCHANNEL;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.persistence.CheckPoint;
import io.github.jjdelcerro.noema.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.noema.lib.persistence.Turn;
import io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.SensorEventCallback;
import io.github.jjdelcerro.noema.ui.AgentUILocator;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import org.apache.commons.lang3.StringUtils;

public class MainLanternaWindow extends BasicWindow {

  private Panel mainPanel;
  private ColoredHistoryRenderer historyRenderer;
  private TextBox chatHistoryBox;
  private TextBox inputArea;
  private Label statusLabel;
  private Label lblTimer;
  private Label lblWorkspace;
  private Button btnSend;

  private Agent agent;
  private Timer thinkingTimer;
  private long thinkingStartTime;
  private String currentStatus = "Desconectado";
  private final Terminal terminal;

  public MainLanternaWindow(Terminal terminal) {
    super("Noema Agent");
    this.terminal = terminal;

    setHints(Arrays.asList(Hint.FULL_SCREEN, Hint.NO_DECORATIONS));

    mainPanel = new Panel(new LinearLayout(Direction.VERTICAL));
    mainPanel.setTheme(LanternaUtils.getMainTheme());

    setComponent(mainPanel);
  }

  public void setAgent(Agent agent) {
    if (this.agent == null) {
      this.agent = agent;

      try {
        this.terminal.clearScreen();
      } catch (IOException ex) {
        // Do nothing
      }

      initComponents();

      runOnGuiThread(() -> {
        if (getTextGUI() != null) {
          try {
            getTextGUI().updateScreen();
          } catch (Exception ignored) {
          }
        }
        currentStatus = "Listo";
        getInputArea().setReadOnly(false);
        getButtonSend().setEnabled(true);
        setFocusedInteractable(getInputArea());
        updateMetadata();
        showHistory(agent);
      });
    }
  }

  private TextBox getChatHistoryBox() {
    if (this.chatHistoryBox == null) {
      chatHistoryBox = new TextBox(new TerminalSize(0, 0), TextBox.Style.MULTI_LINE);
      chatHistoryBox.setReadOnly(false);
      chatHistoryBox.setTheme(LanternaUtils.getMainTheme());
      chatHistoryBox.setLayoutData(LinearLayout.createLayoutData(
              LinearLayout.Alignment.Fill,
              LinearLayout.GrowPolicy.CanGrow
      ));

      historyRenderer = new ColoredHistoryRenderer();
      chatHistoryBox.setRenderer(historyRenderer);
    }
    return chatHistoryBox;
  }

  private Label getStatusLabel() {
    if (statusLabel == null) {
      statusLabel = new Label("Estado: Desconectado | Esperando agente...");
//    statusLabel.setTheme(new SimpleTheme(textMuted, bgDark));
      statusLabel.setTheme(LanternaUtils.getMainTheme());
      statusLabel.setLayoutData(LinearLayout.createLayoutData(
              LinearLayout.Alignment.Fill,
              LinearLayout.GrowPolicy.CanGrow
      ));
    }
    return statusLabel;
  }

  private Label getLabelTimer() {
    if (lblTimer == null) {
      lblTimer = new Label("");
//    lblTimer.setTheme(new SimpleTheme(textWhite, bgDark));
      lblTimer.setTheme(LanternaUtils.getMainTheme());
    }
    return lblTimer;
  }

  private TextBox getInputArea() {
    if (inputArea == null) {
      inputArea = new TextBox(new TerminalSize(0, 3), TextBox.Style.MULTI_LINE);
      inputArea.setTheme(LanternaUtils.getInputTheme());
      inputArea.setReadOnly(true);
      inputArea.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
    }
    return inputArea;
  }

  private Label getLabelWorkspace() {
    if (lblWorkspace == null) {
      lblWorkspace = new Label("");
//    lblWorkspace.setTheme(new SimpleTheme(textMuted, bgDark));
      lblWorkspace.setTheme(LanternaUtils.getMainTheme());
      lblWorkspace.setLayoutData(LinearLayout.createLayoutData(
              LinearLayout.Alignment.Fill,
              LinearLayout.GrowPolicy.CanGrow
      ));
    }
    return lblWorkspace;

  }

  private Button getButtonSend() {
    if (btnSend == null) {
      btnSend = new Button("Enviar", this::handleSend);
      btnSend.setTheme(LanternaUtils.getMainTheme());
      btnSend.setEnabled(false);
      btnSend.setRenderer(new HighlightedButtonRenderer('E'));
    }
    return btnSend;
  }

  private final void initComponents() {

    // --- 0. BARRA SUPERIOR (HEADER) ---
    Panel headerPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    headerPanel.setTheme(LanternaUtils.getMainTheme());
    headerPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));

    Button btnMenu = new Button("= Menú", this::onMenuPressed);
    btnMenu.setTheme(LanternaUtils.getMainTheme());
    btnMenu.setRenderer(new HighlightedButtonRenderer('M'));

    Label headerSpacer = new Label("");
    headerSpacer.setTheme(LanternaUtils.getMainTheme());
    headerSpacer.setLayoutData(LinearLayout.createLayoutData(
            LinearLayout.Alignment.Fill,
            LinearLayout.GrowPolicy.CanGrow
    ));

    Button btnExit = new Button("Cerrar X", this::onExitPressed);
    btnExit.setTheme(LanternaUtils.getMainTheme());
    btnExit.setRenderer(new HighlightedButtonRenderer('X'));

    headerPanel.addComponent(btnMenu);
    headerPanel.addComponent(headerSpacer);
    headerPanel.addComponent(btnExit);

    mainPanel.addComponent(headerPanel);

    // --- 1. HISTORIAL DE CONVERSACIÓN ---
    mainPanel.addComponent(this.getChatHistoryBox());

    // --- 2. BARRA DE ESTADO Y CRONÓMETRO ---
    Panel statusPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    statusPanel.setTheme(LanternaUtils.getMainTheme());
    statusPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));

    statusPanel.addComponent(getStatusLabel());
    statusPanel.addComponent(getLabelTimer());
    mainPanel.addComponent(statusPanel);

    // --- 3. PANEL DE ENTRADA Y FOOTER ---
    Panel inputPanel = new Panel(new LinearLayout(Direction.VERTICAL));
    inputPanel.setTheme(LanternaUtils.getMainTheme());
    inputPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));

    inputPanel.addComponent(getInputArea());

    Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    buttonPanel.setTheme(LanternaUtils.getMainTheme());
    buttonPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));

    buttonPanel.addComponent(getLabelWorkspace());
    buttonPanel.addComponent(getButtonSend());

    inputPanel.addComponent(buttonPanel);
    mainPanel.addComponent(inputPanel);
  }

  // --- ATAJOS DE TECLADO GLOBALES ---
  @Override
  public boolean handleInput(KeyStroke keyStroke) {
    Character c = keyStroke.getCharacter();
    if (keyStroke != null && keyStroke.isAltDown()) {
      if (keyStroke.getKeyType() == KeyType.Enter || c == 'j' || c == 'e' || c == 'E') {
        handleSend();
        return true;
      }
      if (c != null && (c == 'm' || c == 'M')) {
        onMenuPressed();
        return true;
      }
      if (c != null && (c == 'c' || c == 'C')) {
        onConfigPressed();
        return true;
      }
      if (c != null && (c == 'x' || c == 'X')) {
        onExitPressed();
        return true;
      }
    }
    return super.handleInput(keyStroke);
  }

  // --- DELEGACIÓN DE PROPIEDADES AL RENDERIZADOR ---
  public boolean isShowSystemLogs() {
    return historyRenderer.isShowSystemLogs();
  }

  public void setShowSystemLogs(boolean show) {
    historyRenderer.setShowSystemLogs(show);
    runOnGuiThread(() -> {
      if (getTextGUI() != null) {
        try {
          getTextGUI().updateScreen();
        } catch (Exception ignored) {
        }
      }
    });
  }

  public boolean isShowErrorLogs() {
    return historyRenderer.isShowErrorLogs();
  }

  public void setShowErrorLogs(boolean show) {
    historyRenderer.setShowErrorLogs(show);
    runOnGuiThread(() -> {
      if (getTextGUI() != null) {
        try {
          getTextGUI().updateScreen();
        } catch (Exception ignored) {
        }
      }
    });
  }

  // --- MÉTODOS DE MENÚ Y ACCIONES ---
  private void onMenuPressed() {
    if (getTextGUI() == null) {
      return;
    }

    ActionListDialog dialog = new ActionListDialogBuilder()
            .setTitle("Men\u00FA Principal")
            .setDescription("Selecciona una opci\u00F3n:")
            .addAction("Ajustes del Agente (Alt+C)", this::onConfigPressed)
            .addAction("Preferencias de la Terminal", this::onTerminalPreferencesPressed)
            .addAction("Exportar conversaci\u00F3n (.md)", this::onExportConversationPressed)
            .addAction("Copiar al portapapeles", this::onCopyClipboardPressed)
            .addAction("Limpiar pantalla de chat", this::onClearChatPressed)
            .build();

    if (dialog != null) {
      dialog.setTheme(LanternaUtils.getMainTheme());
      dialog.showDialog(getTextGUI());
    }
  }

  private void onExitPressed() {
    close();
  }

  private void onConfigPressed() {
    if (agent == null) {
      return;
    }
    AgentUILocator.getAgentUIManager().createSettings(agent).showWindow();
  }

  private void onTerminalPreferencesPressed() {
    if (getTextGUI() == null) {
      return;
    }

    BasicWindow prefWindow = new BasicWindow("Preferencias de la Terminal");
    prefWindow.setHints(Arrays.asList(Hint.CENTERED));
    prefWindow.setTheme(LanternaUtils.getMainTheme());

    Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
    panel.setTheme(LanternaUtils.getMainTheme());
    CheckBoxList<String> checkList = new CheckBoxList<>();

    checkList.addItem("Mostrar mensajes e incidencias del sistema ([SIS])", isShowSystemLogs());
    checkList.addItem("Mostrar errores del sistema ([ERR])", isShowErrorLogs());

    panel.addComponent(checkList);

    Panel btnPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    btnPanel.addComponent(new Button("Aceptar", () -> {
      setShowSystemLogs(checkList.isChecked(0));
      setShowErrorLogs(checkList.isChecked(1));
      prefWindow.close();
    }));
    btnPanel.addComponent(new Button("Cancelar", prefWindow::close));

    panel.addComponent(btnPanel, LinearLayout.createLayoutData(LinearLayout.Alignment.End));
    prefWindow.setComponent(panel);

    getTextGUI().addWindowAndWait(prefWindow);
  }

  private void onExportConversationPressed() {
    if (getTextGUI() == null) {
      return;
    }

    String defaultFileName = "noema-chat-" + System.currentTimeMillis() + ".md";
    String fileName = TextInputDialog.showDialog(
            getTextGUI(),
            "Exportar Conversación",
            "Nombre del archivo Markdown a crear:",
            defaultFileName
    );

    if (fileName != null && !fileName.trim().isEmpty()) {
      try {
        Path exportPath = (agent != null && agent.getPaths() != null && agent.getPaths().getWorkspaceFolder() != null)
                ? agent.getPaths().getWorkspaceFolder().resolve(fileName.trim())
                : Path.of(fileName.trim());

        String markdownContent = generateMarkdownContent();
        Files.writeString(exportPath, markdownContent, StandardCharsets.UTF_8);

        appendSystemLog("Conversación exportada correctamente a: " + exportPath.getFileName());
      } catch (Exception e) {
        appendSystemLog("Error al exportar conversación: " + e.getMessage());
      }
    }
  }

  private void onCopyClipboardPressed() {
    try {
      String markdownContent = generateMarkdownContent();
      StringSelection selection = new StringSelection(markdownContent);
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
      appendSystemLog("✓ Conversación copiada al portapapeles del sistema");
    } catch (Throwable e) {
      appendSystemLog("Error copiando al portapapeles (entorno sin GUI/SSH): " + e.getMessage());
    }
  }

  private void onClearChatPressed() {
    runOnGuiThread(() -> getChatHistoryBox().setText(""));
  }

  private String generateMarkdownContent() {
    StringBuilder sb = new StringBuilder();
    sb.append("# Conversación Noema Agent\n\n");
    sb.append("_Exportada el: ").append(java.time.LocalDateTime.now()).append("_\n\n");
    sb.append("---\n\n");

    String rawText = getChatHistoryBox().getText();
    if (rawText != null) {
      for (String line : rawText.split("\n")) {
        if (line.startsWith("[USR] ")) {
          sb.append("### 👤 Usuario\n").append(line.substring(6)).append("\n\n");
        } else if (line.startsWith("[RES] ")) {
          sb.append("### 🤖 Noema\n").append(line.substring(6)).append("\n\n");
        } else if (line.startsWith("[SIS] ")) {
          sb.append("> **[SIS]** ").append(line.substring(6)).append("\n\n");
        } else if (line.startsWith("[ERR] ")) {
          sb.append("> **[ERR]** ").append(line.substring(6)).append("\n\n");
        } else {
          sb.append(line).append("\n");
        }
      }
    }
    return sb.toString();
  }

  // --- GESTIÓN DEL AGENTE Y METADATOS ---
  private void runOnGuiThread(Runnable action) {
    TextGUIThread guiThread = (getTextGUI() != null) ? getTextGUI().getGUIThread() : null;

    if (guiThread == null) {
      action.run();
    } else {
      guiThread.invokeLater(() -> {
        action.run();
        try {
          getTextGUI().updateScreen();
        } catch (Exception ignored) {
        }
      });
    }
  }

  public void updateMetadata() {
    if (agent == null) {
      getStatusLabel().setText("Estado: Desconectado | Esperando agente...");
      getLabelWorkspace().setText("");
      return;
    }

    if (agent.getPaths() != null && agent.getPaths().getWorkspaceFolder() != null) {
      getLabelWorkspace().setText(agent.getPaths().getWorkspaceFolder().toString());
    }

    ReasoningService reasoning = (ReasoningService) agent.getService(ReasoningService.NAME);
    String subchannel = agent.getCurrentSubchannel();

    if (reasoning == null) {
      getStatusLabel().setText("Estado: " + currentStatus + " | Modelo: -");
      return;
    }

    Agent.ChatModel model = reasoning.getModel();
    String modelName = (model != null) ? model.getParameters().modelId() : "-";
    int turns = reasoning.getTurnsCount(subchannel);

    double tokensK = (reasoning.estimateToolsTokenCount(subchannel)
            + reasoning.estimateSystemPromptTokenCount(subchannel)
            + reasoning.estimateMessagesTokenCount(subchannel)) / 1024.0;
    double contextK = agent.getConversationContextSize() / 1024.0;

    String text = String.format(
            Locale.ENGLISH,
            "Estado: %s | Modelo: %s | Turnos: %d | Tokens: %.1fk / %.1fk",
            currentStatus,
            modelName,
            turns,
            tokensK,
            contextK
    );
    getStatusLabel().setText(text);
  }

  private void showHistory(Agent agent) {
    AgentConsole console = agent.getConsole(DEFAULT_SUBCHANNEL);
    String subchannel = agent.getCurrentSubchannel();
    try {
      SourceOfTruth sot = agent.getSourceOfTruth();
      CheckPoint activeCheckPoint = sot.getLatestCheckPoint(subchannel);
      List<Turn> turns = sot.getUnconsolidatedTurns(subchannel);

      if (activeCheckPoint != null && StringUtils.isNotBlank(activeCheckPoint.getText())) {
        console.printSystemLog(activeCheckPoint.getText(), AgentConsole.Format.Markdown);
      }

      for (Turn turn : turns) {
        if (StringUtils.isNotBlank(turn.getTextUser())) {
          console.printUserMessage(turn.getTextUser());
        }
        if (StringUtils.isNotBlank(turn.getToolCall())) {
          console.printSystemLog(turn.getToolCall());
        }
        if ("error".equals(turn.getContenttype()) && StringUtils.isNotBlank(turn.getToolResult())) {
          console.printSystemError(turn.getToolResult());
        }
        if (StringUtils.isNotBlank(turn.getTextModel())) {
          console.printModelResponse(turn.getTextModel());
        }
      }
    } catch (Exception e) {
      console.printSystemError("Error al cargar el historial del terminal: " + e.getMessage());
    }
  }

  // --- FLUJO DE ENVÍO Y PENSAMIENTO ---
  private void handleSend() {
    if (agent == null || getInputArea().isReadOnly()) {
      return;
    }

    String text = getInputArea().getText().trim();
    if (text.isEmpty()) {
      return;
    }

    getInputArea().setText("");
    appendUserMessage(text);

    startThinking();
    updateMetadata();

    Thread.ofPlatform().start(() -> {
      try {
        agent.putUsersMessage(DEFAULT_SUBCHANNEL, text, new SensorEventCallback() {
          @Override
          public void onComplete(String response) {
            stopThinking();
            updateMetadata();
          }
        });
      } catch (Exception e) {
        appendSystemLog("Error enviando mensaje: " + e.getMessage());
        stopThinking();
        updateMetadata();
      }
    });
  }

  private void startThinking() {
    currentStatus = "Pensando...";
    getInputArea().setReadOnly(true);
    getButtonSend().setEnabled(false);

    thinkingStartTime = System.currentTimeMillis();
    getLabelTimer().setText("0.0s");

    thinkingTimer = new Timer(true);
    thinkingTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        long elapsed = System.currentTimeMillis() - thinkingStartTime;
        getLabelTimer().setText(String.format(Locale.ENGLISH, "%.1fs", elapsed / 1000.0));
      }
    }, 100, 100);
  }

  private void stopThinking() {
    if (thinkingTimer != null) {
      thinkingTimer.cancel();
      thinkingTimer = null;
    }
    getLabelTimer().setText("");
    currentStatus = "Listo";

    getInputArea().setReadOnly(false);
    getButtonSend().setEnabled(true);

    setFocusedInteractable(getInputArea());
  }

  public void appendUserMessage(String text) {
    this.addHistoryLine("[USR] " + text);
  }

  public void appendModelResponse(String text) {
    this.addHistoryLine("[RES] " + text);
  }

  public void appendSystemLog(String text) {
    this.addHistoryLine("[SIS] " + text);
  }

  private void addHistoryLine(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    runOnGuiThread(() -> {
      this.getChatHistoryBox().addLine(text);
      int totalLineas = this.getChatHistoryBox().getLineCount();
      this.getChatHistoryBox().setCaretPosition(totalLineas - 1, 0);
    });
  }
}

package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.TextBox.DefaultTextBoxRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.TextGUIThread;
import com.googlecode.lanterna.gui2.Window.Hint;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import io.github.jjdelcerro.noema.lib.Agent;
import static io.github.jjdelcerro.noema.lib.Agent.DEFAULT_SUBCHANNEL;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.persistence.CheckPoint;
import io.github.jjdelcerro.noema.lib.persistence.SourceOfTruth;
import io.github.jjdelcerro.noema.lib.persistence.Turn;
import io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService;
import io.github.jjdelcerro.noema.lib.services.sensors.SensorsService.SensorEventCallback;
import io.github.jjdelcerro.noema.ui.AgentUILocator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class MainLanternaWindow extends BasicWindow {

  private final TextBox chatHistoryBox;
  private final TextBox inputArea;
  private final Label statusLabel;
  private final Label lblTimer;
  private final Label lblWorkspace;
  private final Button btnSend;
  private final Button btnConfig;

  private Agent agent;
  private Timer thinkingTimer;
  private long thinkingStartTime;
  private String currentStatus = "Desconectado";

  public MainLanternaWindow() {
    super("Noema Agent - Terminal UI");
    // Quitar bordes y título exterior
    setHints(Arrays.asList(Hint.FULL_SCREEN, Hint.NO_DECORATIONS));

    // Colores estilo Codex
    TextColor bgDark = TextColor.Factory.fromString("#18191A");
    TextColor bgInputBar = TextColor.Factory.fromString("#33373B");
    TextColor textWhite = TextColor.Factory.fromString("#E1E4E8");
    TextColor textMuted = TextColor.Factory.fromString("#8B949E");

    SimpleTheme mainTheme = new SimpleTheme(textWhite, bgDark);
    SimpleTheme inputTheme = new SimpleTheme(textWhite, bgInputBar);

    Panel mainPanel = new Panel(new LinearLayout(Direction.VERTICAL));
    mainPanel.setTheme(mainTheme);

    // 1. Historial de conversación (se expande vertical y horizontalmente)
    chatHistoryBox = new TextBox(new TerminalSize(0, 0), TextBox.Style.MULTI_LINE);
    chatHistoryBox.setReadOnly(false); // En readonly no desplaza el scroll al final al añadir texto
    chatHistoryBox.setTheme(mainTheme);
    chatHistoryBox.setLayoutData(LinearLayout.createLayoutData(
            LinearLayout.Alignment.Fill,
            LinearLayout.GrowPolicy.CanGrow
    ));
    chatHistoryBox.setRenderer(new ColoredHistoryRenderer());

    mainPanel.addComponent(chatHistoryBox);

    // 2. Barra de estado superior + Cronómetro (Status Panel)
    Panel statusPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    statusPanel.setTheme(mainTheme);
    statusPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));

    statusLabel = new Label("Estado: Desconectado | Esperando agente...");
    statusLabel.setTheme(new SimpleTheme(textMuted, bgDark));
    statusLabel.setLayoutData(LinearLayout.createLayoutData(
            LinearLayout.Alignment.Fill,
            LinearLayout.GrowPolicy.CanGrow
    ));

    lblTimer = new Label("");
    lblTimer.setTheme(new SimpleTheme(textWhite, bgDark));

    statusPanel.addComponent(statusLabel);
    statusPanel.addComponent(lblTimer);
    mainPanel.addComponent(statusPanel);

    // 3. Panel de Entrada (Vertical: Caja de texto arriba, Barra inferior abajo)
    Panel inputPanel = new Panel(new LinearLayout(Direction.VERTICAL));
    inputPanel.setTheme(mainTheme);
    inputPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));

    // 3a. Caja de entrada multilínea estándar (Enter insere \n de forma nativa)
    inputArea = new TextBox(new TerminalSize(0, 3), TextBox.Style.MULTI_LINE);
    inputArea.setTheme(inputTheme);
    inputArea.setReadOnly(true); // Deshabilitada hasta setAgent()
    inputArea.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
    inputPanel.addComponent(inputArea);

    // 3b. Barra de botones inferior (Horizontal con fondo de mainTheme)
    // Disposición: [ Ruta Workspace (se expande) ] [ Enviar ] [ Configuración ]
    Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    buttonPanel.setTheme(mainTheme);
    buttonPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));

    lblWorkspace = new Label("");
    lblWorkspace.setTheme(new SimpleTheme(textMuted, bgDark));
    lblWorkspace.setLayoutData(LinearLayout.createLayoutData(
            LinearLayout.Alignment.Fill,
            LinearLayout.GrowPolicy.CanGrow
    ));

    btnSend = new Button("Enviar", this::handleSend);
    btnSend.setTheme(mainTheme);
    btnSend.setEnabled(false);
    btnSend.setRenderer(new HighlightedButtonRenderer('E'));

    btnConfig = new Button("Configuración", this::onConfigPressed);
    btnConfig.setTheme(mainTheme);
    btnConfig.setEnabled(false);
    btnConfig.setRenderer(new HighlightedButtonRenderer('C'));

    buttonPanel.addComponent(lblWorkspace);
    buttonPanel.addComponent(btnSend);
    buttonPanel.addComponent(btnConfig);

    inputPanel.addComponent(buttonPanel);
    mainPanel.addComponent(inputPanel);

    setComponent(mainPanel);
  }

  // --- ATAJOS DE TECLADO GLOBALES (Alt+Enter para Enviar / Alt+C para Configuración) ---
  @Override
  public boolean handleInput(KeyStroke keyStroke) {
    Character c = keyStroke.getCharacter();
    if (keyStroke != null && keyStroke.isAltDown()) {
      if (keyStroke.getKeyType() == KeyType.Enter || c == 'j' || c == 'e' || c == 'E') {
        handleSend();
        return true;
      }
      if (c != null && (c == 'c' || c == 'C')) {
        onConfigPressed();
        return true;
      }
    }
    return super.handleInput(keyStroke);
  }

  // --- GESTIÓN DEL AGENTE Y METADATOS ---
  public void setAgent(Agent agent) {
    this.agent = agent;
    if (agent != null) {
      currentStatus = "Listo";
      inputArea.setReadOnly(false);
      btnSend.setEnabled(true);
      btnConfig.setEnabled(true);
      setFocusedInteractable(inputArea);
      updateMetadata();
      showHistory(agent);
    }
  }

  public void updateMetadata() {
    if (agent == null) {
      statusLabel.setText("Estado: Desconectado | Esperando agente...");
      lblWorkspace.setText("");
      return;
    }

    // Actualizar la ruta del Workspace en la barra inferior
    if (agent.getPaths() != null && agent.getPaths().getWorkspaceFolder() != null) {
      lblWorkspace.setText(agent.getPaths().getWorkspaceFolder().toString());
    }

    ReasoningService reasoning = (ReasoningService) agent.getService(ReasoningService.NAME);
    String subchannel = agent.getCurrentSubchannel();

    if (reasoning == null) {
      statusLabel.setText("Estado: " + currentStatus + " | Modelo: -");
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
    statusLabel.setText(text);
  }

  // --- FLUJO DE ENVÍO Y PENSAMIENTO ---
  private void handleSend() {
    if (agent == null || inputArea.isReadOnly()) {
      return;
    }

    String text = inputArea.getText().trim();
    if (text.isEmpty()) {
      return;
    }

    inputArea.setText("");
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
    inputArea.setReadOnly(true);
    btnSend.setEnabled(false);
    btnConfig.setEnabled(false);

    thinkingStartTime = System.currentTimeMillis();
    lblTimer.setText("0.0s");

    thinkingTimer = new Timer(true);
    thinkingTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        long elapsed = System.currentTimeMillis() - thinkingStartTime;
        lblTimer.setText(String.format(Locale.ENGLISH, "%.1fs", elapsed / 1000.0));
      }
    }, 100, 100);
  }

  private void stopThinking() {
    if (thinkingTimer != null) {
      thinkingTimer.cancel();
      thinkingTimer = null;
    }
    lblTimer.setText("");
    currentStatus = "Listo";

    inputArea.setReadOnly(false);
    btnSend.setEnabled(true);
    btnConfig.setEnabled(true);

    setFocusedInteractable(inputArea);
  }

  private void onConfigPressed() {
    if (agent != null) {
      AgentUILocator.getAgentUIManager().createSettings(agent).showWindow();
    }
  }

  public void appendUserMessage(String text) {
    this.addWrappedLine("[USR] " + text);
  }

  public void appendModelResponse(String text) {
    this.addWrappedLine("[RES] " + text);
  }

  public void appendSystemLog(String text) {
    this.addWrappedLine("[SIS] " + text);
  }

  private void chatHistoryScrollBottom() {
    int totalLineas = this.chatHistoryBox.getLineCount();
    this.chatHistoryBox.setCaretPosition(totalLineas - 1, 0);
  }

  private void addWrappedLine(String theText) {
    if (theText == null || theText.isEmpty()) {
      return;
    }
    Runnable action = () -> {
      int width = chatHistoryBox.getSize().getColumns();
      if (width <= 0) {
        width = 80;
      }
      String text = theText;
      String prefix = "";
      if (text.startsWith("[USR] ")) {
        prefix = "[USR] ";
        text = text.substring(5, text.length() - 5);
      } else if (text.startsWith("[SIS] ")) {
        prefix = "[SIS] ";
        text = text.substring(5, text.length() - 5);
      } else if (text.startsWith("[ERR] ")) {
        prefix = "[ERR] ";
        text = text.substring(5, text.length() - 5);
      } else if (text.startsWith("[RES] ")) {
        prefix = "[RES] ";
        text = text.substring(5, text.length() - 5);
      }

      int maxLineLength = Math.max(20, width - 2);

      for (String rawLine : text.split("\r?\n")) {
        if (rawLine.length() <= maxLineLength) {
          chatHistoryBox.addLine(prefix + rawLine);
        } else {
          StringBuilder currentLine = new StringBuilder();
          for (String word : rawLine.split(" ")) {
            while (word.length() > maxLineLength) {
              if (currentLine.length() > 0) {
                chatHistoryBox.addLine(prefix + currentLine.toString());
                currentLine.setLength(0);
              }
              chatHistoryBox.addLine(prefix + word.substring(0, maxLineLength));
              word = word.substring(maxLineLength);
            }

            if (currentLine.length() + word.length() + (currentLine.length() > 0 ? 1 : 0) > maxLineLength) {
              chatHistoryBox.addLine(prefix + currentLine.toString());
              currentLine = new StringBuilder(word);
            } else {
              if (currentLine.length() > 0) {
                currentLine.append(" ");
              }
              currentLine.append(word);
            }
          }
          if (currentLine.length() > 0) {
            chatHistoryBox.addLine(prefix + currentLine.toString());
          }
        }
      }
      int totalLineas = chatHistoryBox.getLineCount();
      chatHistoryBox.setCaretPosition(totalLineas - 1, 0);

    };
    TextGUIThread guiThread = null;
    if (this.getTextGUI() != null) {
      guiThread = this.getTextGUI().getGUIThread();
    };
    if (guiThread == null) {
      action.run();
    } else {
      guiThread.invokeLater(action);
    }
  }

  private static class ColoredHistoryRenderer extends DefaultTextBoxRenderer {

    // Paleta de colores Codex
    private static final TextColor COLOR_USER = TextColor.Factory.fromString("#3FB950"); // Verde
    private static final TextColor COLOR_LOG = TextColor.Factory.fromString("#8B949E"); // Gris tenue
    private static final TextColor COLOR_ERR = TextColor.Factory.fromString("#F85149"); // Rojo error
    private static final TextColor COLOR_MODEL = TextColor.Factory.fromString("#E1E4E8"); // Blanco brillante

    @Override
    public void drawComponent(TextGUIGraphics graphics, TextBox textBox) {
      // 1. Limpiar fondo con el color del tema
      graphics.applyThemeStyle(textBox.getThemeDefinition().getNormal());
      graphics.fill(' ');

      String cleanText = textBox.getText().replace("\r", "");
      String[] lines = StringUtils.splitPreserveAllTokens(cleanText, "\n");

      int height = graphics.getSize().getRows();

      // 2. Calcular qué línea queda arriba según la posición del cursor (scroll)
      int caretRow = textBox.getCaretPosition().getRow();
      int topLine = Math.max(0, caretRow - height + 1);

      // 3. Dibujar sólo las líneas visibles en pantalla asignando color por prefijo
      for (int row = 0; row < height; row++) {
        int lineIndex = topLine + row;
        if (lineIndex >= lines.length) {
          break;
        }

        String line = lines[lineIndex];

        // Seleccionar color según cómo empieza la línea
        if (line.startsWith("[USR]")) {
          graphics.setForegroundColor(COLOR_USER);
          line = line.substring(5, line.length());
        } else if (line.startsWith("[SIS]")) {
          graphics.setForegroundColor(COLOR_LOG);
          line = line.substring(5, line.length());
        } else if (line.startsWith("[ERR]")) {
          graphics.setForegroundColor(COLOR_ERR);
          line = line.substring(5, line.length());
        } else if (line.startsWith("[RES]")) {
          graphics.setForegroundColor(COLOR_MODEL);
          line = line.substring(5, line.length());
        }

        graphics.putString(0, row, line);
      }
    }
  }

  private void showHistory(Agent agent) {
    AgentConsole console = agent.getConsole(DEFAULT_SUBCHANNEL);
    String subchannel = agent.getCurrentSubchannel();
    try {
      SourceOfTruth sot = agent.getSourceOfTruth();

      // Cargar el punto de guardado (resumen/relato activo) si existe
      CheckPoint activeCheckPoint = sot.getLatestCheckPoint(subchannel);

      // Cargar la lista de turnos sin consolidar
      List<Turn> turns = sot.getUnconsolidatedTurns(subchannel);

      // 2. Renderizar los elementos de forma secuencial en el hilo de Swing (EDT)
      // a) Si hay un CheckPoint anterior, mostramos el relato/resumen consolidado
      if (activeCheckPoint != null && StringUtils.isNotBlank(activeCheckPoint.getText())) {
        console.printSystemLog(activeCheckPoint.getText(), AgentConsole.Format.Markdown);
      }

      // b) Recorremos los turnos no consolidados y dibujamos sus componentes
      for (Turn turn : turns) {
        // Mensaje original del usuario
        if (StringUtils.isNotBlank(turn.getTextUser())) {
          console.printUserMessage(turn.getTextUser());
        }

        // Logs de llamadas a herramientas (si las hubo)
        if (StringUtils.isNotBlank(turn.getToolCall())) {
          console.printSystemLog(turn.getToolCall());
        }

        // Mensajes de error en herramientas (si aplica)
        if ("error".equals(turn.getContenttype()) && StringUtils.isNotBlank(turn.getToolResult())) {
          console.printSystemError(turn.getToolResult());
        }

        // Respuesta final del modelo
        if (StringUtils.isNotBlank(turn.getTextModel())) {
          console.printModelResponse(turn.getTextModel());
        }
      }
    } catch (Exception e) {
      console.printSystemError("Error al cargar el historial del terminal: " + e.getMessage());
    }
  }
}

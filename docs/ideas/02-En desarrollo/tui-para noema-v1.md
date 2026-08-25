
### 1. ¿Qué es Lanterna y por qué encaja tan bien?

[Lanterna](https://github.com/mabe02/lanterna) es una librería en Java puro (sin dependencias JNI/C como ncurses) que permite crear interfaces compuestas por widgets en el terminal (ventanas, botones, cajas de texto, listas, menús, etc.).

Tiene tres niveles de abstracción:
1. **Low-level (Terminal):** Control directo de cursores, colores ANSI y caracteres en consola.
2. **Buffer-level (Screen):** Un buffer virtual bidimensional sobre la pantalla (similar a un *double-buffering* gráfico).
3. **High-level (GUI / WindowBasedTextGUI):** Sistema de componentes, eventos y ventanas que **se parece muchísimo a Swing** (`Window`, `Panel`, `TextBox`, `Button`, `Label`, `LinearLayout`).

Dado que ya implementaste `MainGUI` en Swing, la transición conceptual a Lanterna nivel 3 (**GUI**) te resultará muy familiar.

---

### 2. Estrategia de integración en Noema

Para seguir las convenciones de tu proyecto, el nuevo frontal debe vivir en su propio paquete: `io.github.jjdelcerro.noema.ui.lanterna`.

Deberás implementar:

```
io.github.jjdelcerro.noema.ui.lanterna/
├── AgentLanternaManagerImpl.java   (implementa AgentUIManager)
├── AgentLanternaConsoleImpl.java   (implementa AgentConsole)
├── AgentLanternaSettingsImpl.java  (implementa AgentUISettings)
├── MainLanternaWindow.java         (Ventana principal del chat TUI)
└── MainLanterna.java               (Punto de entrada / Boot)
```

---

### 3. Paso a paso para implementarlo

#### Paso 1: Añadir la dependencia en `pom.xml`

```xml
<dependency>
    <groupId>com.googlecode.lanterna</groupId>
    <artifactId>lanterna</artifactId>
    <version>3.1.2</version>
</dependency>
```

---

#### Paso 2: El Modelo Mental de Lanterna GUI

En Lanterna de alto nivel, la estructura típica de arranque es:

```java
// 1. Crear el Terminal y la Pantalla (Screen)
Terminal terminal = new DefaultTerminalFactory().createTerminal();
Screen screen = new TerminalScreen(terminal);
screen.startScreen();

// 2. Crear la interfaz GUI basada en ventanas
MultiWindowTextGUI gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.BLACK));

// 3. Crear tu ventana
BasicWindow window = new BasicWindow("Noema Agent - TUI");

// 4. Añadir a la GUI y ejecutar el bucle de eventos (bloqueante)
gui.addWindowAndWait(window);
```

---

#### Paso 3: Esquema de la Ventana Principal (`MainLanternaWindow.java`)

A nivel de layout, la consola TUI puede organizarse con un contenedor principal en columna:
1. **Área de mensajes / Historial** (un `TextBox` multilínea o una lista de `Label`s dentro de un panel con scroll).
2. **Barra de estado / Metadatos** (`Label` con modelo actual, tokens, etc.).
3. **Caja de entrada de usuario** (`TextBox` + botón o enviar con `Enter`).

Ejemplo esquemático:

```java
package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;

import java.util.Arrays;

public class MainLanternaWindow extends BasicWindow {

    private final TextBox chatHistoryBox;
    private final TextBox inputArea;
    private final Label statusLabel;

    public MainLanternaWindow() {
        super("Noema Agent - Terminal UI");
        setHints(Arrays.asList(Hint.MAXIMIZED)); // Ocupa toda la terminal

        Panel mainPanel = new Panel(new LinearLayout(Direction.VERTICAL));

        // 1. Historial de conversación (Read-Only)
        chatHistoryBox = new TextBox(new TerminalSize(80, 20), TextBox.Style.MULTI_LINE);
        chatHistoryBox.setReadOnly(true);
        mainPanel.addComponent(chatHistoryBox.withBorder(Borders.singleLine("Conversación")));

        // 2. Barra de estado
        statusLabel = new Label("Estado: Listo | Modelo: - | Tokens: 0");
        mainPanel.addComponent(statusLabel);

        // 3. Área de entrada de usuario
        Panel inputPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        inputArea = new TextBox(new TerminalSize(70, 3));
        
        Button btnSend = new JButton("Enviar", this::onSendPressed);
        
        inputPanel.addComponent(inputArea.withBorder(Borders.singleLine("Mensaje")));
        inputPanel.addComponent(btnSend);

        mainPanel.addComponent(inputPanel);

        setComponent(mainPanel);
    }

    private void onSendPressed() {
        String text = inputArea.getText().trim();
        if (!text.isEmpty()) {
            appendUserMessage(text);
            inputArea.setText("");
            // Disparar mensaje al Agente a través de la consola/puente
        }
    }

    public void appendUserMessage(String text) {
        chatHistoryBox.addLine("Usuario > " + text);
    }

    public void appendModelResponse(String text) {
        chatHistoryBox.addLine("Noema > " + text);
    }

    public void appendSystemLog(String text) {
        chatHistoryBox.addLine("[LOG] " + text);
    }
}
```

---

#### Paso 4: Implementar `AgentConsole` para Lanterna (`AgentLanternaConsoleImpl.java`)

Un aspecto crucial en Lanterna es la **concurrencia**: los hilos del agente (`ReasoningServiceImpl`, `SensorsServiceImpl`) emitirán logs y respuestas desde hilos secundarios. En Lanterna, las actualizaciones UI que vienen de hilos externos deben refrescar la pantalla mediante el bucle del GUI o llamando a `gui.updateScreen()`.

```java
package io.github.jjdelcerro.noema.ui.lanterna;

import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import io.github.jjdelcerro.noema.lib.AgentConsole;

import java.io.IOException;

public class AgentLanternaConsoleImpl implements AgentConsole {

    private final MainLanternaWindow window;
    private final MultiWindowTextGUI gui;

    public AgentLanternaConsoleImpl(MainLanternaWindow window, MultiWindowTextGUI gui) {
        this.window = window;
        this.gui = gui;
    }

    @Override
    public boolean confirm(String message) {
        // En Lanterna puedes mostrar un diálogo modal MessageDialog / MessageDialogBuilder
        return com.googlecode.lanterna.gui2.dialogs.MessageDialog.query(
                gui,
                "Confirmación de Acción",
                message,
                com.googlecode.lanterna.gui2.dialogs.MessageDialogButton.Yes,
                com.googlecode.lanterna.gui2.dialogs.MessageDialogButton.No
        ) == com.googlecode.lanterna.gui2.dialogs.MessageDialogButton.Yes;
    }

    @Override
    public void printSystemLog(String message) {
        window.appendSystemLog(message);
        refreshUi();
    }

    @Override
    public void printSystemLog(String message, Format format) {
        printSystemLog(message);
    }

    @Override
    public void printSystemError(String message) {
        window.appendSystemLog("[ERR] " + message);
        refreshUi();
    }

    @Override
    public void printUserMessage(String message) {
        window.appendUserMessage(message);
        refreshUi();
    }

    @Override
    public void printModelResponse(String message) {
        window.appendModelResponse(message);
        refreshUi();
    }

    @Override
    public void printModelReasoning(String message) {
        window.appendSystemLog("[RAZONAMIENTO] " + message);
        refreshUi();
    }

    private void refreshUi() {
        try {
            gui.updateScreen();
        } catch (IOException ignored) {}
    }
}
```

---

#### Paso 5: Implementar `AgentUIManager` y `MainLanterna`

Creas el gestor de UI para Lanterna:

```java
package io.github.jjdelcerro.noema.ui.lanterna;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentConsole;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.AgentUIManager;
import io.github.jjdelcerro.noema.ui.AgentUISettings;

public class AgentLanternaManagerImpl implements AgentUIManager {

    private final AgentConsole console;

    public AgentLanternaManagerImpl(AgentConsole console) {
        this.console = console;
    }

    @Override
    public AgentConsole createConsole() {
        return this.console;
    }

    @Override
    public AgentUISettings createSettings(Agent agent) {
        return new AgentLanternaSettingsImpl(agent);
    }

    @Override
    public AgentUISettings createSettings(AgentSettings settings) {
        return new AgentLanternaSettingsImpl(settings);
    }
}
```

Y la clase principal `MainLanterna.java` o agregando un flag `-tui` en `Main.java`:

```java
package io.github.jjdelcerro.noema.main;

import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentLocator;
import io.github.jjdelcerro.noema.lib.AgentManager;
import io.github.jjdelcerro.noema.lib.settings.AgentSettings;
import io.github.jjdelcerro.noema.ui.AgentUILocator;
import io.github.jjdelcerro.noema.ui.lanterna.AgentLanternaConsoleImpl;
import io.github.jjdelcerro.noema.ui.lanterna.AgentLanternaManagerImpl;
import io.github.jjdelcerro.noema.ui.lanterna.MainLanternaWindow;

public class MainLanterna {

    public static void main(String[] args) {
        try {
            // 1. Inicializar pantalla de Lanterna
            Terminal terminal = new DefaultTerminalFactory().createTerminal();
            Screen screen = new TerminalScreen(terminal);
            screen.startScreen();

            MultiWindowTextGUI gui = new MultiWindowTextGUI(
                    screen, 
                    new DefaultWindowManager(), 
                    new EmptySpace()
            );

            // 2. Crear Ventana y Consola Lanterna
            MainLanternaWindow chatWindow = new MainLanternaWindow();
            AgentLanternaConsoleImpl console = new AgentLanternaConsoleImpl(chatWindow, gui);

            // 3. Registrar el UIManager
            AgentUILocator.registerAgentUIManager(new AgentLanternaManagerImpl(console));

            // 4. Cargar configuración e inicializar agente
            AgentManager manager = AgentLocator.getAgentManager();
            AgentSettings settings = manager.createSettings(null);
            settings.load();
            settings.setupSettings();

            Agent agent = BootUtils.init(settings);
            agent.start();

            // 5. Iniciar la interfaz GUI de Lanterna (Bloqueante)
            gui.addWindowAndWait(chatWindow);

            // 6. Al cerrar la ventana, apagar el agente limpiamente
            agent.stop();
            screen.stopScreen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

### 4. Ventajas adicionales que te aporta Lanterna en Noema

1. **Cuadros de diálogo modales listos para usar:** Lanterna incluye diálogos preconstruidos como `MessageDialog`, `TextInputDialog`, `ActionListDialog` y `FileDialog`. Te servirán mucho para resolver la interacción cuando las herramientas pidan confirmación (`AgentConsole.confirm(String)`).
2. **Formato Markdown simple en consola:** Aunque una consola no renderiza HTML, puedes hacer un pequeño parser de coloreado ANSI básico (ej. resaltar en negrita `**texto**` o colorear los bloques de código ` ``` `) dentro del `appendModelResponse`.
3. **Gestión de Configuración (`AgentUISettings`):** Lanterna permite construir diálogos de configuración tipo árbol + formulario usando sus widgets de `Tree` o listas compuestas, adaptando el `settingsui.json` que ya usas en Swing y Web.

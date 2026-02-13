package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.fit.cssbox.swingbox.BrowserPane;

import javax.swing.*;
import java.awt.*;

public class AgentSwingConsoleControllerUsingSingleHTML_1 implements AgentConsole {

    private final BrowserPane browserPane;
    private final StringBuilder conversationHistory;
    
    // Parsers de Markdown (se mantienen igual)
    private final Parser mdParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    // --- CSS: Aquí es donde ahora defines el "Look & Feel" del chat ---
    private static final String CSS_STYLES = """
        <style>
            body { 
                font-family: "Segoe UI", Tahoma, sans-serif; 
                background-color: #2b2b2b; 
                color: #e0e0e0; 
                margin: 0; 
                padding: 15px; 
            }
            .message-container {
                clear: both;
                margin-bottom: 12px;
                width: 100%;
                overflow: hidden; /* Para contener los floats */
            }
            .bubble {
                padding: 10px 15px;
                border-radius: 12px;
                max-width: 85%;
                word-wrap: break-word;
                font-size: 14px;
                line-height: 1.5;
            }
            /* --- ESTILO USUARIO (Derecha, Azul) --- */
            .user {
                float: right;
                background-color: #0d47a1; /* Azul oscuro */
                color: #ffffff;
                border-bottom-right-radius: 2px;
            }
            /* --- ESTILO MODELO (Izquierda, Gris oscuro) --- */
            .model {
                float: left;
                background-color: #3c3f41;
                border: 1px solid #555;
                border-bottom-left-radius: 2px;
            }
            /* --- ESTILOS SISTEMA Y ERROR --- */
            .system {
                text-align: center;
                color: #888;
                font-size: 12px;
                font-style: italic;
                margin: 10px 0;
                width: 100%;
            }
            .error {
                text-align: center;
                background-color: #5a1a1a;
                color: #ff9999;
                border: 1px solid #ff4444;
                padding: 8px;
                border-radius: 6px;
                margin: 10px auto;
                max-width: 90%;
            }
            /* --- ELEMENTOS HTML INTERNOS --- */
            pre {
                background-color: #1e1e1e;
                padding: 8px;
                border-radius: 4px;
                overflow-x: auto;
                border: 1px solid #444;
                font-family: "Consolas", monospace;
            }
            code {
                font-family: "Consolas", monospace;
                background-color: #1e1e1e;
                padding: 2px 4px;
                border-radius: 3px;
                color: #ffad66;
            }
            a { color: #64b5f6; text-decoration: none; }
            strong { color: #fff; }
            h1, h2, h3 { margin-top: 10px; margin-bottom: 5px; color: #fff; }
        </style>
    """;

    private static final String HTML_TEMPLATE = "<html><head>%s</head><body>%s</body></html>";
    private final JPanel chatContainer;

    public AgentSwingConsoleControllerUsingSingleHTML_1(JPanel chatContainer) {
        this.chatContainer = chatContainer;
        this.browserPane = new BrowserPane();
        this.chatContainer.setLayout(new BorderLayout());
        this.chatContainer.add(browserPane,BorderLayout.CENTER);
        
        browserPane.setEditable(false); // Solo lectura
        
        this.conversationHistory = new StringBuilder();
        
        // Inicializar vacío
        refreshBrowser();
    }

    private synchronized void addMessage(String cssClass, String htmlContent) {
        // Construimos el bloque HTML del mensaje
        String divWrapper;
        
        if (cssClass.equals("system") || cssClass.equals("error")) {
            // Mensajes de ancho completo (centrados)
            divWrapper = String.format("<div class='message-container'><div class='%s'>%s</div></div>", cssClass, htmlContent);
        } else {
            // Burbujas flotantes (User/Model)
            // Añadimos una pequeña etiqueta (User/Model) dentro si quieres, o solo el texto.
            divWrapper = String.format("<div class='message-container'><div class='bubble %s'>%s</div></div>", cssClass, htmlContent);
        }

        conversationHistory.append(divWrapper);
        refreshBrowser();
    }

    private void refreshBrowser() {
        SwingUtilities.invokeLater(() -> {
            // 1. Componer el HTML completo
            String fullHtml = String.format(HTML_TEMPLATE, CSS_STYLES, conversationHistory.toString());
            
            // 2. Inyectar en el BrowserPane
            // NOTA: setText en CSSBox es sincrono para el parseo, pero el renderizado puede ser complejo.
            browserPane.setText(fullHtml);

            // 3. Auto-scroll al final
            // Al recargar el HTML, CSSBox suele resetear la vista. Forzamos bajar.
            SwingUtilities.invokeLater(this::scrollToBottom);
        });
    }

    private void scrollToBottom() {
        Container parent = browserPane.getParent();
        if (parent instanceof JViewport viewport) {
            // Opción A: Mover el Viewport
            // Point p = new Point(0, browserPane.getHeight());
            // viewport.setViewPosition(p);
            
            // Opción B (Más robusta en JScrollPane): Mover la barra vertical
            if (viewport.getParent() instanceof JScrollPane scrollPane) {
                JScrollBar vertical = scrollPane.getVerticalScrollBar();
                vertical.setValue(vertical.getMaximum());
            }
        }
    }

    // --- Implementación de Interface AgentConsole ---

    @Override
    public void printSystemLog(String m) {
        // Escapamos HTML básico por seguridad si es texto plano
        addMessage("system", escapeHtml(m));
    }

    @Override
    public void printSystemError(String m) {
        addMessage("error", "<b>ERROR:</b> " + escapeHtml(m));
    }

    @Override
    public void printUserMessage(String m) {
        // El usuario suele enviar texto plano, pero convertimos saltos de línea a <br>
        String formatted = escapeHtml(m).replace("\n", "<br>");
        addMessage("user", formatted);
    }

    @Override
    public void printModelResponse(String m) {
        // El modelo envía Markdown -> Convertimos a HTML
        String renderedHtml = htmlRenderer.render(mdParser.parse(m));
        addMessage("model", renderedHtml);
    }

    @Override
    public boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(browserPane),
                message,
                "Confirmación",
                JOptionPane.YES_NO_OPTION
        ) == JOptionPane.YES_OPTION;
    }
    
    public Window getRoot() {
        return SwingUtilities.getWindowAncestor(browserPane);
    }

    // Utilidad simple para escapar HTML en logs del sistema/usuario
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}

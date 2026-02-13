package io.github.jjdelcerro.chatagent.ui.swing;

import io.github.jjdelcerro.chatagent.lib.AgentConsole;
import java.awt.BorderLayout;
import java.awt.Window;
import org.loboevolution.gui.HtmlPanel;
import org.loboevolution.gui.LocalHtmlRendererConfig;
import org.loboevolution.gui.LocalHtmlRendererContext;
import org.loboevolution.html.dom.domimpl.HTMLDocumentImpl;
import org.loboevolution.html.node.Element;
import org.loboevolution.html.parser.DocumentBuilderImpl;
import org.loboevolution.html.parser.InputSourceImpl;
import org.loboevolution.http.UserAgentContext;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.xml.sax.InputSource;

import java.io.StringReader;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.loboevolution.html.dom.domimpl.HTMLElementImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentSwingConsoleControllerUsingLobo implements AgentConsole {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentSwingConsoleControllerUsingLobo.class);

  private final HtmlPanel htmlPanel;
  private HTMLDocumentImpl document;
  private Element bodyElement;

  private final Parser mdParser = Parser.builder().build();
  private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

  // CSS Moderno aprovechando el soporte de Lobo para Flexbox
  private static final String BASE_STYLE = """
        <style>
            body { 
                font-family: 'Segoe UI', Arial, sans-serif; 
                background-color: #2b2b2b; 
                color: #e0e0e0; 
                margin: 0; padding: 20px;
                display: flex; flex-direction: column;
            }
            .msg-row { display: flex; width: 100%; margin-bottom: 15px; }
            .msg-row.user { justify-content: flex-end; }
            .msg-row.model { justify-content: flex-start; }
            
            .bubble { 
                padding: 12px 18px; 
                border-radius: 15px; 
                max-width: 80%; 
                line-height: 1.5;
                box-shadow: 0 2px 5px rgba(0,0,0,0.2);
            }
            .user .bubble { 
                background-color: #0056b3; color: white; 
                border-bottom-right-radius: 2px; 
            }
            .model .bubble { 
                background-color: #3e3e42; color: #e0e0e0; 
                border: 1px solid #454545;
                border-bottom-left-radius: 2px; 
            }
            .system { 
                align-self: center; font-size: 0.85em; color: #888; 
                font-style: italic; margin: 10px 0; 
            }
            .error { 
                align-self: center; background: #4a1a1a; color: #ff9999;
                padding: 8px 15px; border-radius: 8px; border: 1px solid #ff4444;
            }
            pre { background: #1e1e1e; padding: 10px; border-radius: 5px; overflow-x: auto; }
            code { color: #ce9178; font-family: 'Consolas', monospace; }
        </style>
        """;

  public AgentSwingConsoleControllerUsingLobo(JPanel chatContainer) {
    // 1. Inicializar componentes Lobo
    this.htmlPanel = new HtmlPanel();

    // 2. Configurar el contenedor Swing
    chatContainer.setLayout(new BorderLayout());
    chatContainer.add(htmlPanel, BorderLayout.CENTER);

    // 3. Inicializar el documento base
    initEmptyDocument();
  }

  private void initEmptyDocument() {
    try {
      LocalHtmlRendererConfig config = new LocalHtmlRendererConfig();
      UserAgentContext ucontext = new UserAgentContext(config);
      LocalHtmlRendererContext rcontext = new LocalHtmlRendererContext(htmlPanel, ucontext);

      String initialHtml = "<html><head>" + BASE_STYLE + "</head><body></body></html>";
      InputSource is = new InputSourceImpl(new StringReader(initialHtml), "about:blank");

      DocumentBuilderImpl builder = new DocumentBuilderImpl(ucontext, rcontext, config);
      this.document = (HTMLDocumentImpl) builder.parse(is);
      this.htmlPanel.setDocument(this.document, rcontext);

      // Referencia al body para inyectar mensajes
      this.bodyElement = (Element) this.document.getBody();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private synchronized void appendHtml(String htmlSnippet) {
    SwingUtilities.invokeLater(() -> {
      try {
        // Inyectamos el fragmento al final del body
        // Lobo soporta insertAdjacentHTML en su implementación de HTMLElementImpl
        ((HTMLElementImpl) bodyElement).insertAdjacentHTML("beforeend", htmlSnippet);

        // Forzamos el refresco del layout y scroll
        htmlPanel.validate();
      } catch (Exception e) {
        LOGGER.warn("Error inyectando HTML en Lobo", e);
      }
    });
  }

  private void scrollToBottom() {
    // El método scroll(x, y) de HtmlPanel (línea 214 de su fuente) 
    // acepta valores double. Al pasarle Double.MAX_VALUE, el motor 
    // interno lo limita automáticamente a la altura máxima del contenido.
    htmlPanel.scroll(0, Double.MAX_VALUE);
  }

  // --- Implementación de AgentConsole ---
  @Override
  public void printUserMessage(String m) {
    String html = "<div class='msg-row user'><div class='bubble'>"
            + escapeHtml(m).replace("\n", "<br>")
            + "</div></div>";
    appendHtml(html);
  }

  @Override
  public void printModelResponse(String m) {
    String renderedMd = htmlRenderer.render(mdParser.parse(m));
    String html = "<div class='msg-row model'><div class='bubble'>" + renderedMd + "</div></div>";
    appendHtml(html);
  }

  @Override
  public void printSystemLog(String m) {
    String html = "<div class='system'>" + escapeHtml(m) + "</div>";
    appendHtml(html);
  }

  @Override
  public void printSystemError(String m) {
    String html = "<div class='error'><strong>Error:</strong> " + escapeHtml(m) + "</div>";
    appendHtml(html);
  }

  @Override
  public boolean confirm(String message) {
    return JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(htmlPanel),
            message, "Confirmación", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
  }

  public Window getRoot() {
    return SwingUtilities.getWindowAncestor(htmlPanel);
  }

  private String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
  }
}

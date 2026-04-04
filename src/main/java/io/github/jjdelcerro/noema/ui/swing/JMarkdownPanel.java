package io.github.jjdelcerro.noema.ui.swing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Insets;
import java.awt.Toolkit;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.ext.gfm.tables.TablesExtension;

import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.Timer;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.alerts.AlertsExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.image.attributes.ImageAttributesExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;

/**
 * Componente especializado para visualizar Markdown renderizado en HTML con
 * capacidad de copiar el contenido original al portapapeles.
 */
public class JMarkdownPanel extends JTextPane {

  private String rawMarkdown = "";
  private final JButton copyButton;
  private final JPanel buttonContainer;

  // Parsers estáticos para eficiencia
  private static Parser MD_PARSER = null;
  private static HtmlRenderer HTML_RENDERER = null;

  public JMarkdownPanel() {
    super();
    this.setEditable(false);
    this.setContentType("text/html");
    this.setOpaque(false);
    this.setMargin(new Insets(0, 0, 0, 0));

    // 1. Configurar el motor HTML y estilos (Extraído de tu controlador)
    setupHtmlEngine();

    // 2. Crear el botón de copia (Técnica ClearButtonUtils)
    this.copyButton = createCopyButton();
    this.buttonContainer = new JPanel(new BorderLayout());
    this.buttonContainer.setOpaque(false);
    this.buttonContainer.add(copyButton, BorderLayout.NORTH);

    // 3. Layout y posicionamiento
    this.setLayout(new BorderLayout());
    this.add(buttonContainer, BorderLayout.EAST);

    // 4. Gestión de visibilidad (Hover)
    setupHoverLogic();
  }

  private HtmlRenderer getHtmlRenderer() {
    if (HTML_RENDERER == null) {
      List<Extension> extensions = List.of(
              TablesExtension.create(),
              AutolinkExtension.create(),
              StrikethroughExtension.create(),
              AlertsExtension.create(),
              FootnotesExtension.create(),
              HeadingAnchorExtension.create(),
              InsExtension.create(),
              ImageAttributesExtension.create(),
              TaskListItemsExtension.create()
      );
      HTML_RENDERER = HtmlRenderer.builder()
              .extensions(extensions)
              .build();
    }
    return HTML_RENDERER;
  }

  private Parser getMarkdownParser() {
    if (MD_PARSER == null) {
      List<Extension> extensions = List.of(
              TablesExtension.create(),
              AutolinkExtension.create(),
              StrikethroughExtension.create(),
              AlertsExtension.create(),
              FootnotesExtension.create(),
              HeadingAnchorExtension.create(),
              InsExtension.create(),
              ImageAttributesExtension.create(),
              TaskListItemsExtension.create()
      );
      MD_PARSER = Parser.builder()
              .extensions(extensions)
              .build();
    }
    return MD_PARSER;
  }

  private void setupHtmlEngine() {
    HTMLEditorKit kit = new HTMLEditorKit();
    StyleSheet ss = kit.getStyleSheet();

    // Estilos base coherentes con tu tema oscuro
    ss.addRule("body { font-family: sans-serif; font-size: 11pt; color: #e0e0e0; margin: 0; }");
    ss.addRule("pre { background-color: #1e1e1e; color: #dcdcdc; padding: 8px; border-radius: 4px; white-space: pre-wrap; word-wrap: break-word; }");
    ss.addRule("code { font-family: 'Monospaced'; color: #ffad66; background-color: #1e1e1e; }");
    ss.addRule("small { font-size: 0.85em; color: #999999; }");

    this.setEditorKit(kit);
  }

  private JButton createCopyButton() {
    JButton btn = new JButton();
    try {
      // Usamos FlatSVGIcon si está disponible, si no, texto
      btn.setIcon(new FlatSVGIcon("io/github/jjdelcerro/noema/ui/swing/copy.svg", 8, 8));
    } catch (Exception e) {
      btn.setText("C");
    }

    btn.setFocusable(false);
    btn.setBorderPainted(false);
    btn.setContentAreaFilled(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.setToolTipText("Copiar Markdown");
    btn.setVisible(false); // Oculto por defecto

    btn.addActionListener(e -> copyToClipboard());

    return btn;
  }

  private void setupHoverLogic() {
    MouseAdapter hoverHandler = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        if (!rawMarkdown.isEmpty()) {
          copyButton.setVisible(true);
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        copyButton.setVisible(false);
      }
    };
    this.addMouseListener(hoverHandler);
    // También al botón para que no desaparezca al interactuar con él
    copyButton.addMouseListener(hoverHandler);
  }

  /**
   * Asigna el texto en Markdown, lo convierte a HTML y lo almacena.
   */
  public void setMarkdownText(String header, String mdBody) {
    this.rawMarkdown = mdBody;

    String htmlBody = getHtmlRenderer().render(getMarkdownParser().parse(mdBody));
    String fullHtml;
    if (StringUtils.isBlank(header)) {
      fullHtml = String.format(
              "<html><body>%s</body></html>",
              htmlBody
      );
    } else {
      fullHtml = String.format(
              "<html><body><small>%s</small><br>%s</body></html>",
              header,
              htmlBody
      );
    }

    this.setText(fullHtml);

    // Forzar recalculo de altura para evitar cortes
    this.setPreferredSize(null);
    this.validate();
  }

  public String getMarkdownText() {
    return this.rawMarkdown;
  }

  private void copyToClipboard() {
    if (rawMarkdown == null || rawMarkdown.isEmpty()) {
      return;
    }

    StringSelection selection = new StringSelection(rawMarkdown);
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    clipboard.setContents(selection, selection);

    // Opcional: Feedback visual rápido (cambiar icono temporalmente)
    Timer timer = new Timer(1000, null);
    // Aquí podrías cambiar el icono a un 'check' y restaurarlo al finalizar el timer
  }

}

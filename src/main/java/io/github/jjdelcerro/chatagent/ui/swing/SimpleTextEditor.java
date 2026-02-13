package io.github.jjdelcerro.chatagent.ui.swing;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;
import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.ReplaceDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;

public class SimpleTextEditor extends JPanel implements SearchListener {

  private final RSyntaxTextArea textArea;
  private File currentFile; // El archivo actualmente editado
  private FindDialog findDialog;
  private ReplaceDialog replaceDialog;

  public SimpleTextEditor() {
    setLayout(new BorderLayout());

    // 1. Configurar el Área de Texto
    textArea = new RSyntaxTextArea(25, 80);
    textArea.setCodeFoldingEnabled(true);
    textArea.setAntiAliasingEnabled(true);
    textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE); // Por defecto texto plano

    // 2. ScrollPane específico de RSTA
    RTextScrollPane sp = new RTextScrollPane(textArea);
    add(sp, BorderLayout.CENTER);
  }

  /**
   * Carga un archivo en el editor, actualizando el contenido y la sintaxis.
   *
   * @param file El archivo a leer.
   */
  public void load(File file) {
    if (file == null || !file.exists()) {
      JOptionPane.showMessageDialog(this, "El archivo no existe o es nulo.");
      return;
    }

    try {
      String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
      textArea.setText(content);
      textArea.setCaretPosition(0);

      // Actualizamos estado y sintaxis
      this.currentFile = file;
      textArea.setSyntaxEditingStyle(detectSyntax(file));

      // Intentar actualizar el título de la ventana contenedora si existe
      updateWindowTitle();

    } catch (IOException e) {
      JOptionPane.showMessageDialog(this, "Error leyendo fichero: " + e.getMessage());
    }
  }

  /**
   * Muestra el editor en una ventana independiente (JDialog).
   */
  public void showWindow(String title) {
    
    // 1. Averiguamos si debemos ser modales
    JDialog modalParent = SwingUtils.getTopModalDialog();
    boolean shouldBeModal = (modalParent != null);

    // 2. Creamos nuestro diálogo con la modalidad correcta
    // El primer parámetro es el "dueño" de la ventana, que es importante para el foco.
    // Usamos el diálogo modal encontrado como dueño si existe.
    JDialog dialog = new JDialog(modalParent, title, shouldBeModal);
    
    dialog.setContentPane(this);
    dialog.setLocationRelativeTo(modalParent); // Centrar respecto al modal padre
    
    dialog.setContentPane(this);
    dialog.setJMenuBar(createMenuBar(dialog));
    dialog.pack();
    dialog.setVisible(true);

    if (currentFile != null) {
      updateWindowTitle();
    }
    initSearchDialogs(dialog);
  }

  private void chooseAndLoadFile() {
    JFileChooser chooser = new JFileChooser();
    if (currentFile != null) {
      chooser.setCurrentDirectory(currentFile.getParentFile());
    } else {
      chooser.setCurrentDirectory(new File(".")); // Directorio actual del proyecto
    }

    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      load(chooser.getSelectedFile());
    }
  }

  private void saveFile() {
    if (currentFile == null) {
      saveFileAs(); // Si no hay fichero asociado, es un "Guardar como..."
      return;
    }
    try {
      Files.writeString(currentFile.toPath(), textArea.getText(), StandardCharsets.UTF_8);
      // Feedback visual sutil (opcional)
    } catch (IOException e) {
      JOptionPane.showMessageDialog(this, "Error guardando fichero: " + e.getMessage());
    }
  }

  private void saveFileAs() {
    JFileChooser chooser = new JFileChooser();
    if (currentFile != null) {
      chooser.setCurrentDirectory(currentFile.getParentFile());
      chooser.setSelectedFile(currentFile);
    } else {
      chooser.setCurrentDirectory(new File("."));
    }

    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      File target = chooser.getSelectedFile();
      this.currentFile = target;
      saveFile();
      textArea.setSyntaxEditingStyle(detectSyntax(target));
      updateWindowTitle();
    }
  }

  private void updateWindowTitle() {
    Window w = SwingUtilities.getWindowAncestor(this);
    if (w instanceof JDialog dialog) {
      dialog.setTitle("Editor - " + currentFile.getName());
    } else if (w instanceof Frame frame) {
      frame.setTitle("Editor - " + currentFile.getName());
    }
  }

  private JMenuBar createMenuBar(JDialog dialog) {
    JMenuBar mb = new JMenuBar();

    // --- MENU ARCHIVO ---
    JMenu menuFile = new JMenu("Archivo");

    JMenuItem itemOpen = new JMenuItem(new AbstractAction("Abrir...") {
      @Override
      public void actionPerformed(ActionEvent e) {
        chooseAndLoadFile();
      }
    });
    itemOpen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));

    JMenuItem itemSave = new JMenuItem(new AbstractAction("Guardar") {
      @Override
      public void actionPerformed(ActionEvent e) {
        saveFile();
      }
    });
    itemSave.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));

    JMenuItem itemSaveAs = new JMenuItem(new AbstractAction("Guardar como...") {
      @Override
      public void actionPerformed(ActionEvent e) {
        saveFileAs();
      }
    });

    JMenuItem itemClose = new JMenuItem(new AbstractAction("Cerrar") {
      @Override
      public void actionPerformed(ActionEvent e) {
        dialog.dispose();
      }
    });

    menuFile.add(itemOpen);
    menuFile.add(itemSave);
    menuFile.add(itemSaveAs);
    menuFile.addSeparator();
    menuFile.add(itemClose);
    mb.add(menuFile);

    // --- MENU EDICIÓN ---
    JMenu menuEdit = new JMenu("Edición");

    JMenuItem itemFind = new JMenuItem(new AbstractAction("Buscar...") {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (findDialog != null) {
          findDialog.setVisible(true);
        }
      }
    });
    itemFind.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));

    JMenuItem itemReplace = new JMenuItem(new AbstractAction("Reemplazar...") {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (replaceDialog != null) {
          replaceDialog.setVisible(true);
        }
      }
    });
    itemReplace.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK));

    menuEdit.add(itemFind);
    menuEdit.add(itemReplace);
    mb.add(menuEdit);

    return mb;
  }

  private void initSearchDialogs(JDialog parent) {
    findDialog = new FindDialog(parent, this);
    replaceDialog = new ReplaceDialog(parent, this);
    findDialog.setLocationRelativeTo(parent);
    replaceDialog.setLocationRelativeTo(parent);
  }

  // ===========================================================================
  // IMPLEMENTACIÓN DE SearchListener
  // ===========================================================================
  @Override
  public String getSelectedText() {
    return textArea.getSelectedText();
  }

  @Override
  public void searchEvent(SearchEvent e) {
    SearchContext context = e.getSearchContext();
    SearchResult result;

    switch (e.getType()) {
      case MARK_ALL:
        result = SearchEngine.markAll(textArea, context);
        break;
      case FIND:
        result = SearchEngine.find(textArea, context);
        if (!result.wasFound() || result.isWrapped()) {
          // Beep o lógica opcional
        }
        break;
      case REPLACE:
        result = SearchEngine.replace(textArea, context);
        break;
      case REPLACE_ALL:
        result = SearchEngine.replaceAll(textArea, context);
        JOptionPane.showMessageDialog(this, result.getCount() + " ocurrencias reemplazadas.");
        break;
    }
  }

  private String detectSyntax(File f) {
    if (f == null) {
      return SyntaxConstants.SYNTAX_STYLE_NONE;
    }
    String name = f.getName().toLowerCase();
    if (name.endsWith(".json")) {
      return SyntaxConstants.SYNTAX_STYLE_JSON;
    }
    if (name.endsWith(".properties")) {
      return SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
    }
    if (name.endsWith(".xml")) {
      return SyntaxConstants.SYNTAX_STYLE_XML;
    }
    if (name.endsWith(".java")) {
      return SyntaxConstants.SYNTAX_STYLE_JAVA;
    }
    if (name.endsWith(".md")) {
      return SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
    }
    if (name.endsWith(".py")) {
      return SyntaxConstants.SYNTAX_STYLE_PYTHON;
    }
    if (name.endsWith(".sh")) {
      return SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
    }
    return SyntaxConstants.SYNTAX_STYLE_NONE;
  }
}

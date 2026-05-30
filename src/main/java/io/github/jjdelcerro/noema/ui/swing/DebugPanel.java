package io.github.jjdelcerro.noema.ui.swing;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class DebugPanel extends JPanel {

  private JTextArea areaSalida;
  private JTextField campoEntrada;
  private JFrame ventana;
  private final Map<String, Object> contexto;

  private class DebugUtils {
    public void println(Object mensaje) {
      areaSalida.append(">> " + (mensaje != null ? mensaje.toString() : "null") + "\n");
      areaSalida.setCaretPosition(areaSalida.getDocument().getLength());
    }

    public String dir(Object obj) {
      if (obj == null) {
        return "null";
      }

      // Obtiene todos los métodos públicos de la clase
      return Arrays.stream(obj.getClass().getMethods())
              .map(Method::getName)
              .distinct()
              .sorted()
              .collect(Collectors.joining(", "));
    }
  }

  public DebugPanel(Map<String, Object> contexto) {
    this.contexto = contexto;
    setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    // 1. Campo de entrada (Arriba)
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(5, 5, 5, 5);
    campoEntrada = new JTextField();
    add(campoEntrada, gbc);

    // 2. Botón Eval (Debajo del input, a la derecha)
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 0;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.NONE;
    JButton btnEval = new JButton("Eval");
    add(btnEval, gbc);

    // 3. Área de salida (En medio, ocupando espacio)
    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    areaSalida = new JTextArea(15, 40);
    areaSalida.setEditable(false);
    add(new JScrollPane(areaSalida), gbc);

    // 4. Botón Cerrar (Abajo del todo, a la derecha)
    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.NONE;
    JButton btnCerrar = new JButton("Cerrar");
    add(btnCerrar, gbc);

    // Listeners
    btnEval.addActionListener(e -> doEval());
    campoEntrada.addActionListener(e -> doEval());
    btnCerrar.addActionListener(e -> doClose());
  }

  private void doEval() {
    String comando = campoEntrada.getText();
    if (comando.trim().isEmpty()) {
      return;
    }

    areaSalida.append("> " + comando + "\n");

    try {
      // Configurar el contexto para registrar println
      ParserContext parserContext = new ParserContext();
//      parserContext.addImport("println", new NonStaticMethod(this, "println", Object.class));
//      parserContext.addImport("dir", new NonStaticMethod(this, "dir", Object.class));

      Map<String, Object> vars = new HashMap<>();
      vars.putAll(contexto);
      vars.put("debug", new DebugUtils());

      // Compilar y ejecutar
      Object resultado = MVEL.executeExpression(MVEL.compileExpression(comando, parserContext), vars);

      if (resultado != null) {
        areaSalida.append("  = " + resultado.toString() + "\n");
      }
    } catch (Exception e) {
      areaSalida.append("! Error: " + e.getMessage() + "\n");
    }

    campoEntrada.setText("");
    areaSalida.setCaretPosition(areaSalida.getDocument().getLength());
  }

  // Método expuesto a MVEL como función global
  public void println(Object mensaje) {
    areaSalida.append(">> " + (mensaje != null ? mensaje.toString() : "null") + "\n");
    areaSalida.setCaretPosition(areaSalida.getDocument().getLength());
  }

  // Método expuesto a MVEL como función global
  public String dir(Object obj) {
    if (obj == null) {
      return "null";
    }

    // Obtiene todos los métodos públicos de la clase
    return Arrays.stream(obj.getClass().getMethods())
            .map(Method::getName)
            .distinct()
            .sorted()
            .collect(Collectors.joining(", "));
  }

  private void doClose() {
    if (ventana != null) {
      ventana.dispose();
    }
  }

  public void showWindow(String titulo) {
    ventana = new JFrame(titulo);
    ventana.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    ventana.setContentPane(this);
    ventana.pack();
    ventana.setLocationRelativeTo(null);
    ventana.setVisible(true);
    ventana.setAlwaysOnTop(true);
  }
}

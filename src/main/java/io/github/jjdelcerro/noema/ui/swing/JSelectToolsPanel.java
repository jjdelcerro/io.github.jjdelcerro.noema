package io.github.jjdelcerro.noema.ui.swing;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.AgentTool;
import io.github.jjdelcerro.noema.lib.services.reasoning.ReasoningService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import org.apache.commons.lang3.StringUtils;

public class JSelectToolsPanel extends JPanel {

  // Record interno para manejar el modelo de la vista
  private record ToolUIItem(String label, String technicalName, String description, boolean checked) {

  }

  private final Agent agent;
  private final DefaultListModel<ToolUIItem> listModel;
  private final JList<ToolUIItem> list;

  public JSelectToolsPanel(Agent agent) {
    this.agent = agent;
    this.listModel = new DefaultListModel<>();
    this.list = new JList<>(listModel);

    initUI();
    loadToolsFromService();
  }

  private void initUI() {
    setLayout(new BorderLayout(10, 10));
    setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

    // --- 1. Cabecera ---
    JLabel lblHeader = new JLabel("<html><b>Capacidades Activas</b><br><small>Selecciona las herramientas que el agente podrá usar en esta conversación.</small></html>");
    add(lblHeader, BorderLayout.NORTH);

    // --- 2. Lista de Herramientas ---
    list.setCellRenderer(new CheckBoxListRenderer());
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    // Lógica de clic para marcar/desmarcar
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int index = list.locationToIndex(e.getPoint());
        if (index != -1) {
          ToolUIItem item = listModel.getElementAt(index);
          boolean newState = !item.checked();

          // Actualizamos modelo visual
          listModel.set(index, new ToolUIItem(item.label(), item.technicalName(), item.description(), newState));
          list.repaint();

          // Guardamos y aplicamos en caliente
          updateToolState(item.technicalName(), newState);
          applyChanges();
        }
      }
    });

    JScrollPane scrollPane = new JScrollPane(list);
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
    add(scrollPane, BorderLayout.CENTER);

    // --- 3. Botonera Inferior (Propia del panel) ---
    JPanel selectionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    JButton btnSelectAll = new JButton("Marcar todas");
    btnSelectAll.addActionListener(e -> setAllStates(true));

    JButton btnDeselectAll = new JButton("Desmarcar todas");
    btnDeselectAll.addActionListener(e -> setAllStates(false));

    selectionButtonsPanel.add(btnSelectAll);
    // Pequeño margen entre botones
    selectionButtonsPanel.add(Box.createHorizontalStrut(5));
    selectionButtonsPanel.add(btnDeselectAll);

    add(selectionButtonsPanel, BorderLayout.SOUTH);
  }

  /**
   * Muestra el panel envuelto en un JDialog modal con su propio botón de
   * Cerrar.
   */
  public void showWindow(Window parent) {
    JDialog dialog = new JDialog(parent, "Herramientas del Agente", Dialog.ModalityType.APPLICATION_MODAL);

    // Creamos un wrapper que contendrá este panel en el centro y el botón cerrar al sur
    JPanel wrapperPanel = new JPanel(new BorderLayout());
    wrapperPanel.add(this, BorderLayout.CENTER);

    // Panel para el botón cerrar
    JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    closePanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 10, 15)); // Márgenes inferiores

    JButton btnClose = new JButton("Cerrar");
    btnClose.addActionListener(e -> dialog.dispose());
    closePanel.add(btnClose);

    wrapperPanel.add(closePanel, BorderLayout.SOUTH);

    dialog.setContentPane(wrapperPanel);
    dialog.setSize(550, 450);
    dialog.setLocationRelativeTo(parent);
    dialog.getRootPane().setDefaultButton(btnClose); // Enter cierra el diálogo
    dialog.setVisible(true);
  }

  private void loadToolsFromService() {
    listModel.clear();
    ReasoningService reasoningService = (ReasoningService) agent.getService(ReasoningService.NAME);

    if (reasoningService != null) {
      List<AgentTool> tools = reasoningService.getAvailableTools();
      for (AgentTool tool : tools) {
        String technicalName = tool.getName();
        boolean isChecked = reasoningService.isToolActive(technicalName);

        // Capitalizamos el nombre y quitamos los guiones bajos
        String displayName = StringUtils.capitalize(technicalName.replace("_", " "));
        String description = tool.getSpecification().description();

        listModel.addElement(new ToolUIItem(displayName, technicalName, description, isChecked));
      }
    }
  }

  private void updateToolState(String technicalName, boolean state) {
    agent.getSettings().setChecked("reasoning/active_tools", technicalName, state);
  }

  private void applyChanges() {
    agent.getSettings().save();
    agent.getActions().call("REFRESH_REASONING_TOOLS", agent.getSettings());
  }

  private void setAllStates(boolean state) {
    boolean changed = false;
    for (int i = 0; i < listModel.getSize(); i++) {
      ToolUIItem item = listModel.getElementAt(i);
      if (item.checked() != state) {
        updateToolState(item.technicalName(), state);
        listModel.set(i, new ToolUIItem(item.label(), item.technicalName(), item.description(), state));
        changed = true;
      }
    }
    if (changed) {
      applyChanges();
      list.repaint();
    }
  }

  // --- Renderer visual ---
  private static class CheckBoxListRenderer extends JCheckBox implements ListCellRenderer<ToolUIItem> {

    public CheckBoxListRenderer() {
      setOpaque(true);
      setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends ToolUIItem> list, ToolUIItem value, int index, boolean isSelected, boolean cellHasFocus) {
      setSelected(value.checked());

      String desc = value.description();
      if (desc != null && desc.length() > 80) {
        desc = desc.substring(0, 77) + "...";
      }

      setText(String.format("<html><b>%s</b> <small><font color='gray'>[%s]</font></small><br><small>%s</small></html>",
              value.label(), value.technicalName(), desc != null ? desc : ""));

      setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
      setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
      setFocusable(false);
      return this;
    }
  }
}

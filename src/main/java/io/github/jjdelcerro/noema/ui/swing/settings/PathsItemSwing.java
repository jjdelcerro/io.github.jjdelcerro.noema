package io.github.jjdelcerro.noema.ui.swing.settings;

import com.google.gson.JsonObject;
import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.ui.common.AgentSettingsItemUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

public class PathsItemSwing extends AbstractAgentSettingsItemSwing {
  
  public static final String NAME = "paths";

  public PathsItemSwing(AgentSettingsItemUI parent, Agent agent, JsonObject json) {
    super(parent, agent, json);
  }

  @Override
  public JComponent getComponent() {
    JPanel p = new JPanel(new GridBagLayout());
    p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    GridBagConstraints gbc = new GridBagConstraints();

    // 1. Etiqueta
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.NORTHWEST;

    JLabel label = new JLabel("<html><b>" + getLabel() + "</b></html>");
    if (this.isRequired()) {
      // Verificación simple: si la lista está vacía podría considerarse "blank"
      List<Path> currentPaths = agent.getSettings().getPropertyAsPaths(getVariableName());
      if (currentPaths == null || currentPaths.isEmpty()) {
        label.setForeground(Color.RED.darker());
      }
    }
    p.add(label, gbc);

    // 2. Modelo y Lista
    DefaultListModel<String> listModel = new DefaultListModel<>();
    List<Path> currentPaths = agent.getSettings().getPropertyAsPaths(getVariableName());
    if (currentPaths != null) {
      for (Path path : currentPaths) {
        listModel.addElement(path.toString());
      }
    }

    JList<String> list = new JList<>(listModel);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JScrollPane scrollPane = new JScrollPane(list);
    scrollPane.setPreferredSize(new Dimension(400, 150));

    gbc.gridy = 1;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new Insets(10, 0, 0, 0);
    p.add(scrollPane, gbc);

    // 3. Botonera (Añadir / Eliminar)
    JPanel btnPanel = new JPanel(new BorderLayout(5, 5));
    JButton btnAdd = new JButton("Añadir Ruta...");
    JButton btnRemove = new JButton("Eliminar Seleccionado");

    btnAdd.addActionListener(e -> {
      JFileChooser chooser = new JFileChooser();
      chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
      if (chooser.showOpenDialog(p) == JFileChooser.APPROVE_OPTION) {
        File f = chooser.getSelectedFile();
        if (f != null) {
          listModel.addElement(f.getAbsolutePath());
          updateSettings(listModel);
        }
      }
    });

    btnRemove.addActionListener(e -> {
      int idx = list.getSelectedIndex();
      if (idx != -1) {
        listModel.remove(idx);
        updateSettings(listModel);
      }
    });

    btnPanel.add(btnAdd, BorderLayout.WEST);
    btnPanel.add(btnRemove, BorderLayout.EAST);

    gbc.gridy = 2;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    p.add(btnPanel, gbc);

    // 4. Espaciador final
    gbc.gridy = 3;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    p.add(new JPanel(), gbc);

    return p;
  }

  private void updateSettings(DefaultListModel<String> model) {
    List<String> paths = new ArrayList<>();
    for (int i = 0; i < model.size(); i++) {
      paths.add(model.get(i));
    }
    agent.getSettings().setProperty(getVariableName(), paths);
    this.save(); // Guarda y ejecuta acciones
  }
}

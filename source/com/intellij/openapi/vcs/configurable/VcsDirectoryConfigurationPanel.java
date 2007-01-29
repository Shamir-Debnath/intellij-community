/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.configurable;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.AbstractTableCellEditor;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.io.File;

/**
 * @author yole
 */
public class VcsDirectoryConfigurationPanel extends PanelWithButtons {
  private Project myProject;
  private ProjectLevelVcsManager myVcsManager;
  private TableView<VcsDirectoryMapping> myDirectoryMappingTable;
  private ComboboxWithBrowseButton myVcsComboBox = new ComboboxWithBrowseButton();

  private final ColumnInfo<VcsDirectoryMapping, String> DIRECTORY = new ColumnInfo<VcsDirectoryMapping, String>(VcsBundle.message("column.info.configure.vcses.directory")) {
    public String valueOf(final VcsDirectoryMapping mapping) {
      String directory = mapping.getDirectory();
      if (directory.length() == 0) {
        return "<Project Root>";
      }
      return FileUtil.getRelativePath(new File(myProject.getBaseDir().getPath()), new File(directory));
    }
  };


  private final ColumnInfo<VcsDirectoryMapping, String> VCS_SETTING = new ColumnInfo<VcsDirectoryMapping, String>(VcsBundle.message("comumn.name.configure.vcses.vcs")) {
    public String valueOf(final VcsDirectoryMapping object) {
      return object.getVcs();
    }

    public boolean isCellEditable(final VcsDirectoryMapping o) {
      return true;
    }

    public void setValue(final VcsDirectoryMapping o, final String aValue) {
      o.setVcs(aValue);
    }

    public TableCellRenderer getRenderer(final VcsDirectoryMapping p0) {
      return new ColoredTableCellRenderer() {
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          final String vcsName = p0.getVcs();
          String text = vcsName.length() == 0 ? VcsBundle.message("none.vcs.presentation") : myVcsManager.findVcsByName(vcsName).getDisplayName();
          append(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, table.getForeground()));
        }
      };
    }

    @Override
    public TableCellEditor getEditor(final VcsDirectoryMapping o) {
      return new AbstractTableCellEditor() {
        public Object getCellEditorValue() {
          VcsWrapper selectedVcs = (VcsWrapper) myVcsComboBox.getComboBox().getSelectedItem();
          return selectedVcs.getOriginal() == null ? "" : selectedVcs.getOriginal().getName();
        }

        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
          String vcsName = (String) value;
          myVcsComboBox.getComboBox().setSelectedItem(VcsWrapper.fromName(myProject, vcsName));
          return myVcsComboBox;
        }
      };
    }
  };
  private ListTableModel<VcsDirectoryMapping> myModel;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myRemoveButton;

  public VcsDirectoryConfigurationPanel(final Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);

    myDirectoryMappingTable = new TableView<VcsDirectoryMapping>();
    initializeModel();

    myVcsComboBox.getComboBox().setModel(buildVcsWrappersModel(myProject));
    myVcsComboBox.getComboBox().setRenderer(new EditorComboBoxRenderer(myVcsComboBox.getComboBox().getEditor()));
    myVcsComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final VcsWrapper vcsWrapper = ((VcsWrapper)myVcsComboBox.getComboBox().getSelectedItem());
        AbstractVcs abstractVcs = null;
        if (vcsWrapper != null){
          abstractVcs = vcsWrapper.getOriginal();
        }
        new VcsConfigurationsDialog(project, myVcsComboBox.getComboBox(), abstractVcs).show();
      }
    });

    myDirectoryMappingTable.setRowHeight(myVcsComboBox.getPreferredSize().height);
    myDirectoryMappingTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateButtons();
      }
    });
    initPanel();
    updateButtons();
  }

  private void initializeModel() {
    List<VcsDirectoryMapping> mappings = new ArrayList<VcsDirectoryMapping>();
    for(VcsDirectoryMapping mapping: ProjectLevelVcsManager.getInstance(myProject).getDirectoryMappings()) {
      mappings.add(new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs()));
    }
    myModel = new ListTableModel<VcsDirectoryMapping>(new ColumnInfo[]{DIRECTORY, VCS_SETTING}, mappings, 0);
    myDirectoryMappingTable.setModel(myModel);
  }

  private void updateButtons() {
    final boolean hasSelection = myDirectoryMappingTable.getSelectedObject() != null;
    myEditButton.setEnabled(hasSelection);
    myRemoveButton.setEnabled(hasSelection);
  }

  public static DefaultComboBoxModel buildVcsWrappersModel(final Project project) {
    final AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(project).getAllVcss();
    VcsWrapper[] vcsWrappers = new VcsWrapper[vcss.length+1];
    vcsWrappers [0] = new VcsWrapper(null);
    for(int i=0; i<vcss.length; i++) {
      vcsWrappers [i+1] = new VcsWrapper(vcss [i]);
    }
    return new DefaultComboBoxModel(vcsWrappers);
  }

  protected String getLabelText() {
    return null;
  }

  protected JButton[] createButtons() {
    myAddButton = new JButton(CommonBundle.message("button.add"));
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        addMapping();
      }
    });
    myEditButton = new JButton(CommonBundle.message("button.edit"));
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        editMapping();
      }
    });
    myRemoveButton = new JButton(CommonBundle.message("button.remove"));
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        removeMapping();
      }
    });
    return new JButton[] {myAddButton, myEditButton, myRemoveButton};
  }

  private void addMapping() {
    VcsMappingConfigurationDialog dlg = new VcsMappingConfigurationDialog(myProject, "Add VCS Directory Mapping");
    dlg.show();
    if (dlg.isOK()) {
      VcsDirectoryMapping mapping = new VcsDirectoryMapping();
      dlg.saveToMapping(mapping);
      List<VcsDirectoryMapping> items = new ArrayList<VcsDirectoryMapping>(myModel.getItems());
      items.add(mapping);
      myModel.setItems(items);
    }
  }

  private void editMapping() {
    VcsMappingConfigurationDialog dlg = new VcsMappingConfigurationDialog(myProject, "Edit VCS Directory Mapping");
    final VcsDirectoryMapping mapping = myDirectoryMappingTable.getSelectedObject();
    dlg.setMapping(mapping);
    dlg.show();
    if (dlg.isOK()) {
      dlg.saveToMapping(mapping);
      myModel.fireTableDataChanged();
    }
  }

  private void removeMapping() {
    ArrayList<VcsDirectoryMapping> mappings = new ArrayList<VcsDirectoryMapping>(myModel.getItems());
    int index = myDirectoryMappingTable.getSelectionModel().getMinSelectionIndex();
    Collection<VcsDirectoryMapping> selection = myDirectoryMappingTable.getSelection();
    mappings.removeAll(selection);
    myModel.setItems(mappings);
    if (mappings.size() > 0) {
      if (index >= mappings.size()) {
        index = mappings.size()-1;
      }
      myDirectoryMappingTable.getSelectionModel().setSelectionInterval(index, index);
    }
  }

  protected JComponent createMainComponent() {
    return new JScrollPane(myDirectoryMappingTable);
  }

  public void reset() {
    initializeModel();
  }

  public void apply() {
    myVcsManager.setDirectoryMappings(myModel.getItems());
  }

  public boolean isModified() {
    return !myModel.getItems().equals(myVcsManager.getDirectoryMappings());
  }
}
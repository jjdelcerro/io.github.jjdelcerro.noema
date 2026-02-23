package io.github.jjdelcerro.noema.ui.swing;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.LineBorder;


public class WelcomePanel2View extends JPanel
{
   JButton btnBrowse = new JButton();
   JTextArea txtDisclaimer = new JTextArea();
   JComboBox comboWorkspace = new JComboBox();
   JButton btnContinue = new JButton();
   JEditorPane txtConfigSummary = new JEditorPane();
   JButton btnConfigure = new JButton();

   /**
    * Default constructor
    */
   public WelcomePanel2View()
   {
      initializePanel();
   }

   /**
    * Adds fill components to empty cells in the first row and first column of the grid.
    * This ensures that the grid spacing will be the same as shown in the designer.
    * @param cols an array of column indices in the first row where fill components should be added.
    * @param rows an array of row indices in the first column where fill components should be added.
    */
   void addFillComponents( Container panel, int[] cols, int[] rows )
   {
      Dimension filler = new Dimension(10,10);

      boolean filled_cell_11 = false;
      CellConstraints cc = new CellConstraints();
      if ( cols.length > 0 && rows.length > 0 )
      {
         if ( cols[0] == 1 && rows[0] == 1 )
         {
            /** add a rigid area  */
            panel.add( Box.createRigidArea( filler ), cc.xy(1,1) );
            filled_cell_11 = true;
         }
      }

      for( int index = 0; index < cols.length; index++ )
      {
         if ( cols[index] == 1 && filled_cell_11 )
         {
            continue;
         }
         panel.add( Box.createRigidArea( filler ), cc.xy(cols[index],1) );
      }

      for( int index = 0; index < rows.length; index++ )
      {
         if ( rows[index] == 1 && filled_cell_11 )
         {
            continue;
         }
         panel.add( Box.createRigidArea( filler ), cc.xy(1,rows[index]) );
      }

   }

   /**
    * Helper method to load an image file from the CLASSPATH
    * @param imageName the package and name of the file to load relative to the CLASSPATH
    * @return an ImageIcon instance with the specified image file
    * @throws IllegalArgumentException if the image resource cannot be loaded.
    */
   public ImageIcon loadImage( String imageName )
   {
      try
      {
         ClassLoader classloader = getClass().getClassLoader();
         java.net.URL url = classloader.getResource( imageName );
         if ( url != null )
         {
            ImageIcon icon = new ImageIcon( url );
            return icon;
         }
      }
      catch( Exception e )
      {
         e.printStackTrace();
      }
      throw new IllegalArgumentException( "Unable to load image: " + imageName );
   }

   /**
    * Method for recalculating the component orientation for 
    * right-to-left Locales.
    * @param orientation the component orientation to be applied
    */
   public void applyComponentOrientation( ComponentOrientation orientation )
   {
      // Not yet implemented...
      // I18NUtils.applyComponentOrientation(this, orientation);
      super.applyComponentOrientation(orientation);
   }

   public JPanel createPanel()
   {
      JPanel jpanel1 = new JPanel();
      FormLayout formlayout1 = new FormLayout("FILL:8DLU:NONE,FILL:DEFAULT:GROW(1.0),FILL:DEFAULT:NONE,FILL:DEFAULT:NONE,FILL:8DLU:NONE","CENTER:4DLU:NONE,CENTER:DEFAULT:NONE,CENTER:4DLU:NONE,CENTER:DEFAULT:NONE,CENTER:4DLU:NONE,CENTER:DEFAULT:NONE,CENTER:4DLU:NONE,FILL:DEFAULT:GROW(0.5),CENTER:4DLU:NONE,CENTER:DEFAULT:NONE,CENTER:2DLU:NONE,CENTER:DEFAULT:NONE,CENTER:4DLU:NONE,FILL:DEFAULT:GROW(0.5),CENTER:4DLU:NONE,CENTER:DEFAULT:NONE,CENTER:4DLU:NONE");
      CellConstraints cc = new CellConstraints();
      jpanel1.setLayout(formlayout1);

      btnBrowse.setActionCommand("Seleccionar");
      btnBrowse.setName("btnBrowse");
      btnBrowse.setText("Seleccionar");
      jpanel1.add(btnBrowse,cc.xy(4,4));

      txtDisclaimer.setEditable(false);
      txtDisclaimer.setName("txtDisclaimer");
      txtDisclaimer.setOpaque(false);
      LineBorder lineborder1 = new LineBorder(new Color(128,128,128),1,false);
      txtDisclaimer.setBorder(lineborder1);
      JScrollPane jscrollpane1 = new JScrollPane();
      jscrollpane1.setViewportView(txtDisclaimer);
      jscrollpane1.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
      jscrollpane1.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      jpanel1.add(jscrollpane1,cc.xywh(2,14,3,1));

      comboWorkspace.setEditable(true);
      comboWorkspace.setName("comboWorkspace");
      comboWorkspace.setOpaque(false);
      comboWorkspace.setRequestFocusEnabled(false);
      jpanel1.add(comboWorkspace,cc.xy(2,4));

      btnContinue.setActionCommand("Continuar");
      btnContinue.setName("btnContinue");
      btnContinue.setText("Continuar");
      jpanel1.add(btnContinue,cc.xy(4,16));

      txtConfigSummary.setEditable(false);
      txtConfigSummary.setName("txtConfigSummary");
      txtConfigSummary.setOpaque(false);
      LineBorder lineborder2 = new LineBorder(new Color(128,128,128),1,true);
      txtConfigSummary.setBorder(lineborder2);
      JScrollPane jscrollpane2 = new JScrollPane();
      jscrollpane2.setViewportView(txtConfigSummary);
      jscrollpane2.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
      jscrollpane2.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      jpanel1.add(jscrollpane2,cc.xywh(2,8,3,1));

      JLabel jlabel1 = new JLabel();
      jlabel1.setText("Seleccione la carpeta de trabajo");
      jpanel1.add(jlabel1,cc.xy(2,2));

      JLabel jlabel2 = new JLabel();
      jlabel2.setText("Configuracion basica");
      jpanel1.add(jlabel2,cc.xy(2,6));

      JLabel jlabel3 = new JLabel();
      jlabel3.setText("Aviso legal y condiciones");
      jpanel1.add(jlabel3,cc.xy(2,12));

      btnConfigure.setActionCommand("Configurar");
      btnConfigure.setName("btnConfigure");
      btnConfigure.setText("Configurar");
      jpanel1.add(btnConfigure,cc.xy(4,10));

      addFillComponents(jpanel1,new int[]{ 1,2,3,4,5 },new int[]{ 1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17 });
      return jpanel1;
   }

   /**
    * Initializer
    */
   protected void initializePanel()
   {
      setLayout(new BorderLayout());
      add(createPanel(), BorderLayout.CENTER);
   }


}

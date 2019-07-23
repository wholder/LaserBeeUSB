import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.prefs.Preferences;

class LaserBeeUSB extends JFrame implements JSSCPort.RXEvent {
  private Preferences   prefs = Preferences.userRoot().node(this.getClass().getName());
  private Readout       reading, peak;
  private int           peakPower;
  private StringBuilder buf = new StringBuilder();

  class Readout extends JPanel {
    JTextField power = new JTextField("- -");

    Readout (String label) {
      super(new BorderLayout());
      setBackground(Color.white);
      Border outside = BorderFactory.createEmptyBorder(10, 10, 10, 10);
      TitledBorder inside = BorderFactory.createTitledBorder(label);
      inside.setTitleFont(new Font("Helvetica", Font.PLAIN, 18));
      Border border = BorderFactory.createCompoundBorder(outside, inside);
      setBorder(border);
      power.setEditable(false);
      power.setBackground(Color.white);
      power.setBorder(BorderFactory.createEmptyBorder(18, 8, 8, 8));
      power.setFont(new Font("Helvetica", Font.BOLD, 110));
      power.setHorizontalAlignment(SwingConstants.RIGHT);
      power.setPreferredSize(new Dimension(300, 120));
      power.addMouseListener(new MouseAdapter() {
        public void mousePressed (MouseEvent ev1) {
          if (ev1.isPopupTrigger()) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem menuItem = new JMenuItem("Copy Value");
            menu.add(menuItem);
            menu.show(ev1.getComponent(), ev1.getX(), ev1.getY());
            menuItem.addActionListener( ev2 -> {
              // Copy value to clipboard
              StringSelection stringSelection = new StringSelection(power.getText());
              Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
              clipboard.setContents(stringSelection, null);
            });
          }
        }
      });
      add(power, BorderLayout.CENTER);
    }

    void setValue (String value) {
      power.setText(value);
      repaint();
    }
  }

  private LaserBeeUSB () {
    super("LaserBee\u2122 USB Power Meter");
    setLayout(new BorderLayout());
    JPanel panel = new JPanel(new GridLayout(2, 1));
    panel.add(reading =  new Readout("Power Level (milliwatts)"));
    panel.add(peak =  new Readout("Peak Level (milliwatts)"));
    add(panel, BorderLayout.CENTER);
    setBackground(Color.white);
    JButton reset = new JButton("RESET PEAK READING");
    reset.addActionListener(ev -> peakPower = 0);
    add(reset,BorderLayout.SOUTH);
    // Add menu for Port selection
    JMenuBar menuBar = new JMenuBar();
    JSSCPort jPort = new JSSCPort(prefs);
    jPort.setRXHandler(this);
    menuBar.add(jPort.getPortMenu());
    setJMenuBar(menuBar);
    // Add window close handler
    addWindowListener(new WindowAdapter() {
      public void windowClosing (WindowEvent ev) {
        jPort.close();
        System.exit(0);
      }
    });
    // Track window move events and save in prefs
    addComponentListener(new ComponentAdapter() {
      public void componentMoved (ComponentEvent ev) {
        Rectangle bounds = ev.getComponent().getBounds();
        prefs.putInt("window.x", bounds.x);
        prefs.putInt("window.y", bounds.y);
      }
    });
    pack();
    setResizable(false);
    setLocation(prefs.getInt("window.x", 100), prefs.getInt("window.y", 100));
    setVisible(true);
  }

  // Implement JSSCPort.RXEvent
  public void rxChar (byte cc) {
    if (cc == '\n') {
      String value = buf.toString().trim();
      String[] parts = value.split(",");
      if (parts.length == 2 && parts[0].equals(parts[1])) {
        reading.setValue(parts[0]);
        reading.repaint();
        int power = Integer.parseInt(parts[0]);
        peakPower = Math.max(peakPower, power);
        peak.setValue(Integer.toString(peakPower));
      }
      buf.setLength(0);
    } else if (cc >= ' ') {
      buf.append((char) cc);
    }
  }

  public static void main (String[] args) {
    SwingUtilities.invokeLater(() -> {
      try {
        new LaserBeeUSB();
      } catch (Exception ex) {
        ex.printStackTrace(System.out);
      }
    });
  }
}
package jemu.ui;

import jemu.core.*;
import jemu.core.cpu.*;
import jemu.core.device.*;
import jemu.core.device.FileDescriptor;
import jemu.core.device.floppy.*;
import jemu.util.diss.*;
import java.awt.*;
import java.awt.event.*;
import java.applet.*;
import java.net.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * Title:        JEMU
 * Description:  The Java Emulation Platform
 * Copyright:    Copyright (c) 2002
 * Company:
 * @author
 * @version 1.0
 */

public class JEMU extends Applet implements KeyListener, MouseListener, ItemListener, 
  ActionListener, FocusListener, Runnable, DriveListener
{

  protected Computer computer = null;

  protected boolean isStandalone = false;
  protected Display display = new Display();
  protected Debugger debug = null;
  protected JComboBox cbGameChooser = new JComboBox();
  protected JButton bReset = new JButton("Reset");
  protected boolean started = false;
  protected boolean large = true;
  protected Thread focusThread = null;
  protected Color background;
  protected boolean gotGames = false;
  protected JFileChooser fileChooser;

  public String getParameter(String key, String def) {
    return isStandalone ? System.getProperty(key, def) :
      (getParameter(key) != null ? getParameter(key) : def);
  }

  public JEMU() {
    enableEvents(AWTEvent.KEY_EVENT_MASK);
  }

  public void init() {
    requestFocus();
  }

  public void start() {
    try {
      System.out.println("init()");
      removeAll();
      background = getBackground();
      setBackground(Color.black);
      setLayout(new BorderLayout());
      add(display,BorderLayout.CENTER);
      display.setDoubleBuffered(false);
      display.setBackground(Color.black);
      //display.setBorder(new LineBorder(Color.red,1));
      display.addKeyListener(this);
      display.addMouseListener(this);
      display.addFocusListener(this);
      boolean debug = Util.getBoolean(getParameter("DEBUG","false"));
      boolean pause = Util.getBoolean(getParameter("PAUSE","false"));
      large = Util.getBoolean(getParameter("LARGE","true"));
      System.out.println("DEBUG=" + debug + ", PAUSE=" + pause + ", LARGE=" + large);
      if (computer == null)
        setComputer(getParameter("COMPUTER",Computer.DEFAULT_COMPUTER),!(debug || pause));
      else if (!(debug || pause))
        computer.start();
      System.out.println("Computer Set");
      this.getInputContext().selectInputMethod(new Locale("en", "US"));
      boolean status = Util.getBoolean(getParameter("STATUS","false"));
      JPanel bottom = null;
      if (status) {
        bottom = new JPanel();
        bottom.setLayout(new FlowLayout());
      }
      boolean selector = Util.getBoolean(getParameter("SELECTOR","true"));
      if (selector) {
        if (bottom == null) {
          bottom = new JPanel();
          bottom.setLayout(new FlowLayout());
        }
        JLabel label = new JLabel("Computer:");
        label.setForeground(new Color(0,0,127));
        bottom.add(label);
        JComboBox box = new JComboBox();
        box.setFocusable(false);
        for (int i = 0; i < Computer.COMPUTERS.length; i++) {
          ComputerDescriptor desc = Computer.COMPUTERS[i];
          if (desc.shown) {
            box.addItem(desc);
            if (computer.getName().equalsIgnoreCase(desc.key))
              box.setSelectedIndex(box.getItemCount() - 1);
          }
        }
        bottom.add(box);
        box.addItemListener(this);
        label = new JLabel("Program:");
        label.setForeground(new Color(0,0,127));
        bottom.add(label);
        cbGameChooser.addItemListener(this);
        cbGameChooser.setFocusable(false);
        bottom.add(cbGameChooser);
        bReset.addActionListener(this);
        bReset.setFocusable(false);
        bottom.add(bReset);
      }
      if (bottom != null) {
        bottom.setBackground(background);
        add(bottom,BorderLayout.SOUTH);
      }
      display.requestFocus();
      if (debug)
        showDebugger();
      started = true;
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  public boolean isStarted() {
    return started;
  }

  public void waitStart() {
    try {
      while (!started)
        Thread.sleep(10);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void focusDisplay() {
    display.requestFocus();
    /*if (!display.isFocused())
      (focusThread = new Thread(this)).start();*/
  }

  public void run() {
    /*for (int i = 0; i < 500 && !display.isFocused(); i++)
      try {
        Thread.sleep(10);
        display.requestFocus();
      } catch(Exception e) {
        e.printStackTrace();
      }
    focusThread = null; */
  }

  public void startComputer() {
    computer.start();
  }

  public void stop() {
    System.out.println("stop()");
    computer.stop();
  }

  public void destroy() { }

  public String getAppletInfo() {
    return "Applet Information";
  }

  public String[][] getParameterInfo() {
    return null;
  }

  public static void main(String[] args) {
    JEMU applet = new JEMU();
    applet.isStandalone = true;
    JFrame frame = new JFrame() {

      protected void processWindowEvent(WindowEvent e) {
        super.processWindowEvent(e);
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
          System.exit(0);
        }
      }

      public synchronized void setTitle(String title) {
        super.setTitle(title);
        enableEvents(AWTEvent.WINDOW_EVENT_MASK);
      }

    };
    frame.setIconImage(Toolkit.getDefaultToolkit().getImage("pc128s.gif"));
    frame.setTitle("PC128S Olivetti Prodest");
    frame.getContentPane().add(applet, BorderLayout.CENTER);
    //frame.setBackground(Color.black);
    applet.init();
    applet.start();
    frame.pack();
    Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
    frame.setLocation((d.width - frame.getSize().width) / 2, (d.height - frame.getSize().height) / 2);
    frame.setVisible(true);
  }

  public void update(Graphics g) {
    paint(g);
  }

  public void keyTyped(KeyEvent e) { }

  public void keyPressed(KeyEvent e) {
    computer.processKeyEvent(e);
  }

  public void keyReleased(KeyEvent e) {
    computer.processKeyEvent(e);
  }

  public void mouseClicked(MouseEvent e) {
    if (isStandalone && e.getClickCount() == 2)
      if ((e.getModifiers() & MouseEvent.BUTTON1_MASK) != 0)
        showDebugger();
      else {
        System.out.println("Load File");       
        if (fileChooser == null) {
          fileChooser = new JFileChooser();
          fileChooser.setDialogTitle("Load Emulator File");
        }
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
          loadFile(Computer.TYPE_SNAPSHOT, fileChooser.getSelectedFile().getAbsolutePath(), false);
      }
  }

  public void mousePressed(MouseEvent e) {
    display.requestFocus();
  }

  public void mouseReleased(MouseEvent e) { }

  public void mouseEntered(MouseEvent e) { }

  public void mouseExited(MouseEvent e) { }

  public void showDebugger() {
    try {
      System.out.println("showDebugger");
      if (debug == null) {
        debug = (Debugger)Util.secureConstructor(Debugger.class,
          new Class[] { },new Object[] { });
        debug.setBounds(0,0,640,450);
        debug.setComputer(computer);
      }
      System.out.println("Showing Debugger");
      debug.setVisible(true);
      debug.toFront();
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  public Computer getComputer() {
    return computer;
  }

  public String loadFile(String name) {
    return loadFile(Computer.TYPE_UNKNOWN, name, true);
  }

  public String loadFile(int type, String name, boolean usePath) {
    String result = null;
    try {
      boolean running = computer.isRunning();
      computer.stop();
      try {
        computer.loadFile(type, (usePath ? computer.getFilePath() : "") + name);
        result = computer.getFileInfo(name);
      } finally {
        display.requestFocus();
        if (running)
          computer.start();
      }
    } catch(Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  public void resetComputer() {
    computer.reset();
    computer.start();
  }

  public void setComputer(String name) throws Exception {
    setComputer(name, true);
  }

  public void setComputer(String name, boolean start) throws Exception {
    if (computer == null || !name.equalsIgnoreCase(computer.getName())) {
      Computer newComputer = Computer.createComputer(this, name);
      if (computer != null) {
        Drive[] floppies = computer.getFloppyDrives();
        if (floppies != null)
          for (int i = 0; i < floppies.length; i++)
            if (floppies[i] != null)
              floppies[i].setActiveListener(null);
        computer.dispose();
        computer = null;
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        runtime.runFinalization();
        runtime.gc();
        System.out.println("Computer Disposed");
      }
      computer = newComputer;
      setFullSize(large);
      computer.initialise();
      Drive[] floppies = computer.getFloppyDrives();
      if (floppies != null)
        for (int i = 0; i < floppies.length; i++)
          if (floppies[i] != null)
            floppies[i].setActiveListener(this);
      cbGameChooser.removeAllItems();
      gotGames = false;
      try {
        Vector files = computer.getFiles();
        for (int i = 0; i < files.size(); i++)
          cbGameChooser.addItem(files.elementAt(i));
        cbGameChooser.setSelectedIndex(-1);
      } finally {
        gotGames = true;
      }
      if (debug != null)
        debug.setComputer(computer);
      if (start)
        computer.start();
    }
  }
  
  public void setFullSize(boolean value) {
    large = value;
    boolean running = computer.isRunning();
    computer.stop();
    computer.setLarge(large);
    display.setImageSize(computer.getDisplaySize(large), computer.getDisplayScale(large));
    computer.setDisplay(display);
    if (running) computer.start();
  }

  public void itemStateChanged(ItemEvent e) {
    if (e.getStateChange() == ItemEvent.SELECTED && gotGames) {
      Object item = ((JComboBox)e.getSource()).getSelectedItem();
      if (e.getSource() == cbGameChooser)
        loadFile(((FileDescriptor)item).filename);
      else
        try {
          setComputer(((ComputerDescriptor)item).key);
          findWindow(this).pack();
        } catch(Exception ex) {
          ex.printStackTrace();
        }
    }
  }
  
  public Window findWindow(Component comp) {
    while (comp != null)
      if (comp instanceof Window)
        return (Window)comp;
      else
        comp = comp.getParent();
    return null;
  }

  public Vector getFiles() {
    Vector files = computer == null ? new Vector() : computer.getFiles();
    Vector result = files.size() == 0 ? files : new Vector(files.size() * 2);
    for (int i = 0; i < files.size(); i++) {
      FileDescriptor file = (FileDescriptor)files.elementAt(i);
      result.addElement(file.description); // Description
      result.addElement(file.filename); // Name
    }
    return result;
  }

  public Vector getComputers() {
    int count = Computer.COMPUTERS.length;
    Vector result = new Vector(count);
    for (int i = 0; i < count; i++) {
      ComputerDescriptor desc = Computer.COMPUTERS[i];
      if (desc.shown) {
        result.addElement(desc.name);
        result.addElement(desc.key);
      }
    }
    return result;
  }

  public void focusLost(FocusEvent e) {
    computer.displayLostFocus();
  }

  public void focusGained(FocusEvent e) { }

  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == bReset)
      computer.reset();
  }
  
  public void driveActiveChanged(Drive drive, boolean active) {
    // Put Drive LED change code here
    // NOTE: Must OR the active state from all Drives for the value
    if (!active) {
      Drive[] floppies = computer.getFloppyDrives();
      if (floppies != null)
        for (int i = 0; i < floppies.length; i++)
          if (floppies[i] != null && floppies[i].isActive()) {
            active = true;
            break;
          }
    }
    display.setBorder(new LineBorder(active ? Color.red : Color.black,2));
  }
  
  public String getFloppyImageName(int drive, int head) {
    Drive[] floppies = computer.getFloppyDrives();
    String result = "Invalid Drive " + drive;
    if (floppies != null && floppies.length > drive + 1) {
      DiscImage image = floppies[drive].getDisc(head);
      result = image != null ? image.getName() : "No Disc in Drive " + drive;
    }
    return result;
  }
  
  public String getFloppyImageName(int drive) {
    return getFloppyImageName(drive,Drive.HEAD_0);
  }

}
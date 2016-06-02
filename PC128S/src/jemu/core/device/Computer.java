package jemu.core.device;

import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.zip.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import jemu.core.*;
import jemu.core.cpu.*;
import jemu.core.device.floppy.*;
import jemu.core.device.memory.*;
import jemu.ui.*;
import jemu.util.diss.*;

/**
 * Title:        JEMU
 * Description:  The Java Emulation Platform
 * Copyright:    Copyright (c) 2002
 * Company:
 * @author
 * @version 1.0
 */

public abstract class Computer extends Device implements Runnable {

  // Entries are Name, Key, Class, Shown in list
  
  public static final ComputerDescriptor[] COMPUTERS = {
    new ComputerDescriptor("PC128S",     "PC128S Olivetti Prodest",        "jemu.system.pc128s.PC128S",           true)
  };

  public static final String DEFAULT_COMPUTER = "PC128S";
  
  public static final int TYPE_UNKNOWN    = 0;
  public static final int TYPE_SNAPSHOT   = 1;
  public static final int TYPE_DISC_IMAGE = 2;
  public static final int TYPE_TAPE_IMAGE = 3;

  public static final int STOP      = 0;
  public static final int STEP      = 1;
  public static final int STEP_OVER = 2;
  public static final int RUN       = 3;

  public static final int MAX_FRAME_SKIP = 20;
  public static final int MAX_FILE_SIZE  = 1024 * 1024;  // 1024K maximum

  public static boolean debugTiming = false;

  protected Applet applet;
  protected Thread thread = new Thread(this);
  protected boolean stopped = false;
  protected int action = STOP;
  protected boolean running = false;
  protected boolean waiting = false;
  protected long startTime;
  protected long startCycles;
  protected String name;
  protected String romPath;
  protected String filePath;
  protected Vector files = null;
  protected Display display;
  protected int frameSkip = 0;
  protected int runTo = -1;
  protected int mode = STOP;
  protected ComputerTimer timer;
  protected long maxResync = 200;
  
  // Devices used in this computer
  protected Vector devices = new Vector();

  // Listeners for stopped emulation
  protected Vector listeners = new Vector(1);

  public static Computer createComputer(Applet applet, String name) throws Exception {
    for (int index = 0; index < COMPUTERS.length; index++) {
      if (COMPUTERS[index].key.equalsIgnoreCase(name)) {
        Class cl = Util.findClass(null,COMPUTERS[index].className);
        Constructor con = cl.getConstructor(new Class[] { Applet.class, String.class });
        return (Computer)con.newInstance(new Object[] { applet, name });
      }
    }
    throw new Exception("Computer " + name + " not found");
  }

  public Computer(Applet applet, String name) {
    super("Computer: " + name);
    this.applet = applet;
    this.name = name;
//    thread.setPriority(Thread.MIN_PRIORITY);
    thread.start();
  }

  protected void setBasePath(String path) {
    romPath = "system/" + path + "/rom/";
    filePath = "system/" + path + "/file/";
  }

  public void initialise() {
    reset();
  }
  
  public Device addDevice(Device device) {
    return addDevice(device,null);
  }
  
  public Device addDevice(Device device, String name) {
    if (name != null)
      device.setName(name);
    devices.addElement(device);
    return device;
  }
  
  public Vector getDevices() {
    return devices;
  }

  public InputStream openFile(String name) throws Exception {
    System.out.println("File: " + name);
    InputStream result;
    try {
      result = new URL(applet.getCodeBase(),name).openStream();
    } catch(Exception e) {
//      e.printStackTrace();
      result = new FileInputStream(name);
    }
    if (name.toLowerCase().endsWith(".zip")) {
      ZipInputStream str = new ZipInputStream(result);
      str.getNextEntry();
      result = str;
    }
    return result;
  }

  protected int readStream(InputStream stream, byte[] buffer, int offs, int size)
    throws Exception
  {
    return readStream(stream, buffer, offs, size, true);
  }
  
  protected int readStream(InputStream stream, byte[] buffer, int offs, int size, boolean error)
    throws Exception
  {
    while (size > 0) {
      int read = stream.read(buffer, offs, size);
      if (read == -1) {
        if (error)
          throw new Exception("Unexpected end of stream");
        else
          break;
      }
      else {
        offs += read;
        size -= read;
      }
    }
    return offs;
  }
  
  protected static byte[] SKIP_BUFFER = new byte[1024];
  
  protected void skipStream(InputStream stream, int size) throws Exception {
    while (size > 0) {
      int bytes = size > 1024 ? 1024 : size;
      stream.read(SKIP_BUFFER, 0, bytes);
      size -= bytes;
    }
  }
  
  public byte[] getFile(String name) {
    return getFile(name, MAX_FILE_SIZE, true);
  }
  
  public byte[] getFile(String name, int size) {
    return getFile(name, size, false);
  }
  
  public byte[] getFile(String name, int size, boolean crop) {
    byte[] buffer = new byte[size];
    int offs = 0;
    try {
      InputStream stream = null;
      try {
        stream = openFile(name);
        while (size > 0) {
          int read = stream.read(buffer,offs,size);
          if (read == -1)
            break;
          else {
            offs += read;
            size -= read;
          }
        }
      } finally {
        if (stream != null)
          stream.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (crop && offs < buffer.length) {
      byte[] result = new byte[offs];
      System.arraycopy(buffer,0,result,0,offs);
      buffer = result;
    }
    return buffer;
  }

  public void setDisplay(Display value) {
    display = value;
    displaySet();
  }

  public Display getDisplay() {
    return display;
  }

  protected void displaySet() { }

  // For now, only supporting a single Processor
  public Disassembler getDisassembler() {
    return null;
  }

  public abstract Processor getProcessor();

  public abstract Memory getMemory();

  public void processKeyEvent(KeyEvent e) {
    if (mode == RUN) {
      if (e.getID() == KeyEvent.KEY_PRESSED) {
        keyPressed(e);
        if (e.getKeyCode() == KeyEvent.VK_F12)
          reset();
      }
      else if (e.getID() == KeyEvent.KEY_RELEASED)
        keyReleased(e);
    }
  }
  
  public abstract void keyPressed(KeyEvent e);
  
  public abstract void keyReleased(KeyEvent e);

  public void loadFile(int type, String name) throws Exception { }

  public abstract Dimension getDisplaySize(boolean large);
  
  public void setLarge(boolean value) { }
  
  public Dimension getDisplayScale(boolean large) {
    return large ? Display.SCALE_2 : Display.SCALE_1;
  }

  public void start() {
    setAction(RUN);
  }

  public void stop() {
    setAction(STOP);
  }

  public void step() {
    setAction(STEP);
  }

  public void stepOver() {
    setAction(STEP_OVER);
  }

  public synchronized void setAction(int value) {
    if (running && value != RUN) {
      action = STOP;
      //System.out.println(this + " Stopping " + getProcessor());
      getProcessor().stop();
      display.setPainted(true);
      while(running) {
        try {
          //System.out.println("stopping...");
          Thread.sleep(200);
        } catch(Exception e) {
          e.printStackTrace();
        }
      }
    }
    //System.out.println("Entering synchronized");
    synchronized(thread) {
      action = value;
      thread.notify();
    }
  }

  public void dispose() {
    stopped = true;
    //System.out.println(this + " thread stopped: " + thread);
    stop();
    //System.out.println(this + " has stopped");
    try {
      if (thread != Thread.currentThread())
        thread.join();
    } catch(Exception e) {
      e.printStackTrace();
    }
    thread = null;
    display = null;
    applet = null;
  }

  protected void emulate(int mode) {
    switch(mode) {
      case STEP:
        getProcessor().step();
        break;

      case STEP_OVER:
        getProcessor().stepOver();
        break;

      case RUN:
        if (runTo == -1)
          getProcessor().run();
        else
          getProcessor().runTo(runTo);
        break;
    }
  }

  public void addActionListener(ActionListener listener) {
    listeners.addElement(listener);
  }

  public void removeActionListener(ActionListener listener) {
    listeners.removeElement(listener);
  }

  protected void fireActionEvent() {
    ActionEvent e = new ActionEvent(this,0,null);
    for (int i = 0; i < listeners.size(); i++)
      ((ActionListener)listeners.elementAt(i)).actionPerformed(e);
  }

  public String getROMPath() {
    return romPath;
  }

  public String getFilePath() {
    return filePath;
  }

  public void reset() {
    //System.out.println(this + " Reset");
    boolean run = running;
    stop();
    getProcessor().reset();
    if (run)
      start();
  }

  public void run() {
    while(!stopped) {
      try {
        if (action == STOP) {
          synchronized(thread) {
            //System.out.println(this + " Waiting");
            thread.wait();
            //System.out.println(this + " Not Waiting");
          }
        }
        if (action != STOP) {
          try {
            //System.out.println(this + " Running");
            running = true;
            synchronized(thread) {
              mode = action;
              action = STOP;
            }
            startCycles = getProcessor().getCycles();
            startTime = timer != null ? timer.getCount() : System.currentTimeMillis();
            emulate(mode);
          } finally {
            running = false;
            //System.out.println(this + " Not running");
            fireActionEvent();
          }
        }
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
  }

  public Vector getFiles() {
    if (files == null) {
      files = new Vector();
      LineNumberReader reader = null;
      try {
        reader = new LineNumberReader(new InputStreamReader(openFile(filePath+"Files.txt")));
        String line;
        while((line = reader.readLine()) != null) {
          int iDesc = line.indexOf('=');
          if (iDesc != -1) {
            String desc = line.substring(0,iDesc).trim();
            int iName = line.indexOf(',',iDesc + 1);
            if (iName == -1)
              iName = line.length();
            String name = line.substring(iDesc + 1,iName).trim();
            String instructions = iName < line.length() ?
              line.substring(iName + 1).trim().replace('|','\n') : "";
            files.addElement(new FileDescriptor(desc,name,instructions));
          }
        }
      } catch(Exception e) {
        System.out.println("Cannot get file list for " + this);
      } finally {
        if (reader != null)
          try {
            reader.close();
          } catch(Exception e) {
            e.printStackTrace();
          }
      }
    }
    return files;
  }

  public String getFileInfo(String fileName) {
    String result = null;
    getFiles();
    for (int i = 0; i < files.size(); i++) {
      FileDescriptor file = (FileDescriptor)files.elementAt(i);
      if (file.filename.equalsIgnoreCase(fileName)) {
        result = file.instructions;
        break;
      }
    }
    return result;
  }

  public boolean isRunning() {
    return running;
  }

  public void setTimer(ComputerTimer value) {
    timer = value;
  }
  
  protected void syncProcessor() {
    if (timer != null)
      syncProcessor(timer.getUpdates(), timer.getDeviation());
    else
      syncProcessor((((getProcessor().getCycles() - startCycles) * 2000 /
        getProcessor().getCyclesPerSecond()) + 1) / 2, 200);
  }
  
  @SuppressWarnings( { "SleepWhileHoldingLock", "CallToThreadDumpStack" })
  protected void syncProcessor(long count, long deviation) {
    startTime += count;
    startCycles = getProcessor().getCycles();
    long time = timer != null ? timer.getCount() : System.currentTimeMillis();
    if (debugTiming)
      System.out.println(" E: " + startTime + ", " + time + ", " + deviation +
        ", cycles=" + startCycles + " @ " + System.currentTimeMillis());
    if (time < startTime - (deviation * 2)) {
      System.out.println(" P: " + (startTime - time));
      setFrameSkip(0);
      startTime = time;
    }
    else if (time > startTime) {
      if (frameSkip == MAX_FRAME_SKIP) {
        setFrameSkip(0);
        if (timer != null) timer.resync();
        //System.out.println(" R: " + (time - startTime));
        startTime = (timer != null ? timer.getCount() : System.currentTimeMillis());
      }
      else {
        if (debugTiming)
          System.out.println(" S" + frameSkip);
        setFrameSkip(frameSkip + 1);
      }
    }
    else {
      try {
        setFrameSkip(0);
        long start = System.currentTimeMillis();
        long last = time;
        int cnt = 0;
        while ((time = timer != null ? timer.getCount() : System.currentTimeMillis()) < startTime) {
          if (timer != null && System.currentTimeMillis() - start > maxResync) {
            timer.resync();
            System.out.println("Resync 2");
            startTime = timer.getCount();
            break;
          }
          else
            Thread.sleep(1);
          if (debugTiming && last != time)
            System.out.print("/" + (time - last));
          last = time;
          cnt++;
        }
        if (debugTiming)
          System.out.println(" C:" + cnt + "/" + (time - last) + " @ " + System.currentTimeMillis());
      } catch(Exception e) {
        e.printStackTrace();
        return;
      }
    }
  }
  
  public void setMaxResync(long value) {
    maxResync = value;
  }

  public void setFrameSkip(int value) {
    frameSkip = value;
  }

  public void displayLostFocus() { }
  
  public void updateDisplay(boolean wait) { }

  public String getName() {
    return name;
  }

  public void setRunToAddress(int value) {
    runTo = value;
  }

  public void clearRunToAddress() {
    runTo = -1;
  }

  public int getMode() {
    return mode;
  }
  
  public Drive[] getFloppyDrives() {
    return null;
  }

}
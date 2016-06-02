package jemu.core.device;

/**
 * Title:        JEMU
 * Description:  The Java Emulation Platform
 * Copyright:    Copyright (c) 2002
 * Company:
 * @author
 * @version 1.0
 */

public abstract class Device {
  
  public static final Register[] NO_REGISTERS = new Register[0];

  protected String type;
  protected String name;

  public Device(String type) {
    this.type = type;
  }

  public int readByte(int address) {
    return -1;
  }

  public int writeByte(int address, int value) {
    return value & 0xff;
  }

  public int readPort(int port) {
    return -1;
  }

  public void writePort(int port, int value) { }

  public void cycle() { }

  public void reset() { }

  public void setInterrupt(int mask) { }
  
  public void clearInterrupt(int mask) { }
  
  public int event(int id) { return 0; }
  
  public void setName(String value) {
    name = value;
  }
  
  public String getName() {
    return name;
  }
  
  public String getType() {
    return type;
  }

  public String toString() {
    return type + (name == null ? "" : " - " + name);
  }

  public static int getWord(byte[] buffer, int offs) {
    return (buffer[offs] & 0xff) | ((buffer[offs + 1] << 8) & 0xff00);
  }
  
  public static int getWordBE(byte[] buffer, int offs) {
    return (buffer[offs + 1] & 0xff) | ((buffer[offs] << 8) & 0xff00);
  }
  
  public static int getDWord(byte[] buffer, int offs) {
    return (buffer[offs] & 0xff) | ((buffer[offs + 1] & 0xff) << 8) |
      ((buffer[offs + 2] & 0xff) << 16) | ((buffer[offs + 3] & 0xff) << 24);
  }

  public Register[] getRegisters() {
    return NO_REGISTERS;
  }

  public int getRegisterValue(int index) {
    throw new RuntimeException(toString() + ": No such register: " + index);
  }
  
  public void setRegisterValue(int index, int value) {
    throw new RuntimeException(toString() + ": No such register: " + index);
  }

}
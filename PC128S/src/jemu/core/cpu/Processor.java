package jemu.core.cpu;

import jemu.core.*;
import jemu.core.breakpoint.*;
import jemu.core.device.*;

/**
 * Title:        JEMU
 * Description:  The Java Emulation Platform
 * Copyright:    Copyright (c) 2002
 * Company:
 * @author
 * @version 1.0
 */

public abstract class Processor extends Device {
  
  // Breakpoint map
  public static final int BREAK_MAP_MASK = 0x1fff;

  // Memory for processor
  protected Device memory;

  // Input Devices
  protected DeviceMapping[] inputDevice = new DeviceMapping[0];

  // Output Devices
  protected DeviceMapping[] outputDevice = new DeviceMapping[0];

  // Cycle devices
  protected Device cycleDevice = null;

  // Interrupt device
  protected Device interruptDevice = null;
  
  // Interrupt mask
  protected int interruptPending = 0;

  // Total number of cycles executed
  protected long cycles = 0;

  // Cycles per second of CPU
  protected long cyclesPerSecond;

  // Processor stopped
  protected boolean stopped = false;
  
  // Breakpoint map
  protected byte[] breakMap = new byte[BREAK_MAP_MASK + 1];
  
  // Breakpoints list
  protected Breakpoint[] breakPoints = new Breakpoint[0];

  public Processor(String type, long cyclesPerSecond) {
    super(type);
    this.cyclesPerSecond = cyclesPerSecond;
  }

  public final void cycle(int count) {
    for (; count > 0; count--) cycle();
  }
  
  @Override
  public void cycle() {
    cycles++;
    if (cycleDevice != null)
      cycleDevice.cycle();
  }
  
  @Override
  public void reset() {
    cycles = 0;
  }

  public long getCycles() {
    return cycles;
  }

  public abstract void step();

  public abstract void stepOver();

  public void run() {
    stopped = false;
    do {
      step();
    } while(!stopped);
  }

  public void runTo(int address) {
    stopped = false;
    do {
      step();
    } while(!stopped && getProgramCounter() != address);
  }

  public synchronized void stop() {
    stopped = true;
  }

  public final int readWord(int addr) {
    return readByte(addr) + (readByte((addr + 1) & 0xffff) << 8);
  }

  public final void writeWord(int addr, int value) {
    writeByte(addr,value);
    writeByte((addr + 1) & 0xffff, value >> 8);
  }

  @Override
  public int readByte(int address) {
    return memory.readByte(address);
  }

  @Override
  public int writeByte(int address, int value) {
    return memory.writeByte(address,value);
  }

  public final int in(int port) {
    int result = 0xff;
    for (int i = 0; i < inputDevice.length; i++)
      result &= inputDevice[i].readPort(port);
    return result;
  }

  public final void out(int port, int value) {
    for (int i = 0; i < outputDevice.length; i++)
      outputDevice[i].writePort(port,value);
  }

  public final void setMemoryDevice(Device value) {
    memory = value;
  }

  public final Device getMemoryDevice() {
    return memory;
  }

  public final void addInputDeviceMapping(DeviceMapping value) {
    inputDevice = (DeviceMapping[])Util.arrayInsert(inputDevice,inputDevice.length,1,
      value);
  }

  public final void removeInputDeviceMapping(DeviceMapping value) {
    inputDevice = (DeviceMapping[])Util.arrayDeleteElement(inputDevice,value);
  }

  public final void addOutputDeviceMapping(DeviceMapping value) {
    outputDevice = (DeviceMapping[])Util.arrayInsert(outputDevice,outputDevice.length,1,
      value);
  }

  public final void removeOutputDeviceMapping(DeviceMapping value) {
    outputDevice = (DeviceMapping[])Util.arrayDeleteElement(outputDevice,value);
  }

  public final void setCycleDevice(Device value) {
    cycleDevice = value;
  }

  public final void setInterruptDevice(Device value) {
    interruptDevice = value;
  }
  
  @Override
  public void setInterrupt(int mask) {
    interruptPending |= mask;
  }
  
  @Override
  public void clearInterrupt(int mask) {
    interruptPending &= ~mask;
  }

  public abstract String getState();

  public abstract int getProgramCounter();
  
  public long getCyclesPerSecond() {
    return cyclesPerSecond;
  }
  
  public void setCyclesPerSecond(long value) {
    cyclesPerSecond = value;
  }

}
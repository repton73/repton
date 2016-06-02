package jemu.core.device.floppy;

import jemu.core.Util;
import jemu.core.cpu.MC65C12;
import jemu.core.device.Device;

/**
 * WD1770 Floppy Controller Emulation.
 *
 * @author Richard Wilson
 */
public class WD1770 extends Device {
  
  public static final int MOTOR_ON_DELAY   =  500000;
  public static final int MOTOR_OFF_DELAY  = 1500000;
  
  public static final int MOTOR_ON      = 0x80;
  public static final int WRITE_PROTECT = 0x40;
  public static final int SPIN_UP       = 0x20;
  public static final int DELETED_DATA  = 0x20;
  public static final int NOT_FOUND     = 0x10;
  public static final int CRC_ERROR     = 0x08;
  public static final int TRACK_ZERO    = 0x04;
  public static final int LOST_DATA     = 0x04;
  public static final int INDEX         = 0x02;
  public static final int DATA_REQUEST  = 0x02;
  public static final int BUSY          = 0x01;
  
  public static final int NO_COMMAND    = 0;
  public static final int EXECUTE       = 1;
  public static final int SETTLE        = 2;
  public static final int SPIN_DOWN     = 3;
  
  protected int status;
  protected int track;
  protected int sector;
  protected int data;
  protected int offset;
  protected int size;
  protected int side;
  protected boolean read;
  protected boolean write;
  protected byte[] buffer;
  
  protected int command;             // Current command
  protected int count;               // Current count
  protected int count2;              // Next count
  protected int count3;              // Third count
  protected int mode = NO_COMMAND;
  protected int currentTrack = 0;
  protected int direction = -1;
  protected int stepRate;
  protected int[] stepRates = { 6000, 12000, 20000, 30000 };
  protected int settleTime = 30000;
  
  protected Device interruptDevice;
  protected int interruptMask;
  protected Device dataRequestDevice;
  protected int dataRequestMask;
  protected Drive drive;
  
  /** Creates a new instance of WD1770 */
  public WD1770() {
    super("WD1770 Floppy Controller");
  }
  
  public void setDrive(Drive drive) {
	this.drive  = drive;
  }
  
  public Drive getDrive() {
	return this.drive;
  }
  
  public void setInterruptDevice(Device device, int mask) {
    interruptDevice = device;
    interruptMask = mask;
  }
  
  public void setDataRequestDevice(Device device, int mask) {
    dataRequestDevice = device;
    dataRequestMask = mask;
  }
  
  protected void interrupt(boolean set) {
    if (interruptDevice != null) {
      if (set)
        interruptDevice.setInterrupt(interruptMask);
      else
        interruptDevice.clearInterrupt(interruptMask);
    }
  }
  
  protected void dataRequest(boolean set) {
    if (dataRequestDevice != null) {
      if (set)
        dataRequestDevice.setInterrupt(dataRequestMask);
      else
        dataRequestDevice.clearInterrupt(dataRequestMask);
    }
  }
  
  protected void spinup(int command) {
    status &= ~SPIN_UP;
    if ((command & 0x08) == 0 && (status & MOTOR_ON) == 0) {
      count = MOTOR_ON_DELAY;
      mode = SPIN_UP;
    }
    else
      mode = EXECUTE;
  }
  
  protected void seek(int command, int track) {
    //System.out.println("Seek: " + track + ", current=" + currentTrack);
    spinup(command);
    currentTrack = track;
    stepRate = stepRates[command & 0x03];
    int diff = currentTrack - track;
    count2 = (diff < 0 ? -diff : diff) * stepRate;
    if (track < 0) currentTrack = 0;
    else if (track > 255) currentTrack = 255;
    else currentTrack = track;
    if ((command & 0x04) != 0)
      count2 += settleTime;
  }
    
  public int readPort(int port) {
    //System.out.println(Util.hex((short)((MC65C12)interruptDevice).getProgramCounter()) +": Read 1770 " + Util.hex((byte)port) + " " + Util.hex((byte)status) + " " + Util.hex((byte)track)+ " " + Util.hex((byte)sector)+ " " + Util.hex((byte)data));
    switch(port) {
      case 0: interrupt(false); return status;
      case 1: return track;
      case 2: return sector;
      case 3: // Data read
    	  status&=~DATA_REQUEST; 
    	  return data;
    }
    return 0xfe;
  }
  
  public void writePort(int port, int value) {
    //System.out.println(Util.hex((short)((MC65C12)interruptDevice).getProgramCounter()) +": Write 1770 " + Util.hex((byte)port) + " " + Util.hex((byte)value) + " " + Util.hex((byte)status));
    switch(port) {
      case 0: if ((status & BUSY) == 0 || (value & 0xf0) == 0xd0) command(value); break;
      case 1: if ((status & BUSY) == 0) track = value;                            break;
      case 2: if ((status & BUSY) == 0) sector = value;                           break;
      case 3: // Data write
    	  status&=~DATA_REQUEST;
    	  data = value;
    	  break;
      case 4: side = (value&16)!=0?1:0;
    }
  }
  
  protected void command(int value) {
    //System.out.println("1770 command "+Util.hex((byte)value)+" "+Util.hex((byte)status)+" "+Util.hex((byte)track)+" "+Util.hex((byte)sector)+" "+Util.hex((byte)data));
    command = value;
    status |= BUSY;
    switch(command & 0xf0) {
      case 0x00: seek(command,0);                                break; // Restore
      case 0x10: seek(command,data);                             break; // Seek
      case 0x20:
      case 0x30: seek(command,currentTrack + direction);         break; // Step
      case 0x40:
      case 0x50: direction = 1; seek(command,currentTrack + 1);                break; // Step out
      case 0x60: 
      case 0x70: direction = -1; seek(command,currentTrack - 1); break; // Step in
                  
      case 0x80: /*Read sector*/
    	  spinup(command);
    	  status=0x81;
    	  read = true;
    	  offset = 0;
    	  break; 
      case 0xA0: /*Write sector*/
    	  status=0x83;
    	  spinup(command);
          break; 
      case 0xC0: /*Read address*/
    	  status=0x81;
    	  spinup(command);
          break; 
      case 0xD0: /*Force interrupt*/
    	  status=0x80;
    	  if (currentTrack == 0) status &= ~TRACK_ZERO; else status |= TRACK_ZERO;
    	  spinup(command);
          break; 
      case 0xF0: /*Write track*/
    	  status=0x81;
    	  spinup(command);
          break;       
    }
    if (mode == EXECUTE) {
      count = count2;
      count2 = 0;
    }
    //System.out.println("Command started: mode=" + mode + ", count=" + count + ", count2=" + count2 + ", status=" + Util.hex((byte)status));
    //((Processor)interruptDevice).stop();
  }
  
  public void reset() {
    status = track = sector = data = offset = size = side = 0;
    read = write = false;
  }
  
  public void cycle() {
    if (mode != NO_COMMAND && --count <= 0) {
      if (mode == SPIN_UP) {
        mode = EXECUTE;
        status |= SPIN_UP | MOTOR_ON;
        count = count2;
        count2 = 0;
      }
      else if (mode == EXECUTE) {
    	//System.out.println("FDC("+Util.hex((byte)command)+","+Util.hex((byte)status)+")");
        status &= ~BUSY;
        if (command < 0x80) {
          if (currentTrack == 0) status &= ~TRACK_ZERO; else status |= TRACK_ZERO;
          if (command < 0x30 || (command < 0x80 && (command & 0x10) != 0)) {
            track = currentTrack;
            drive.setCylinder(track);
          }
        } else if (read && (command&0xf0)==0x80) {
        	if (drive.getDisc(1)==null) {
                status|=NOT_FOUND;
                read = false;
            } else if (read) {
            	if (offset == 0) {
        	    	//System.out.println("1770 read sector "+Util.hex((byte)track)+" "+Util.hex((byte)sector)+" inizio");
            		buffer = drive.getSector(track,side,sector,0);
            		size = 256;
            	    if (buffer.length < size) {
            	        size = buffer.length;
            	    }
        	    }
            	if (offset<size) {        	    	
	        	    data = buffer[offset++] & 0xff;
	        	    status|=DATA_REQUEST|BUSY; 
	        	    count = 50;
	        	    interrupt(true);
	            	return;
            	} else {
        	    	//System.out.println("1770 read sector "+Util.hex((byte)track)+" "+Util.hex((byte)sector)+" fine");
            		read = false;
            		offset = 0;
        	    }
            }
        }
        interrupt(true);
        mode = SPIN_DOWN;
        count = MOTOR_OFF_DELAY;
      }
      else if (mode == SPIN_DOWN) {
        mode = NO_COMMAND;
        status &= ~MOTOR_ON;
      }
      //System.out.println("New Mode: " + mode + ", count=" + count + ", status=" + Util.hex((byte)status));
    }
  }
  
}

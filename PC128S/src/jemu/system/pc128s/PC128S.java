package jemu.system.pc128s;

import java.applet.Applet;
import java.awt.Dimension;
import java.awt.event.KeyEvent;

import jemu.core.Util;
import jemu.core.cpu.MC65C12;
import jemu.core.cpu.Processor;
import jemu.core.device.Computer;
import jemu.core.device.crtc.Basic6845;
import jemu.core.device.crtc.SAA505x;
import jemu.core.device.floppy.Drive;
import jemu.core.device.floppy.WD1770;
import jemu.core.device.io.R6522;
import jemu.core.device.memory.Memory;
import jemu.core.device.sound.SN76489;
import jemu.ui.Display;
import jemu.util.diss.Disassembler;
import jemu.util.diss.Diss65C12;

/**
 * Emaulate the PC128S Olivetti Prodest.
 *
 * @author Carlo Giovanardi
 */
public class PC128S extends Computer {
  
  protected static Dimension HALF_DISPLAY_SIZE = new Dimension(384,270);
  protected static Dimension FULL_DISPLAY_SIZE = new Dimension(768,540);
  
  protected static final int CYCLES_PER_SECOND = 2000000;
  protected static final int AUDIO_TEST        = 0x40000000;
  
  protected static final int SYS_VIA_PORT_A = 0;
  protected static final int SYS_VIA_PORT_B = 1;
  
  protected static final int SYS_VIA_INT_MASK  = 0x00001;
  protected static final int USER_VIA_INT_MASK = 0x00002;
  protected static final int FDC_INT_MASK      = 0x10000;  // NMI
  
  protected static final int KEYBOARD_WRITE_ENABLE = 0x08;
  protected static final int SOUND_WRITE_ENABLE    = 0x01;

  protected MC65C12 cpu = (MC65C12)addDevice(new MC65C12(CYCLES_PER_SECOND));
  protected PC128SMemory memory = (PC128SMemory)addDevice(new PC128SMemory(cpu));
  protected Basic6845 crtc = (Basic6845)addDevice(new Basic6845());
  protected SAA505x saa = (SAA505x)addDevice(new SAA505x());
  protected R6522 sysVIA = (R6522)addDevice(new R6522(),"System VIA");
  protected R6522 userVIA = (R6522)addDevice(new R6522(),"User VIA");
  protected Video video = new Video(this);
  protected SN76489 psg = (SN76489)addDevice(new SN76489());
  protected WD1770 fdc = (WD1770)addDevice(new WD1770());
  protected Keyboard keyboard = (Keyboard)addDevice(new Keyboard(sysVIA));
  protected Disassembler disassembler = new Diss65C12();
  protected int latchState = 0x00;
  protected int fdcControl = 0x01;
  protected boolean oddCycle = false;
  protected boolean oddFrame = false;
  protected int audioCount = 0;
  protected int audioAdd = psg.getSoundPlayer().getClockAdder(AUDIO_TEST,CYCLES_PER_SECOND >> 1);
  protected Drive[] floppies = new Drive[1];
  protected int acccon = 0;  
  
  /** Creates a new instance of PC128S */
  public PC128S(Applet applet, String name) {
    super(applet,name);
    cpu.setMemoryDevice(this);
    cpu.setCycleDevice(this);
    sysVIA.getPort(R6522.PORT_A).setInputDevice(this,SYS_VIA_PORT_A);
    sysVIA.getPort(R6522.PORT_A).setOutputDevice(this,SYS_VIA_PORT_A);
    sysVIA.getPort(R6522.PORT_B).setOutputDevice(this,SYS_VIA_PORT_B);
    sysVIA.setInterruptDevice(cpu,SYS_VIA_INT_MASK);
    userVIA.setInterruptDevice(cpu,USER_VIA_INT_MASK);
    fdc.setInterruptDevice(cpu,FDC_INT_MASK);
    fdc.setDrive(floppies[0] = new Drive(2));
    crtc.setCRTCListener(video);
    psg.setClockSpeed(CYCLES_PER_SECOND * 2);
    setBasePath("pc128s");
  }
  
  public void initialise() {
    memory.loadROM(0x00,new byte[0x4000]);
    memory.loadROM(0x01,new byte[0x4000]);
    memory.loadROM(0x02,new byte[0x4000]);
    memory.loadROM(0x03,new byte[0x4000]);
    memory.loadROM(0x04,new byte[0x4000]);
    memory.loadROM(0x05,new byte[0x4000]);
    memory.loadROM(0x06,new byte[0x4000]);
    memory.loadROM(0x07,new byte[0x4000]);
    memory.loadROM(0x08,new byte[0x4000]);
    memory.loadROM(0x09,new byte[0x4000]);
    memory.loadROM(0x0a,new byte[0x4000]);
    memory.loadROM(0x0b,new byte[0x4000]);
    memory.setOSROM(getFile(romPath + "OS51.ROM",0x4000));
    memory.loadROM(0x0d,getFile(romPath + "ADFS210.ROM",0x4000));
    memory.loadROM(0x0e,getFile(romPath + "BASIC48.ROM",0x4000));
    memory.loadROM(0x0f,getFile(romPath + "UTILS.ROM",0x4000));
    video.setMemory(memory.getMemory());
    saa.setCharacterROM(getFile(romPath + "SAA5050.fnt",0x360));
    //fdc.getDrive().setDisc(3,new PC128SDiscImage("Welcome Disc",getFile(filePath + "welcome.zip")));
    psg.getSoundPlayer().play();
    super.initialise();
  }
  
  public void dispose() {
    super.dispose();
    psg.getSoundPlayer().dispose();
  }
  
  public void reset() {
    //keyboard.reset();
    sysVIA.reset();
    userVIA.reset();
    fdc.reset();
    //crtc.reset();
    psg.reset();
    super.reset();
  }
  
  public void loadFile(int type, String name) throws Exception {
	  fdc.getDrive().setDisc(3,new PC128SDiscImage(name,getFile(name)));
  }

  public Memory getMemory() {
    return memory;
  }
  
  public void cycle() {
    video.cycle();
    if (oddCycle = !oddCycle) {
      if ((latchState & KEYBOARD_WRITE_ENABLE) != 0)
        keyboard.cycle();
      sysVIA.cycle();
      userVIA.cycle();
      fdc.cycle();
      psg.cycle(4);
      if ((audioCount += audioAdd) >= AUDIO_TEST) {
        //System.out.println("Audio Out:  " + cpu.getCycles());
        psg.writeAudio();
        audioCount -= AUDIO_TEST;
      }
    }
  }
  
  public void setFrameSkip(int value) {
    super.setFrameSkip(value);
    video.setRendering(value == 0);
  }
  
  long lastCycles;

  public void vSync() {
    if (frameSkip == 0)
      updateDisplay(true);
    syncProcessor();//psg.getSoundPlayer()
    lastCycles = cpu.getCycles();
  }
  
  public void updateDisplay(boolean wait) {
    display.setSourceRect(video.getImageRect());
    display.updateImage(wait);
  }

  public final int readByte(int addr) {
	int value = readByteLog(addr);
	if (addr >= 0xfe30 && addr < 0xfe38) {
	    //System.out.println(Util.hex((short)cpu.getProgramCounter())+": readByte(" + Util.hex((short)addr) + "," + Util.hex((byte)value) + ")");
	}
	return value;
  }

  public final int readByteLog(int addr) {
	int temp;
    if (addr >= 0xfc00 && addr < 0xff00) {
    	if (addr >= 0xfe00) {
        if (oddCycle) cpu.cycle();
        cpu.cycle();
        switch(addr & 0xe0) {
          case 0x00: {
            if (addr >= 0xfe00 && addr < 0xfe08)
              return crtc.readPort(addr & 0x07);
            else if (addr < 0xfe10) {
              //System.out.println("ACIA read " + Util.hex((short)addr)); // 6850 ACIA
              return 0x7f;
            }  
            else
              //System.out.println("Serial ULA read " + Util.hex((short)addr)); // Serial ULA
            break;
          }
          case 0x20: {
          	if (addr >= 0xfe24 && addr < 0xfe2c) {
          		return fdc.readPort(addr & 0x03);
          	} else if (addr >= 0xfe34 && addr < 0xfe38) {
          		return acccon;
          	}
        	return 0xff;       // Video ULA and ROM Select not readable
          }
          
          case 0x40: 
        	  temp = sysVIA.readPort(addr & 0x0f);
        	  if (cpu.trace) System.out.println(Util.hex((short)cpu.getProgramCounter())+": readByte(" + Util.hex((short)addr) + "," + Util.hex((byte)temp)+ ")");
        	  return temp;

          case 0x60: 
        	  temp = userVIA.readPort(addr & 0x0f);
        	  //System.out.println(Util.hex((short)cpu.getProgramCounter())+": readByte(" + Util.hex((short)addr) + "," + Util.hex((byte)temp)+ ")");
        	  return temp;
          
          //case 0x80: return fdc.readPort(addr & 0x07);
          
          case 0xa0: {
            //System.out.println("Econet read " + Util.hex((short)addr)); // Econet
            break;
          }
          
          case 0xc0: {
            //System.out.println("ADC read " + Util.hex((short)addr));
            break;
          }
          
          default: {     // 0xfee0..0xfeff - Tube reads
            //System.out.println("Tube read " + Util.hex((short)addr));
            return 0;
          }
        }
      }
      return 0xff;
    }
    return memory.readByte(addr);
  }
  
  public final int writeByte(int addr, int value) {
	  if (addr >= 0xfc00 && addr < 0xfe00) {
		  //System.out.println(Util.hex((short)cpu.getProgramCounter())+": writeByte(" + Util.hex((short)addr) + "," + Util.hex((byte)value)+") "+memory.vidbank);  
	  }
	  if (addr >= 0xfc00 && addr < 0xff00) {
      if (addr >= 0xfe00) {
        if (oddCycle) cpu.cycle();
        cpu.cycle();
        switch(addr & 0xe0) {
          case 0x00: {  // CRTC, ACIA, SERPROC, INTOFF/STATID
        	if (addr >= 0xfe00 && addr < 0xfe08) {
        	  crtc.writePort(addr & 0x07,value);
        	}
            //else if (addr < 0xfe10)
              //System.out.println("ACIA write " + Util.hex((short)addr) + "=" + Util.hex((byte)value));
            //else
              //System.out.println("Serial ULA write " + Util.hex((short)addr) + "=" + Util.hex((byte)value));
            break;
          }
          
          case 0x20: {
        	if (addr >= 0xfe20 && addr < 0xfe24) {
            	video.writePort(addr & 0x01,value);
        	} else if (addr >= 0xfe24 && addr < 0xfe2c) {
        		fdc.writePort(addr & 0x07,value);
        	} else if (addr >= 0xfe34 && addr < 0xfe38) {
        		acccon = value;
        		memory.selectRAM(value);
        	} else if (addr >= 0xfe30 && addr < 0xfe34) {
                memory.selectROM(value);
            }
            break;
          }
          
          case 0x40: 
          if (cpu.trace) System.out.println(Util.hex((short)cpu.getProgramCounter())+": writeByte(" + Util.hex((short)addr) + "," + Util.hex((byte)value)+")");
          sysVIA.writePort(addr & 0x0f,value); break;

          case 0x60: 
          //System.out.println(Util.hex((short)cpu.getProgramCounter())+": writeByte(" + Util.hex((short)addr) + "," + Util.hex((byte)value)+")");
          userVIA.writePort(addr & 0x0f,value); break;

          //case 0x80: fdc.writePort(addr & 0x07,value); break;
          
          case 0xa0: {
            //System.out.println("Econet write " + Util.hex((short)addr) + "=" + Util.hex((byte)value));
            break;            
          }
          
          case 0xc0: {
            //System.out.println("ADC write " + Util.hex((short)addr) + "=" + Util.hex((byte)value));
            break;
          }
          
          default: {     // 0xfee0..0xfeff - Tube write
            //System.out.println("Tube write " + Util.hex((short)addr) + "=" + Util.hex((byte)value));
            break;
          }
        }
      }
      return value & 0xff;
    }
    return memory.writeByte(addr,value);
  }
  
  public int readPort(int port) {
    if (port == SYS_VIA_PORT_A) {
      //System.out.println("Key read: " + Util.hex((short)cpu.getProgramCounter()));
      return keyboard.isKeyPressed() ? 0xff : 0x7f;
    }
    return 0xff;
  }
  
  public void writePort(int port, int value) {
    if (port == SYS_VIA_PORT_A) {
      //System.out.println("Keyboard value: " + Util.hex((byte)value) + ": " + Util.hex((byte)sysVIA.getPort(0).getPortMode()));
      if ((latchState & KEYBOARD_WRITE_ENABLE) == 0)
        keyboard.setColumnAndRow(value & 0x0f, (value >> 4) & 0x07);
      //if ((latchState & SOUND_WRITE_ENABLE) == 0)
        //psg.writePort(0,value);
    }
    else {
      int bit = value & 0x07;
      boolean set = (value & 0x08) != 0;
      //System.out.println("Sys VIA Port B Write: " + bit + " = " + set);
      // TODO: Some bits may need to be processed
      if (set)
        latchState |= 0x01 << bit;
      else
        latchState &= ~(0x01 << bit);
      video.setAddMA((latchState & 0x30) << 10);
      if ((latchState & SOUND_WRITE_ENABLE) == 0)
        psg.writePort(0,sysVIA.getPort(R6522.PORT_A).getOutput());
    }
  }

  public Processor getProcessor() {
    return cpu;
  }
  
  public void setDisplay(Display display) {
    super.setDisplay(display);
    video.setPixels(display.getPixels());
  }

  public Dimension getDisplaySize(boolean large) {
    return large ? FULL_DISPLAY_SIZE : HALF_DISPLAY_SIZE;
  }
  
  public Dimension getDisplayScale(boolean large) {
    return Display.SCALE_1;
  }
  
  public void keyPressed(KeyEvent e) {
    keyboard.keyPressed(e.getKeyCode());
  }
  
  public void keyReleased(KeyEvent e) {
    keyboard.keyReleased(e.getKeyCode());
  }
  
  public Disassembler getDisassembler() {
    return disassembler;
  }
  
  public void setLarge(boolean value) {
    video.setLarge(value);
  }
  
  public Drive[] getFloppyDrives() {
    return floppies;
  }

}

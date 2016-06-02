package jemu.system.pc128s;

import jemu.core.Util;
import jemu.core.cpu.MC65C12;
import jemu.core.device.memory.DynamicMemory;

 /**
  * Actual memory mapping from I/O map performed by PC128S.
  *
  * Memory is allocated in 16K blocks.
  */
public class PC128SMemory extends DynamicMemory {
  
  public int[] ramBank = new int[16];
  public int[] memStat = new int[16];
  protected int[] readMap = new int[16];
  protected int[] writeMap = new int[16];
  protected boolean swram = false;
  protected int vidbank = 0;
  protected MC65C12 cpu;
  
  protected static final int BASE_RAM    = 0;
  protected static final int BASE_OS_ROM = 3;
  protected static final int BASE_ROM    = BASE_OS_ROM + 1;
  protected static final int LAST_ROM    = BASE_ROM + 15;
  
  public PC128SMemory(MC65C12 cpu) {
    super("PC128S Memory",0x10000,LAST_ROM + 1);
    this.cpu = cpu;
    for (int i = BASE_RAM; i < BASE_ROM; i++)
      getMem(i,0x4000);
    for (int i = 0; i < 16; i++) {
      readMap[i] = i*4096;
      writeMap[i] = i<8?readMap[i]:-1;
    }
    for (int i = 0; i < 16; i++) ramBank[i]=0;
  }
  
  public int readByte(int address) {
	int addr = readMap[address >> 12];
	if (cpu.vis20k && addr>=0x3000 && addr<0x8000) addr+=vidbank;
	addr = addr + (address & 0x0fff);
	return mem[addr] & 0xff;
  }

  public int writeByte(int address, int value) {
	int addr = writeMap[address >> 12];
    if (addr==-1) return 0;
	if (cpu.vis20k && addr>=0x3000 && addr<0x8000) addr+=vidbank;
	addr = addr + (address & 0x0fff);
	mem[addr] = (byte)value;
    return value & 0xff;
  }
  
  public void setOSROM(byte[] value) {
	loadROM(0x0c,value);
	int base = baseAddr[16];
    for (int i = 12; i < 16; i++) {
    	readMap[i] = base + (i%4)*4096;
		writeMap[i] = -1;
    }
  }
  
  public void loadROM(int slot, byte[] value) {
    int start = getMem(BASE_ROM + (slot & 0x0f),0x4000);
    System.arraycopy(value,0,mem,start,Math.min(value.length,0x4000));
  }
  
  public void selectROM(int val) {
	swram = (val&15)>=4 && (val&15)<8;
    int base = baseAddr[BASE_ROM + (val&0x0f)];
    for (int i = 8; i < 12; i++) {
    	if (base != -1) {
    		readMap[i] = base+(i%4)*4096;
    		writeMap[i] = swram?readMap[i]:-1;
    	} else {
    		readMap[i] = i*4096;
    		writeMap[i] = -1;
    	}
    }
    if ((val&0x80)!=0) for (int c=8;c<9;c++) readMap[c]=writeMap[c]=c*4096;
  }
  
  public void selectRAM(int val) {
	  int ram8k = (val&8);
	  int ram20k = (val&4);
	  vidbank = (val&1)!=0?0x8000:0;
	  ramBank[0xC]=ramBank[0xD]=(val&2)!=0?1:0;
      for (int c=3;c<8;c++) readMap[c]=writeMap[c]=c*4096+(ram20k!=0?32768:0);
	  if (ram8k!=0) {
		  	for (int c=12;c<14;c++) readMap[c]=writeMap[c]=(c-3)*4096;
	  } else {
		    int base = baseAddr[16];
		    for (int i = 12; i < 16; i++) {
	    		readMap[i] = base+(i%4)*4096;
	    		writeMap[i] = -1;
		    }
	  }
  }
  
}
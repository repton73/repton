package jemu.core.cpu;

import jemu.core.*;
import jemu.core.device.*;

/**
 * Title:        JEMU
 * Description:  The Java Emulation Platform
 * Copyright:    Copyright (c) 2002 - 2011
 * Company:
 * @author Richard Wilson
 * @version 2.0
 */

public class Z80 extends Processor {

  // Standard registers
  public static final int B = 0;
  public static final int C = 1;
  public static final int D = 2;
  public static final int E = 3;
  public static final int H = 4;
  public static final int L = 5;
  public static final int F = 6;
  public static final int A = 7;

  // Alternate register set
  public static final int B1 = 8;
  public static final int C1 = 9;
  public static final int D1 = 10;
  public static final int E1 = 11;
  public static final int H1 = 12;
  public static final int L1 = 13;
  public static final int F1 = 14;
  public static final int A1 = 15;

  // Declarations for get/setWordReg function
  public static final int BC = 0;
  public static final int DE = 2;
  public static final int HL = 4;
  public static final int AF = 6;

  // Flags
  public static final int FS  = 0x80;
  public static final int FZ  = 0x40;
  public static final int F5  = 0x20;
  public static final int FH  = 0x10;
  public static final int F3  = 0x08;
  public static final int FPV = 0x04;
  public static final int FN  = 0x02;
  public static final int FC  = 0x01;

  protected static final int FLAG_MASK_LDIR  = 0xc1;
  protected static final int FLAG_MASK_CPIR  = 0xfa;
  protected static final int FLAG_MASK_CPL   = 0xc5;
  protected static final int FLAG_MASK_CCF   = 0xc5;
  protected static final int FLAG_MASK_SCF   = 0xc5;
  protected static final int FLAG_MASK_ADDHL = 0xc4;
  protected static final int FLAG_MASK_RLCA  = 0xc4;
  protected static final int FLAG_MASK_RLD   = 0x01;
  protected static final int FLAG_MASK_BIT   = 0x01;
  protected static final int FLAG_MASK_IN    = 0x01;
  protected static final int FLAG_MASK_INI   = 0xe8;

  // Parity values
  protected static int[] PARITY = new int[256];
  static {
    for (int i = 0; i < 256; i++) {
      int p = (i & 0x01) == 0 ? FPV : 0;
      if ((i & 0x02) != 0) p ^= FPV;
      if ((i & 0x04) != 0) p ^= FPV;
      if ((i & 0x08) != 0) p ^= FPV;
      if ((i & 0x10) != 0) p ^= FPV;
      if ((i & 0x20) != 0) p ^= FPV;
      if ((i & 0x40) != 0) p ^= FPV;
      if ((i & 0x80) != 0) p ^= FPV;
      PARITY[i] = p;
    }
  };

  // Register set
  protected int[] reg = new int[16];

  // 16-bit only registers
  protected int SP, PC, IX, IY;

  // 8-bit special registers
  protected int I, R, R7, IM;

  // Hidden MEMPTR register
  protected int MEMPTR;

  // Interrupt flip-flops
  protected boolean IFF1, IFF2;

  // Currently executing a HALT instruction?
  protected boolean inHalt = false;

  // Flag to cause an interrupt to execute
  protected boolean interruptExecute = false;

  // Interrupt Vector
  protected int interruptVector = 0xff;

  // Standard, Interrupt and Current Fetchers
  protected Fetcher standardFetcher = new Fetcher();
  protected Fetcher interruptFetcher = new InterruptFetcher();
  protected Fetcher fetcher = standardFetcher;

  public Z80(long cyclesPerSecond) {
    super("Zilog Z80", cyclesPerSecond);
  }
  
  @Override
  public void reset() {
    super.reset();
    for (int i = 0; i < reg.length; i++)
      reg[i] = 0xff;
    IX = IY = SP = 0xffff;
    PC = 0;
    I = R = R7 = IM = MEMPTR = 0;
    IFF1 = IFF2 = false;
    interruptPending = 0;
    interruptExecute = inHalt = false;
  }

  public void stepOver() {
    int opcode = memory.readByte(PC);
    switch(opcode) {
      case 0x76: runTo((PC + 1) & 0xffff);        break;

      case 0xed: stepOverED();                    break;

      case 0xc4:
      case 0xcc:
      case 0xcd:
      case 0xd4:
      case 0xdc:
      case 0xe4:
      case 0xf4:
      case 0xfc: runTo((PC + 3) & 0xffff);        break;

      default:   step();                          break;
    }
  }

  protected void stepOverED() {
    int opcode = memory.readByte((PC + 1) & 0xffff);
    switch(opcode) {
      case 0xb0:
      case 0xb1:
      case 0xb2:
      case 0xb3:
      case 0xb8:
      case 0xb9:
      case 0xba:
      case 0xbb: runTo((PC + 2) & 0xffff);        break;

      default:   step();                          break;
    }
  }

  public final void step() {
    if (interruptExecute)
      doInterrupt();
    else
      executeNormal(fetchOpCode());
  }

  protected void executeNormal(int opcode) {
    R++;
    switch(opcode) {
      case 0x00: nop();                           break;

      case 0x01:
      case 0x11:
      case 0x21:
      case 0x31: ldddnn(opcode, fetchWord());     break;

      case 0x02: ldbca();                         break;

      case 0x03:
      case 0x13:
      case 0x23:
      case 0x33: incss(opcode);                   break;

      case 0x04:
      case 0x0c:
      case 0x14:
      case 0x1c:
      case 0x24:
      case 0x2c:
      case 0x3c: incr(opcode);                    break;

      case 0x05:
      case 0x0d:
      case 0x15:
      case 0x1d:
      case 0x25:
      case 0x2d:
      case 0x3d: decr(opcode);                    break;

      case 0x06:
      case 0x0e:
      case 0x16:
      case 0x1e:
      case 0x26:
      case 0x2e:
      case 0x3e: ldrn(opcode, fetchByte());       break;

      case 0x07: rlca();                          break;

      case 0x08: exafaf1();                       break;

      case 0x09:
      case 0x19:
      case 0x29:
      case 0x39: addhlss(opcode);                 break;

      case 0x0a: ldabc();                         break;

      case 0x0b:
      case 0x1b:
      case 0x2b:
      case 0x3b: decss(opcode);                   break;

      case 0x0f: rrca();                          break;

      case 0x10: djnze((byte)fetchByte());        break;

      case 0x12: lddea();                         break;

      case 0x17: rla();                           break;

      case 0x18: jre((byte)fetchByte());          break;

      case 0x1a: ldade();                         break;

      case 0x1f: rra();                           break;

      case 0x20: jrnze((byte)fetchByte());        break;

      case 0x22: ldxxhl(fetchWord());             break;

      case 0x27: daa();                           break;

      case 0x28: jrze((byte)fetchByte());         break;

      case 0x2a: ldhlxx(fetchWord());             break;

      case 0x2f: cpl();                           break;

      case 0x30: jrnce((byte)fetchByte());        break;

      case 0x32: ldxxa(fetchWord());              break;

      case 0x34: incchl();                        break;

      case 0x35: decchl();                        break;

      case 0x36: ldhln(fetchByte());              break;

      case 0x37: scf();                           break;

      case 0x38: jrce((byte)fetchByte());         break;

      case 0x3a: ldaxx(fetchWord());              break;

      case 0x3f: ccf();                           break;

      case 0x40:
      case 0x41:
      case 0x42:
      case 0x43:
      case 0x44:
      case 0x45:
      case 0x47:
      case 0x48:
      case 0x49:
      case 0x4a:
      case 0x4b:
      case 0x4c:
      case 0x4d:
      case 0x4f:
      case 0x50:
      case 0x51:
      case 0x52:
      case 0x53:
      case 0x54:
      case 0x55:
      case 0x57:
      case 0x58:
      case 0x59:
      case 0x5a:
      case 0x5b:
      case 0x5c:
      case 0x5d:
      case 0x5f:
      case 0x60:
      case 0x61:
      case 0x62:
      case 0x63:
      case 0x64:
      case 0x65:
      case 0x67:
      case 0x68:
      case 0x69:
      case 0x6a:
      case 0x6b:
      case 0x6c:
      case 0x6d:
      case 0x6f:
      case 0x78:
      case 0x79:
      case 0x7a:
      case 0x7b:
      case 0x7c:
      case 0x7d:
      case 0x7f: ldrr(opcode);                    break;

      case 0x46:
      case 0x4e:
      case 0x56:
      case 0x5e:
      case 0x66:
      case 0x6e:
      case 0x7e: ldrhl(opcode);                   break;

      case 0x70:
      case 0x71:
      case 0x72:
      case 0x73:
      case 0x74:
      case 0x75:
      case 0x77: ldhlr(opcode);                   break;

      case 0x76: halt();                          break;

      case 0x80:
      case 0x81:
      case 0x82:
      case 0x83:
      case 0x84:
      case 0x85:
      case 0x87: addar(opcode);                   break;

      case 0x86: addahl();                        break;

      case 0x88:
      case 0x89:
      case 0x8a:
      case 0x8b:
      case 0x8c:
      case 0x8d:
      case 0x8f: adcar(opcode);                   break;

      case 0x8e: adcahl();                        break;

      case 0x90:
      case 0x91:
      case 0x92:
      case 0x93:
      case 0x94:
      case 0x95:
      case 0x97: subar(opcode);                   break;

      case 0x96: subahl();                        break;

      case 0x98:
      case 0x99:
      case 0x9a:
      case 0x9b:
      case 0x9c:
      case 0x9d:
      case 0x9f: sbcar(opcode);                   break;

      case 0x9e: sbcahl();                        break;

      case 0xa0:
      case 0xa1:
      case 0xa2:
      case 0xa3:
      case 0xa4:
      case 0xa5:
      case 0xa7: andar(opcode);                   break;

      case 0xa6: andahl();                        break;

      case 0xa8:
      case 0xa9:
      case 0xaa:
      case 0xab:
      case 0xac:
      case 0xad:
      case 0xaf: xorar(opcode);                   break;

      case 0xae: xorahl();                        break;

      case 0xb0:
      case 0xb1:
      case 0xb2:
      case 0xb3:
      case 0xb4:
      case 0xb5:
      case 0xb7: orar(opcode);                    break;

      case 0xb6: orahl();                         break;

      case 0xb8:
      case 0xb9:
      case 0xba:
      case 0xbb:
      case 0xbc:
      case 0xbd:
      case 0xbf: cpar(opcode);                    break;

      case 0xbe: cpahl();                         break;

      case 0xc0:
      case 0xc8:
      case 0xd0:
      case 0xd8:
      case 0xe0:
      case 0xe8:
      case 0xf0:
      case 0xf8: retcc(opcode);                   break;

      case 0xc1:
      case 0xd1:
      case 0xe1:
      case 0xf1: popqq(opcode);                   break;

      case 0xc2:
      case 0xca:
      case 0xd2:
      case 0xda:
      case 0xe2:
      case 0xea:
      case 0xf2:
      case 0xfa: jpccnn(opcode, fetchWord());     break;

      case 0xc3: jpnn(fetchWord());               break;

      case 0xc4:
      case 0xcc:
      case 0xd4:
      case 0xdc:
      case 0xe4:
      case 0xec:
      case 0xf4:
      case 0xfc: callccnn(opcode, fetchWord());   break;

      case 0xc5:
      case 0xd5:
      case 0xe5:
      case 0xf5: pushqq(opcode);                  break;

      case 0xc6: addan(fetchByte());              break;

      case 0xc7:
      case 0xcf:
      case 0xd7:
      case 0xdf:
      case 0xe7:
      case 0xef:
      case 0xf7:
      case 0xff: rstp(opcode);                    break;

      case 0xc9: ret();                           break;

      case 0xcb: executeCB(fetchOpCode(), false); break;

      case 0xcd: callnn(fetchWord());             break;

      case 0xce: adcan(fetchByte());              break;

      case 0xd3: outna(fetchByte());              break;

      case 0xd6: suban(fetchByte());              break;

      case 0xd9: exx();                           break;

      case 0xdb: inan(fetchByte());               break;

      case 0xdd: executeDDFD(true);               break;  // No Interrupts during sequence!!

      case 0xde: sbcan(fetchByte());              break;

      case 0xe3: exsphl();                        break;

      case 0xe6: andan(fetchByte());              break;

      case 0xe9: jphl();                          break;

      case 0xeb: exdehl();                        break;

      case 0xed: executeED(fetchOpCode());        break;

      case 0xee: xoran(fetchByte());              break;

      case 0xf3: di();                            break;

      case 0xf6: oran(fetchByte());               break;

      case 0xf9: ldsphl();                        break;

      case 0xfb: ei();                            break;

      case 0xfd: executeDDFD(false);              break;  // No Interrupts during sequence!!

      case 0xfe: cpan(fetchByte());               break;

      default:
        throw new RuntimeException("Invalid Opcode: " + Util.hex((byte)opcode));
    }
  }

  protected void executeCB(int opcode, boolean ixiy) {
    R++;
    int result = -1;
    switch(ixiy ? (opcode & 0xf8) | 0x06 : opcode) {
      case 0x00:
      case 0x01:
      case 0x02:
      case 0x03:
      case 0x04:
      case 0x05:
      case 0x07: rlcr(opcode);                    break;

      case 0x06: result = rlchl();                break;

      case 0x08:
      case 0x09:
      case 0x0a:
      case 0x0b:
      case 0x0c:
      case 0x0d:
      case 0x0f: rrcr(opcode);                    break;

      case 0x0e: result = rrchl();                break;

      case 0x10:
      case 0x11:
      case 0x12:
      case 0x13:
      case 0x14:
      case 0x15:
      case 0x17: rlr(opcode);                     break;

      case 0x16: result = rlhl();                 break;

      case 0x18:
      case 0x19:
      case 0x1a:
      case 0x1b:
      case 0x1c:
      case 0x1d:
      case 0x1f: rrr(opcode);                     break;

      case 0x1e: result = rrhl();                 break;

      case 0x20:
      case 0x21:
      case 0x22:
      case 0x23:
      case 0x24:
      case 0x25:
      case 0x27: slar(opcode);                    break;

      case 0x26: result = slahl();                break;

      case 0x28:
      case 0x29:
      case 0x2a:
      case 0x2b:
      case 0x2c:
      case 0x2d:
      case 0x2f: srar(opcode);                    break;

      case 0x2e: result = srahl();                break;

      case 0x30:
      case 0x31:
      case 0x32:
      case 0x33:
      case 0x34:
      case 0x35:
      case 0x37: sllr(opcode);                    break;

      case 0x36: result = sllhl();                break;

      case 0x38:
      case 0x39:
      case 0x3a:
      case 0x3b:
      case 0x3c:
      case 0x3d:
      case 0x3f: srlr(opcode);                    break;

      case 0x3e: result = srlhl();                break;

      case 0x40:
      case 0x41:
      case 0x42:
      case 0x43:
      case 0x44:
      case 0x45:
      case 0x47:
      case 0x48:
      case 0x49:
      case 0x4a:
      case 0x4b:
      case 0x4c:
      case 0x4d:
      case 0x4f:
      case 0x50:
      case 0x51:
      case 0x52:
      case 0x53:
      case 0x54:
      case 0x55:
      case 0x57:
      case 0x58:
      case 0x59:
      case 0x5a:
      case 0x5b:
      case 0x5c:
      case 0x5d:
      case 0x5f:
      case 0x60:
      case 0x61:
      case 0x62:
      case 0x63:
      case 0x64:
      case 0x65:
      case 0x67:
      case 0x68:
      case 0x69:
      case 0x6a:
      case 0x6b:
      case 0x6c:
      case 0x6d:
      case 0x6f:
      case 0x70:
      case 0x71:
      case 0x72:
      case 0x73:
      case 0x74:
      case 0x75:
      case 0x77:
      case 0x78:
      case 0x79:
      case 0x7a:
      case 0x7b:
      case 0x7c:
      case 0x7d:
      case 0x7f: bitbr(opcode);                   break;

      case 0x46:
      case 0x4e:
      case 0x56:
      case 0x5e:
      case 0x66:
      case 0x6e:
      case 0x76:
      case 0x7e: bitbhl(opcode);                  break;

      case 0x80:
      case 0x81:
      case 0x82:
      case 0x83:
      case 0x84:
      case 0x85:
      case 0x87:
      case 0x88:
      case 0x89:
      case 0x8a:
      case 0x8b:
      case 0x8c:
      case 0x8d:
      case 0x8f:
      case 0x90:
      case 0x91:
      case 0x92:
      case 0x93:
      case 0x94:
      case 0x95:
      case 0x97:
      case 0x98:
      case 0x99:
      case 0x9a:
      case 0x9b:
      case 0x9c:
      case 0x9d:
      case 0x9f:
      case 0xa0:
      case 0xa1:
      case 0xa2:
      case 0xa3:
      case 0xa4:
      case 0xa5:
      case 0xa7:
      case 0xa8:
      case 0xa9:
      case 0xaa:
      case 0xab:
      case 0xac:
      case 0xad:
      case 0xaf:
      case 0xb0:
      case 0xb1:
      case 0xb2:
      case 0xb3:
      case 0xb4:
      case 0xb5:
      case 0xb7:
      case 0xb8:
      case 0xb9:
      case 0xba:
      case 0xbb:
      case 0xbc:
      case 0xbd:
      case 0xbf: resbr(opcode);                   break;

      case 0x86:
      case 0x8e:
      case 0x96:
      case 0x9e:
      case 0xa6:
      case 0xae:
      case 0xb6:
      case 0xbe: result = resbhl(opcode);         break;

      case 0xc0:
      case 0xc1:
      case 0xc2:
      case 0xc3:
      case 0xc4:
      case 0xc5:
      case 0xc7:
      case 0xc8:
      case 0xc9:
      case 0xca:
      case 0xcb:
      case 0xcc:
      case 0xcd:
      case 0xcf:
      case 0xd0:
      case 0xd1:
      case 0xd2:
      case 0xd3:
      case 0xd4:
      case 0xd5:
      case 0xd7:
      case 0xd8:
      case 0xd9:
      case 0xda:
      case 0xdb:
      case 0xdc:
      case 0xdd:
      case 0xdf:
      case 0xe0:
      case 0xe1:
      case 0xe2:
      case 0xe3:
      case 0xe4:
      case 0xe5:
      case 0xe7:
      case 0xe8:
      case 0xe9:
      case 0xea:
      case 0xeb:
      case 0xec:
      case 0xed:
      case 0xef:
      case 0xf0:
      case 0xf1:
      case 0xf2:
      case 0xf3:
      case 0xf4:
      case 0xf5:
      case 0xf7:
      case 0xf8:
      case 0xf9:
      case 0xfa:
      case 0xfb:
      case 0xfc:
      case 0xfd:
      case 0xff: setbr(opcode);                   break;

      case 0xc6:
      case 0xce:
      case 0xd6:
      case 0xde:
      case 0xe6:
      case 0xee:
      case 0xf6:
      case 0xfe: result = setbhl(opcode);         break;

      default:
        throw new RuntimeException("Invalid Opcode: CB " + opcode);
    }
    if (ixiy && ((opcode & 0x07) != 0x06) && ((opcode & 0xc0) != 0x40)) {
      // Undocumented DD CB and FD CB opcodes LD r,....
      int r = opcode & 0x07;
      reg[r] = result;
    }
  }

  protected void executeED(int opcode) {
    R++;
    switch(opcode) {
      case 0x40:
      case 0x48:
      case 0x50:
      case 0x58:
      case 0x60:
      case 0x68:
      case 0x78: inrc(opcode);                    break;

      case 0x41:
      case 0x49:
      case 0x51:
      case 0x59:
      case 0x61:
      case 0x69:
      case 0x79: outcr(opcode);                   break;

      case 0x42:
      case 0x52:
      case 0x62:
      case 0x72: sbchlss(opcode);                 break;


      case 0x43:
      case 0x53:
      case 0x63:
      case 0x73: ldxxdd(opcode, fetchWord());     break;

      case 0x44:
      case 0x4c:
      case 0x54:
      case 0x5c:
      case 0x64:
      case 0x6c:
      case 0x74:
      case 0x7c: neg();                           break;

      case 0x45:
      case 0x55:
      case 0x65:
      case 0x75: retn();                          break;

      case 0x46:
      case 0x4e:
      case 0x66:
      case 0x6e: imn(0);                          break;

      case 0x47: ldia();                          break;

      case 0x4a:
      case 0x5a:
      case 0x6a:
      case 0x7a: adchlss(opcode);                 break;

      case 0x4b:
      case 0x5b:
      case 0x6b:
      case 0x7b: ldddxx(opcode, fetchWord());     break;

      case 0x4d:
      // TODO: Check these could be RETN
      case 0x5d:
      case 0x6d:
      case 0x7d: reti();                          break;

      case 0x4f: ldra();                          break;

      case 0x56:
      case 0x76: imn(1);                          break;

      case 0x57: ldai();                          break;

      case 0x5e:
      case 0x7e: imn(2);                          break;

      case 0x5f: ldar();                          break;

      case 0x67: rrd();                           break;

      case 0x6f: rld();                           break;

      case 0x70: inc();                           break;

      case 0x71: outc0();                         break;

      case 0xa0: ldi();                           break;

      case 0xa1: cpi();                           break;

      case 0xa2: ini();                           break;

      case 0xa3: outi();                          break;

      case 0xa8: ldd();                           break;

      case 0xa9: cpd();                           break;

      case 0xaa: ind();                           break;

      case 0xab: outd();                          break;

      case 0xb0: ldir();                          break;

      case 0xb1: cpir();                          break;

      case 0xb2: inir();                          break;

      case 0xb3: otir();                          break;

      case 0xb8: lddr();                          break;

      case 0xb9: cpdr();                          break;

      case 0xba: indr();                          break;

      case 0xbb: otdr();                          break;

      default:   nop();                           break;
    }
  }

  protected void executeDDFD(boolean ix) {
    int opcode;
    do {
      opcode = fetchOpCode();
      if (opcode == 0xdd)
        ix = true;
      else if (opcode == 0xfd)
        ix = false;
      else
        break;
    } while(true);

    int ixiy = ix ? IX : IY;
    switch(opcode) {
      case 0x09:
      case 0x19:
      case 0x21:
      case 0x22:
      case 0x23:
      case 0x24:
      case 0x25:
      case 0x26:
      case 0x29:
      case 0x2a:
      case 0x2b:
      case 0x2c:
      case 0x2d:
      case 0x2e:
      case 0x39:
      case 0x44:
      case 0x45:
      case 0x4c:
      case 0x4d:
      case 0x54:
      case 0x55:
      case 0x5c:
      case 0x5d:
      case 0x60:
      case 0x61:
      case 0x62:
      case 0x63:
      case 0x65:
      case 0x67:
      case 0x68:
      case 0x69:
      case 0x6a:
      case 0x6b:
      case 0x6c:
      case 0x6f:
      case 0x7c:
      case 0x7d:
      case 0x84:
      case 0x85:
      case 0x8c:
      case 0x8d:
      case 0x94:
      case 0x95:
      case 0x9c:
      case 0x9d:
      case 0xa4:
      case 0xa5:
      case 0xac:
      case 0xad:
      case 0xb4:
      case 0xb5:
      case 0xbc:
      case 0xbd:
      case 0xe1:
      case 0xe3:
      case 0xe5:
      case 0xe9:
      case 0xf9: ixiy = swapDDFD(ixiy, opcode);                 break;

      case 0x34:
      case 0x35:
      case 0x46:
      case 0x4e:
      case 0x56:
      case 0x5e:
      case 0x70:
      case 0x71:
      case 0x72:
      case 0x73:
      case 0x77:
      case 0x7e:
      case 0x86:
      case 0x8e:
      case 0x96:
      case 0x9e:
      case 0xa6:
      case 0xae:
      case 0xb6:
      case 0xbe: indexDDFD(ixiy, opcode);                       break;

      case 0x36: indexDDFD(ixiy, opcode);                       break;

      case 0xcb: indexDDFDCB(ixiy);                             break;

      case 0x66:
      case 0x6e: ldrixiyd(ixiy, opcode);                        break;

      case 0x74:
      case 0x75: ldixiydr(ixiy, opcode);                        break;
      
      default: executeNormal(opcode);                           break;

    }
    if (ix)
      IX = ixiy;
    else
      IY = ixiy;
  }

  protected int swapDDFD(int ixiy, int opcode) {
    int hl = getqq(HL);
    setqq(HL, ixiy);
    executeNormal(opcode);
    ixiy = getqq(HL);
    setqq(HL, hl);
    return ixiy;
  }

  protected void indexDDFD(int ixiy, int opcode) {
    int hl = getqq(HL);
    setqq(HL, MEMPTR = (ixiy + (byte)fetchByte()) & 0xffff);
    executeNormal(opcode);
    setqq(HL, hl);
  }

  protected void indexDDFDCB(int ixiy) {
    int hl = getqq(HL);
    setqq(HL, MEMPTR = (ixiy + (byte)fetchByte()) & 0xffff);
    executeCB(fetchOpCode(), true);
    setqq(HL, hl);
  }

  protected void ldrixiyd(int ixiy, int opcode) {
    R++;
    int r = (opcode & 0x38) >> 3;
    reg[r] = readByte(MEMPTR = (ixiy + (byte)fetchByte()) & 0xffff);
  }

  protected void ldixiydr(int ixiy, int opcode) {
    R++;
    int r = opcode & 0x07;
    writeByte(MEMPTR = (ixiy + (byte)fetchByte()) & 0xffff, reg[r]);
  }

  protected void stopHalt() {
    if (inHalt) {
      inHalt = false;
      PC = (PC + 1) & 0xffff;
    }
  }

  public void nmi() {
    IFF1 = false;   // RETN will copy value back from IFF2
    stopHalt();
    callnn(0x0066);
  }

  protected void doInterrupt() {
    interruptExecute = false;
    stopHalt();
    if (interruptDevice != null)
      interruptDevice.setInterrupt(1);
    interruptNotify();

    // Do two wait cycles
    cycle();
    cycle();
    
    IFF1 = IFF2 = false;
    switch(IM) {
      case 0:
        // TODO: Change fetcher!!
        executeNormal(interruptVector);
        // And replace it!!
        break;

      case 1:
        executeNormal(0xff);
        break;

      case 2:
        push(PC);
        MEMPTR = PC = readWord((I << 8) | interruptVector);
        break;
    }
  }

  protected void interruptNotify() { }

  protected int fetchWord() {
    // This is a normal memory read with the fetcher
    int lsb = fetchByte();
    return lsb | (fetchByte() << 8);
  }

  protected int fetchOpCode() {
    // TODO: Really should set M1 low, MREQ low, Addr Bus = PC
    cycle();
    // TODO: Check WAIT is high
    cycle();
    // TODO: Then swap to M1 high, MREQ high (half cycle), RFSH low, Addr Bus = Refresh Addr
    int result = fetcher.fetch();
    cycle();
    interruptExecute = (interruptPending != 0) && IFF1;  // Done here for convenience
    cycle();
    return result;
  }

  protected int fetchByte() {
    // TODO: MREQ low, Addr Bus = PC
    cycle();
    // TODO: Check WAIT is high
    cycle();
    int result = fetcher.fetch();
    // TODO: Then swap MREQ high (half cycle)
    interruptExecute = (interruptPending != 0) && IFF1;
    cycle();
    return result;
  }

  // =============================================================
  // Op-Code functions.
  // =============================================================

  // -------------------------------------------------------------
  // 8-Bit Load Group.
  // -------------------------------------------------------------

  protected void ldrr(int opcode) {
    int r = (opcode & 0x38) >> 3;
    int r1 = opcode & 0x07;
    reg[r] = reg[r1];
  }

  protected void ldrn(int opcode, int n) {
    int r = (opcode & 0x38) >> 3;
    reg[r] = n;
  }

  protected void ldrhl(int opcode) {
    int r = (opcode & 0x38) >> 3;
    reg[r] = readByte(getqq(HL));
  }

  protected void ldhlr(int opcode) {
    int r = opcode & 0x07;
    writeByte(getqq(HL), reg[r]);
  }

  protected void ldhln(int n) {
    writeByte(getqq(HL), n);
  }

  protected void ldabc() {
    int addr = getqq(BC);
    reg[A] = readByte(addr);
    MEMPTR = addr + 1;
  }

  protected void ldade() {
    int addr = getqq(DE);
    reg[A] = readByte(addr);
    MEMPTR = addr + 1;
  }

  protected void ldaxx(int xx) {
    reg[A] = readByte(xx);
    MEMPTR = xx + 1;
  }

  protected void ldbca() {
    int addr = getqq(BC);
    int a = reg[A];
    writeByte(addr, a);
    MEMPTR = ((addr + 1) & 0xff) | (a << 8);
  }

  protected void lddea() {
    int addr = getqq(DE);
    int a = reg[A];
    writeByte(addr, a);
    MEMPTR = ((addr + 1) & 0xff) | (a << 8);
  }

  protected void ldxxa(int xx) {
    int a = reg[A];
    writeByte(xx, a);
    MEMPTR = ((xx + 1) & 0xff) | (a << 8);
  }

  protected void ldai() {
    ldair(I);
  }

  protected void ldar() {
    ldair(R & 0x7f | R7);
  }

  protected void ldia() {
    I = reg[A];
  }

  protected void ldra() {
    R = reg[A];
    R7 = reg[A] & 0x80;
  }

  // -------------------------------------------------------------
  // 16-Bit Load Group.
  // -------------------------------------------------------------

  protected void ldddnn(int opcode, int nn) {
    int dd = (opcode & 0x30) >> 3;
    setdd(dd, nn);
  }

  protected void ldhlxx(int xx) {
    setqq(HL, readWord(xx));
    MEMPTR = xx + 1;
  }

  protected void ldddxx(int opcode, int xx) {
    int dd = (opcode & 0x30) >> 3;
    setdd(dd, readWord(xx));
    MEMPTR = xx + 1;
  }

  protected void ldxxhl(int xx) {
    writeWord(xx, getdd(HL));
    MEMPTR = xx + 1;
  }

  protected void ldxxdd(int opcode, int xx) {
    int dd = (opcode & 0x30) >> 3;
    writeWord(xx, getdd(dd));
    MEMPTR = xx + 1;
  }

  protected void ldsphl() {
    SP = getqq(HL);
  }

  protected void pushqq(int opcode) {
    int qq = (opcode & 0x30) >> 3;
    push(getqq(qq));
  }

  protected void popqq(int opcode) {
    int qq = (opcode & 0x30) >> 3;
    setqq(qq, pop());
  }

  // -------------------------------------------------------------
  // Exchange, Block Transfer, Block Search Group.
  // -------------------------------------------------------------

  protected void exdehl() {
    int temp = reg[D];
    reg[D] = reg[H];
    reg[H] = temp;
    temp = reg[E];
    reg[E] = reg[L];
    reg[L] = temp;
  }

  protected void exafaf1() {
    int temp = reg[A];
    reg[A] = reg[A1];
    reg[A1] = temp;
    temp = reg[F];
    reg[F] = reg[F1];
    reg[F1] = temp;
  }

  protected void exx() {
    for (int i = B; i <= L; i++) {
      int temp = reg[i];
      reg[i] = reg[i + 8];
      reg[i + 8] = temp;
    }
  }

  protected void exsphl() {
    int data = readWord(SP);
    writeWord(SP, getqq(HL));
    setqq(HL, MEMPTR = data);
  }

  protected void ldi() {
    int de = getqq(DE);
    int hl = getqq(HL);
    int n = readByte(hl++);
    writeByte(de++, n);
    endldi(de, hl, n);
  }

  protected void ldir() {
    ldi();
    if ((reg[F] & FPV) != 0) {
      PC = (PC - 2) & 0xffff;
      MEMPTR = PC + 1;
    }
  }

  protected void ldd() {
    int de = getqq(DE);
    int hl = getqq(HL);
    int n = readByte(hl--);
    writeByte(de--, n);
    endldi(de, hl, n);
  }

  protected void lddr() {
    ldd();
    if ((reg[F] & FPV) != 0) {
      PC = (PC - 2) & 0xffff;
      MEMPTR = PC + 1;
    }
  }

  protected void cpi() {
    cpid(1);
  }

  protected void cpir() {
    cpid(1);
    if ((reg[F] & (FPV | FZ)) == FPV) {
      PC = (PC - 2) & 0xffff;
      MEMPTR = PC + 1;
    }
  }

  protected void cpd() {
    cpid(-1);
  }

  protected void cpdr() {
    cpid(-1);
    if ((reg[F] & (FPV | FZ)) == FPV) {
      PC = (PC - 2)  & 0xffff;
      MEMPTR = PC + 1;
    }
  }

  // -------------------------------------------------------------
  // 8-Bit Arithmetic and Logical Group.
  // -------------------------------------------------------------

  protected void addar(int opcode) {
    int r = opcode & 0x07;
    addan(reg[r]);
  }

  protected void addahl() {
    addan(readByte(getqq(HL)));
  }

  protected void adcar(int opcode) {
    int r = opcode & 0x07;
    adcan(reg[r]);
  }

  protected void adcahl() {
    adcan(readByte(getqq(HL)));
  }

  protected void subar(int opcode) {
    int r = opcode & 0x07;
    suban(reg[r]);
  }

  protected void subahl() {
    suban(readByte(getqq(HL)));
  }

  protected void sbcar(int opcode) {
    int r = opcode & 0x07;
    sbcan(reg[r]);
  }

  protected void sbcahl() {
    sbcan(readByte(getqq(HL)));
  }

  protected void andar(int opcode) {
    int r = opcode & 0x07;
    andan(reg[r]);
  }

  protected void andan(int n) {
    int a = reg[A] = reg[A] & n;
    int f = a & 0xa8 | FH | PARITY[a];
    reg[F] = a == 0 ? f | FZ : f;
  }

  protected void andahl() {
    andan(readByte(getqq(HL)));
  }

  protected void orar(int opcode) {
    int r = opcode & 0x07;
    oran(reg[r]);
  }

  protected void oran(int n) {
    int a = reg[A] = reg[A] | n;
    int f = a & 0xa8 | PARITY[a];
    reg[F] = a == 0 ? f | FZ : f;
  }

  protected void orahl() {
    oran(readByte(getqq(HL)));
  }

  protected void xorar(int opcode) {
    int r = opcode & 0x07;
    xoran(reg[r]);
  }

  protected void xoran(int n) {
    int a = reg[A] = reg[A] ^ n;
    int f = a & 0xa8 | PARITY[a];
    reg[F] = a == 0 ? f | FZ : f;
  }

  protected void xorahl() {
    xoran(readByte(getqq(HL)));
  }

  protected void cpar(int opcode) {
    int r = opcode & 0x07;
    cpan(reg[r]);
  }

  protected void cpan(int n) {
    int a = reg[A];
    int result = a - n;
    int f = (result & FS) | FN | (n & (F5 | F3));
    if (result < 0)
      f |= FC;
    result &= 0xff;
    if (((a ^ n) & 0x80) != 0 && ((result ^ a) & 0x80) != 0)
      f |= FPV;
    if ((a & 0x0f) - (n & 0x0f) < 0)
      f |= FH;
    reg[F] = result == 0 ? f | FZ : f;
  }

  protected void cpahl() {
    cpan(readByte(getqq(HL)));
  }

  protected void incr(int opcode) {
    int r = (opcode & 0x38) >> 3;
    reg[r] = incn(reg[r]);
  }

  protected void incchl() {
    int hl = getqq(HL);
    writeByte(hl, incn(readByte(hl)));
  }

  protected void decr(int opcode) {
    int r = (opcode & 0x038) >> 3;
    reg[r] = decn(reg[r]);
  }

  protected void decchl() {
    int hl = getqq(HL);
    writeByte(hl, decn(readByte(hl)));
  }

  // -------------------------------------------------------------
  // General Purpose Arithmetic and CPU Control Groups.
  // -------------------------------------------------------------

  // TODO: Extensive testing on this instruction
  protected void daa() {
    int a = reg[A];
    int f = reg[F];
    int lsn = a & 0x0f;
    int add;
    add = (lsn > 9) || ((f & FH) != 0) ? 0x06 : 0;
    if ((f & FC) != 0 || a > 0x99) {
      f |= FC;
      add |= 0x60;
    }
    if ((f & FN) != 0)
      suban(add);
    else
      addan(add);
    reg[F] = reg[F] & (~(FC | FPV)) | (f & FC) | PARITY[reg[A]];
  }

  protected void cpl() {
    int a = reg[A] = ~reg[A] & 0xff;
    reg[F] = reg[F] & FLAG_MASK_CPL | (a & (F5 | F3)) | FH | FN;
  }

  protected void neg() {
    int a = reg[A];
    reg[A] = 0;
    suban(a);
  }

  protected void ccf() {
    int f = reg[F];
    reg[F] = ((f & FLAG_MASK_CCF) ^ FC) | (reg[A] & (F5 | F3)) | ((f & FC) == 0 ? 0 : FH);
  }

  protected void scf() {
    reg[F] = (reg[F] & FLAG_MASK_SCF) | (reg[A] & (F5 | F3)) | FC;
  }

  protected void nop() { }

  protected void halt() {
    inHalt = true;
    PC = (PC - 1) & 0xffff;
  }

  public void di() {
    interruptExecute = IFF1 = IFF2 = false;
  }

  public void ei() {
    IFF1 = IFF2 = true;
    interruptExecute = false;
  }

  protected void imn(int n) {
    IM = n;
  }

  // -------------------------------------------------------------
  // 16-Bit Arithmetic Group.
  // -------------------------------------------------------------

  protected void addhlss(int opcode) {
    int ss = (opcode & 0x30) >> 3;
    int hl = getqq(HL);
    MEMPTR = hl + 1;
    int n = getdd(ss);
    int result = hl + n;
    int f = (reg[F] & FLAG_MASK_ADDHL) | ((result >> 8) & (F5 | F3));
    if ((result & 0x10000) != 0)
      f |= FC;
    if ((hl & 0xfff) + (n & 0xfff) > 0xfff)
      f |= FH;
    setqq(HL, result);
    reg[F] = f;
  }

  protected void adchlss(int opcode) {
    int ss = (opcode & 0x30) >> 3;
    int hl = getqq(HL);
    MEMPTR = hl + 1;
    int n = getdd(ss);
    int c = reg[F] & FC;
    int result = hl + n + c;
    int f = (result >> 8) & (FS | F5 | F3);
    if ((result & 0x10000) != 0)
      f |= FC;
    if ((hl & 0xfff) + (n & 0xfff) + c > 0xfff)
      f |= FH;
    if (((hl ^ n) & 0x8000) == 0 && ((result ^ hl) & 0x8000) != 0)
      f |= FPV;
    setqq(HL, result &= 0xffff);
    reg[F] = result == 0 ? f | FZ : f;
  }

  protected void sbchlss(int opcode) {
    int ss = (opcode & 0x30) >> 3;
    int hl = getqq(HL);
    MEMPTR = hl + 1;
    int n = getdd(ss);
    int c = reg[F] & FC;
    int result = hl - n - c;
    int f = (result >> 8) & (FS | F5 | F3) | FN;
    if (result < 0)
      f |= FC;
    if ((hl & 0xfff) - (n & 0xfff) - c < 0)
      f |= FH;
    if (((hl ^ n) & 0x8000) != 0 && ((result ^ hl) & 0x8000) != 0)
      f |= FPV;
    setqq(HL, result &= 0xffff);
    reg[F] = result == 0 ? f | FZ : f;
  }

  protected void incss(int opcode) {
    int ss = (opcode & 0x30) >> 3;
    setdd(ss, (getdd(ss) + 1) & 0xffff);
  }

  protected void decss(int opcode) {
    int ss = (opcode & 0x30) >> 3;
    setdd(ss, (getdd(ss) - 1) & 0xffff);
  }

  // -------------------------------------------------------------
  // Rotate and Shift Group.
  // -------------------------------------------------------------

  protected void rlca() {
    int a = reg[A];
    int c = (a & 0x80) == 0 ? 0 : 1;
    reg[A] = a = ((a << 1) | c) & 0xff;
    reg[F] = (reg[F] & FLAG_MASK_RLCA) | (a & (F5 | F3)) | c;
  }

  protected void rla() {
    int a = reg[A];
    int f = reg[F];
    a = (a << 1) | (f & FC);
    f = (f & FLAG_MASK_RLCA) | (a & (F5 | F3));
    reg[A] = a & 0xff;
    reg[F] = (a & 0x100) != 0 ? f | FC : f;
  }

  protected void rrca() {
    int a = reg[A];
    int c = a & 0x01;
    reg[A] = a = ((a >> 1) | (c << 7)) & 0xff;
    reg[F] = (reg[F] & FLAG_MASK_RLCA) | (a & (F5 | F3)) | c;
  }

  protected void rra() {
    int a = reg[A];
    int f = reg[F];
    int c = a & 0x01;
    reg[A] = a = ((f & FC) == 0 ? a >> 1 : (a >> 1) | 0x80) & 0xff;
    reg[F] = (reg[F] & FLAG_MASK_RLCA) | (a & (F5 | F3)) | c;
  }

  protected void rlcr(int opcode) {
    int r = opcode & 0x07;
    reg[r] = rlcn(reg[r]);
  }

  protected int rlchl() {
    int hl = getqq(HL);
    return writeByte(hl, rlcn(readByte(hl)));
  }

  protected void rlr(int opcode) {
    int r = opcode & 0x07;
    reg[r] = rln(reg[r]);
  }

  protected int rlhl() {
    int hl = getqq(HL);
    return writeByte(hl, rln(readByte(hl)));
  }

  protected void rrcr(int opcode) {
    int r = opcode & 0x07;
    reg[r] = rrcn(reg[r]);
  }

  protected int rrchl() {
    int hl = getqq(HL);
    return writeByte(hl, rrcn(readByte(hl)));
  }

  protected void rrr(int opcode) {
    int r = opcode & 0x07;
    reg[r] = rrn(reg[r]);
  }

  protected int rrhl() {
    int hl = getqq(HL);
    return writeByte(hl, rrn(readByte(hl)));
  }

  protected void slar(int opcode) {
    int r = opcode & 0x07;
    reg[r] = slan(reg[r]);
  }

  protected int slahl() {
    int hl = getqq(HL);
    return writeByte(hl, slan(readByte(hl)));
  }

  protected void sllr(int opcode) {
    int r = opcode & 0x07;
    reg[r] = slln(reg[r]);
  }

  protected int sllhl() {
    int hl = getqq(HL);
    return writeByte(hl, slln(readByte(hl)));
  }

  protected void srar(int opcode) {
    int r = opcode & 0x07;
    reg[r] = sran(reg[r]);
  }

  protected int srahl() {
    int hl = getqq(HL);
    return writeByte(hl, sran(readByte(hl)));
  }

  protected void srlr(int opcode) {
    int r = opcode & 0x07;
    reg[r] = srln(reg[r]);
  }

  protected int srlhl() {
    int hl = getqq(HL);
    return writeByte(hl, srln(readByte(hl)));
  }

  // TODO: Extensive testing on this instruction
  protected void rld() {
    int a = reg[A];
    int hl = getqq(HL);
    MEMPTR = hl + 1;
    int b = readByte(hl);
    writeByte(hl, ((b << 4) | (a & 0x0f)) & 0xff);
    reg[A] = a = (a & 0xf0) | ((b >> 4) & 0x0f);
    int f = (reg[F] & FLAG_MASK_RLD) | (a & (FS | F5 | F3)) | PARITY[a];
    reg[F] = a == 0 ? f | FZ : f;
  }

  // TODO: Extensive testing on this instruction
  protected void rrd() {
    int a = reg[A];
    int hl = getqq(HL);
    MEMPTR = hl + 1;
    int b = readByte(hl);
    writeByte(hl, ((a << 4) & 0xf0) | ((b >> 4) & 0x0f));
    reg[A] = a = (a & 0xf0) | (b & 0x0f);
    int f = (reg[F] & FLAG_MASK_RLD) | (a & (FS | F5 | F3)) | PARITY[a];
    reg[F] = a == 0 ? f | FZ : f;
  }

  // -------------------------------------------------------------
  // Bit Set, Reset and Test Group.
  // -------------------------------------------------------------

  protected void bitbr(int opcode) {
    int r = opcode & 0x07;
    bitbn(reg[r], opcode);
  }

  protected void bitbhl(int opcode) {
    int hl = getqq(HL);
    int b = (opcode & 0x38) >> 3;
    int n = readByte(hl) & (1 << b);
    int f = (reg[F] & FLAG_MASK_BIT) | (n & FS) | ((MEMPTR >> 8) & (F5 | F3)) | FH;
    reg[F] = n == 0 ? f | (FZ | FPV) : f;
  }

  protected void setbr(int opcode) {
    int r = opcode & 0x07;
    reg[r] = setbn(reg[r], opcode);
  }

  protected int setbhl(int opcode) {
    int hl = getqq(HL);
    return writeByte(hl, setbn(readByte(hl), opcode));
  }

  protected void resbr(int opcode) {
    int r = opcode & 0x07;
    reg[r] = resbn(reg[r], opcode);
  }

  protected int resbhl(int opcode) {
    int hl = getqq(HL);
    return writeByte(hl, resbn(readByte(hl), opcode));
  }

  // -------------------------------------------------------------
  // Jump Group.
  // -------------------------------------------------------------

  protected void jpnn(int nn) {
    MEMPTR = PC = nn;
  }

  protected static final int[] CC_MASK = { FZ, FZ, FC, FC, FPV, FPV, FS, FS };
  protected static final int[] CC_TEST = { 0, FZ, 0, FC, 0, FPV, 0, FS };

  protected void jpccnn(int opcode, int nn) {
    int cc = (opcode & 0x38) >> 3;
    if ((reg[F] & CC_MASK[cc]) == CC_TEST[cc])
      PC = nn;
    MEMPTR = nn;
  }

  protected void jre(byte e) {
    MEMPTR = PC = (PC + e) & 0xffff;
  }

  protected void jrce(byte e) {
    if ((reg[F] & FC) != 0)
      MEMPTR = PC = (PC + e) & 0xffff;
  }

  protected void jrnce(byte e) {
    if ((reg[F] & FC) == 0)
      MEMPTR = PC = (PC + e) & 0xffff;
  }

  protected void jrze(byte e) {
    if ((reg[F] & FZ) != 0)
      MEMPTR = PC = (PC + e) & 0xffff;
  }

  protected void jrnze(byte e) {
    if ((reg[F] & FZ) == 0)
      MEMPTR = PC = (PC + e) & 0xffff;
  }

  protected void jphl() {
    PC = getqq(HL);
  }

  protected void djnze(byte e) {
    int b = reg[B] = (reg[B] - 1) & 0xff;
    if (b != 0)
      MEMPTR = PC = (PC + e) & 0xffff;
  }

  // -------------------------------------------------------------
  // Call and Return Group.
  // -------------------------------------------------------------

  protected void callnn(int nn) {
    push(PC);
    MEMPTR = PC = nn;
  }

  protected void callccnn(int opcode, int nn) {
    int cc = (opcode & 0x38) >> 3;
    if ((reg[F] & CC_MASK[cc]) == CC_TEST[cc]) {
      push(PC);
      PC = nn;
    }
    MEMPTR = nn;
  }

  protected void ret() {
    MEMPTR = PC = pop();
  }

  protected void retcc(int opcode) {
    int cc = (opcode & 0x38) >> 3;
    if ((reg[F] & CC_MASK[cc]) == CC_TEST[cc])
      MEMPTR = PC = pop();
  }

  protected void reti() {
    MEMPTR = PC = pop();
    IFF1 = IFF2;
  }

  protected void retn() {
    MEMPTR = PC = pop();
    IFF1 = IFF2;
  }

  protected void rstp(int opcode) {
    push(PC);
    MEMPTR = PC = opcode & 0x38;
  }

  // -------------------------------------------------------------
  // Input and Output Groups.
  // -------------------------------------------------------------

  protected void inan(int n) {
    int addr = reg[A] << 8 | n;
    reg[A] = in(addr);
    MEMPTR = addr + 1;
  }

  protected void inrc(int opcode) {
    int r = (opcode & 0x38) >> 3;
    int bc = getqq(BC);
    int result = reg[r] = in(bc);
    int f = (reg[F] & FLAG_MASK_IN) | (result & (FS | F5 | F3)) | PARITY[result];
    reg[F] = result == 0 ? f | FZ : f;
    MEMPTR = bc + 1;
  }

  protected void inc() {
    int bc = getqq(BC);
    int result = in(bc);
    int f = (reg[F] & FLAG_MASK_IN) | (result & (FS | F5 | F3)) | PARITY[result];
    reg[F] = result == 0 ? f | FZ : f;
    MEMPTR = bc + 1;
  }

  protected void ini() {
    inid(1);
  }

  protected void inir() {
    inid(1);
    if ((reg[F] & FZ) == 0) {
      PC = (PC - 2) & 0xffff;
    }
  }

  protected void ind() {
    inid(-1);
  }

  protected void indr() {
    inid(-1);
    if ((reg[F] & FZ) == 0) {
      PC = (PC - 2) & 0xffff;
    }
  }

  protected void outna(int n) {
    int a = reg[A];
    int a8 = a << 8;
    out(a8 | n, a);
    MEMPTR = ((n + 1) & 0xff) | a8;
  }

  protected void outcr(int opcode) {
    int r = (opcode & 0x38) >> 3;
    int bc = getqq(BC);
    out(bc, reg[r]);
    MEMPTR = bc + 1;
  }

  protected void outc0() {
    int bc = getqq(BC);
    out(bc, 0);
    MEMPTR = bc + 1;
  }

  protected void outi() {
    outid(1);
  }

  protected void otir() {
    outid(1);
    if ((reg[F] & FZ) == 0) {
      PC = (PC - 2) & 0xffff;
    }
  }

  protected void outd() {
    outid(-1);
  }

  protected void otdr() {
    outid(-1);
    if ((reg[F] & FZ) == 0) {
      PC = (PC - 2) & 0xffff;
    }
  }

  // =============================================================
  // Extra Op-Code Utility functions.
  // =============================================================

  protected void endldi(int de, int hl, int n) {
    setqq(DE, de & 0xffff);
    setqq(HL, hl & 0xffff);
    int bc = (getqq(BC) - 1) & 0xffff;
    setqq(BC, bc);
    n += reg[A];
    int f = reg[F] & FLAG_MASK_LDIR | ((n << 4) & F5) | (n & F3);
    reg[F] = bc != 0 ? f | FPV : f;
  }

  protected void cpid(int add) {
    int hl = getqq(HL);
    int a = reg[A];
    int n = readByte(hl);
    int result = (a - n) & 0xff;
    int f = (reg[F] & FC) | (result & FS) | FN;
    if (result == 0)
      f |= FZ;
    if ((a & 0x0f) - (n & 0x0f) < 0) {
      f |= FH;
      result--;
    }
    hl = (hl + add) & 0xffff;
    setqq(HL, hl);
    int bc = (getqq(BC) - 1) & 0xffff;
    setqq(BC, bc);
    reg[F] = (bc != 0 ? f | FPV : f) | ((result << 4) & F5) | (result & F3);
    MEMPTR += add;
  }

  protected void ldair(int a) {
    reg[A] = a;
    int f = (a & 0xa8) | (reg[F] & FC);
    if (IFF2)
      f |= FPV;
    reg[F] = a == 0 ? f | FZ : f;
  }

  // TODO: Extensive testing of the PV flag
  protected void adcan(int n) {
    int a = reg[A];
    int c = reg[F] & FC;
    int result = a + n + c;
    int f = result & (FS | F3 | F5);
    if ((result & 0x100) != 0)
      f |= FC;
    reg[A] = (result &= 0xff);
    // TODO: Is carry included in the first part of this test?
    if (((a ^ n) & 0x80) == 0 && ((result ^ a) & 0x80) != 0)
      f |= FPV;
    if ((a & 0x0f) + (n & 0x0f) + c > 0x0f)
      f |= FH;
    reg[F] = result == 0 ? f | FZ : f;
  }

  protected void addan(int n) {
    int a = reg[A];
    int result = a + n;
    int f = result & (FS | F3 | F5);
    if ((result & 0x100) != 0)
      f |= FC;
    reg[A] = (result &= 0xff);
    // TODO: Is carry included in the first part of this test?
    if (((a ^ n) & 0x80) == 0 && ((result ^ a) & 0x80) != 0)
      f |= FPV;
    if ((a & 0x0f) + (n & 0x0f) > 0x0f)
      f |= FH;
    reg[F] = result == 0 ? f | FZ : f;
  }

  // TODO: Extensive testing of the PV flag
  protected void sbcan(int n) {
    int a = reg[A];
    int c = reg[F] & FC;
    int result = a - n - c;
    int f = result & (FS | F3 | F5) | FN;
    if (result < 0)
      f |= FC;
    reg[A] = (result &= 0xff);
    // TODO: Is carry included in the first part of this test?
    if (((a ^ n) & 0x80) != 0 && ((result ^ a) & 0x80) != 0)
      f |= FPV;
    if ((a & 0x0f) - (n & 0x0f) - c < 0)
      f |= FH;
    reg[F] = result == 0 ? f | FZ : f;
  }

  // TODO: Extensive testing of the PV flag
  protected void suban(int n) {
    int a = reg[A];
    int result = a - n;
    int f = result & (FS | F3 | F5) | FN;
    if (result < 0)
      f |= FC;
    reg[A] = (result &= 0xff);
    // TODO: Is carry included in the first part of this test?
    if (((a ^ n) & 0x80) != 0 && ((result ^ a) & 0x80) != 0)
      f |= FPV;
    if ((a & 0x0f) - (n & 0x0f) < 0)
      f |= FH;
    reg[F] = result == 0 ? f | FZ : f;
  }

  protected int incn(int n) {
    n = (n + 1) & 0xff;
    int f = n & 0xa8 | (reg[F] & FC);
    if ((n & 0x0f) == 0)
      f |= FH;
    if (n == 0x80)
      f |= FPV;
    else if (n == 0)
      f |= FZ;
    reg[F] = f;
    return n;
  }

  protected int decn(int n) {
    n = (n - 1) & 0xff;
    int f = n & 0xa8 | (reg[F] & FC) | FN;
    if ((n & 0x0f) == 0x0f)
      f |= FH;
    if (n == 0x7f)
      f |= FPV;
    else if (n == 0)
      f |= FZ;
    reg[F] = f;
    return n;
  }

  protected int rlcn(int n) {
    int c = (n & 0x80) == 0 ? 0 : 1;
    n = ((n << 1) | c) & 0xff;
    int f = (n & (FS | F5 | F3)) | c | PARITY[n];
    reg[F] = n == 0 ? f | FZ : f;
    return n;
  }

  protected int rln(int n) {
    int f = reg[F];
    n = (n << 1) | (f & FC);
    f = (n & (FS | F5 | F3));
    if ((n & 0x100) != 0)
      f |= FC;
    n &= 0xff;
    reg[F] = (n == 0 ? f | FZ : f) | PARITY[n];
    return n;
  }

  protected int rrcn(int n) {
    int c = n & 0x01;
    n = ((n >> 1) | (c << 7)) & 0xff;
    int f = (n & (FS | F5 | F3)) | c | PARITY[n];
    reg[F] = n == 0 ? f | FZ : f;
    return n;
  }

  protected int rrn(int n) {
    int f = reg[F];
    int c = n & 0x01;
    n = ((f & FC) == 0 ? n >> 1 : (n >> 1) | 0x80) & 0xff;
    f = (n & (FS | F5 | F3)) | c | PARITY[n];
    reg[F] = n == 0 ? f | FZ : f;
    return n;
  }

  protected int slan(int n) {
    n <<= 1;
    int f = n & (FS | F5 | F3);
    if ((n & 0x100) != 0)
      f |= FC;
    n &= 0xff;
    reg[F] = (n == 0 ? f | FZ : f) | PARITY[n];
    return n;
  }

  protected int slln(int n) {
    n = (n << 1) | 1;
    int f = n & (FS | F5 | F3);
    if ((n & 0x100) != 0)
      f |= FC;
    n &= 0xff;
    reg[F] = (n == 0 ? f | FZ : f) | PARITY[n];
    return n;
  }

  protected int sran(int n) {
    int c = n & 0x01;
    n = (n >> 1) | (n & 0x80);
    int f = (n & (FS | F5 | F3)) | c | PARITY[n];
    reg[F] = n == 0 ? f | FZ : f;
    return n;
  }

  protected int srln(int n) {
    int c = n & 0x01;
    n >>= 1;
    int f = (n & (FS | F5 | F3)) | c | PARITY[n];
    reg[F] = n == 0 ? f | FZ : f;
    return n;
  }

  protected void bitbn(int n, int opcode) {
    int b = (opcode & 0x38) >> 3;
    n &= (1 << b);
    int f = (reg[F] & FLAG_MASK_BIT) | (n & (FS | F5 | F3)) | FH;
    reg[F] = n == 0 ? f | (FZ | FPV) : f;
  }

  protected int setbn(int n, int opcode) {
    int b = (opcode & 0x38) >> 3;
    return n | (0x01 << b);
  }

  protected int resbn(int n, int opcode) {
    int b = (opcode & 0x38) >> 3;
    return n & ~(0x01 << b);
  }

  protected void inid(int add) {
    int hl = getqq(HL);
    int b = reg[B];
    int c = reg[C];
    int bc = (b << 8) | c;
    int result = in(bc);
    writeByte(hl, result);
    setqq(HL, (hl + add) & 0xffff);
    // TODO: This is from Z80 CPU Specifications by Sean Young
    c = ((((c + add) & 0xff) + result) & 0x100) != 0 ? FC | FH : 0;
    reg[B] = b = decn(b);
    // TODO: The documentation is not clear about the parity result
    reg[F] = (reg[F] & FLAG_MASK_INI) | c | PARITY[result] | ((result & 0x80) >> 6);
    MEMPTR = bc + add;
  }

  protected void outid(int add) {
    int hl = getqq(HL);
    int b = reg[B] = decn(reg[B]);
    int addr = (b << 8) | reg[C];
    out(addr, readByte(hl));
    setqq(HL, (hl + add) & 0xffff);
    // TODO: Check other flags in these instructions
    MEMPTR = addr + add;
  }

  // =============================================================
  // Utility functions.
  // =============================================================

  protected int getqq(int index) {
    return index == AF ? reg[index] | (reg[index + 1] << 8) :
      reg[index + 1] | (reg[index] << 8);
  }

  protected void setqq(int index, int value) {
    if (index == AF) {
      reg[index] = value & 0xff;
      reg[index + 1] = (value >> 8) & 0xff;
    }
    else {
      reg[index + 1] = value & 0xff;
      reg[index] = (value >> 8) & 0xff;
    }
  }

  protected int getdd(int index) {
    return index == AF ? SP : reg[index + 1] | (reg[index] << 8);
  }

  protected void setdd(int index, int value) {
    if (index == AF)
      SP = value & 0xffff;
    else {
      reg[index + 1] = value & 0xff;
      reg[index] = (value >> 8) & 0xff;
    }
  }

  protected int pop() {
    int result = readWord(SP);
    SP = (SP + 2) & 0xffff;
    return result;
  }

  protected void push(int data) {
    SP = (SP - 2) & 0xffff;
    writeWord(SP, data);
  }

  public String getState() {
    String result =
      "AF :" + Util.hex((short)getqq(AF)) + " HL :" + Util.hex((short)getqq(HL)) +
      " DE :" + Util.hex((short)getqq(DE)) + " BC :" + Util.hex((short)getqq(BC)) +
      " IX :" + Util.hex((short)IX) + " IY :" + Util.hex((short)IY) + "\n" +
      "AF':" + Util.hex((byte)reg[A1]) + Util.hex((byte)reg[F1]) + " HL':" + Util.hex((short)getqq(H1)) +
      " DE':" + Util.hex((short)getqq(D1)) + " BC':" + Util.hex((short)getqq(B1)) +
      " Cycles: " + Util.hex((int)cycles);
    return result;
  }
  
  protected static final Register[] REGISTERS = {
    new Register("Flags",8,"SZ-H-VNC"),
    new Register("AF", 16), new Register("AF'", 16, 1),
    new Register("HL", 16), new Register("HL'", 16, 1),
    new Register("DE", 16), new Register("DE'", 16, 1),
    new Register("BC", 16), new Register("BC'", 16, 1),
    new Register("IX", 16), new Register("SP", 16, 1),
    new Register("IY", 16), new Register("I", 8, 1),
    new Register("PC", 16), new Register("R", 8, 1)
  };

  @Override
  public Register[] getRegisters() {
    return REGISTERS;
  }

  @Override
  public int getRegisterValue(int index) {
    int result;
    switch(index) {
      case 0:  result = reg[F];                   break;
      case 1:  result = getqq(AF);                break;
      case 2:  result = reg[F1] | (reg[A1] << 8); break;
      case 3:  result = getqq(HL);                break;
      case 4:  result = getqq(H1);                break;
      case 5:  result = getqq(DE);                break;
      case 6:  result = getqq(D1);                break;
      case 7:  result = getqq(BC);                break;
      case 8:  result = getqq(B1);                break;
      case 9:  result = IX;                       break;
      case 10: result = SP;                       break;
      case 11: result = IY;                       break;
      case 12: result = I;                        break;
      case 13: result = PC;                       break;
      case 14: result = (R & 0x7f) | R7;          break;

      default: result = 0;
    }
    return result;
  }

  public int getProgramCounter() {
    return PC;
  }

  public void setAF(int value) {
    setqq(AF, value);
  }

  public void setBC(int value) {
    setqq(BC, value);
  }

  public void setDE(int value) {
    setqq(DE, value);
  }

  public void setHL(int value) {
    setqq(HL, value);
  }

  public void setR(int value) {
    R = value & 0xff;
    R7 = value & 0x80;
  }

  public void setI(int value) {
    I = value & 0xff;
  }

  public void setIFF1(boolean value) {
    IFF1 = value;
  }

  public void setIFF2(boolean value) {
    IFF2 = value;
  }

  public void setIX(int value) {
    IX = value & 0xffff;
  }

  public void setIY(int value) {
    IY = value & 0xffff;
  }

  public void setSP(int value) {
    SP = value & 0xffff;
  }

  public int getPC() {
    return PC;
  }

  public void setPC(int value) {
    PC = value & 0xffff;
  }

  public int getMEMPTR() {
    return MEMPTR & 0xffff;
  }

  public void setIM(int value) {
    IM = value & 0xff;
  }

  public void setAF1(int value) {
    reg[F1] = value & 0xff;
    reg[A1] = (value >> 8) & 0xff;
  }

  public void setBC1(int value) {
    setqq(B1, value);
  }

  public void setDE1(int value) {
    setqq(D1, value);
  }

  public void setHL1(int value) {
    setqq(H1, value);
  }

  public boolean isInHalt() {
    return inHalt;
  }

  public class Fetcher {

    public int fetch() {
      int result = readByte(PC);
      PC = (PC + 1) & 0xffff;
      return result;
    }
    
  }

  public class InterruptFetcher extends Fetcher {

    @Override
    public int fetch() {
      return readByte(PC);
    }
    
  }

}
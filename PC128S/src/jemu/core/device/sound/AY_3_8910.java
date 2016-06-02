package jemu.core.device.sound;

import jemu.core.device.*;

/**
 * Title:        JEMU
 * Description:  The Java Emulation Platform
 * Copyright:    Copyright (c) 2002
 * Company:
 * @author
 * @version 1.0
 */

public class AY_3_8910 extends SoundDevice {

  public static final int BDIR_MASK = 0x04;
  public static final int BC2_MASK  = 0x02;
  public static final int BC1_MASK  = 0x01;

  public static final int PORT_A     = 0;
  public static final int PORT_B     = 1;

  // Possible states
  protected static final int INACTIVE = 0;
  protected static final int LATCH    = 1;
  protected static final int READ     = 2;
  protected static final int WRITE    = 3;

  protected static final int[] STATES = {
    INACTIVE, LATCH, INACTIVE, READ, LATCH, INACTIVE, WRITE, LATCH
  };
  
  // Registers
  protected static final int AFINE       = 0;
  protected static final int ACOARSE     = 1;
  protected static final int BFINE       = 2;
  protected static final int BCOARSE     = 3;
  protected static final int CFINE       = 4;
  protected static final int CCOARSE     = 5;
  protected static final int NOISEPERIOD = 6;
  protected static final int ENABLE      = 7;
  protected static final int AVOL        = 8;
  protected static final int BVOL        = 9;
  protected static final int CVOL        = 10;
  protected static final int EFINE       = 11;
  protected static final int ECOARSE     = 12;
  protected static final int ESHAPE      = 13;
  protected static final int REG_PORTA   = 14;
  protected static final int REG_PORTB   = 15;
  
  // Bits of ENABLE register
  protected static final int ENABLE_A    = 0x01;
  protected static final int ENABLE_B    = 0x02;
  protected static final int ENABLE_C    = 0x04;
  protected static final int NOISE_A     = 0x08;
  protected static final int NOISE_B     = 0x10;
  protected static final int NOISE_C     = 0x20;
  protected static final int PORT_A_OUT  = 0x40;
  protected static final int PORT_B_OUT  = 0x80;
  
  protected static final int NOISE_ALL = NOISE_A | NOISE_B | NOISE_C;
  
  // Sound Channels (inc Noise and Envelope)
  protected static final int A        = 0;
  protected static final int B        = 1;
  protected static final int C        = 2;
  protected static final int NOISE    = 3;
  protected static final int ENVELOPE = 4;

  protected int[] regs = new int[16];
  protected int selReg = 0;
  protected int bdirBC2BC1 = 0;
  protected int state = INACTIVE;
  protected int clockSpeed = 1000000;
  protected IOPort[] ports = new IOPort[] {
    new IOPort(IOPort.READ), new IOPort(IOPort.READ)
  };
  protected int divide;

  protected int periodA, periodB, periodC, periodNoise, periodEnv;
  protected int countA, countB, countC, countNoise, countEnv;
  protected int outputA, outputB, outputC, outputNoise;
  protected int volumeA, volumeB, volumeC, volumeEnv;
  protected boolean envA, envB, envC;
  protected int outA, outB, outC;
  protected int random;
  protected int envStep, hold, alternate, attack, holding;

  protected int[] levels = { 0, 1, 2, 3, 5, 7, 10, 17, 23, 32, 45, 57, 72, 90, 107, 127 };
    //{ 0, 8, 16, 24, 32, 40, 48, 56, 64, 72, 80, 88, 96, 104, 112, 120 };

  public AY_3_8910() {
    super("AY-3-8910/2/3 Programmable Sound Generator");
    player = SoundUtil.getSoundPlayer(true);
    player.setFormat(SoundUtil.UPCM8);
    setClockSpeed(1000000);
    reset();
  }
  
  public void setClockSpeed(int value) {
    clockSpeed = value;
  }

  @Override
  public void reset() {
    divide = 15;
    selReg = 0;
    random = 1;
    outputA = outputB = outputC = 0;
    outputNoise = 1;
    countA = countB = countC = countNoise = countEnv = 0;
    outA = outB = outC = 0;
    for (int i = 0; i < 14; i++)
      setRegister(i, 0);
  }

  public void setSelectedRegister(int value) {
    selReg = value & 0x0f;
  }

  public void setBDIR_BC2_BC1(int value, int dataValue) {
    if (bdirBC2BC1 != value) {
      bdirBC2BC1 = value;
      state = STATES[bdirBC2BC1];
      writePort(0,dataValue);
    }
  }

  @Override
  public int readPort(int port) {
    return state == READ ? readRegister(selReg) : 0xff;
  }

  @Override
  public void writePort(int port, int value) {
    switch(state) {
      case LATCH: selReg = value & 0x0f;              break;
      case WRITE: setRegister(selReg,value);          break;
    }
  }

  public int getRegister(int index) {
    return regs[index];
  }

  public int readRegister(int index) {
    return index < REG_PORTA ? regs[index] : ports[index - REG_PORTA].read();
  }

  public void setRegister(int index, int value) {
    if (regs[index] != value) {
      if (index < REG_PORTA) {
        if (index == ESHAPE || regs[index] != value) {
          regs[index] = value;
          switch(index) {
            case ACOARSE:
            case AFINE:       periodA = (regs[ACOARSE] & 0x0f) << 8 | regs[AFINE]; break;

            case BCOARSE:
            case BFINE:       periodB = (regs[BCOARSE] & 0x0f) << 8 | regs[BFINE]; break;

            case CCOARSE:
            case CFINE:       periodC = (regs[CCOARSE] & 0x0f) << 8 | regs[CFINE]; break;
            
            case NOISEPERIOD: periodNoise = value & 0x1f; break;
            
            case ENABLE:      break;
            
            case AVOL:        volumeA = value & 0x0f; envA = (value & 0x10) != 0; break;

            case BVOL:        volumeB = value & 0x0f; envB = (value & 0x10) != 0; break;

            case CVOL:        volumeC = value & 0x0f; envC = (value & 0x10) != 0; break;
            
            case EFINE:
            case ECOARSE:     periodEnv = ((regs[ECOARSE] << 8) | regs[EFINE]) << 1; break;
            
            case ESHAPE: {
              attack = (value & 0x04) == 0 ? 0 : 0x0f;
              if ((value & 0x08) == 0) {
                hold = 1;
                alternate = attack;
              }
              else {
                hold = value & 0x01;
                alternate = value & 0x02;
              }
              envStep = 0x0f;
              countEnv = 0x0f;
              holding = 0;
              volumeEnv = attack ^ 0x0f;
              break;
            }
          }
        }
      }
      else
        ports[index - REG_PORTA].write(value);
    }
  }

  @Override
  public void cycle() {
    if (divide == 0) {
      divide = 7;

      if (++countA >= periodA) {
        countA = 0;
        outputA ^= 1;
      }

      if (++countB >= periodB) {
        countB = 0;
        outputB ^= 1;
      }

      if (++countC >= periodC) {
        countC = 0;
        outputC ^= 1;
      }

      if (++countNoise >= periodNoise) {
        countNoise = 0;
        if (((random + 1) & 2) != 0)
          outputNoise ^= 1;

        if ((random & 1) != 0)
          random ^= 0x24000;
        random >>= 1;
      }

      int enable = regs[ENABLE];
      int enableA = (outputA | (enable & 0x01)) &        (outputNoise | ((enable >> 3) & 0x01));
      int enableB = (outputB | ((enable >> 1) & 0x01)) & (outputNoise | ((enable >> 4) & 0x01));
      int enableC = (outputC | ((enable >> 2) & 0x01)) & (outputNoise | ((enable >> 5) & 0x01));

      if (holding == 0) {
        if (++countEnv >= periodEnv) {
          countEnv = 0;

          if (envStep == 0) {
            if (hold != 0) {
              if (alternate != 0)
                attack ^= 0x0f;
              holding = 1;
              // envStep == 0 (Already)
            }
            else {
              if (alternate != 0) /* && (envStep & 0x10) != 0 - Original code always true */
                attack ^= 0x0f;
              envStep = 0x0f;
            }
          }
          else
            envStep--;
        }
      }
      volumeEnv = envStep ^ attack;

      outA = levels[enableA * (envA ? volumeEnv : volumeA)];
      outB = levels[enableB * (envB ? volumeEnv : volumeB)];
      outC = levels[enableC * (envC ? volumeEnv : volumeC)];
    }
    else
      divide--;
  }

  public void writeAudio() {
    player.writeStereo(outA + outB, outB + outC);
  }

  public void setReadDevice(int port, Device device, int readPort) {
    ports[port].setInputDevice(device,readPort);
  }

  public void setWriteDevice(int port, Device device, int writePort) {
    ports[port].setOutputDevice(device,writePort);
  }

}
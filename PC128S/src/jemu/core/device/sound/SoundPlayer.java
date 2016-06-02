package jemu.core.device.sound;

import jemu.core.device.*;

/**
 * An abstract class designed to play sound and provide emulation timing.
 *
 * @author Richard Wilson
 */
public abstract class SoundPlayer implements ComputerTimer {     // Probably should be an interface, but how efficient are they?
  
  protected int format = SoundUtil.ULAW;
  
  public int getClockAdder(int test, int cyclesPerSecond) {
    return (int)((long)test * (long)getSampleRate() / (long)cyclesPerSecond);
  }

  public abstract int getSampleRate();

  public abstract void writeMono(int value);

  public abstract void writeStereo(int a, int b);
  
  public abstract void play();
  
  public abstract void stop();
  
  public abstract void resync();
  
  public abstract void dispose();
  
  public void setFormat(int value) {
    format = value;
  }
  
  public int getFormat() {
    return format;
  }
  
}

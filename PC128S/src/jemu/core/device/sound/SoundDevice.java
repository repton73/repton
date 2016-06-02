package jemu.core.device.sound;

import jemu.core.device.*;

/**
 * A Sound Device is connected to a SoundPlayer to provide sound.
 *
 * @author Richard Wilson
 */
public class SoundDevice extends Device {
  
  protected SoundPlayer player;
  
  /** Creates a new instance of SoundDevice */
  public SoundDevice(String name) {
    super(name);
  }
  
  public SoundPlayer getSoundPlayer() {
    return player;
  }
  
}

package jemu.core.device.floppy;

/**
 * Provides an interface to listen for the active state of a Disc Drive.
 *
 * @author Richard Wilson
 */
public interface DriveListener {

  public void driveActiveChanged(Drive drive, boolean active);
  
}

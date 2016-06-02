package jemu.core.device;

/**
 * An interface to provide emulation timing for a Computer/Device.
 *
 * @author Richard Wilson
 */
public interface ComputerTimer {
  
  public long getCount();
  
  public long getUpdates();
  
  public long getDeviation();

  public long getRate();
  
  public void resync();
  
}

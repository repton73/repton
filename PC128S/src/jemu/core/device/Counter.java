package jemu.core.device;

/**
 * Provides a simple masked counter. Each time cycle() is called the value is
 * set to (value + increment) & mask
 *
 * @author Richard Wilson
 */
public class Counter extends Device {
  
  protected int mask;
  protected int increment;
  protected int count;
  protected int value;
  
  /** Creates a new instance of Counter */
  public Counter(int bits, boolean down) {
    super("Counter (" + bits + " bit " + (down ? "down)" : "up)"));
    mask = 0;
    for (int i = 0; i < bits; i++)
      mask = (mask << 1) | 0x00000001;
    increment = down ? -1 : 1;
  }
  
  public int getCount() {
    return count;
  }
  
  public void setCount(int value) {
    count = value;
  }
  
  public int getValue() {
    return value;
  }
  
  public void setValue(int value) {
    this.value = value;
  }
  
  public void cycle() {
    value = (value + increment) & mask;
  }
  
}

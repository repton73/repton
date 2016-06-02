package jemu.core.device;

/**
 * Describes a computer/machine.
 *
 * @author Richard Wilson
 */
public class ComputerDescriptor {
  
  public String key, name, className;
  public boolean shown;
  
  /** Creates a new instance of ComputerDescriptor */
  public ComputerDescriptor(String key, String name, String className, boolean shown) {
    this.key = key;
    this.name = name;
    this.className = className;
    this.shown = shown;
  }
  
  public String toString() {
    return name;
  }
  
}

package jemu.core.device;

/**
 * Describes an emulator file, and provides optional instructions.
 *
 * @author Richard Wilson
 */
public class FileDescriptor {
  
  public String description, filename, instructions;
  
  /** Creates a new instance of FileDescriptor */
  public FileDescriptor(String description, String filename, String instructions) {
    this.description = description;
    this.filename = filename;
    this.instructions = instructions;
  }
  
  public String toString() {
    return description;
  }
  
}

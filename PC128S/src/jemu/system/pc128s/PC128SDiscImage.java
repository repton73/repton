package jemu.system.pc128s;

import jemu.core.device.floppy.*;

/**
 * Provide a PC128S Single Sided (SSD) or
 * TODO: Double Sided (DSD) disc image.
 *
 * @author Richard Wilson
 */
public class PC128SDiscImage extends DiscImage {
  
  protected byte[][][][] sectors;
  
  /** Creates a new instance of PC128SDiscImage */
  public PC128SDiscImage(String name, byte[] data) {
    super(name);
    int side = data.length>320*1024?2:1;
    sectors = new byte[80][2][16][256];
    int index = 0;
    for (int c = 0; c < 80; c++) {
     for (int h = 0; h < side; h++) {
      for (int r = 0; r < 16; r++) {
        if (index < data.length) {
          int size = Math.min(256,data.length - index);
          System.arraycopy(data,index,sectors[c][h][r],0,size);
          index += size;
        }
      }
     }
    }
  }

  public byte[] readSector(int cylinder, int head, int c, int h, int r, int n) {
    return c >= 80 || r >= 16 ? null : sectors[c][h][r];
  }

  public int[] getSectorID(int cylinder, int head, int index) {
    return new int[] { 0, 0, 0, 0 };
  }

  public int getSectorCount(int cylinder, int head) {
    return cylinder >= sectors.length ? 0 : sectors[cylinder].length;
  }
  
}

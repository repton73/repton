package jemu.core.device.hdd;

import java.io.*;
import java.text.*;
import java.util.*;

/**
 * An IDE file is a hierarchical file containing sector information for a virtual hard disc drive.
 *
 * @author Richard Wilson
 * @version 1.0
 */
public class IDEFile {

  public static final byte[] IDENTIFIER = "BITWISE DRIVE IMAGE FILE".getBytes();

  // The Creator of an IDE file. This should be setup by JEMU
  public static String CREATOR     = "JEMU Version 1.0";

  public static String DESCRIPTION = "For IDE Emulation";

  // Current version is 1.0.0
  public static final int VER_MAJOR   = 1;
  public static final int VER_MINOR   = 0;
  public static final int VER_RELEASE = 0;

  // Maximum size in sectors
  public static final int MAX_SIZE       = 0x10000000;
  public static final int BLANK_VALUE    = 0x0000ffff;
  public static final long MAX_MAP_VALUE = 0xffff0000L;
  public static final int SECTOR_SIZE    = 512;
  public static final int MAP_SIZE       = 128;  // 512 / Size of DWORD
  public static final int MAX_DEPTH      = 4;
  public static final int FREE_LIST_MAX  = 128;

  public static final DateFormat CREATE_FORMAT = new SimpleDateFormat("yyyyMMdd hhmmss");

  protected RandomAccessFile file;
  protected boolean readOnly;
  protected long size;
  protected long offset;

  // The Header Structure (512 bytes)
  // 0000: ID (IDE_HEAD value above) (24 bytes)
  // 0018: Version Major (1)
  // 001C: Version Minor (0)
  // 0020: Version Release (0)
  // 0024: Maximum Size
  // 0028: Blank Value
  // 002C: Unused (4 bytes)
  // 0030: Create Time (16 bytes)
  // 0040: Creator (64 bytes)
  // 0080: Author (64 bytes)
  // 00C0: Description (64 bytes)
  // 0100: Unused (256 bytes)

  protected byte[] header = new byte[512];  // Same as SECTOR_SIZE

  // Which maps are cached?
  protected long[] cached = new long[MAX_DEPTH];

  // Cached LBA Maps
  protected byte[][] cache = new byte[MAX_DEPTH][SECTOR_SIZE];

  // Free List
  protected long[] freeList = new long[MAP_SIZE];
  protected long nextFree;
  protected int freeSize;

  // Each sector is 512 Bytes
  // A Map sector is 128 DWORD values (512 bytes)

  public IDEFile(File source, boolean readOnly) throws Exception {
    this.readOnly = readOnly;
    file = new RandomAccessFile(source, readOnly ? "r" : "rws");
    try {
      int count = file.read(header);
      if (count == 0 && !readOnly) {
        for (int i = 0; i < header.length; i++)
          header[i] = 0;
        System.arraycopy(IDENTIFIER, 0, header, 0, IDENTIFIER.length);
        writeInt(header, 0x18, VER_MAJOR);
        writeInt(header, 0x1c, VER_MINOR);
        writeInt(header, 0x20, VER_RELEASE);
        writeInt(header, 0x24, MAX_SIZE);
        writeInt(header, 0x28, BLANK_VALUE);
        writeString(header, 0x30, 0x10, CREATE_FORMAT.format(new Date()));
        writeString(header, 0x40, 0x40, CREATOR);
        writeString(header, 0x80, 0x40, System.getProperty("user"));
        writeString(header, 0xc0, 0x40, DESCRIPTION);
        file.write(header);
        file.write(cache[0]);
      }
      else {
        if (count != SECTOR_SIZE || file.read(cache[0]) != SECTOR_SIZE)
          throw new Exception("Invalid IDE File");
        int verMajor = getInt(header, 0x18);
        if (verMajor < 1 || verMajor > VER_MAJOR)
          throw new Exception("Invalid IDE File Version");
      }
      size = getInt(header, 0x24) & 0xffffffffL;  // MAX_SIZE
      nextFree = file.length() / SECTOR_SIZE;
      freeSize = 0;
      cached[0] = 1;  // Index of first LBAMap after header
    } catch(Exception e) {
      file.close();
      throw e;
    }
  }

  public void destroy() throws IOException {
    try {
      if (!readOnly)
        compress();
    } catch(Exception e) {
      e.printStackTrace();
    } finally {
      file.close();
    }
  }

  protected void compress() throws Exception {
    if (freeSize > 0) {
      nextFree -= freeSize;
      int sz = 0;
      for (int i = 0; i < freeSize; i++)
        if (freeList[i] < nextFree)
          freeList[sz++] = freeList[i];
      freeSize = sz;
      if (sz > 0)
        compressBlock(1, cache[0], 1);
      file.setLength((long)nextFree * SECTOR_SIZE);
      if (freeSize != 0)
        throw new Exception("FreeList not fully cleared");
    }
  }

  protected void compressBlock(long block, byte[] map, int depth) throws IOException {
    boolean modified = false;
    byte[] sec = new byte[MAP_SIZE];
    for (int i = MAP_SIZE - 1; i >= 0; i--) {
      long current = getMap(map, i);
      if (current != 0 && current < MAX_MAP_VALUE) {
        byte[] p = sec;
        if (current >= nextFree) {
          long newBlk = freeList[--freeSize];
          if (depth < MAX_DEPTH && current == cached[depth]) {
            cached[depth] = newBlk;
            p = cache[depth];
          }
          else
            readBlock(current, p);
          current = newBlk & 0xffffffffL;
          writeBlock(current, p);
          writeMap(map, i, current);
          modified = true;
          if (freeSize == 0)
            break;
        }
        else if (depth < MAX_DEPTH) {
          if (current == cached[depth])
            p = cache[depth];
          readBlock(current, p);
        }
        if (depth < MAX_DEPTH) {
          compressBlock(current, p, depth + 1);
          if (freeSize == 0)
            break;
        }
      }
    }
    if (modified)
      writeBlock(block, map);
  }

  protected int readBlock(long block, byte[] data) throws IOException {
    file.seek(block * SECTOR_SIZE);
    return file.read(data, 0, SECTOR_SIZE);
  }

  protected void writeBlock(long block, byte[] data) throws IOException {
    file.seek(block * SECTOR_SIZE);
    file.write(data, 0, SECTOR_SIZE);
  }

  protected void checkCached(long block, int depth) throws IOException {
    if (cached[depth] != (int)block) {
      readBlock(block, cache[depth]);
      cached[depth] = (int)block;
    }
  }

  protected void fillSect(byte[] buffer, int value) {
    for (int i = 0; i < SECTOR_SIZE; i += 2) {
      buffer[i] = (byte)(value & 0xff);
      buffer[i + 1] = (byte)((value >> 8) & 0xff);
    }
  }

  public int readSector(byte[] buffer) throws IOException {
    if (offset >= size)
      return 0;
    int fill = getInt(header, 0x28);
    long block = getMap(cache[0], (int)(offset >> 21) & 0x7f);
    if (block != 0) {
      checkCached(block, 1);
      block = getMap(cache[1], (int)(offset >> 14) & 0x7f);
      if (block != 0) {
        checkCached(block, 2);
        block = getMap(cache[2], (int)(offset >> 7) & 0x7f);
        if (block != 0) {
          checkCached(block, 3);
          block = getMap(cache[3], (int)offset & 0x7f);
          if (block != 0) {
            if (block < MAX_MAP_VALUE) {
              offset++;
              return readBlock(block, buffer);
            }
            else
              fill = (int)block & 0xffff;
          }
        }
      }
    }
    fillSect(buffer, fill);
    offset++;
    return SECTOR_SIZE;
  }

  protected long checkSector(byte[] buffer) {
    byte val0 = buffer[0];
    byte val1 = buffer[1];
    for (int i = 2; i < SECTOR_SIZE; i += 2) {
      if (buffer[i] != val0 || buffer[i + 1] != val1)
        return 0;
    }
    return 0xffff0000L | (val0 & 0xff) | ((val1 << 8) & 0xff00);
  }

  protected boolean checkBlank(byte[] buffer) {
    for (int i = 0; i < SECTOR_SIZE; i++)
      if (buffer[i] != 0)
        return false;
    return true;
  }

  protected long getFree() {
    if (freeSize == 0)
      return nextFree++;
    else
      return freeList[--freeSize];
  }

  protected long zeroFree(byte[] buffer) throws IOException {
    long result = getFree();
    for (int i = 0; i < SECTOR_SIZE; i++)
      buffer[i] = 0;
    writeBlock(result, buffer);
    return result;
  }

  protected void freeBlock(long block) throws Exception {
    freeList[freeSize++] = block;
    if (freeSize == FREE_LIST_MAX)
      compress();
  }

  protected boolean updateCache(int depth, int index, long value) throws Exception {
    byte[] map = cache[depth];
    long prev = getMap(map, index);
    if (prev != value) {
      writeMap(map, index, value);
      writeBlock(cached[depth], map);
      if (value == 0 || value >= 0xffff0000L) {
        if (prev != 0 && prev < 0xffff0000L) {
          if (depth < 4 && cached[depth + 1] == prev)
            cached[depth + 1] = 0;
          freeBlock(prev);
        }
        return value != 0;
      }
    }
    return false;
  }

  protected void newMap(int depth, int index) throws Exception {
    long block = zeroFree(cache[depth]);
    cached[depth] = block;
    updateCache(depth - 1, index, block);
  }

  public int writeSector(byte[] buffer) throws Exception {
    if (readOnly || offset >= size)
      return 0;
    long value = checkSector(buffer);
    if (value < 0xffff0000L || (value & 0xffff) != (getInt(header, 0x28) & 0xffff)) {
      long block = getMap(cache[0], (int)(offset >> 21) & 0x7f);
      if (block == 0)
        newMap(1, (int)(offset >> 21) & 0x7f);
      else
        checkCached(block, 1);
      block = getMap(cache[1], (int)(offset >> 14) & 0x7f);
      if (block == 0)
        newMap(2, (int)(offset >> 14) & 0x7f);
      else
        checkCached(block, 2);
      block = getMap(cache[2], (int)(offset >> 7) & 0x7f);
      if (block == 0)
        newMap(3, (int)(offset >> 7) & 0x7f);
      else
        checkCached(block, 3);
      block = getMap(cache[3], (int)offset & 0x7f);
      if (value < 0xffff0000L) {
        if (block == 0 || block >= 0xffff0000L)
          value = getFree();
        else
          value = block;
        writeBlock(value, buffer);
      }
      updateCache(3, (int)offset & 0x7f, value);
    }
    else {
      long block = getMap(cache[0], (int)(offset >> 21) & 0x7f);
      if (block != 0) {
        checkCached(block, 1);
        block = getMap(cache[1], (int)(offset >> 14) & 0x7f);
        if (block != 0) {
          checkCached(block, 2);
          block = getMap(cache[2], (int)(offset >> 7) & 0x7f);
          if (block != 0) {
            checkCached(block, 3);
            int index = (int)offset & 0x7f;
            if (getMap(cache[3], index) != 0) {
              if (updateCache(3, index, 0) && checkBlank(cache[3])) {
                if (updateCache(2, (int)(offset >> 7) & 0x7f, 0) && checkBlank(cache[2])) {
                  if (updateCache(1, (int)(offset >> 14) & 0x7f, 0) && checkBlank(cache[1]))
                    updateCache(0, (int)(offset >> 21) & 0x7f, 0);
                }
              }
            }
          }
        }
      }
    }
    offset++;
    return SECTOR_SIZE;
  }

  /**
   * Write a Little-Endian int value to a byte array.
   *
   * @param dest The destination array
   * @param offset The offset in the array to write the value
   * @param value The value to write
   */
  protected void writeInt(byte[] dest, int offset, int value) {
    dest[offset] = (byte)(value & 0xff);
    dest[offset + 1] = (byte)((value >> 8) & 0xff);
    dest[offset + 2] = (byte)((value >> 16) & 0xff);
    dest[offset + 3] = (byte)((value >> 24) & 0xff);
  }

  protected void writeString(byte[] dest, int offset, int maxLength, String source) {
    if (source != null) {
      byte[] data = source.getBytes();
      System.arraycopy(data, 0, dest, offset, Math.min(maxLength, data.length));
    }
  }

  protected int getInt(byte[] map, int index) {
    return (map[index] & 0xff) | ((map[index + 1] & 0xff) << 8) | ((map[index + 2] & 0xff) << 16) |
      ((map[index + 3] & 0xff) << 24);
  }

  protected long getMap(byte[] map, int index) {
    return getInt(map, index * 4) & 0xffffffffL;
  }

  protected void writeMap(byte[] map, int index, long value) {
    writeInt(map, index * 4, (int)value);
  }
}

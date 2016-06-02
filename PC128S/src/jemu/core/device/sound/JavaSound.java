package jemu.core.device.sound;

import javax.sound.sampled.*;

/**
 * Provides Mono or Stereo sound using the javax.sound.sampled API.
 *
 * @author Richard Wilson
 */
public class JavaSound extends SunAudio {
  
  public static final int SAMPLE_RATE = 62500;
  
  protected static AudioFormat STEREO_FORMAT = new AudioFormat(SAMPLE_RATE, 8, 2, false, false);
  protected static AudioFormat MONO_FORMAT   = new AudioFormat(SAMPLE_RATE, 8, 1, false, false);
  
  protected SourceDataLine line;
  protected byte[] data;
  protected int offset = 0;
  protected int channels;
  protected long startCount;
  
  /** Creates a new instance of JavaSound.
   *
   * @samples Number of samples written to DataLine at a time. Keep low ~32
   * @stereo  true for Stereo, false for Mono
   */
  public JavaSound(int samples, boolean stereo) {
    super(samples, stereo);
  }
  
  @Override
  public int getSampleRate() {
    return SAMPLE_RATE;
  }
  
  @Override
  @SuppressWarnings("CallToThreadDumpStack")
  protected void init() {
    format = SoundUtil.UPCM8;
    channels = stereo ? 2 : 1;
    data = new byte[samples * channels];
    AudioFormat fmt = stereo ? STEREO_FORMAT : MONO_FORMAT;
    try {
      line = AudioSystem.getSourceDataLine(fmt);
      line.open(fmt, SAMPLE_RATE / 12 * channels);
      System.out.println("JavaSound: " + samples + " x " + channels);
      System.out.println("Line Buffer: " + line.getBufferSize() + " for " +
        line.getClass());
    } catch(Exception e) {
      e.printStackTrace();
    }
  }
  
  @Override
  public void resync() {
    line.flush();
    
    startCount = line.getLongFramePosition();
    int count = SAMPLE_RATE / 10 * channels;   // 1/10 sec (100 ms) delay
    while (count > 0) {
      int len = Math.min(data.length, count);
      line.write(data, 0, len);
      count -= len;
    }
    System.out.println("resync: start=" + startCount + " at " + System.currentTimeMillis());
  }

  @Override
  public long getUpdates() {
    /* if (offset != 0 && line.available() >= offset) {
      line.write(data, 0, offset);
      offset = 0;
    } */
    return super.getUpdates();
  }
  
  @Override
  public long getCount() {
    return line.getLongFramePosition() - startCount - (getDeviation() / 2);
  }
  
  @Override
  public long getDeviation() {
    return SAMPLE_RATE / 25;    // 100 ms
  }
  
  @Override
  public void play() {
    resync();
    line.start();
  }
  
  @Override
  public void stop() {
    line.stop();
  }
  
  @Override
  public void dispose()  {
    line.close();
  }
  
  @Override
  public void writeMono(int value) {
    switch(format) {
      case SoundUtil.ULAW:  data[offset] = SoundUtil.ulawToUPCM8((byte)value); break;
      case SoundUtil.UPCM8: data[offset] = (byte)value; break;
    }
    if (++offset == data.length) {
      line.write(data, 0, data.length);
      offset = 0;
    }
    updates++;
  }
  
  @Override
  public void writeStereo(int a, int b) {
    switch(format) {
      case SoundUtil.ULAW:
        data[offset] = SoundUtil.ulawToUPCM8((byte)a);
        data[offset + 1] = SoundUtil.ulawToUPCM8((byte)b);
        break;
        
      case SoundUtil.UPCM8:
        data[offset] = (byte)a;
        data[offset + 1] = (byte)b;
        break;
    }
    if ((offset += 2) == data.length) {
      line.write(data, 0, data.length);
      offset = 0;
    }
    updates++;
  }
  
}

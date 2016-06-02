package jemu.ui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.*;

/**
 * Title:        JEMU
 * Description:  The Java Emulation Platform
 * Copyright:    Copyright (c) 2002
 * Company:
 * @author
 * @version 1.0
 */

public class Display extends JComponent {
  
  public static final int CENTER = 0;
  
  public static final Dimension SCALE_1   = new Dimension(1,1);
  public static final Dimension SCALE_2   = new Dimension(2,2);
  public static final Dimension SCALE_1x2 = new Dimension(1,2);

  protected BufferedImage image;
  protected WritableRaster raster;
  protected int[] pixels;
  protected int imageWidth, imageHeight;
  protected int scaleWidth, scaleHeight;
  protected Rectangle imageRect = new Rectangle();
  protected Rectangle sourceRect = null;             // Source rectangle in image
  protected int imagePos = CENTER;
  protected boolean sameSize;
  protected boolean painted = false;

  public Display() {
    setDoubleBuffered(false);
    enableEvents(AWTEvent.FOCUS_EVENT_MASK);
    setFocusTraversalKeysEnabled(false);
    setRequestFocusEnabled(true);
  }

  public void setImageSize(Dimension size, Dimension scale) {
    imageWidth = size.width;
    imageHeight = size.height;
    image = new BufferedImage(imageWidth,imageHeight,BufferedImage.TYPE_INT_RGB);
    raster = image.getRaster();
    pixels = new int[imageWidth * imageHeight];
    for (int i = 0; i < pixels.length; i++)
      pixels[i] = 0xff000000;
    if (scale == null) scale = SCALE_1;
    scaleWidth = imageWidth * scale.width;
    scaleHeight = imageHeight * scale.height;
    checkSize();
    Graphics g = getGraphics();
    if (g != null) {
      size = getSize();
      g.setColor(getBackground());
      g.fillRect(0,0,size.width,size.height);
      paint(g);
      g.dispose();
    }
  }

  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x,y,width,height);
    checkSize();
  }

  protected void checkSize() {
    Dimension size = getSize();
    Insets insets = getInsets();
    int clientWidth = size.width - insets.left - insets.right;
    int clientHeight = size.height - insets.top - insets.bottom;
    if (true) //imagePos == CENTRE)
      imageRect = new Rectangle(insets.left + (clientWidth - scaleWidth) / 2,
        insets.top + (clientHeight - scaleHeight) / 2,scaleWidth,scaleHeight);
    else
      imageRect = new Rectangle(insets.left,insets.top,clientWidth,clientHeight);
    sameSize = imageRect.width == imageWidth && imageRect.height == imageHeight;
  }
  
  public int[] getPixels() {
    return pixels;
  }
  
  public void setSourceRect(Rectangle value) {
    sourceRect = value;
  }

  public void updateImage(boolean wait) {
    painted = false;
    if (imageRect.width != 0 && imageRect.height != 0 && isShowing()) {
      raster.setDataElements(0,0,imageWidth,imageHeight,pixels);
      repaint(0,imageRect.x,imageRect.y,imageRect.width,imageRect.height);
      if (wait)
        waitPainted();
    }
  }

  protected void paintImage(Graphics g) {
    if (sourceRect != null)
      g.drawImage(image,imageRect.x,imageRect.y,imageRect.x + imageRect.width,
        imageRect.y + imageRect.height,sourceRect.x,sourceRect.y,
        sourceRect.x + sourceRect.width,sourceRect.y + sourceRect.height,null);
    else if (sameSize)
      g.drawImage(image,imageRect.x,imageRect.y,null);
    else
      g.drawImage(image,imageRect.x,imageRect.y,imageRect.width,imageRect.height,null);
  }

  protected Rectangle paintRect = new Rectangle();
  protected Rectangle clipRect = new Rectangle();
  
  public void paintComponent(Graphics g) {
    if (image != null)
      paintImage(g);
    painted = true;
  }

  public Dimension getPreferredSize() {
    Insets insets = getInsets();
    return new Dimension(imageRect.width + insets.left + insets.right + 2,
      imageRect.height + insets.top + insets.bottom + 2);
  }

  public int getImageWidth() {
    return imageWidth;
  }

  public int getImageHeight() {
    return imageHeight;
  }

  public boolean isPainted() {
    return painted;
  }

  public void setPainted(boolean value) {
    painted = value;
  }

  public void waitPainted() {
    while (!painted && isShowing())
      Thread.yield();
  }

  protected void processFocusEvent(FocusEvent e) {
    super.processFocusEvent(e);
    if (e.getID() == FocusEvent.FOCUS_GAINED) {
      System.out.println("Display Focused");
    }
    else
      System.out.println("Display Lost Focus");
  }

}
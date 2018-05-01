package com.ibm.pilotbrief.verticalprofile;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;

public class UnpackedRPMLayer {
	
	public static int TILE_ZOOM_LEVEL = 2;
	public static int maxTileNumber = (int) Math.pow(2, TILE_ZOOM_LEVEL);
	public static int tileSize = 256;
	public static int totalTileSize = tileSize * maxTileNumber;
	
	public enum TurbulenceSeverity {
		NONE, LIGHT, OCCASIONAL, MODERATE, MODERATE_PLUS
	}
	
	public String layerId;
	
	public String layerName;
	
	public Long timestamp;
	
	public Long forecastTimestamp;
	
	public BufferedImage bitmap = new BufferedImage(256 * maxTileNumber, 256 * maxTileNumber, BufferedImage.TYPE_4BYTE_ABGR);
	
	public void addTile(Image image, int tileX, int tileY) {
		/**
		Graphics g2 = bitmap.getGraphics();
		g2.drawImage(image, (tileX * 256), (tileY * 256), (tileX * 256) + 255, (tileY * 256), 0, 0, 255, 255, null);
		**/
		int[] pixels = new int[256 * 256];
		PixelGrabber pg = new PixelGrabber(image, 0, 0, 256, 256, pixels, 0, 256);
		try {
			pg.grabPixels();
			for (int x = 0; x < 256; x++) {
				for (int y = 0; y < 256; y++) {
					int c = pixels[(y * 256) + x];
					bitmap.setRGB((tileX * 256) + x, (tileY * 256) + y, c);
				}
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}

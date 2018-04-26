package com.ibm.pilotbrief.verticalprofile;

import java.awt.Image;
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
	
	public TurbulenceSeverity[][] severityMap = new TurbulenceSeverity[256 * maxTileNumber][256 * maxTileNumber];
	
	public void addTile(Image image, int tileX, int tileY) {
		int[] pixels = new int[256 * 256];
		PixelGrabber pg = new PixelGrabber(image, 0, 0, 256, 256, pixels, 0, 256);
		try {
			pg.grabPixels();
			for (int x = 0; x < 256; x++) {
				for (int y = 0; y < 256; y++) {
					int c = pixels[(x * 256) + y];
					switch (c) {
					case 0xFFFFCD2E:
						severityMap[(tileX * 256) + x][(tileY * 256) + y] = TurbulenceSeverity.LIGHT;
						break;
					case 0xFFFF9C00:
						severityMap[(tileX * 256) + x][(tileY * 256) + y] = TurbulenceSeverity.OCCASIONAL;
						break;
					case 0xFFFF7701:
						severityMap[(tileX * 256) + x][(tileY * 256) + y] = TurbulenceSeverity.MODERATE;
						break;
					case 0xFFE24800:
						severityMap[(tileX * 256) + x][(tileY * 256) + y] = TurbulenceSeverity.MODERATE_PLUS;
						break;
					case 0:
						severityMap[(tileX * 256) + x][(tileY * 256) + y] = TurbulenceSeverity.NONE;
						break;
					default:
						System.out.format("BOGUS COLOR: %x\n", c);
					}
				}
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}

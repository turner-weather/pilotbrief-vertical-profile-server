package com.ibm.pilotbrief.verticalprofile;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.ibm.pilotbrief.verticalprofile.SSDSVersionFetcher.RPMLayer;
import com.ibm.pilotbrief.verticalprofile.SSDSVersionFetcher.SSDSUpdateSubscriber;

@WebListener
public class TurbulenceTileFetcher implements ServletContextListener {


	public void startUp() {
		final TurbulenceTileFetcher me = this;
		SSDSVersionFetcher.shared().subscribeToUpdates(new SSDSUpdateSubscriber() {
			@Override
			public void newVersionAvailable(Map<String, RPMLayer> layers) {
				me.fetchTiles(layers);
			}
		});
	}

	public static void main(String[] args) {
		TurbulenceTileFetcher fetcher = new TurbulenceTileFetcher();
		fetcher.startUp();
		while (true) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void contextInitialized(ServletContextEvent servletContextEvent) {
		this.startUp();
	}


	private final static TurbulenceTileFetcher  sharedInstance = new TurbulenceTileFetcher();

	public static TurbulenceTileFetcher shared() {
		return sharedInstance;
	}

	public void fetchTiles(Map<String, RPMLayer> layers) {
		try {
			for (RPMLayer layer : layers.values()) {
				for (Long fts : layer.forecastTimestamp) {
					System.out.format("Fetching layer: %s, FTS = %d\n", layer.layerName, fts);
					UnpackedRPMLayer rpmLayer = new UnpackedRPMLayer();
					for (int x = 0; x < UnpackedRPMLayer.maxTileNumber; x++) {
						CountDownLatch l = new CountDownLatch(UnpackedRPMLayer.maxTileNumber);
						for (int y = 0; y < UnpackedRPMLayer.maxTileNumber; y++) {
							final int myX = x;
							final int myY = y;
							Thread t = new Thread(new Runnable() {
								
								@Override
								public void run() {
									try {
										URI uri = new URI(String.format("https://%s/v3/TileServer/tile?product=%s&apiKey=%s&ts=%d&fts=%d&xyz=%d:%d:%d", 
												System.getenv("SSDS_ENDPOINT"),
												layer.layerId,
												System.getenv("SSDS_CREDENTIALS"),
												layer.timestamp,
												fts,
												myX,
												myY,
												UnpackedRPMLayer.TILE_ZOOM_LEVEL));
										HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
										conn.setRequestMethod("GET");
										conn.setRequestProperty("Connection", "keep-alive");
										conn.connect();
										BufferedInputStream in = new BufferedInputStream(conn.getInputStream()); 
										BufferedImage image = ImageIO.read(in);
										if ((image.getHeight() == 256) && (image.getWidth() == 256)) {
											rpmLayer.addTile(image, myX, myY);
										}
									} catch (Exception ex) {
											
									} finally {
										l.countDown();
									}
									
								}
							});
							t.start();
						}
						l.await();
					}
				}
			}
		}catch (Exception ex) {
			ex.printStackTrace();
		}
	}


}

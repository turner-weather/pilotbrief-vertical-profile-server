package com.ibm.pilotbrief.verticalprofile;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.ibm.pilotbrief.verticalprofile.SSDSVersionFetcher.RPMLayer;
import com.ibm.pilotbrief.verticalprofile.SSDSVersionFetcher.SSDSUpdateSubscriber;

@WebListener
public class TurbulenceTileFetcher implements ServletContextListener {

	final static int NTHREADS = 10;
	
	public boolean isReady = false;
	public boolean isFetching = false;
	public Map<String, RPMLayer> currentFetch;
	
	public void startUp() {
		final TurbulenceTileFetcher me = this;
		SSDSVersionFetcher.shared().subscribeToUpdates(new SSDSUpdateSubscriber() {
			@Override
			public void newVersionAvailable(Map<String, RPMLayer> layers) {
				if (currentFetch == layers) {
					return;
				}
				currentFetch = layers;
				while(me.isFetching) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
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
		TurbulenceTileFetcher.sharedInstance.startUp();
	}


	private final static TurbulenceTileFetcher  sharedInstance = new TurbulenceTileFetcher();

	public static TurbulenceTileFetcher shared() {
		return sharedInstance;
	}
	
	public Map<String, UnpackedRPMLayer> unpackedLayers = null;
	
	public void fetchTiles(Map<String, RPMLayer> layers) {
		this.isFetching = true;
		try {
			ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(NTHREADS);
			Map<String, UnpackedRPMLayer> newMap = new HashMap<String, UnpackedRPMLayer>();
			for (RPMLayer layer : layers.values()) {
				for (Long fts : layer.forecastTimestamp) {
					System.out.format("Fetching layer: %s, FTS = %d\n", layer.layerName, fts);
					UnpackedRPMLayer rpmLayer = new UnpackedRPMLayer();
					newMap.put(layer.getMappingKey(), rpmLayer);
//					System.out.println("KB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024);
					for (int x = 0; x < UnpackedRPMLayer.maxTileNumber; x++) {
						for (int y = 0; y < UnpackedRPMLayer.maxTileNumber; y++) {
							final int myX = x;
							final int myY = y;
							pool.execute(new Runnable() {
								
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
//										System.out.format("Width = %d, height = %d\n", image.getWidth(), image.getHeight());
										if ((image.getHeight() == 256) && (image.getWidth() == 256)) {
//											images.add(image);
											rpmLayer.addTile(image, myX, myY);
										}
										in.close();
										conn.disconnect();
									} catch (Exception ex) {
										ex.printStackTrace();
									}
									
								}
							});
						}
					}
				}
			}
			
			pool.shutdown();
			while (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
				System.out.format("Remaining = %d\n", pool.getQueue().size());
			}
			unpackedLayers = newMap;
			this.isReady = true;
			this.isFetching = false;
			synchronized(this) {
				this.notifyAll();
			}
			System.out.println("DONE!");
		}catch (Exception ex) {
			ex.printStackTrace();
		}
	}


}

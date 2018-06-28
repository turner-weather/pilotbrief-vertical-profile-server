package com.ibm.pilotbrief.verticalprofile;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.annotation.CacheKey;
import javax.cache.spi.CachingProvider;
import javax.imageio.ImageIO;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.hazelcast.config.CacheConfig;
import com.ibm.pilotbrief.verticalprofile.SSDSVersionFetcher.RPMLayer;
import com.ibm.pilotbrief.verticalprofile.SSDSVersionFetcher.SSDSUpdateSubscriber;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.manager.GeoPackageManager;
import mil.nga.geopackage.tiles.user.TileDao;

@WebListener
public class TurbulenceTileFetcher implements ServletContextListener {

	public static int TILE_ZOOM_LEVEL = 4;
	public static int MAX_TILE_NUMBER = (int) Math.pow(2, TILE_ZOOM_LEVEL);
	public static int TILE_SIZE = 256;
	public static int TOTAL_LAYER_IMAGE_SIZE = TILE_SIZE * MAX_TILE_NUMBER;
	
	public enum TurbulenceSeverity {
		NONE, LIGHT, OCCASIONAL, MODERATE, MODERATE_PLUS
	}

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
		Runnable r = new Runnable() {
			
			@Override
			public void run() {
				SSDSVersionFetcher.shared().startFetching();
			}
		};
		
		r.run();
	}

	public static void main(String[] args) {
		TurbulenceTileFetcher fetcher = new TurbulenceTileFetcher();
		fetcher.startUp();
		while (!fetcher.isReady) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private void dumpTestImage() {
		ArrayList<LayerInfo> infos = this.layerTimestamps.get("globalGTG110");
		LayerInfo lastInfo = infos.get(0);
		BufferedImage im = new BufferedImage(TOTAL_LAYER_IMAGE_SIZE, TOTAL_LAYER_IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
	     Graphics2D g2d = im.createGraphics();
	     g2d.setComposite(
	             AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0F));
		for (long tileX = 0; tileX < 16; tileX++) {
			for (long tileY = 0; tileY < 16; tileY++) {
				try {
					BufferedImage img = TurbulenceTileFetcher.shared().getTileForLayer(this.new TileInfo(lastInfo, tileX, tileY));
				     g2d.drawImage(img, (int) tileX * 256, (int) tileY * 256, null);
				     g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				    	        RenderingHints.VALUE_ANTIALIAS_ON);
				     g2d.drawString(String.format("x=%d, y=%d", tileX, tileY), (int) tileX * 256 + 128, (int) tileY * 256 + 128);
				     
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		}
	     g2d.dispose();
		File outputfile = new File("/tmp/image.png");
		try {
			ImageIO.write(im, "png", outputfile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	public static CacheManager cacheMgr;
	public static Cache<String, int[]> cache;
//	@Inject private CacheManager cacheMgr;
	@Override
	public void contextInitialized(ServletContextEvent servletContextEvent) {
		CachingProvider cachingProvider = Caching.getCachingProvider();
		cacheMgr = cachingProvider.getCacheManager();
		/**
		CacheConfig config = CacheConfig();
		config.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS,6)));
		EvictionConfig econfig = new EvictionConfig();
		econfig.setSize(MAX_TILE_NUMBER * MAX_TILE_NUMBER / 4);
		config.setEvictionConfig(econfig);
		**/
		cache = cacheMgr.createCache("com.ibm.verticalprofile.layertilecache", new CacheConfig<String, int[]>());
		/**
		cacheMgr.createCache("com.ibm.verticalprofile.layertilecache",config);
		**/
		TurbulenceTileFetcher.sharedInstance.startUp();
	}


	private final static TurbulenceTileFetcher  sharedInstance = new TurbulenceTileFetcher();

	public static TurbulenceTileFetcher shared() {
		return sharedInstance;
	}
	
	public void fetchTiles(Map<String, RPMLayer> layers) {
		this.isFetching = true;
		Long ts = layers.values().iterator().next().timestamp;
		String gpkgPath = String.format("/tmp/RPM-%d.gpkg", ts);
		File gpkgFile = new File(gpkgPath);
		if (gpkgFile.exists()) {
			updateGeopackage(gpkgPath);
			return;
		}
				try {
					URI uri = new URI(String.format("https://%s/v3/ssds/geopackage?apiKey=%s", 
							System.getenv("SSDS_ENDPOINT"),
							System.getenv("SSDS_CREDENTIALS")));
					HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
					conn.setRequestMethod("POST");
					conn.setDoOutput(true);
					conn.setRequestProperty("Connection", "keep-alive");
					conn.setDoOutput(true);
					conn.setRequestProperty( "Content-Type", "application/json"); 
					conn.setRequestProperty( "charset", "utf-8");
					StringBuffer prodListCoda = new StringBuffer("{\n" + 
							"  \"aoi\": [{\n" + 
							"      \"lod\": [" + TILE_ZOOM_LEVEL + "],\n" + 
							"      \"bbox\": [-180.0, -90.0, 180.0, 90.0]\n" + 
							"      }\n" + 
							"    ],\n" + 
							"  \"products\": [\n");
					for (RPMLayer layer : layers.values()) {
						prodListCoda.append(String.format("{\"id\" : \"%s\", \"dataType\" : \"image/png\", \"rt\" : %d, \"t\" : [", layer.layerId, layer.timestamp));
						Long lastts = layer.forecastTimestamp.get(layer.forecastTimestamp.size() - 1);
						for (Long t : layer.forecastTimestamp) {
							prodListCoda = prodListCoda.append(String.format("%d", t));
							if (t != lastts) {
								prodListCoda.append(",");
							}
						}
						prodListCoda.append("]\n" +
						"},");
					}
					prodListCoda.deleteCharAt(prodListCoda.length() - 1);
					prodListCoda.append("]}");
					System.out.println(prodListCoda.toString());
					conn.connect();
					OutputStream os = conn.getOutputStream();
			        os.write(prodListCoda.toString().getBytes());
			        os.flush();					
					if (conn.getResponseCode() != 200) {
						//TODO: Figure out right thing to do here.
						System.out.println("Request response is " + conn.getResponseCode());
						return;
					}
					Object response = new JSONParser().parse(new InputStreamReader(conn.getInputStream()));
					if (response == null) {
						//TODO: Also figure out what to do here...
					}
					JSONObject responseObj = (JSONObject) response;
					String status = (String) responseObj.get("status");
					String id = (String) responseObj.get("id");
					if (status.equalsIgnoreCase("success") || status.equalsIgnoreCase("pending")) {
						this.getResponseTiles(id, ts);
					}
					return;
				} catch (Exception ex) {
					ex.printStackTrace();
				}
	}
	
	private void getResponseTiles(String id, Long ts) {
		try {
			URI uri = new URI(String.format("https://%s/v3/ssds/geopackage?apiKey=%s&id=%s", 
					System.getenv("SSDS_ENDPOINT"),
					System.getenv("SSDS_CREDENTIALS"),
					id));
			HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Connection", "keep-alive");
			conn.setRequestProperty( "charset", "utf-8");
			conn.connect();
			if (conn.getResponseCode() != 200) {
				System.out.println("Request " + id + " response is " + conn.getResponseCode());
				return;
			}
			Object response = new JSONParser().parse(new InputStreamReader(conn.getInputStream()));
			if (response == null) {
				//TODO: Also figure out what to do here...
			}
			JSONObject responseObj = (JSONObject) response;
			String status = (String) responseObj.get("status");
			final TurbulenceTileFetcher fetcher = this;
			if ("Pending".equalsIgnoreCase(status) || "InProgress".equalsIgnoreCase(status)) {
				System.out.println("Request " + id + " is pending");
				new java.util.Timer().schedule( 
			        new java.util.TimerTask() {
			            @Override
			            public void run() {
			                fetcher.getResponseTiles(id, ts);
			            }
			        }, 
			        10000 
						);
				return;
			}
			if (!"Complete".equalsIgnoreCase(status)) {
				System.out.println("Request " + id + " status is " + status);
				return;
				//TODO: Figure out what to do here
			}
			String fetchUri = (String) responseObj.get("fetchUrl");
			System.out.println("Fetching " + fetchUri);
			String gpkgPath = String.format("/tmp/RPM-%d.gpkg", ts);
			FileUtils.copyURLToFile(new URL(fetchUri), new File(gpkgPath + ".gz"));
			byte[] buffer = new byte[1024];


			GZIPInputStream gzis = 
					new GZIPInputStream(new FileInputStream(gpkgPath + ".gz"));

			FileOutputStream out = 
					new FileOutputStream(gpkgPath);

			int len;
			while ((len = gzis.read(buffer)) > 0) {
				out.write(buffer, 0, len);
			}

			gzis.close();
			out.close();
			updateGeopackage(gpkgPath);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public GeoPackage currentGeopackage;
	
	public Long currrentGeopackageTimestamp = -1L;
	
	public class LayerInfo implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 826748802017341927L;
		String layerName;
		Long createdTimestamp = -1L;
		Long effectiveStartTimestamp = -1L;
		Long effectiveEndTimestamp = -1L;
		String daoName;
		public LayerInfo(String layerName, Long createdTimestamp, Long effectiveStartTimestamp, Long effectiveEndTimestamp) {
			super();
			this.layerName = layerName;
			this.createdTimestamp = createdTimestamp;
			this.effectiveStartTimestamp = effectiveStartTimestamp;
			this.effectiveEndTimestamp = effectiveEndTimestamp;
			this.daoName = String.format("%s_%d_%d", layerName, createdTimestamp / 1000, effectiveStartTimestamp / 1000);
		}
		
	}
	
	public class TileInfo implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 2966236699605518958L;
		public TileInfo(LayerInfo layerInfo, Long x, Long y) {
			super();
			this.layerInfo = layerInfo;
			this.x = x;
			this.y = y;
			this.cacheKey = String.format("%s_%d_%d", layerInfo.daoName, x, y);
		}
		LayerInfo layerInfo;
		Long x;
		Long y;
		String cacheKey;
	}
	
//	@CacheResult(cacheName="com.ibm.verticalprofile.layertilecache")
	public BufferedImage getTileForLayer(@CacheKey TileInfo info) throws IOException {
/*		int[] rgbData =	cache.get(info.cacheKey);
		if (rgbData != null) {
			BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
			img.setRGB(0, 0, 256, 256, rgbData, 0, 256);
			return img;
		}
		System.out.format("No cache hit for %s, %d, %d\n", info.layerInfo.daoName, info.y, info.x);
*/
		TileDao tileDao = this.currentGeopackage.getTileDao(info.layerInfo.daoName);
		BufferedImage img = tileDao.queryForTile(info.x, info.y, 4L).getTileDataImage();
//		int[] imgArray = new int[256 * 256];
//		if (img.getWidth() + img.getHeight() == 512) {
//			img.getRGB(0, 0, 256, 256, imgArray, 0, 256);
//		}
//		cache.put(info.cacheKey, imgArray);
		return img;
	}
	
	public Map<String, ArrayList<LayerInfo>> layerTimestamps = new HashMap<String, ArrayList<LayerInfo>>();
	
	public void updateGeopackage(String gpkgPath) {
		GeoPackage geoPackage = GeoPackageManager.open(new File(gpkgPath));
		List<String> tiles = geoPackage.getTileTables();
		Map<String, ArrayList<LayerInfo>> newLayerTimestamps = new HashMap<String, ArrayList<LayerInfo>>();
		for (String tile : tiles) {
			String[] comps = tile.split("_");
			String layerName = comps[0];
			Long created = Long.valueOf(comps[1]) * 1000;
			Long timeStamp = Long.valueOf(comps[2]) * 1000;
			ArrayList<LayerInfo> timestamps = newLayerTimestamps.get(layerName);
			if (timestamps == null) {
				timestamps = new ArrayList<LayerInfo>();
				newLayerTimestamps.put(layerName, timestamps);
			}
			timestamps.add(new LayerInfo(layerName, created, timeStamp, timeStamp + 3599999));
		}
		currentGeopackage = geoPackage;
		layerTimestamps = newLayerTimestamps;
		this.isFetching = false;
		this.isReady = true;
		this.dumpTestImage();
	}
	public static class TurbulenceValue {
		public TurbulenceValue(TurbulenceSeverity severity, Date forecastTimestamp) {
			super();
			this.severity = severity;
			this.forecastTimestamp = forecastTimestamp;
		}
		TurbulenceSeverity severity;
		Date forecastTimestamp;
	}
	
	public TurbulenceValue getLayerValueForAltitudeAndTime(BufferedImage bitmap, LayerInfo info, int x, int y) throws IOException {
		y = (TILE_SIZE - 1) - (y % TILE_SIZE);
		x = y % TILE_SIZE;
		if (bitmap == null || bitmap.getWidth() <= x || bitmap.getHeight() <= y) {
			return new TurbulenceValue(TurbulenceSeverity.NONE, new Date(info.effectiveStartTimestamp));
			
		}
		int color = bitmap.getRGB((x % TILE_SIZE), y);
		switch (color) {
		case 0xFFFFCD2E:
			return new TurbulenceValue(TurbulenceSeverity.LIGHT, new Date(info.effectiveStartTimestamp));
		case 0xFFFF9C00:
			return new TurbulenceValue(TurbulenceSeverity.OCCASIONAL, new Date(info.effectiveStartTimestamp));
		case 0xFFFF7701:
			return new TurbulenceValue(TurbulenceSeverity.MODERATE, new Date(info.effectiveStartTimestamp));
		case 0xFFE24800:
			return new TurbulenceValue(TurbulenceSeverity.MODERATE_PLUS, new Date(info.effectiveStartTimestamp));
		case 0:
		case 0xFF000000:
			return new TurbulenceValue(TurbulenceSeverity.NONE, new Date(info.effectiveStartTimestamp));
		}
		System.out.format("BOGUS COLOR: %x\n", color);
		return new TurbulenceValue(TurbulenceSeverity.NONE, new Date(info.effectiveStartTimestamp));
	}


}

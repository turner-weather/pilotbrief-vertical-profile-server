package com.ibm.pilotbrief.verticalprofile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.apache.commons.io.FileUtils;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.ibm.pilotbrief.verticalprofile.SSDSVersionFetcher.RPMLayer;
import com.ibm.pilotbrief.verticalprofile.SSDSVersionFetcher.SSDSUpdateSubscriber;
import com.ibm.pilotbrief.verticalprofile.UnpackedRPMLayer.TurbulenceSeverity;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.manager.GeoPackageManager;

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
		SSDSVersionFetcher.shared().startFetching();
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
	
	public Map<RPMLayer, SortedMap<Date, UnpackedRPMLayer>> layerMap = new HashMap<RPMLayer, SortedMap<Date, UnpackedRPMLayer>>();
	
	public void fetchTiles(Map<String, RPMLayer> layers) {
		this.isFetching = true;
			ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(NTHREADS);
			Map<String, UnpackedRPMLayer> newMap = new HashMap<String, UnpackedRPMLayer>();
			Map<RPMLayer, SortedMap<Date, UnpackedRPMLayer>> newLayerMap = new HashMap<RPMLayer, SortedMap<Date, UnpackedRPMLayer>>();
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
							"      \"lod\": [4],\n" + 
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
						this.getResponseTiles(id, layers.values().iterator().next().timestamp);
					}
					return;
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				/**
				newLayerMap.put(layer, new TreeMap<Date, UnpackedRPMLayer>());
				if ((layer == null) || (layer.forecastTimestamp == null)) {
					continue;
				}
				for (Long fts : layer.forecastTimestamp) {
					Date ftsDate = new Date(fts * 1000);
//					System.out.format("Fetching layer: %s, FTS = %s\n", layer.layerName, ftsDate.toGMTString());
					UnpackedRPMLayer rpmLayer = new UnpackedRPMLayer();
					rpmLayer.forecastTimestamp = ftsDate.getTime();
					newMap.put(layer.getMappingKey(), rpmLayer);
					newLayerMap.get(layer).put(new Date(fts), rpmLayer);
//					System.out.println("KB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024);
					for (int x = 0; x < UnpackedRPMLayer.maxTileNumber; x++) {
						for (int y = 0; y < UnpackedRPMLayer.maxTileNumber; y++) {
							final int myX = x;
							final int myY = y;
							pool.execute(new Runnable() {
								
								@Override
								public void run() {
									try {
										URI uri = new URI(String.format("https://%s/v3/ssds/geopackage?apiKey=%s", 
												System.getenv("SSDS_ENDPOINT"),
												System.getenv("SSDS_CREDENTIALS")));
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
			
			pool.shutdown();
			while (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
			}
			unpackedLayers = newMap;
			layerMap = newLayerMap;
			this.isReady = true;
			this.isFetching = false;
			synchronized(this) {
				this.notifyAll();
			}
		}catch (Exception ex) {
			ex.printStackTrace();
		}
						**/
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
			Runtime rt = Runtime.getRuntime();
			Process pr = rt.exec("gunzip -c " + gpkgPath + ".gz > " + gpkgPath);
			pr.waitFor();
			GeoPackage geoPackage = GeoPackageManager.open(new File(gpkgPath));
			List<String> tiles = geoPackage.getTileTables();
			for (String tile : tiles) {
				System.out.println(tile);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
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
	
	public TurbulenceValue getLayerValueForAltitudeAndTime(RPMLayer layer, Date time, int x, int y) {
		long timestamp = time.getTime() ;
		for (UnpackedRPMLayer rpmLayer : this.layerMap.get(layer).values()) {
			long fts = rpmLayer.forecastTimestamp;
			if (fts > timestamp) {
				break;
			}
			if ((fts <= timestamp) &&
					(fts + 3600000 >= timestamp)) {
				y = rpmLayer.bitmap.getHeight() - y;
				int color = rpmLayer.bitmap.getRGB(x, y);
				switch (color) {
				case 0xFFFFCD2E:
					return new TurbulenceValue(TurbulenceSeverity.LIGHT, new Date(fts));
				case 0xFFFF9C00:
					return new TurbulenceValue(TurbulenceSeverity.OCCASIONAL, new Date(fts));
				case 0xFFFF7701:
					return new TurbulenceValue(TurbulenceSeverity.MODERATE, new Date(fts));
				case 0xFFE24800:
					return new TurbulenceValue(TurbulenceSeverity.MODERATE_PLUS, new Date(fts));
				case 0:
					return new TurbulenceValue(TurbulenceSeverity.NONE, new Date(fts));
				}
				System.out.format("BOGUS COLOR: %x\n", color);
				return new TurbulenceValue(TurbulenceSeverity.NONE, new Date(fts));
			}
			
		}
		
		return null;
	}


}

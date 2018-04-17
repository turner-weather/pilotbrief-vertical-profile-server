package com.ibm.pilotbrief.verticalprofile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.ibm.pilotbrief.verticalprofile.SSDSVersionFetcher.RPMLayer;

@WebListener
public class SSDSVersionFetcher implements ServletContextListener {
	
	public interface SSDSUpdateSubscriber {
		public void newVersionAvailable(Map<String, RPMLayer> layers);
	}

	public static void main(String[] args) {
		SSDSVersionFetcher f = new SSDSVersionFetcher();
	}
	
	public SSDSVersionFetcher() {
		super();
		final SSDSVersionFetcher me = this;
		Thread t = new Thread(new Runnable() {
			
			@Override
			public void run() {
				me.fetchCurrentVersions();
			}
		});
		t.run();
	}

	private final static SSDSVersionFetcher  sharedInstance = new SSDSVersionFetcher();

	public static SSDSVersionFetcher shared() {
		return sharedInstance;
	}
	
	List<SSDSUpdateSubscriber> subscribers = new ArrayList<SSDSUpdateSubscriber>();
	
	public void subscribeToUpdates(SSDSUpdateSubscriber subscriber) {
		synchronized(subscribers) {
			subscribers.add(subscriber);
			if (rpmLayers.values().stream().allMatch(new Predicate<RPMLayer>() {
				@Override
				public boolean test(RPMLayer t) {
					return t.timestamp != null;
				}
			})) {
				subscriber.newVersionAvailable(rpmLayers);
			}
		}
	}
	
	public void unsubscribeToUpdates(SSDSUpdateSubscriber subscriber) {
		synchronized (subscribers) {
			subscribers.remove(subscriber);
		}
		
	}
	
	public class RPMLayer {
		String layerName;
		String layerId;
		int floorFL;
		int ceilingFL;
		List<Long> forecastTimestamp;
		Long timestamp;

		public RPMLayer(String layerName, String layerId, int floorFL, int ceilingFL) {
			super();
			this.layerId = layerId;
			this.layerName = layerName;
			this.floorFL = floorFL;
			this.ceilingFL = ceilingFL;
		}
		
		public String getMappingKey() {
			return String.format("%s-%ld", this.layerId, this.timestamp);
		}
		
		
		
	}

	private Map<String, RPMLayer> rpmLayers = generateRPMLayers();
	
	private Map<String, RPMLayer> generateRPMLayers() {
		Map<String, RPMLayer> map = new HashMap<String, RPMLayer>();
		map.put("globalGTG110", new RPMLayer("RPM Turbulence FL110", "globalGTG110", 100, 120));
		map.put("globalGTG130", new RPMLayer("RPM Turbulence FL130", "globalGTG130", 120, 140));
		map.put("globalGTG150", new RPMLayer("RPM Turbulence FL150", "globalGTG150", 140, 165));
		map.put("globalGTG180", new RPMLayer("RPM Turbulence FL180", "globalGTG180", 165, 195));
		map.put("globalGTG210", new RPMLayer("RPM Turbulence FL210", "globalGTG210", 195, 225));
		map.put("globalGTG240", new RPMLayer("RPM Turbulence FL240", "globalGTG240", 225, 255));
		map.put("globalGTG270", new RPMLayer("RPM Turbulence FL270", "globalGTG270", 255, 285));
		map.put("globalGTG300", new RPMLayer("RPM Turbulence FL300", "globalGTG300", 285, 320));
		map.put("globalGTG340", new RPMLayer("RPM Turbulence FL340", "globalGTG340", 320, 365));
		map.put("globalGTG390", new RPMLayer("RPM Turbulence FL390", "globalGTG390", 365, 410));
		map.put("globalGTG430", new RPMLayer("RPM Turbulence FL430", "globalGTG430", 410, 450));
	
		return map;
	}
	
	public Map<String, RPMLayer> getRPMLayers() {
		synchronized(this) {
			return rpmLayers;
		}
	}
	
	public void fetchCurrentVersions() {
		try {

			URI uri = new URI("https://" + System.getenv("SSDS_ENDPOINT") + "/v3/TileServer/series/productSet?productSet=aviation&apiKey=" + System.getenv("SSDS_CREDENTIALS"));
			HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Connection", "keep-alive");
			conn.connect();
			Object obj = new JSONParser().parse(new InputStreamReader(conn.getInputStream()));
			boolean somethingChanged = false;
			Map<String, RPMLayer> newLayers = generateRPMLayers();
				JSONObject jo = (JSONObject) obj;
				JSONObject seriesInfo = (JSONObject) jo.get("seriesInfo");
				for (Object key : seriesInfo.keySet()) {
					String keyString = (String) key;
					JSONObject info = (JSONObject) seriesInfo.get(keyString);
					JSONArray series = (JSONArray) info.get("series");
					RPMLayer layer = newLayers.get(keyString);
					if (layer == null) {
						continue;
					}
					if (series.size() == 0) {
						continue;
					}
					JSONObject mostCurrent = (JSONObject) series.get(0);
					RPMLayer oldLayer = rpmLayers.get(keyString);
					Long newTimestamp = (Long) mostCurrent.get("ts");
					if (!newTimestamp.equals(oldLayer.timestamp)) {
						somethingChanged = true;
					}
					layer.timestamp = newTimestamp;
					JSONArray fts = (JSONArray) mostCurrent.get("fts");
					layer.forecastTimestamp = new ArrayList<Long>();
					for (Object ft : fts) {
						Long ftLong = (Long) ft;
						layer.forecastTimestamp.add(ftLong);
					}
			}
				if (somethingChanged) {
					synchronized(this) {
						this.rpmLayers = newLayers;
					}
					synchronized (subscribers) {
						for (SSDSUpdateSubscriber subscriber : subscribers) {
							subscriber.newVersionAvailable(rpmLayers);
						}
						
					}
			} 
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			Timer t = new Timer();
			final SSDSVersionFetcher me = this;
			t.schedule(new TimerTask() {

				@Override
				public void run() {
					me.fetchCurrentVersions();
				}
			}, 60 * 1000);
		}



	}

}

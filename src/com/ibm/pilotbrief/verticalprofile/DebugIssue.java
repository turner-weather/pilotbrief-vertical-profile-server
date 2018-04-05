package com.ibm.pilotbrief.verticalprofile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

public class DebugIssue  {

	public static void main(String[] args) {
		DebugIssue i = new DebugIssue();
		i.fetchCurrentVersions();

	}
	
	private final static DebugIssue  sharedInstance = new DebugIssue();
	
	public static DebugIssue shared() {
		return sharedInstance;
	}
	
    public void fetchCurrentVersions() {
    		try {
    			
				URI uri = new URI("https://" + System.getenv("SSDS_ENDPOINT") + "/v3/TileServer/series/productSet?productSet=aviation&apiKey=" + System.getenv("SSDS_CREDENTIALS"));
				System.out.println(uri.toString());
    			  HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
				conn.setRequestMethod("GET");
				conn.setRequestProperty("Connection", "keep-alive");
				conn.connect();
				InputStream s = conn.getInputStream();
				int i;
				while ((i = s.read()) != -1) {
					System.out.print(i);
				}
				/**
				HttpClient httpClient = HttpClientBuilder.create().build();
				HttpResponse response = httpClient.execute(new HttpGet(uri));
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					System.out.println(entity.toString());
				}
				**/
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		
    		
    }

}

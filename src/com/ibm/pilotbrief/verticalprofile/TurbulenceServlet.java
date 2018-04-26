package com.ibm.pilotbrief.verticalprofile;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GeodeticCurve;
import org.gavaghan.geodesy.GlobalCoordinates;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.pilotbrief.verticalprofile.SSDSVersionFetcher.RPMLayer;
import com.ibm.pilotbrief.verticalprofile.UnpackedRPMLayer.TurbulenceSeverity;


@Path("turbulence")
public class TurbulenceServlet {
	static double POSITION_DELTA = 4.0;
	
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTurbulence(FlightPlan flightplan, @Context HttpServletRequest request) {
		String authenticationRequired = System.getenv("NO_AUTHENTICATION_NEEDED"); 
		if (authenticationRequired == null || !authenticationRequired.equalsIgnoreCase("YES")) {
			Boolean validated = (Boolean) request.getSession().getAttribute("validated");
			if (validated == null || validated == true) {
				return Response.status(Status.UNAUTHORIZED).build();
			}
		}
		
		// Still in startup
		while (!TurbulenceTileFetcher.shared().isReady) {
			try {
				synchronized(TurbulenceTileFetcher.shared()) {
					TurbulenceTileFetcher.shared().wait(1000);
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		Ellipsoid e = Ellipsoid.WGS84;
		GeodeticCalculator c = new GeodeticCalculator();
		SegmentList<Segment> segList = new SegmentList<Segment>();
		double cumulativeNM = 0;
		GlobalCoordinates current = new GlobalCoordinates(flightplan.getWaypoints().get(0).getLatitude(), flightplan.getWaypoints().get(0).getLongitude());
		for (int i = 1; i < flightplan.getWaypoints().size(); i++) {
			GlobalCoordinates next = new GlobalCoordinates(flightplan.getWaypoints().get(i).getLatitude(), flightplan.getWaypoints().get(i).getLongitude());
			GeodeticCurve curve = c.calculateGeodeticCurve(e, current, next);
			double distNM = curve.getEllipsoidalDistance() * 5.39956804e-4;
			segList.add(new Segment(cumulativeNM, distNM, current, next));
			cumulativeNM += distNM;
			current = next;
		}
		
	
		ObjectMapper m = new ObjectMapper();
		List<IntermediatePosition> positions = this.getPositions(segList, flightplan.getDepartureTime(), flightplan.getArrivalTime());
		
		/**
		 * Iterate over flight levels, and then X/Y positions inside of each level, to find congruent features
		 */
		Map<String, Object> responseJSON = new HashMap<String, Object>();
		responseJSON.put("type", "FeatureCollection");
		List<Map<String, Object>> featureJSON = new ArrayList<Map<String, Object>>();
		responseJSON.put("features"	, featureJSON);
		for (RPMLayer rpmLayer : SSDSVersionFetcher.shared().getRPMLayers().values()) {
			TurbulenceSeverity lastTurb = TurbulenceSeverity.NONE;
			Double lastTurbLat = null;
			Double lastTurbLong = null; 
			for (IntermediatePosition pos : positions) {
				TurbulenceSeverity severity = TurbulenceTileFetcher.shared().getLayerValueForAltitudeAndTime(rpmLayer, pos.eta, pos.x,pos.y);
				if (severity == null) {
					severity = TurbulenceSeverity.NONE;
				}
				if (severity == lastTurb) {
					continue;
				}
				if ((severity == TurbulenceSeverity.NONE) || (pos == positions.get(positions.size() - 1))) {
					Map<String, Object> newFeature = new HashMap<String, Object>();
					newFeature.put("type", "Feature");
					Map<String, Object> geometry = new HashMap<String, Object>();
					newFeature.put("geometry", geometry);
					geometry.put("type", "Polygon");
					List<Double[]> coordinates = new ArrayList<Double[]>();
					Double[] leftBottom = {lastTurbLong, lastTurbLat, rpmLayer.floorFL * 100.0};
					Double[] leftTop = {lastTurbLong, lastTurbLat, rpmLayer.ceilingFL * 100.0};
					Double[] rightTop = {pos.longitude, pos.lat,  rpmLayer.ceilingFL * 100.0};
					Double[] rightBottom = {pos.longitude, pos.lat, rpmLayer.floorFL * 100.0};
					coordinates.add(leftBottom);
					coordinates.add(leftTop);
					coordinates.add(rightTop);
					coordinates.add(rightBottom);
					coordinates.add(leftBottom);
					List<List<Double[]>> wrapper = new ArrayList<List<Double[]>>();
					wrapper.add(coordinates);
					geometry.put("coordinates", wrapper);
					Map<String, Object> props = new HashMap<String, Object>();
					newFeature.put("properties", props);
					props.put("severity", lastTurb);
					featureJSON.add(newFeature);
					lastTurb = TurbulenceSeverity.NONE;
					continue;
				}
				lastTurb = severity;
				lastTurbLat = pos.lat;
				lastTurbLong = pos.longitude;
			}
		}
		try {
			String json = m.writeValueAsString(responseJSON);
			return Response.ok(json).build();
		} catch (JsonProcessingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return Response.serverError().build();
		
	}
	
	public class IntermediatePosition {
	    public double lat;
	    public double longitude;
	    public double distance;
	    public Date eta;
	    public int x;
	    public int y;
	    
		public IntermediatePosition(double lat, double longitude, double distance, Date eta, int x, int y) {
			super();
			this.lat = lat;
			this.longitude = longitude;
			this.distance = distance;
			this.eta = eta;
			this.x = x;
			this.y = y;
		}
	    
	}
	
	public long[] getXYForCoordinate(GlobalCoordinates coord) {
		double halfSize = (UnpackedRPMLayer.totalTileSize - 1) / 2.0;
		double x1 = (coord.getLongitude() / 360.0) * Double.valueOf(UnpackedRPMLayer.totalTileSize - 1);
		long x = Math.round(x1 + halfSize) ;
        double tan1 = Math.tan(Math.toRadians(coord.getLatitude() + 90.0) / 2.0);
        double log1 =  Math.toDegrees(Math.log(tan1));
        double y1 = log1 / 180.0;

		long y = Math.max(0, Math.min(Math.round(Math.round(y1 * UnpackedRPMLayer.totalTileSize * 0.5) + halfSize), UnpackedRPMLayer.totalTileSize - 1));
		
		long[] retval = { x, y };
		return retval;
	}
	
	public List<IntermediatePosition> getPositions(SegmentList<Segment> segments, Date departureTime, Date arrivalTime) {
		List<IntermediatePosition> positions = new ArrayList<IntermediatePosition>();
		
		long milliDuration = arrivalTime.getTime() - departureTime.getTime();
		
		double distance = 0;
		
		if (segments.size() == 0) {
			return positions;
		}
		
		Segment last = segments.get(segments.size() - 1);
		
		double totalDistance = last.cumulativeDistanceNM + last.segmentLengthNM;
		
		//millisecond per NM
		double speed = (double) milliDuration / totalDistance;
		
		long[] lastXY = {-1000, -1000};
		while (distance < totalDistance) {
			GlobalCoordinates coord = segments.getCoordinatesForDistance(distance);
			if (coord == null) {
				return positions;
			}
			long[] xy = this.getXYForCoordinate(coord);
			if (xy[0] == lastXY[0] && xy[1] == lastXY[1]) {
				distance += POSITION_DELTA;
				continue;
			}
			if (distance > 0) {
				double dist = distance - (POSITION_DELTA / 2.0);
				GlobalCoordinates coord1 = segments.getCoordinatesForDistance(dist);
				long[] xy1 = this.getXYForCoordinate(coord1);
				positions.add(new IntermediatePosition(coord1.getLatitude(), coord1.getLongitude(), dist, new Date(departureTime.getTime() + Math.round(dist * speed)), (int) xy1[0], (int) xy1[1]));
			}
			positions.add(new IntermediatePosition(coord.getLatitude(), coord.getLongitude(), distance, new Date(departureTime.getTime() + Math.round(distance * speed)), (int) xy[0], (int) xy[1]));
			distance += POSITION_DELTA;
			lastXY = xy;
		}
		
		return positions;
	}
	
	
}

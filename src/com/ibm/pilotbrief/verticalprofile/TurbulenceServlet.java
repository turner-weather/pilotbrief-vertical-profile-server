package com.ibm.pilotbrief.verticalprofile;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.gavaghan.geodesy.GlobalCoordinates;


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
				TurbulenceTileFetcher.shared().wait(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
		return Response.ok().build();
		
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
	
	public List<IntermediatePosition> getPositions(SegmentList<Segment> segments) {
		List<IntermediatePosition> positions = new ArrayList<IntermediatePosition>();
		
		double distance = 0;
		
		if (segments.size() == 0) {
			return positions;
		}
		
		Segment last = segments.get(segments.size() - 1);
		
		while (distance < last.cumulativeDistanceNM + last.segmentLengthNM) {
			GlobalCoordinates coord = segments.getCoordinatesForDistance(distance);
			if (coord == null) {
				return positions;
			}
			long[] xy = this.getXYForCoordinate(coord);
			if (distance > 0) {
				double dist = distance - (POSITION_DELTA / 2.0);
				GlobalCoordinates coord1 = segments.getCoordinatesForDistance(dist);
				long[] xy1 = this.getXYForCoordinate(coord1);
				positions.add(new IntermediatePosition(coord1.getLatitude(), coord1.getLongitude(), dist, null, (int) xy1[0], (int) xy1[1]));
			}
			positions.add(new IntermediatePosition(coord.getLatitude(), coord.getLongitude(), distance, null, (int) xy[0], (int) xy[1]));
			distance += POSITION_DELTA;
		}
		
		return positions;
	}
	
	
}

/**
 * 
 */
package com.ibm.pilotbrief.verticalprofile;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GeodeticCurve;
import org.gavaghan.geodesy.GlobalCoordinates;
import org.junit.Test;

import com.ibm.pilotbrief.verticalprofile.TurbulenceServlet.IntermediatePosition;

/**
 * @author turner
 *
 */
public class TestTurbulenceServlet {

	/**
	 * Test method for {@link com.ibm.pilotbrief.verticalprofile.TurbulenceServlet#getXYForCoordinate(org.gavaghan.geodesy.GlobalCoordinates)}.
	 */
	@Test
	public void testGetXYForCoordinate() {
		double maxDegrees = 85.051129;
		TurbulenceServlet s = new TurbulenceServlet();
		long[] coords = s.getXYForCoordinate(new GlobalCoordinates(0, 0));
		assertEquals(UnpackedRPMLayer.totalTileSize / 2, coords[0]);
		assertEquals(UnpackedRPMLayer.totalTileSize / 2, coords[1]);
		coords = s.getXYForCoordinate(new GlobalCoordinates(-maxDegrees, -179.99));
		assertEquals(0, coords[0]);
		assertEquals(0, coords[1]);
		coords = s.getXYForCoordinate(new GlobalCoordinates(maxDegrees, 179.99));
		assertEquals(UnpackedRPMLayer.totalTileSize - 1, coords[0]);
		assertEquals(UnpackedRPMLayer.totalTileSize - 1, coords[1]);
	}

	/**
	 * Test method for {@link com.ibm.pilotbrief.verticalprofile.TurbulenceServlet#getPositions(com.ibm.pilotbrief.verticalprofile.SegmentList)}.
	 */
	@Test
	public void testGetPositions() {
		GlobalCoordinates ksea = new GlobalCoordinates(47.4502, -122.3088);
		GlobalCoordinates kbos = new GlobalCoordinates(42.3656, -71.0096);
		Ellipsoid e = Ellipsoid.WGS84;
		GeodeticCalculator c = new GeodeticCalculator();
		GeodeticCurve curve = c.calculateGeodeticCurve(e, ksea, kbos);
		Segment kseaKBOS = new Segment(0,  curve.getEllipsoidalDistance() * 5.39956804e-4, ksea, kbos);
		SegmentList<Segment> seglist = new SegmentList<Segment>();
		seglist.add(kseaKBOS);
		TurbulenceServlet s = new TurbulenceServlet();
		List<IntermediatePosition> positions = s.getPositions(seglist);
		assertEquals(1085, positions.size());
		
	}

}

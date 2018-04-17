/**
 * 
 */
package com.ibm.pilotbrief.verticalprofile;

import static org.junit.Assert.*;

import org.gavaghan.geodesy.GlobalCoordinates;
import org.junit.Test;

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
		fail("Not yet implemented");
	}

}

package com.ibm.pilotbrief.verticalprofile;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GeodeticCurve;
import org.gavaghan.geodesy.GlobalCoordinates;

@XmlRootElement
public class SegmentList<T> extends ArrayList<T> implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5241146291726929523L;
	
	@SuppressWarnings("unchecked")
	public GlobalCoordinates getCoordinatesForDistance(double distance) {
		List<Segment> segList = (List<Segment>) this;
		for (Segment segment : segList) {
			if ((segment.cumulativeDistanceNM + segment.segmentLengthNM) < distance) {
				continue;
			}
			GlobalCoordinates startPos = segment.startPos;
			GlobalCoordinates endPos = segment.endPos;
			
			Ellipsoid e = Ellipsoid.WGS84;
			GeodeticCalculator c = new GeodeticCalculator();
			GeodeticCurve curve = c.calculateGeodeticCurve(e, startPos, endPos);
			double distAlong = distance - segment.cumulativeDistanceNM;
			double distanceMeters = distAlong * 1852;
			return c.calculateEndingGlobalCoordinates(e, startPos, curve.getAzimuth(), distanceMeters);
		}
		return null;
	}
	
}
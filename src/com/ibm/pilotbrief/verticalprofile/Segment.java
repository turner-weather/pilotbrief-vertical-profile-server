package com.ibm.pilotbrief.verticalprofile;

import org.gavaghan.geodesy.GlobalCoordinates;

public class Segment {
	public double cumulativeDistanceNM;
	public double segmentLengthNM;
	GlobalCoordinates startPos;
	GlobalCoordinates endPos;
	
	public Segment(double cumulativeDistanceNM, double segmentLengthNM, GlobalCoordinates startPos,
			GlobalCoordinates endPos) {
		super();
		this.cumulativeDistanceNM = cumulativeDistanceNM;
		this.segmentLengthNM = segmentLengthNM;
		this.startPos = startPos;
		this.endPos = endPos;
	}
	
	
	
}
package com.ibm.pilotbrief.verticalprofile;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class FlightPlan {
	private List<Waypoint> waypoints;

	public List<Waypoint> getWaypoints() {
		return waypoints;
	}

	public void setWaypoints(List<Waypoint> waypoints) {
		this.waypoints = waypoints;
	}
}

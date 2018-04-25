package com.ibm.pilotbrief.verticalprofile;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName(value = "FlightPlan")
public class FlightPlan {
	private List<Waypoint> waypoints;
	private Date departureTime;
	private Date arrivalTime;

	public List<Waypoint> getWaypoints() {
		return waypoints;
	}

	public void setWaypoints(List<Waypoint> waypoints) {
		this.waypoints = waypoints;
	}

	public long getDepartureTimeMillis() {
		return departureTime.getTime();
	}
	
	public void setDepartureTimeMillis(long millis) {
		departureTime = new Date(millis);
	}

	public long getArrivalTimeMillis() {
		return arrivalTime.getTime();
	}
	
	public void setArrivalTimeMillis(long millis) {
		arrivalTime = new Date(millis);
	}

	public Date getDepartureTime() {
		return departureTime;
	}

	public void setDepartureTime(Date departureTime) {
		this.departureTime = departureTime;
	}

	public Date getArrivalTime() {
		return arrivalTime;
	}

	public void setArrivalTime(Date arrivalTime) {
		this.arrivalTime = arrivalTime;
	}
}

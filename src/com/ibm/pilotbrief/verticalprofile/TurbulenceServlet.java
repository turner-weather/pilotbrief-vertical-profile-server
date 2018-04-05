package com.ibm.pilotbrief.verticalprofile;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;


@Path("turbulence")
public class TurbulenceServlet {
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
		
		return Response.ok().build();
		
	}
}

package com.ibm.pilotbrief;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("login")
public class AuthenticationServlet {
       
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public void login(@Context HttpServletRequest request, @QueryParam("username") String username, @QueryParam("password") String password) {
		request.getSession().setAttribute("validated", true);
	}
}

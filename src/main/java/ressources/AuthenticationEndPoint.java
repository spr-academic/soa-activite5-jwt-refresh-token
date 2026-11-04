package ressources;

import entities.Credentials;
import entities.TokenResponse;
import utilities.JwtService;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

@Path("authentication")
public class AuthenticationEndPoint {
    @Context private UriInfo uriInfo;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response authenticateUser(Credentials credentials) {
        if (credentials == null || blank(credentials.getUsername()) || blank(credentials.getPassword())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        // Authentification pédagogique du sujet : deux champs non vides.
        return tokenResponse(credentials.getUsername());
    }

    @POST
    @Path("refresh")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response refresh(@FormParam("refreshToken") String token) {
        try {
            return tokenResponse(JwtService.useRefreshToken(token, issuer()));
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }

    @POST
    @Path("logout")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response logout(@FormParam("refreshToken") String token) {
        JwtService.revokeRefreshToken(token, issuer());
        return Response.noContent().build();
    }

    private Response tokenResponse(String username) {
        TokenResponse tokens = new TokenResponse(JwtService.generateAccessToken(username, issuer()),
                JwtService.generateRefreshToken(username, issuer()), JwtService.ACCESS_MINUTES * 60L);
        return Response.ok(tokens).header("Cache-Control", "no-store").header("Pragma", "no-cache").build();
    }

    private String issuer() { return uriInfo.getBaseUri().toString(); }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

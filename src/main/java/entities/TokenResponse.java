package entities;

public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;

    public TokenResponse() { }
    public TokenResponse(String accessToken, String refreshToken, long expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String value) { accessToken = value; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String value) { refreshToken = value; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String value) { tokenType = value; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long value) { expiresIn = value; }
}

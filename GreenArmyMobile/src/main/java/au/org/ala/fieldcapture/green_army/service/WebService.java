package au.org.ala.fieldcapture.green_army.service;


import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Base class for classes that want to use web services.
 */
public class WebService {

    private static final String USER_NAME_HEADER = "userName";
    private static final String AUTH_KEY_HEADER = "authKey";

    private String userName;
    private String authKey;

    public WebService(String userName, String authKey) {

        this.userName = userName;
        this.authKey = authKey;
        // This shouldn't be necessary however I am seeing frequent failures
        // due to recycled closed connections (possibly something I am doing
        // wrong).  This is working around that.
        System.setProperty("http.keepAlive", "false");
    }

    /** So we can add auth headers to the requests */
    public static class RequestFactory extends SimpleClientHttpRequestFactory {

        private String username;
        private String authKey;
        public RequestFactory(String userName, String authKey) {
            this.username = userName;
            this.authKey = authKey;
            setReadTimeout(60000);
            setConnectTimeout(10000);
        }

        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);

            connection.setRequestProperty(USER_NAME_HEADER, username);
            connection.setRequestProperty(AUTH_KEY_HEADER, authKey);

        }
    }


    protected RestTemplate getRestTemplate(boolean requiresAuthentication) {
        RestTemplate restTemplate = new RestTemplate();


        restTemplate.getMessageConverters().add(new JSONObjectMessageConverter());
        restTemplate.getMessageConverters().add(new JSONArrayMessageConverter());
        restTemplate.getMessageConverters().add(new FormHttpMessageConverter());

        if (requiresAuthentication && userName != null && authKey != null) {
            restTemplate.setRequestFactory(new RequestFactory(userName, authKey));
        }
        return restTemplate;
    }

}

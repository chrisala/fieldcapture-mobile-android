package au.org.ala.fieldcapture.green_army.data;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import au.org.ala.fieldcapture.green_army.R;
import au.org.ala.fieldcapture.green_army.service.WebService;

/**
 * Manages communication between the app and the fieldcapture / ecodata server.
 */
public class EcodataInterface extends WebService {

    /** A container for the results of a login attempt */
    public static class LoginResult {

        public static final int LOGIN_FAILED_NO_NETWORK = R.string.no_network;
        public static final int LOGIN_FAILED_INVALID_CREDENTIALS = R.string.bad_credentials;
        public static final int LOGIN_FAILED_SERVER_ERROR = R.string.server_error;


        public String authKey;
        public boolean success;
        public int failureReason;

        public LoginResult() {
            success = false;
            authKey = null;
        }

    }

    public static class SaveSiteResult {
        public String siteId;
        public boolean success;
    }

    private static final String FIELDCAPTURE_URL = "https://fieldcapture.ala.org.au/mobile";

    /** If you use this constructor, methods requiring authentication won't work. (only the login will work...) */
    public EcodataInterface() {
        super(null, null);
    }

    public EcodataInterface(String userName, String authKey) {
        super(userName, authKey);

    }

    /**
     * Checks the users credentials are correct.
     * @param username the username to check.
     * @param password the password to check.
     * @return the result of the login.
     */
    public LoginResult login(String username, String password) {


        RestTemplate template = getRestTemplate(false);
        MultiValueMap<String, Object> params = new LinkedMultiValueMap<String, Object>();
        params.add("userName", username);
        params.add("password", password);

        LoginResult result = new LoginResult();
        try {
            Log.i("EcodataInterface", "Logging in user: " + username);

            String url = FIELDCAPTURE_URL+ "/login/?userName={username}&password={password}";
            JSONObject response = template.getForObject(url, JSONObject.class, username, password);

            String key = response.getString("authKey");
            if (key != null) {
                result.success = true;
                result.authKey = key;
            }
            else {
                result.failureReason = LoginResult.LOGIN_FAILED_SERVER_ERROR;
            }

        }
//        catch(SSLException e) {
//            // We seem to be getting random connection resets from the
//            // server when using SSL.  Trying again will normally work.
//            Log.d("LoginActivity", "Got SSL error, retrying");
//            return login(username, password);
//        }
        catch (HttpClientErrorException e) {
            // 400 series error - in this case it means invalid credentials
            result.failureReason = LoginResult.LOGIN_FAILED_INVALID_CREDENTIALS;
        }
        catch (HttpServerErrorException e) {
            // 500 series error from server.
            Log.e("EcodataInterface", "Login failed", e);
            result.failureReason = LoginResult.LOGIN_FAILED_SERVER_ERROR;
        }
        catch (RestClientException e) {
            // Unknown error - likely network related.
            Log.e("EcodataInterface", "Login failed", e);
            result.failureReason = LoginResult.LOGIN_FAILED_NO_NETWORK;
        }
        catch (Exception e) {
            // Unknown error - likely a bug.
            Log.e("EcodataInterface", "Login failed", e);
            result.failureReason = LoginResult.LOGIN_FAILED_NO_NETWORK;
        }
        return result;
    }


    public List<JSONObject> getProjectsForUser() {

        String url = FIELDCAPTURE_URL + "/userProjects?program=Green Army";

        RestTemplate restTemplate = getRestTemplate(true);

        List<JSONObject> projects = null;

        try {
            JSONArray results = restTemplate.getForObject(url, JSONArray.class);
            projects = new ArrayList(results.length());
            for (int i=0; i<results.length(); i++) {
                JSONObject projectHolder = results.getJSONObject(i);
                projects.add(projectHolder.getJSONObject("project"));
            }
        }
        catch (RestClientException e) {
            Log.e("EcodataInferface", "Error getting user projects", e);
        }
        catch (JSONException e) {
            Log.e("EcodataInferface", "Error getting user projects", e);
        }

        return projects;
    }

    public JSONObject getProjectDetails(String projectId) {

        String url = FIELDCAPTURE_URL + "/projectDetails/{projectId}";

        RestTemplate restTemplate = getRestTemplate(true);

        JSONObject results = null;

        try {
            results = restTemplate.getForObject(url, JSONObject.class, projectId);
        }
        catch (HttpClientErrorException e) {
            Log.e("EcodataInterface", "getProjectActivities failed for url: "+url+"projectId:"+projectId+":", e);
        }
        catch (HttpServerErrorException e) {
            Log.e("EcodataInterface", "getProjectActivities failed for url: "+url+"projectId:"+projectId+":", e);

        }
        catch (RestClientException e) {
            Log.e("EcodataInterface", "getProjectActivities failed for url: "+url+"projectId:"+projectId+":", e);

        }

        return results;
    }

    public boolean saveActivity(JSONObject activityJSON) {

        String url = FIELDCAPTURE_URL + "/updateActivity/{activityId}";
        String activityId = "";
        RestTemplate template = getRestTemplate(true);

        boolean success = false;
        try {
            activityId = activityJSON.getString(FieldCaptureContent.ACTIVITY_ID);

            JSONObject result = template.postForObject(url, activityJSON, JSONObject.class, activityId);
            Log.d("Ecodatainterface", "saveActivity returned: "+result.toString());
            String error = result.optString("error");
            if (error.length() == 0) {
                success = true;
            }
        }
        catch (HttpClientErrorException e) {
            Log.e("EcodataInterface", "saveActivity failed for url: "+url+"activityId:"+activityId+":"+activityJSON, e);


        }
        catch (HttpServerErrorException e) {
            Log.e("EcodataInterface", "saveActivity failed for url: "+url+"activityId:"+activityId+":"+activityJSON, e);

        }
        catch (RestClientException e) {
            Log.e("EcodataInterface", "saveActivity failed for url: "+url+"activityId:"+activityId+":"+activityJSON, e);

        }
        catch (JSONException e) {
            Log.e("EcodataInterface", "saveActivity failed for url: "+url+"activityId:"+activityId+":"+activityJSON, e);
        }

        return success;
    }

    public SaveSiteResult saveSite(JSONObject siteJSON) {

        String url = FIELDCAPTURE_URL + "/createSite";
        RestTemplate template = getRestTemplate(true);
        SaveSiteResult result = new SaveSiteResult();
        result.success = false;

        try {

            JSONObject resultJSON = template.postForObject(url, siteJSON, JSONObject.class);
            Log.d("Ecodatainterface", "saveSite returned: "+result.toString());
            String error = resultJSON.optString("error");
            String siteId = resultJSON.optString("siteId");

            result.success = error.length() == 0;
            result.siteId = siteId;
        }
        catch (HttpClientErrorException e) {
            Log.e("EcodataInterface", "createSite failed for url: "+url+"site:"+siteJSON, e);


        }
        catch (HttpServerErrorException e) {
            Log.e("EcodataInterface", "createSite failed for url: "+url+"site:"+siteJSON, e);

        }
        catch (RestClientException e) {
            Log.e("EcodataInterface", "createSite failed for url: "+url+"site:"+siteJSON, e);

        }


        return result;
    }
}

package au.org.ala.fieldcapture.green_army.data;

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

    //private static final String ECODATA_URL = "http://152.83.195.62:8080/ecodata";
    private static final String FIELDCAPTURE_URL = "https://fieldcapture-test.ala.org.au";
    private static final String ECODATA_URL= "http://ecodata-test.ala.org.au";


    private static final String AUTH_URL ="http://m.ala.org.au/mobileauth/mobileKey/generateKey";


    /**
     * Checks the users credentials are correct.
     * @param username the username to check.
     * @param password the password to check.
     * @return the result of the login.
     */
    public LoginResult login(String username, String password) {


        RestTemplate template = getRestTemplate();
        MultiValueMap<String, Object> params = new LinkedMultiValueMap<String, Object>();
        params.add("userName", username);
        params.add("password", password);

        LoginResult result = new LoginResult();
        try {
            Log.i("EcodataInterface", "Logging in user: " + username);

            String url = AUTH_URL+ "?userName={username}&password={password}";
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


    public List<JSONObject> getProjectsForUser(String userName, String authKey) {

        String url = FIELDCAPTURE_URL + "/mobile/userProjects?userName={username}&authKey={authKey}";

        RestTemplate restTemplate = getRestTemplate();

        List<JSONObject> projects = null;

        try {
            JSONArray results = restTemplate.getForObject(url, JSONArray.class, userName, authKey);
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

    public JSONArray getProjectActivities(String projectId, String userName, String authKey) {

        String url = FIELDCAPTURE_URL + "/mobile/projectDetails/{projectId}?userName={userName}&authKey={authKey}";

        RestTemplate restTemplate = getRestTemplate();

        JSONArray activities = null;

        try {
            JSONObject results = restTemplate.getForObject(url, JSONObject.class, projectId, userName, authKey);
            activities = results.getJSONArray("activites");
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
        catch (JSONException e) {
            Log.e("EcodataInferface", "Error getting project activities", e);
        }
        return activities;
    }

    public boolean saveActivity(JSONObject activityJSON) {

        String url = ECODATA_URL + "/ws/activity/{activityId}";
        String activityId = "";
        RestTemplate template = getRestTemplate();

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

    /** Returns html / javascript for the form identified by type */
    void getActivityForm(String type) {}


}
package au.org.ala.fieldcapture.green_army;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.UUID;

import au.org.ala.fieldcapture.green_army.data.FieldCaptureContent;
import au.org.ala.fieldcapture.green_army.data.PreferenceStorage;


public class SiteActivity extends FragmentActivity implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener, GoogleMap.OnMarkerDragListener {

    public static final String LOCATION_KEY = "location";
    public static final String LOCATION_UPDATES_KEY = "locationUpdates";
    public static final String GPS_TIMEOUT_KEY = "gpsTimeout";
    public static final String VALIDATION_VISIBLE_KEY = "validationVisible";
    public static final String VALIDATION_TEXT_KEY = "validationText";

    public static final String SITE_KEY = "site";
    public static final float ACCEPTABLE_ACCURACY_THRESHOLD = 100f;
    // Accept a new location if the old location is 10 minutes old, even if the accuracy is worse.
    public static final long STALE_LOCATION_THRESHOLD = 10*60*1000;

    public static final long GPS_TIMEOUT = 120000;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    private LocationClient mLocationClient;
    private boolean receivingLocationUpdates;
    private LocationRequest mRequest;
    private Location location;
    private EditText nameField;
    private EditText description;
    private TextView lat;
    private TextView lon;
    private TextView accuracy;
    private View progressBar;
    private TextView locationStatus;
    private TextView networkStatus;
    private TextView locationValidationError;
    private boolean gpsTimeout = false;
    private Marker marker;
    private boolean paused;


    public static class TimeoutWithLocationDialogFragment extends DialogFragment {

        public TimeoutWithLocationDialogFragment() {}

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.gps_timeout).setTitle(R.string.gps_timout_title).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            return builder.create();

        }
    }

    public static class TimeoutDialogFragment extends DialogFragment {

        public TimeoutDialogFragment() {}

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.gps_timeout_no_location).setTitle(R.string.gps_timout_title).setPositiveButton(R.string.ok,  new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            return builder.create();


        }

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_site);
        setUpMapIfNeeded();

        receivingLocationUpdates = true;
        mLocationClient = new LocationClient(this, this, this);
        mRequest = LocationRequest.create();
        mRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mRequest.setInterval(5*1000);
        mRequest.setFastestInterval(1*1000);


        nameField = (EditText)findViewById(R.id.site_name);
        description = (EditText)findViewById(R.id.site_description);
        lat = (TextView)findViewById(R.id.latitude);
        lon = (TextView)findViewById(R.id.longitude);
        accuracy = (TextView)findViewById(R.id.accuracy);
        progressBar = findViewById(R.id.progress_bar);
        locationStatus = (TextView)findViewById(R.id.location_status);
        networkStatus = (TextView)findViewById(R.id.network_status);
        locationValidationError = (TextView)findViewById(R.id.location_validation_error);

        if (savedInstanceState != null) {
            location = savedInstanceState.getParcelable(LOCATION_KEY);
            receivingLocationUpdates = savedInstanceState.getBoolean(LOCATION_UPDATES_KEY);
            gpsTimeout = savedInstanceState.getBoolean(GPS_TIMEOUT_KEY);
            boolean validationVisible = savedInstanceState.getBoolean(VALIDATION_VISIBLE_KEY);
            locationValidationError.setVisibility(validationVisible?View.VISIBLE:View.GONE);
            if (validationVisible) {
                locationValidationError.setText(savedInstanceState.getString(VALIDATION_TEXT_KEY));
            }
        }

    }

    @Override
    protected void onResume() {
        paused = false;
        super.onResume();
        setUpMapIfNeeded();
        if (isNetworkAvailable()) {
            networkStatus.setVisibility(View.GONE);
        }
        else {
            networkStatus.setVisibility(View.VISIBLE);
        }

        PreferenceStorage storage = PreferenceStorage.getInstance(this);
        if (!storage.askedAboutGps()) {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
                LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                // Some tablets incorrectly report they have GPS via the package manager.x
                if (locationManager.getProvider(LocationManager.GPS_PROVIDER) != null) {
                    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        showNoGpsDialog();
                    }
                }
            }
            storage.setAskedAboutGps(true);
        }
        if (location != null) {
            LatLng latlng = new LatLng(location.getLatitude(), location.getLongitude());
            marker = mMap.addMarker(new MarkerOptions().position(latlng).draggable(true));
            setLocation(location);
        }
        else {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-27.0, 133.0), 4));
        }

    }

    private void showNoGpsDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.no_gps_title)
                .setMessage(R.string.no_gps_message)
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                }).setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, final int id) {
                dialog.cancel();
            }
        });
        builder.create().show();

    }
    /*
     * Called when the Activity becomes visible.
     */
    @Override
    protected void onStart() {
        super.onStart();
        // Connect the client.
        if (receivingLocationUpdates) {
            mLocationClient.connect();
        }
    }

    @Override
    protected void onPause() {
        paused = true;
        super.onPause();
        if (marker != null) {
            marker.remove();
        }
        // Having the keyboard open on rotate was causing some strange drawing errors.
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(nameField.getWindowToken(), 0);

    }

    /*
     * Called when the Activity is no longer visible.
     */
    @Override
    protected void onStop() {
        // Disconnecting the client invalidates it.
        mLocationClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedState) {

        super.onSaveInstanceState(savedState);
        savedState.putParcelable(LOCATION_KEY, location);
        savedState.putBoolean(LOCATION_UPDATES_KEY, receivingLocationUpdates);
        savedState.putBoolean(GPS_TIMEOUT_KEY, gpsTimeout);
        savedState.putBoolean(VALIDATION_VISIBLE_KEY, locationValidationError.getVisibility() == View.VISIBLE);
        savedState.putString(VALIDATION_TEXT_KEY, locationValidationError.getText().toString());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.site, menu);

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.done:
                done();
                return true;
        }
        return false;
    }

    private boolean validate() {

        Editable siteName = nameField.getText();
        boolean valid = true;
        if (siteName.length() == 0) {
            nameField.setError(getResources().getString(R.string.site_name_required));
            valid = false;
        }
        if (location == null) {

            locationValidationError.setText(getString(R.string.no_location));
            locationValidationError.setVisibility(View.VISIBLE);
            valid = false;
        }
        else if (location != null && !gpsTimeout) {
            if (location.getAccuracy() > ACCEPTABLE_ACCURACY_THRESHOLD) {
                locationValidationError.setText(getString(R.string.location_accuracy_too_low));
                locationValidationError.setVisibility(View.VISIBLE);
                valid = false;
            }
        }
        return valid;
    }

    private void done() {

        if (validate()) {
            String siteId = UUID.randomUUID().toString();
            ContentValues values = new ContentValues();
            values.put(FieldCaptureContent.SITE_ID, siteId);
            values.put("name", nameField.getText().toString());
            values.put("description", description.getText().toString());
            if (location != null) {
                values.put("centroidLat", location.getLatitude());
                values.put("centroidLon", location.getLongitude());
            }
            // Return the site data here to prevent any synchronisation issues when returning to the
            // data entry activity.
            Intent result = new Intent();
            result.putExtra(SITE_KEY, values);
            setResult(Activity.RESULT_OK, result);
            finish();
        }
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p>
     * If it isn't installed {@link com.google.android.gms.maps.SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }

    }

    @Override
    public void onLocationChanged(Location location) {
        // Update location

        float currentAccuracy = this.location != null ? this.location.getAccuracy() : Float.MAX_VALUE;
        long lastTime = this.location != null ? this.location.getTime() : 0;
        if (location.getAccuracy() < currentAccuracy || (location.getTime() - lastTime) > STALE_LOCATION_THRESHOLD) {
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());

            if (this.location == null) {
                marker = mMap.addMarker(new MarkerOptions().position(position).draggable(true));
            }
            setLocation(location);

        }
    }

    private void setLocation(Location location) {
        lat.setText(Double.toString(location.getLatitude()));
        lon.setText(Double.toString(location.getLongitude()));
        float accuracy = location.getAccuracy();
        if (accuracy < 0f) {
            this.accuracy.setText("Unknown");
            progressBar.setVisibility(View.GONE);
            locationStatus.setVisibility(View.GONE);
            locationValidationError.setVisibility(View.GONE);
            if (receivingLocationUpdates) {
                mLocationClient.removeLocationUpdates(this);
                receivingLocationUpdates = false;
            }
        }
        else {
            if (accuracy <= ACCEPTABLE_ACCURACY_THRESHOLD) {
                progressBar.setVisibility(View.GONE);
                locationStatus.setVisibility(View.GONE);
                locationValidationError.setVisibility(View.GONE);
                if (receivingLocationUpdates) {
                    mLocationClient.removeLocationUpdates(this);
                    receivingLocationUpdates = false;
                }
            }
            else {
                locationStatus.setText(getResources().getString(R.string.status_refining_location));
                locationValidationError.setText(getString(R.string.location_accuracy_too_low));
            }
            this.accuracy.setText(Float.toString(accuracy));
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 16));
        this.location = location;
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        mMap.setOnMarkerDragListener(this);

    }

    public void onMarkerDrag(Marker marker) {

    }
    public void onMarkerDragStart(Marker arg0) {


    }
    public void onMarkerDragEnd(Marker marker) {
        mLocationClient.removeLocationUpdates(this);
        Location location = new Location("Drawn on Map");
        location.setLatitude(marker.getPosition().latitude);
        location.setLongitude(marker.getPosition().longitude);
        location.setAccuracy(-1);

        setLocation(location);
    }

    /**
     * Called when {@code mGoogleApiClient} is connected.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i("SiteActivity", "GoogleApiClient connected");

        if (receivingLocationUpdates) {
            mLocationClient.requestLocationUpdates(mRequest, this);

            new Handler().postDelayed(new Runnable() {
                public void run() {
                    gpsTimeout();
                }
            }, GPS_TIMEOUT);
        }
    }

    private void gpsTimeout() {

        if (paused) {

            Log.i("Site Activity", "GPS timer fired while paused.");
            return;
        }
        if (receivingLocationUpdates) {
            locationValidationError.setVisibility(View.GONE);

            DialogFragment dialog;
            if (location != null && location.getAccuracy() > ACCEPTABLE_ACCURACY_THRESHOLD) {
                 dialog = new TimeoutWithLocationDialogFragment();
            }
            else {
                dialog = new TimeoutDialogFragment();
            }
            if (dialog != null) {
                dialog.show(getSupportFragmentManager(), "Timeout dialog");
            }
            gpsTimeout = true;
        }
    }

    @Override
    public void onDisconnected() {

    }

    /*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    private final static int
            CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    // Define a DialogFragment that displays the error dialog
    public static class ErrorDialogFragment extends DialogFragment {
        // Global field to contain the error dialog
        private Dialog mDialog;
        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }
        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }
        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    /*
     * Handle results returned to the FragmentActivity
     * by Google Play services
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        // Decide what to do based on the original request code
        switch (requestCode) {

            case CONNECTION_FAILURE_RESOLUTION_REQUEST :
            /*
             * If the result code is Activity.RESULT_OK, try
             * to connect again
             */
                switch (resultCode) {
                    case Activity.RESULT_OK :


                        break;
                }

        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private boolean servicesConnected() {
        // Check that Google Play services is available
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            Log.d("Location Updates", "Google Play services is available.");
            // Continue
            return true;
            // Google Play services was not available for some reason
        } else {
            showErrorDialog(resultCode);

        }
        return false;
    }

    private void showErrorDialog(int errorCode) {
        // Get the error dialog from Google Play services
        Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                errorCode,
                this,
                CONNECTION_FAILURE_RESOLUTION_REQUEST);

        // If Google Play services can provide an error dialog
        if (errorDialog != null) {
            // Create a new DialogFragment for the error dialog
            ErrorDialogFragment errorFragment =
                    new ErrorDialogFragment();
            // Set the dialog in the DialogFragment
            errorFragment.setDialog(errorDialog);
            // Show the error dialog in the DialogFragment
            errorFragment.show(getSupportFragmentManager(),
                    "Location Updates");
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(
                        this,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                 * Thrown if Google Play services canceled the original
                 * PendingIntent
                 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            showErrorDialog(connectionResult.getErrorCode());
        }
    }
}

package com.example.mia.gtdnoti;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main activity that contains the logic:
 * check about the walkiing activity on a daily basis, if in the past one week, user
 * walk less than 12 hours, then a activity of walk will be trigger and created
 * automatically in the caldendar with a reminder.
 * <p/>
 * Using Activity Recognition API and google calendar API.
 *
 * Created by mia on 2/1/16.
 */
public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {com.google.api.services
            .calendar.CalendarScopes.CALENDAR_READONLY};
    // @param count counting to control call walking activity calculation per day.
    public int count = 0;

    GoogleAccountCredential mCredential;
    ProgressDialog mProgress;
    private SQLiteDatabase myDatabase;
    private Context context;
    private GoogleApiClient mGApiClient;
    private BroadcastReceiver mReceiver;

    /**
     * Create the main activity.
     *
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Debugger: onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Set the context
        context = this;
        //Set progress message
        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Google Calendar API ...");

        // Initialize Database
        myDatabase = openOrCreateDatabase("MiaDB0.1", context.MODE_PRIVATE, null);
        myDatabase.execSQL("CREATE TABLE IF NOT EXISTS RecordMyActivities(activity VARCHAR, time DATE,confidence INT);");

        // Initialize credentials and service object.
        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff())
                .setSelectedAccountName(settings.getString(PREF_ACCOUNT_NAME, null));

        // Check Google Play Service Available
        if (isPlayServiceAvailable()) {
            // ATTENTION: This "addApi(AppIndex.API)"was auto-generated to implement the App Indexing API.
            // See https://g.co/AppIndexing/AndroidStudio for more information.
            mGApiClient = new GoogleApiClient.Builder(this)
                    .addApi(ActivityRecognition.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(AppIndex.API).build();
            //Connect to Google API
            mGApiClient.connect();
        } else {
            Toast.makeText(context, "Google Play Service not Available", Toast.LENGTH_LONG).show();
        }

        //Broadcast receiver
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Debugger: BroadcastReceiver->onReceive()");
                count++;
                //Add current time
                java.util.Calendar rightNow = java.util.Calendar.getInstance();
                SimpleDateFormat sdf = new SimpleDateFormat("dd-mm-yyyy");
                String strDate = sdf.format(rightNow.getTime());
                String sql = "INSERT INTO RecordMyActivities VALUES('" + intent.getStringExtra("activity") + "','" +
                        strDate + "'," + intent.getExtras().getInt("confidence") + ");";
                Log.d(TAG, "this is no. " + count + "data");
                myDatabase.execSQL(sql);
                if (count == 2) {
                    // one day having 288 records input into DB, here for testing, we
                    // allow after inputting 2 times then check
                    //if(count == 288)
                    count = 0;
                    if (isPlayServiceAvailable()) {
                        addWalkingEvent();
                    } else {
                        showMessage("Google Play Services required", "after installing, close and relaunch this app.");
                    }
                }
            }
        };
        //Filter the Intent and register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction("ImActive");
        registerReceiver(mReceiver, filter);
    }
    /**
     * Called whenever this activity is pushed to the foreground, such as after
     * a call to onCreate().
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Debugger: onResume()");
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "Debugger: onActivityResult()");

        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    isPlayServiceAvailable();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        mCredential.setSelectedAccountName(accountName);
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                    }
                } else if (resultCode == RESULT_CANCELED) {
                    showMessage("Ops!", "Account unspecified.");
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode != RESULT_OK) {
                    chooseAccount();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    /**
     * Add walking event in user's calendar, notify user changes
     */
    public void addWalkingEvent() {
        Log.d(TAG, "Debugger: addWalkingEvent()");
        // Read all waking records from database
        /*Cursor c0 = myDatabase.rawQuery("SELECT count(*) FROM RecordMyActivities WHERE " +
                "activity = 'Walking' and time < datetime('now', '-7 days')", null);
        //Cursor c0 = myDatabase.rawQuery("SELECT count(*) FROM RecordMyActivities
        // WHERE activity = 'Still' and time < datetime('now', '-6 days')", null);
        c0.moveToFirst();
        c0.close();
*/
        // Calculate total time of walking for past 7 days
        Cursor c = myDatabase.rawQuery("SELECT confidence FROM RecordMyActivities WHERE" +
                " activity ='Walking' and time < datetime('now', '-7 days')", null);
        //Cursor c = myDatabase.rawQuery("SELECT confidence FROM RecordMyActivities WHERE activity = 'Still' and time < datetime('now', '-6 days')", null);
        c.moveToFirst();
        int sumMins = -1;
        int mins = 5;
        for (int i = 0; i < c.getCount(); i++) {
            sumMins = sumMins + mins * c.getInt(0);
            c.moveToNext();
        }
        c.close();

        // Calculate the waking hours in past week.
        int hours = -1;
        hours = sumMins / 60;
        // if total waking hours for last week less than 12 hours, tigger startActivity
        // to book an appointment in calendar
        if (hours < 12) {
            refreshResults();
        }
    }

    /**
     * Show message AlterDialog
     */
    public void showMessage(String title, String message) {
        Log.d(TAG, "Debugger: showMessage()" +"title = "+title+"message = "+ message);
    }



    @Override
    public void onConnected(Bundle bundle) {
        Intent i = new Intent(this, ActivityRecognitionIntentService.class);
        PendingIntent mActivityRecongPendingIntent = PendingIntent
                .getService(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGApiClient,
                0, mActivityRecongPendingIntent);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Suspended to ActivityRecognition");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "Not connected to ActivityRecognition");
    }


    /**
     * Check for Google play services available on device
     */
    private boolean isPlayServiceAvailable() {
        return GooglePlayServicesUtil.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
    }
    /**
     * Attempt to get a set of data from the Google Calendar API to display. If the
     * email address isn't known yet, then call chooseAccount() method so the
     * user can pick an account.
     */
    private void refreshResults() {
        if (mCredential.getSelectedAccountName() == null) {
            Log.d(TAG, "Debugger: refreshResults()->chooseAccount");
            chooseAccount();
        } else {
            Log.d(TAG, "Debugger: refreshResults()->isDeviceOnline");
            if (isDeviceOnline()) {
                new MakeRequestTask(mCredential).execute();
            } else {
                showMessage("No network connection available.", "Sorry!");
            }
        }
    }

    /**
     * Starts an activity in Google Play Services so the user can pick an
     * account.
     */
    private void chooseAccount() {
        Log.d(TAG, "Debugger: chooseAccount");
        startActivityForResult(
                mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
    }

    /**
     * Checks whether the device currently has a network connection.
     *
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        Log.d(TAG, "Debugger: isDeviceOnline");
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * An asynchronous task that handles the Google Calendar API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {

        private com.google.api.services.calendar.Calendar mService = null;
        private Exception mLastError = null;

        public MakeRequestTask(GoogleAccountCredential credential) {
            Log.d(TAG, "Debugger: MakeRequestTask");
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new Calendar.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Google Calendar API Android Quickstart")
                    .build();
        }

        /**
         * Background task to call Google Calendar API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            Log.d(TAG, "Debugger: doInBackground");
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of the next 10 events from the primary calendar.
         *
         * @return List of Strings describing returned events.
         * @throws IOException
         */
        protected List<String> getDataFromApi() throws IOException {
            // List the next 10 events from the primary calendar.
            Log.d(TAG, "Debugger: getDataFromApi");
            DateTime now = new DateTime(System.currentTimeMillis());
            List<Long> eventDates = new ArrayList<Long>();
            List<String> eventStrings = new ArrayList<String>();
            Events events = mService.events().list("primary")
                    .setMaxResults(10)
                    .setTimeMin(now)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();

            List<Event> items = events.getItems();

            for (Event event : items) {
                Log.d(TAG, "Debugger: getDataFromApi->Event");
                //SimpleDateFormat sdf = new SimpleDateFormat("dd-mm-yyyy");
                DateTime start = event.getStart().getDateTime();
                DateTime end = event.getEnd().getDateTime();

                if (start == null) {
                    start = event.getStart().getDate();
                }
                eventDates.add(start.getValue());
                eventDates.add(end.getValue());

                eventStrings.add(
                        String.format("%s (%s)", event.getSummary(), start));
            }

            Intent l_intent = new Intent(Intent.ACTION_EDIT);
            for (int i = 0, j = 0; i < eventDates.size(); i = i + 2) {
                j = i + 2;
                if ((eventDates.get(j) - 3600000) >= eventDates.get(i + 1)) {
                    l_intent.setType("vnd.android.cursor.item/event");
                    l_intent.putExtra("title", "Walk");
                    l_intent.putExtra("description", "Hey! It is time to take a break.");
                    l_intent.putExtra("beginTime", eventDates.get(i + 1));
                    l_intent.putExtra("endTime", eventDates.get(i + 1) + 3600000);
                    //status: 0~ tentative; 1~ confirmed; 2~ canceled
                    l_intent.putExtra("eventStatus", 1);
                    //0~ default; 1~ confidential; 2~ private; 3~ public
                    l_intent.putExtra("visibility", 0);
                    //0~ opaque, no timing conflict is allowed; 1~ transparency, allow overlap of scheduling
                    l_intent.putExtra("transparency", 0);
                    //0~ false; 1~ true
                    l_intent.putExtra("hasAlarm", 1);
                    try {
                        Log.d(TAG, "Debugger: getDataFromApi->startActivity");
                        startActivity(l_intent);
                    } catch (Exception e) {
                        Toast.makeText(context, "Sorry, no compatible calendar is " +
                                "found!", Toast.LENGTH_LONG).show();
                    }
                }
            }
            return eventStrings;
        }

        @Override
        protected void onPreExecute() {
            mProgress.show();
            Log.d(TAG, "Debugger: onPreExecute");
        }

        @Override
        protected void onPostExecute(List<String> output) {
            mProgress.hide();
            Log.d(TAG, "Debugger: onPostExecute");
            if (output == null || output.size() == 0) {
                showMessage("Ops", "No calendar results returned.");
            }
        }

        @Override
        protected void onCancelled() {
            Log.d(TAG, "Debugger: onCancelled");
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    showMessage("The following error occurred:\n", mLastError.getMessage());
                }
            } else {
                showMessage("Ops", "Request cancelled.");
            }
        }

        /**
         * Display an error dialog showing that Google Play Services is missing
         * or out of date.
         *
         * @param connectionStatusCode code describing the presence (or lack of)
         *                             Google Play Services on this device.
         */
        void showGooglePlayServicesAvailabilityErrorDialog(
                final int connectionStatusCode) {
            Log.d(TAG, "Debugger: showGooglePlayServicesAvailabilityErrorDialog");
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
                    connectionStatusCode,
                    MainActivity.this,
                    REQUEST_GOOGLE_PLAY_SERVICES);
            dialog.show();
        }
    }
}
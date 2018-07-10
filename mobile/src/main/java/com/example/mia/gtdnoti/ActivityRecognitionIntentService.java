package com.example.mia.gtdnoti;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

/**
 * Intent Sevice for searching for detecting the activity and get name back for storage
 * into database
 *
 * Created by mia on 2/1/16.
 */
public class ActivityRecognitionIntentService extends IntentService {
    //LogCat
    private static final String TAG = ActivityRecognitionIntentService.class.getSimpleName();

    public ActivityRecognitionIntentService() {
        super("ActivityRecognitionIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            // Sleep for 5 mins for next detection of human activity
            Log.d(TAG, "Debugger: onHandleIntent()");
            Thread.sleep(300000);
            if (ActivityRecognitionResult.hasResult(intent)) {
                // Extract the result from the Response
                ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
                DetectedActivity detectedActivity = result.getMostProbableActivity();

                // Get the Confidence and Name of Activity
                int confidence = detectedActivity.getConfidence();
                String mostProbableName = getActivityName(detectedActivity.getType());

                // Fire the intent with activity name & confidence
                Intent i = new Intent("ImActive");
                i.putExtra("activity", mostProbableName);
                i.putExtra("confidence", confidence);

                // Send Broadcast to be listen in MainActivity
                this.sendBroadcast(i);

            } else {
                Log.d(TAG, "Intent had no data returned");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the activity name
     */
    public String getActivityName(int type) {
        switch (type) {
            case DetectedActivity.IN_VEHICLE:
                return "In Vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "On Bicycle";
            case DetectedActivity.ON_FOOT:
                return "On Foot";
            case DetectedActivity.WALKING:
                return "Walking";
            case DetectedActivity.STILL:
                return "Still";
            case DetectedActivity.TILTING:
                return "Tilting";
            case DetectedActivity.RUNNING:
                return "Running";
            case DetectedActivity.UNKNOWN:
                return "Unknown";
        }
        return "N/A";
    }
}
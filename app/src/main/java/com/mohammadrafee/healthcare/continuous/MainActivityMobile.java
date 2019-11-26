/*
 * Copyright (C) 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohammadrafee.healthcare.continuous;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.SensorsClient;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SessionReadRequest;
import com.google.android.gms.fitness.result.SessionReadResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.mohammadrafee.healthcare.common.logger.Log;
import com.mohammadrafee.healthcare.common.logger.LogWrapper;
import com.mohammadrafee.healthcare.common.logger.MessageOnlyLogFilter;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.google.android.gms.wearable.Wearable.getMessageClient;


/**
 * This sample demonstrates combining the Recording API and History API of the Google Fit platform
 * to record steps, and display the daily current step count. It also demonstrates how to
 * authenticate a user with Google Play Services.
 */
public class MainActivityMobile extends AppCompatActivity implements MessageClient.OnMessageReceivedListener {

    HeartRate heartRate = new HeartRate();
    SensorsClient sensorsClient;
    public static final String TAG = "StepCounter";
    private static final int REQUEST_OAUTH_REQUEST_CODE = 0x1001;
    private static final String START_ACTIVITY_PATH = "/start-activity";
    FusedLocationProviderClient fusedLocationClient;
    private OnDataPointListener mListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // This method sets up our custom logger, which will print all log messages to the device
        // screen, as well as to adb logcat.
        initializeLogging();

        FitnessOptions fitnessOptions =
                FitnessOptions.builder()
                        .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE)
                        .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
                        .addDataType(DataType.TYPE_ACTIVITY_SEGMENT)
                        .addDataType(DataType.TYPE_HEART_RATE_BPM)
                        .build();

        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this,
                    REQUEST_OAUTH_REQUEST_CODE,
                    GoogleSignIn.getLastSignedInAccount(this),
                    fitnessOptions);
        } else {
            subscribe();
        }

        // Get Location

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);


        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_DENIED) {

            Log.d("permission", "permission denied to Check Location - requesting it");
            String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};

            requestPermissions(permissions, REQUEST_OAUTH_REQUEST_CODE);

        }

        // End Location

        // Get Heart Rate
        getMessageClient(this).addListener(this);
    }

    public void onEmergencyClick(View view) {
        new StartWearableActivityTask().execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        JSONObject postObject = new JSONObject();
        final JSONObject locationObject = new JSONObject();
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            try {
                                locationObject.put("latitude", location.getLatitude());
                                locationObject.put("longitude", location.getLongitude());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            Log.i(TAG, "Current Latitude:" + location.getLatitude());
                        }
                    }
                });

        String id = "5dcb7f0072c6150d68c07a59";
        final String url = "http://ec2-3-85-74-6.compute-1.amazonaws.com/doctors/emergencies/:patientId";
        final String name = "Rafee";
        try {
            postObject.put("correpond_id", id);
            postObject.put("locaton", locationObject);
            postObject.put("heartrate", heartRate.getHeartRate());
            postObject.put("name", name);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        postRequest(url, postObject);

//        final DataReadRequest readRequest = new DataReadRequest.Builder().read(DataType.TYPE_HEART_RATE_BPM).setTimeRange(System.currentTimeMillis()-1000,System.currentTimeMillis(),TimeUnit.MILLISECONDS).build();
//        Log.d(TAG, "Read from Sensor " + readRequest);
//        DataReadResult dataReadResult=Fitness.HistoryApi.readData(readRequest).await(3,TimeUnit.SECONDS);
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(START_ACTIVITY_PATH)) {
            Log.d(TAG, "Received Message" + messageEvent.toString());
            Log.d(TAG, String.valueOf(ByteBuffer.wrap(messageEvent.getData()).getInt()));
            heartRate.setHeartRate(ByteBuffer.wrap(messageEvent.getData()).getInt());
//            mTextView.setText(messageEvent.toString());
        }
    }

    public void onSyncClick(View view) {

        // Get Sleep Time
        final TextView sleepBox = findViewById(R.id.sleep);
        final String url = "https://c7trbjve0a.execute-api.us-east-1.amazonaws.com/v1/datas/steps";
        final JSONObject jsonObject = new JSONObject();
        String id = "5dcb7f0072c6150d68c07a59";
        try {
            long today = System.currentTimeMillis();
            jsonObject.put("id", id);
            jsonObject.put("date", today);
        } catch (JSONException e) {
            e.printStackTrace();
        }
//        sleepBox.setText(String.valueOf(sleepTime));
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_MONTH, -1);
        long startTime = cal.getTimeInMillis();

        final java.text.DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Log.i(TAG, "Range Start: " + dateFormat.format(startTime));
        Log.i(TAG, "Range End: " + dateFormat.format(endTime));
        // Getting Sleep Data
        final SessionReadRequest.Builder sessionBuilder = new SessionReadRequest.Builder()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .read(DataType.TYPE_ACTIVITY_SEGMENT)
                .readSessionsFromAllApps()
                .enableServerQueries();

        final SessionReadRequest readRequest = sessionBuilder.build();
        Fitness.getSessionsClient(this, GoogleSignIn.getLastSignedInAccount(this))
                .readSession(readRequest)
                .addOnSuccessListener(new OnSuccessListener<SessionReadResponse>() {
                    @Override
                    public void onSuccess(SessionReadResponse sessionReadResponse) {
                        // Get a list of the sessions that match the criteria to check the result.
                        List<Session> sessions = sessionReadResponse.getSessions();
                        Log.i(TAG, "Session read was successful. Number of returned sessions is: "
                                + sessions.size());

                        for (Session session : sessions) {
                            // Process the session
                            if (session.getName().equals("Sleep")) {
                                long sleepTime;
                                DecimalFormat decimalFormat = new DecimalFormat("#.00");
                                Log.i(TAG, "Time to start sleep" + dateFormat.format(session.getStartTime(TimeUnit.MILLISECONDS)));
                                Log.i(TAG, "Time to end sleep" + dateFormat.format(session.getEndTime(TimeUnit.MILLISECONDS)));
                                sleepTime = session.getEndTime(TimeUnit.MINUTES) - session.getStartTime(TimeUnit.MINUTES);
                                Log.i(TAG, "Total Sleep Time" + sleepTime + "Minutes");
                                int sleepTimeInHours = (int) (sleepTime / 60);
                                String sleepTimeAsString = decimalFormat.format(sleepTimeInHours);
                                sleepBox.setText(sleepTimeAsString);
                                try {
                                    jsonObject.put("sleepingHours", sleepTimeInHours);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        if (sessions.size() == 0) {
                            try {
                                jsonObject.put("sleepingHours", 0);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                })
                .

                        addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.i(TAG, "Failed to read session");
                            }
                        });

        Fitness.getHistoryClient(this, GoogleSignIn.getLastSignedInAccount(this))
                .

                        readDailyTotal(DataType.TYPE_STEP_COUNT_DELTA)
                .

                        addOnSuccessListener(
                                new OnSuccessListener<DataSet>() {
                                    @Override
                                    public void onSuccess(DataSet dataSet) {
                                        long total =
                                                dataSet.isEmpty()
                                                        ? 0
                                                        : dataSet.getDataPoints().get(0).getValue(Field.FIELD_STEPS).asInt();
//                                steps[0] = total;
                                        Log.i(TAG, "Total no of steps: " + total);
                                        TextView stepsBox = findViewById(R.id.steps);
                                        stepsBox.setText(String.valueOf(total));
                                        try {
                                            jsonObject.put("steps", total);
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                        putRequest(url, jsonObject);
                                    }
                                })
                .

                        addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.w(TAG, "There was a problem getting the step count.", e);
                                    }
                                });
//                sendRequest();
    }

    @WorkerThread
    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<>();

        Task<List<Node>> nodeListTask =
                Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();

        try {
            // Block on a task and get the result synchronously (because this is on a background
            // thread).
            List<Node> nodes = Tasks.await(nodeListTask);

            for (Node node : nodes) {
                results.add(node.getId());
            }

        } catch (ExecutionException exception) {
            android.util.Log.e(TAG, "Task failed: " + exception);

        } catch (InterruptedException exception) {
            android.util.Log.e(TAG, "Interrupt occurred: " + exception);
        }

        return results;
    }

    private void putRequest(String url, JSONObject jsonObject) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest putRequest;
        putRequest = new JsonObjectRequest(Request.Method.PUT, url, jsonObject,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        // response
                        Log.i(TAG, response.toString());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // error
                        Log.w(TAG, error.toString());
                    }
                }
        );
        queue.add(putRequest);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_OAUTH_REQUEST_CODE) {
                subscribe();
            }
        }
    }

    /**
     * Records step data by requesting a subscription to background step data.
     */
    public void subscribe() {
        // To create a subscription, invoke the Recording API. As soon as the subscription is
        // active, fitness data will start recording.
        Fitness.getRecordingClient(this, GoogleSignIn.getLastSignedInAccount(this))
                .subscribe(DataType.TYPE_STEP_COUNT_CUMULATIVE)
                .addOnCompleteListener(
                        new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (task.isSuccessful()) {
                                    Log.i(TAG, "Successfully subscribed!");
                                } else {
                                    Log.w(TAG, "There was a problem subscribing.", task.getException());
                                }
                            }
                        });
    }

    private void postRequest(String url, JSONObject jsonObject) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest postRequest;
        postRequest = new JsonObjectRequest(Request.Method.POST, url, jsonObject,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        // response
                        Log.i(TAG, response.toString());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // error
                        Log.w(TAG, error.toString());
                    }
                }
        );
        queue.add(postRequest);
    }

    @WorkerThread
    private void sendStartActivityMessage(String node) {

        Task<Integer> sendMessageTask =
                Wearable.getMessageClient(this).sendMessage(node, START_ACTIVITY_PATH, new byte[0]);

        try {
            // Block on a task and get the result synchronously (because this is on a background
            // thread).
            Integer result = Tasks.await(sendMessageTask);
            Log.d(TAG, "Message sent: " + result);

        } catch (ExecutionException exception) {
            android.util.Log.e(TAG, "Task failed: " + exception);

        } catch (InterruptedException exception) {
            android.util.Log.e(TAG, "Interrupt occurred: " + exception);
        }
    }

    /**
     * Reads the current daily step total, computed from midnight of the current day on the device's
     * current timezone.
     */

    public class HeartRate {
        int heartRate;

        public int getHeartRate() {
            return heartRate;
        }

        public void setHeartRate(int heartRate) {
            this.heartRate = heartRate;
        }
    }

    private class StartWearableActivityTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... args) {
            Collection<String> nodes = getNodes();
            for (String node : nodes) {
                Log.d(TAG, "Node " + node);
                sendStartActivityMessage(node);
            }
            return null;
        }

    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the main; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.main, menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        int id = item.getItemId();
//        if (id == R.id.action_read_data) {
//            readData();
//            return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }

    /**
     * Initializes a custom log class that outputs both to in-app targets and logcat.
     */
    private void initializeLogging() {
        // Wraps Android's native log framework.
        LogWrapper logWrapper = new LogWrapper();
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper);
        // Filter strips out everything except the message text.
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);
        Log.i(TAG, "Ready");
    }
}

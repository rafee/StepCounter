package com.mohammadrafee.healthcare.healthcarewear;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.google.android.gms.wearable.Wearable.getMessageClient;
import static com.google.android.gms.wearable.Wearable.getNodeClient;

//import com.google.android.gms.wearable.Wearable;

public class MainActivityWear extends WearableActivity implements MessageClient.OnMessageReceivedListener, SensorEventListener {

    HeartRate heartRate = new HeartRate();

    String TAG = "Wear";
    private static final String START_ACTIVITY_PATH = "/start-activity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_wear);

        mTextView = findViewById(R.id.text);
        SensorManager mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));
        Sensor mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);

        // Enables Always-on
//        setAmbientEnabled();
        getMessageClient(this).addListener(this);
    }

    private TextView mTextView;

    //
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            String msg = "Heart rate " + (int) event.values[0];
            mTextView.setText(msg);
            heartRate.setHeartRate((int) event.values[0]);
            Log.d(TAG, msg);
        }
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(START_ACTIVITY_PATH)) {
            Log.d(TAG, "Received Message" + messageEvent.toString());
            new StartWearableActivityTask().execute();
//            mTextView.setText(messageEvent.toString());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
    }

    @WorkerThread
    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<>();

        Task<List<Node>> nodeListTask =
                getNodeClient(getApplicationContext()).getConnectedNodes();

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

    @WorkerThread
    private void sendStartActivityMessage(String node) {

        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(heartRate.getHeartRate());
        Log.d(TAG, String.valueOf(heartRate.getHeartRate()));

        Task<Integer> sendMessageTask =
                getMessageClient(this).sendMessage(node, START_ACTIVITY_PATH, b.array());

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
}

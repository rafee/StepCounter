package com.mohammadrafee.healthcare.healthcarewear;

import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;

public class MainActivityWear extends WearableActivity implements MessageClient.OnMessageReceivedListener {

    String TAG = "Wear";
    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_wear);

        mTextView = findViewById(R.id.text);
//        SensorManager mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));
//        Sensor mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
//        mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);

        // Enables Always-on
        setAmbientEnabled();
    }

//    @Override
//    public void onSensorChanged(SensorEvent event) {
//        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
//            String msg = "" + (int) event.values[0];
//            mTextView.setText(msg);
//            Log.d(TAG, msg);
//        }
//    }
//
//    @Override
//    public void onAccuracyChanged(Sensor sensor, int accuracy) {
//        Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
//    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        mTextView.setText(messageEvent.toString());
    }
}

package com.example.android.sunshine.app;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public class WearableTestActivity extends AppCompatActivity implements
        DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private int cnt;
    private EditText text;
    private GoogleApiClient mGoogleApiClient;
    private static final String COUNT_KEY = "com.example.android.sunshine.app.key.count";

    private static final String TAG = WearableTestActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wearable_test);

        text = (EditText) findViewById(R.id.editText);

        Button button = (Button) findViewById(R.id.sendDataItem);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendDataItem();
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();
    }

    /*
        PutDataRequest.setUrgent() added in Google Play services 8.3.0
     */
    private void sendDataItem() {
        cnt++;
        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/weather_info");
        putDataMapReq.getDataMap().putInt(COUNT_KEY, cnt);

        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        putDataReq.setUrgent();

        PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(final DataApi.DataItemResult result) {
                if (result.getStatus().isSuccess()) {
                    Log.d(TAG, "sendDataItem - data item set: " + result.getDataItem().getUri());
                } else {
                    Log.d(TAG, "sendDataItem - unsuccessful");
                }
            }
        });

        text.setText("Sent data item: " + cnt);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.v(TAG, "onConnected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.v(TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.v(TAG, "onConnectionFailed");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.v(TAG, "onDataChanged");
    }
}

/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
// /Users/business/Documents//android-sdk-macosx/platform-tools/adb -d forward tcp:5601 tcp:5601
// git push -u origin
/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WeatherWatchFace extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WeatherWatchFace.Engine> mWeakReference;

        public EngineHandler(WeatherWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WeatherWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine  implements
            DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        private GoogleApiClient mGoogleApiClient;
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        float mXOffset;
        float mYOffset;
        private int mLowTemp = -10;
        private int mHighTemp = 99;
        private int mPicIdx;
        private int mTextSize = 22;
        private int topPosition = 96;
        private int distBetweenLines = 41;

        private static final String LOW_TEMP = "com.example.android.sunshine.app.key.LOW.TEMP";
        private static final String HIGH_TEMP = "com.example.android.sunshine.app.key.HIGGH.TEMP";
        private static final String PIC_IDX = "com.example.android.sunshine.app.key.PIC.IDX";
        private static final String ADDED_TO_COMPILE = "com.example.android.sunshine.app.key.PIC.IDX.XXX";

        private static final String TEXT_SIZE = "com.example.android.sunshine.app.key.TEXT.SIZE";
        private static final String TOP_POSITION = "com.example.android.sunshine.app.key.TOP.POSITION";
        private static final String DISTANCE_BETWEEN_LINES = "com.example.android.sunshine.app.key.DISTANCE.BETWEEN.LINES";

        private final String TAG = Engine.class.getSimpleName() + "BR";

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mGoogleApiClient = new GoogleApiClient.Builder(WeatherWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(WeatherWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = WeatherWatchFace.this.getResources();
//            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mYOffset = resources.getDimension(R.dimen.digital_time_y_offset);

            mBackgroundPaint = new Paint();
//            mBackgroundPaint.setColor(resources.getColor(R.color.background));
            mBackgroundPaint.setColor(resources.getColor(R.color.background0));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
//            mTextPaint.setTextSize(mTextSize);
//            Log.v(TAG, "onCreate - text size/mYOffset: " + mTextPaint.getTextSize() + "/" + mYOffset);
//            I do not see the effect of the below
//            mTextPaint.setTextSize(40);

            mTime = new Time();
        }

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.v(TAG, "onConnectionSuspended - i: " + i);
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.v(TAG, "onDataChanged - start");
            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    // DataItem changed
                    DataItem item = event.getDataItem();
                    if (item.getUri().getPath().compareTo("/weather_info") == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        mLowTemp = dataMap.getInt(LOW_TEMP);
                        mHighTemp = dataMap.getInt(HIGH_TEMP);
                        mPicIdx = dataMap.getInt(PIC_IDX);
                        mTextSize = dataMap.getInt(TEXT_SIZE);
                        topPosition = dataMap.getInt(TOP_POSITION);
                        distBetweenLines = dataMap.getInt(DISTANCE_BETWEEN_LINES);

                        mTextPaint.setTextSize(mTextSize);
                        Log.v(TAG, "onDataChanged - mLowTemp/mHighTemp/mPicIdx: " + mLowTemp + "/" + mHighTemp + "/" + mPicIdx);
                        Log.v(TAG, "onDataChanged - mTextSize/topPosition/distBetweenLines: " + mTextSize + "/" + topPosition + "/" + distBetweenLines);
                    }
                } else if (event.getType() == DataEvent.TYPE_DELETED) {
                    // DataItem deleted
                }
            }
            dataEvents.release();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                Wearable.DataApi.removeListener(mGoogleApiClient, this);
                mGoogleApiClient.disconnect();
            }
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mGoogleApiClient.connect();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WeatherWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WeatherWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WeatherWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            // FIXME: 23/06/2016 add the value below to resources and use code above
            textSize = mTextSize;

            Log.v(TAG, "onApplyWindowInsets - mXOffset: " + mXOffset);
            mXOffset = 22.5F;
//            mYOffset = 97.5F;
            mYOffset = topPosition;
            Log.v(TAG, "onApplyWindowInsets - mXOffset/mYOffset: " + mXOffset + "/" + mYOffset);
            mTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = WeatherWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
//                            R.color.background : R.color.background2));
                            R.color.background0 : R.color.background2));
                    break;
            }
            invalidate();
        }

        private SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy");
        private float verticalCenterPos;

        private boolean fstTimeDone;
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            if (!fstTimeDone) {
                verticalCenterPos = bounds.width() / 2F;
                Log.v(TAG, "onDraw - verticalCenterPos/mTextSize should/is : " + verticalCenterPos + "/" + mTextSize + "/" + mTextPaint.getTextSize());
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            float yOffset = mYOffset;
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d = %3d", mTime.hour, mTime.minute, mLowTemp)
                    : String.format("%d:%02d = %3d", mTime.hour, mTime.minute, mLowTemp);
            float xOffset = getXOffset(text);
            canvas.drawText(text, xOffset, yOffset, mTextPaint);

            // Draw today's date
            yOffset = yOffset + distBetweenLines;
            Date today = new Date();
            String formattedDate = formatter.format(today);
            canvas.drawText(formattedDate, getXOffset(formattedDate), yOffset, mTextPaint);

            // Draw low and high temperatures
            yOffset = yOffset + distBetweenLines;
            String lowHighTemps = mLowTemp + " - " + mHighTemp;
            canvas.drawText(lowHighTemps, getXOffset(lowHighTemps), yOffset, mTextPaint);
            fstTimeDone = true;
        }

        private float getXOffset(String  text) {
            float textWidth = mTextPaint.measureText(text);
            float xOffset = verticalCenterPos - (mTextPaint.measureText(text) / 2F);
//            Log.v(TAG, "getXOffset - xOffset : " + xOffset);
            return xOffset;
//            if (!fstTimeDone) {
//                Log.v(TAG, "onDraw - bounds left/textWidth/xOffset : " + bounds.left + "/" + textWidth + "/" + xOffset);
//            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.v(TAG, "onConnectionFailed");
        }
    }
}

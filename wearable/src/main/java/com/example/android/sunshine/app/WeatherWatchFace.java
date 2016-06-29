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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
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
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
// /Users/business/Documents//android-sdk-macosx/platform-tools/adb -d forward tcp:5601 tcp:5601
// git push -u origin
/**
 * Digital weather watch face. In ambient mode, the temperatures aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WeatherWatchFace extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

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
        // FIXME: 29/06/2016 make private below
        Paint mBackgroundPaint;
        Paint mTimePaint;
        Paint mDatePaint;
        Paint mWeatherImagePaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;
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

        private float mYOffset;
        private String mLowTemp = "10";
        private String mHighTemp = "25";
        private int mWeatherId;
        private int mDistBetweenLines;
        private Bitmap mCurrWeatherArt;
        private int mWeatherConditionImageWithAndHeight;

        private static final String LOW_TEMP = "com.example.android.sunshine.app.key.LOW.TEMP";
        private static final String HIGH_TEMP = "com.example.android.sunshine.app.key.HIGH.TEMP";
        private static final String WEATHER_ID = "com.example.android.sunshine.app.key.WEATHER_ID";

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

            mYOffset = resources.getDimension(R.dimen.digital_time_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background0));

            mTimePaint = new Paint();
            mTimePaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTimePaint.setTypeface(BOLD_TYPEFACE);

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_text));

            mWeatherImagePaint = new Paint();
            mWeatherImagePaint = createTextPaint(resources.getColor(R.color.digital_text));

            mHighTempPaint = new Paint();
            mHighTempPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mHighTempPaint.setTypeface(BOLD_TYPEFACE);

            mLowTempPaint = new Paint();
            mLowTempPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTime = new Time();
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.v(TAG, "onConnected - start");
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.v(TAG, "onConnectionSuspended - i: " + i);
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem item = event.getDataItem();
                    if (item.getUri().getPath().compareTo("/weather_info") == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        mLowTemp = dataMap.getString(LOW_TEMP);
                        mHighTemp = dataMap.getString(HIGH_TEMP);
                        mWeatherId = dataMap.getInt(WEATHER_ID);

                        int i = Utility.getArtResourceForWeatherCondition(mWeatherId);
                        if (i != -1) {
                            BitmapDrawable weatherDrawable =
                                    (BitmapDrawable) getResources().getDrawable(Utility.getArtResourceForWeatherCondition(mWeatherId),null);

                            Bitmap origBitmap = null;
                            if (weatherDrawable != null ) {
                                origBitmap = weatherDrawable.getBitmap();
                                mCurrWeatherArt = Bitmap.createScaledBitmap(origBitmap,
                                        mWeatherConditionImageWithAndHeight,
                                        mWeatherConditionImageWithAndHeight,
                                        true);
                            }
                        }
                    }
                }
                fstTimeDone = false;
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
            float textTimeSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_time_size_round : R.dimen.digital_text_time_size);
            float firstLineYPosition = resources.getDimension(R.dimen.digital_first_line_y_position);
            float textDateSize = resources.getDimension(R.dimen.digital_text_date_size);
            float textHighTempSize = resources.getDimension(R.dimen.digital_text_high_temp_size);
            float textLowTempSize = resources.getDimension(R.dimen.digital_text_low_temp_size);

            mWeatherConditionImageWithAndHeight = (int) resources.getDimension(R.dimen.digital_weather_condition_image_size);
            mDistBetweenLines = (int) resources.getDimension(R.dimen.digital_distance_between_lines);
            mYOffset = firstLineYPosition;
            mTimePaint.setTextSize(textTimeSize);
            mDatePaint.setTextSize(textDateSize);
            mWeatherImagePaint.setTextSize(22);
            mHighTempPaint.setTextSize(textHighTempSize);
            mLowTempPaint.setTextSize(textLowTempSize);
            Log.v(TAG, "onApplyWindowInsets - end Jun 29 9:56");
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
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mWeatherImagePaint.setAntiAlias(!inAmbientMode);
                    mHighTempPaint.setAntiAlias(!inAmbientMode);
                    mLowTempPaint.setAntiAlias(!inAmbientMode);
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

        private SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault());
        private boolean fstTimeDone;

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            float verticalCenterPos = bounds.centerX();

            float yOffset = mYOffset;
            mTime.setToNow();
            String text = String.format("%d:%02d", mTime.hour, mTime.minute);
            float xOffset = getXOffset(text, mTimePaint, verticalCenterPos);
            canvas.drawText(text, xOffset, yOffset, mTimePaint);

            // Draw today's date
            yOffset = yOffset + mDistBetweenLines;
            Date today = new Date();
            String formattedDate = formatter.format(today);
            canvas.drawText(formattedDate, getXOffset(formattedDate, mDatePaint, verticalCenterPos), yOffset, mDatePaint);

            if (!mAmbient) {

                // Draw line separator
                yOffset = yOffset + mDistBetweenLines / 2;
                float lineSepHalfLgth = bounds.width() / 10;
                canvas.drawLine(verticalCenterPos - lineSepHalfLgth, yOffset, verticalCenterPos + lineSepHalfLgth, yOffset, mTimePaint);

                yOffset = yOffset + mDistBetweenLines;
                float spacePix = 25;
                float highTempSize = mHighTempPaint.measureText(mHighTemp);
                float lowTempSize = mLowTempPaint.measureText(mLowTemp);
                float lineWidth;
                float imageXOffset;
                float imageTopPosition;
                float highXOffset;
                if (mCurrWeatherArt == null) {
                    lineWidth = highTempSize
                            + spacePix
                            + lowTempSize;
                    imageXOffset = 0;
                    imageTopPosition = 0;
                    highXOffset = verticalCenterPos - lineWidth / 2;
                } else {
                    lineWidth = mWeatherConditionImageWithAndHeight
                            + spacePix
                            + highTempSize
                            + spacePix
                            + lowTempSize;
                    imageXOffset = verticalCenterPos - lineWidth / 2;
                    imageTopPosition = calculateImageTopPosition(yOffset, mHighTempPaint, mHighTemp);
                    highXOffset = imageXOffset + mWeatherConditionImageWithAndHeight + spacePix;
                }
                float lowXOffset = highXOffset + highTempSize + spacePix;

                // Draw image
                if (mCurrWeatherArt != null) {
                    canvas.drawBitmap(mCurrWeatherArt, imageXOffset, imageTopPosition, mWeatherImagePaint);
                }
                // Draw high temp
                canvas.drawText(mHighTemp, highXOffset, yOffset, mHighTempPaint);
                // Draw low temp
                canvas.drawText(mLowTemp, lowXOffset, yOffset, mLowTempPaint);
            }

            fstTimeDone = true;
        }

        private float calculateImageTopPosition(float yOffset, Paint textPaint, String highTemp) {
            Rect rect = new Rect();
            textPaint.getTextBounds(highTemp, 0, highTemp.length(), rect);
            return yOffset - rect.height()/2 - mWeatherConditionImageWithAndHeight / 2;
        }

        private void logFstTime(String msg) {
            if (!fstTimeDone) {
                Log.v(TAG, msg);
            }
        }

        private float getXOffset(String text, Paint paint, float verticalCenterPos) {
            float xOffset = verticalCenterPos - (paint.measureText(text) / 2F);
            return xOffset;
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

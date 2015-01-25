package com.example.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Created by ferdy182 on 21/12/14.
 */
public class DigitalWatchFaceService extends CanvasWatchFaceService {
    static final int MSG_UPDATE_TIME = 0;

    /**
     * Update rate in milliseconds for normal (not ambient and not mute) mode. We update twice
     * a second to blink the colons.
     */
    private static final long NORMAL_UPDATE_RATE_MS = 500;

    /**
     * Update rate in milliseconds for mute mode. We update every minute, like in ambient mode.
     */
    private static final long MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

    Time mTime;
    Date mDate;
    Paint fontPaint = new Paint();
    int fontColor = Color.argb(255,43,43,43);
    int bgColor = Color.argb(255,147,177,162);
    boolean mLowBitAmbient;
    boolean mBurnInProtection;
    private final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm");
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("c dd MMM");
    private boolean mRegisteredZoneReceiver;


    @Override
    public Engine onCreateEngine() {
        /* provide your watch face implementation */
        return new Engine();
    }

    /* implement service callback methods */
    private class Engine extends CanvasWatchFaceService.Engine {

        /** How often {@link #mUdateTimerHandler} ticks in milliseconds. */
        long mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;

        final Handler mUdateTimerHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case  MSG_UPDATE_TIME:
                        Log.i("watch","message handled. timer "+shouldTimeBeRunning());
                        invalidate();
                        if(shouldTimeBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = NORMAL_UPDATE_RATE_MS - (timeMs % NORMAL_UPDATE_RATE_MS);
                            mUdateTimerHandler.sendEmptyMessageAtTime(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };
        private float mXoffset;


        @Override
        public void onCreate(SurfaceHolder holder) {
            /* initialize your watch face */
            fontPaint.setTypeface(Typefaces.get(getApplicationContext(), "font/digital.ttf"));
            fontPaint.setTextSize(100f);
//            fontPaint.setTextAlign(Paint.Align.CENTER);
            mTime = new Time();
            mDate = new Date();

            setWatchFaceStyle(new WatchFaceStyle.Builder(DigitalWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build()
            );


        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            /* get device features (burn-in, low-bit ambient) */
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            /* the time changed */
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            /* the wearable switched between modes */
            if(mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                fontPaint.setAntiAlias(antiAlias);
            }

            invalidate();
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            /* draw your watch face */
            if(isInAmbientMode()) {
                fontPaint.setColor(Color.LTGRAY);
                canvas.drawColor(Color.BLACK);
            } else {
                fontPaint.setColor(fontColor);
                canvas.drawColor(bgColor);
            }

            mTime.setToNow();

            int width = bounds.width();
            int height = bounds.height();

            boolean drawColon = (System.currentTimeMillis() % 1000 < 500) || isInAmbientMode();

            float x = getResources().getDimension(R.dimen.offset_x_square);
            float y = height/2f;
            String hourString = String.format("%02d",mTime.hour);
            String minuteString = String.format("%02d",mTime.minute);

            canvas.drawText(hourString, x, y, fontPaint);
            x += fontPaint.measureText(hourString);
            if(drawColon)
                canvas.drawText(":", x, y, fontPaint);
            x += fontPaint.measureText(":");

            canvas.drawText(minuteString, x,y,fontPaint);

            if(!isInAmbientMode()) {
                x += fontPaint.measureText(minuteString);
                canvas.drawText(String.format(":%02d",mTime.second),x,y,fontPaint);
            }
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            mXoffset = getResources().getDimension(R.dimen.offset_x_square);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            /* the watch face became visible or invisible */
            if(visible) {
                registerReceiver();

                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            updateTimer();
        }

        private void updateTimer() {
            mUdateTimerHandler.removeMessages(MSG_UPDATE_TIME);
            if(shouldTimeBeRunning())
                mUdateTimerHandler.sendEmptyMessage(MSG_UPDATE_TIME);
        }

        private boolean shouldTimeBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }

    private void unregisterReceiver() {
        if(!mRegisteredZoneReceiver)
            return;

        mRegisteredZoneReceiver = false;
        unregisterReceiver(mTimeZoneReceiver);
    }

    private void registerReceiver() {
        if(mRegisteredZoneReceiver)
            return;
        mRegisteredZoneReceiver = true;
        registerReceiver(mTimeZoneReceiver, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
    }

    /* receiver to update the time zone */
    final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mTime.clear(intent.getStringExtra("time-zone"));
            mTime.setToNow();
        }
    };


}

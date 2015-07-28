package com.mojo.yamate.server;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.Status;

import org.json.JSONException;
import org.json.JSONArray;

import com.spotify.sdk.android.Spotify;
import com.spotify.sdk.android.playback.Config;
import com.spotify.sdk.android.playback.ConnectionStateCallback;
import com.spotify.sdk.android.playback.Player;
import com.spotify.sdk.android.playback.PlayerNotificationCallback;
import com.spotify.sdk.android.playback.PlayerState;
import com.spotify.sdk.android.playback.PlayerStateCallback;

import java.util.concurrent.RejectedExecutionException;

public class YamateService extends Service implements ConnectionStateCallback, PlayerNotificationCallback {
    private static final String TAG = YamateService.class.getSimpleName();

    private static final int MESSAGE_CONTACT_SUBMISSION = 1;
    private static final long MILLIS_IN_SECOND = 1000L;
    private static final long MILLIS_IN_MINUTE = 60 * MILLIS_IN_SECOND;
    private static final long MILLIS_IN_HALF_HOUR = 30 * MILLIS_IN_MINUTE;
    private static final long MILLIS_IN_HOUR = 60 * MILLIS_IN_MINUTE;
    private static final long MILLIS_IN_DAY = 24 * MILLIS_IN_HOUR;

    private Context mContext;
    private NotificationManager mNM;
    private Thread mThread;
    private Handler mBusHandler;
    private YMService mYMService;
    private Player mPlayer;
    private String mAccessToken;
    private int mNewSeekPosition;

    static {
        System.loadLibrary("alljoyn_java");
    }

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.local_service_started;

    @Override
    public void onCreate() {
        Log.d(TAG, "enable_catalog_contacts");
        mContext = this;
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();

        startAllJoynServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        // if (null != intent && intent.getBooleanExtra(LoginReceiver.EXTRA_SUBMISSION, false)) {
        //     submitCabDocument(100);
        // }

        mAccessToken = intent.getStringExtra(YamateServer.EXTRA_KEY_ACCESS_TOKEN);
        Log.d(TAG, "Service onCreate AccessToken:" + mAccessToken);

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "disable_catalog_contacts");
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);
        if (mThread != null) {
            mThread.interrupt();
        }
        mBusHandler.sendEmptyMessage(BusHandler.DISCONNECT);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();

        /* Disconnect to prevent resource leaks. */
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind, return null");
        return null;
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.local_service_started);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_spotify, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, YamateServer.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.local_service_label),
                text, contentIntent);

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }


    private void startAllJoynServer() {
        /* Make all AllJoyn calls through a separate handler thread to prevent blocking the UI. */
        HandlerThread busThread = new HandlerThread("BusHandler");
        busThread.start();
        mBusHandler = new BusHandler(busThread.getLooper());

        /* Start our service. */
        mYMService = new YMService();
        mBusHandler.sendEmptyMessage(BusHandler.CONNECT);
    }

    class BusHandler extends Handler {
        private static final String SERVICE_NAME = "com.ubnt.sleepdroid.server";
        private static final short CONTACT_PORT = 42;

        private BusAttachment mBus;

        /* These are the messages sent to the BusHandler from the UI. */
        public static final int CONNECT = 1;
        public static final int DISCONNECT = 2;

        public BusHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            /* Connect to the bus and start our service. */
                case CONNECT: {
                    org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
                    mBus = new BusAttachment(getPackageName(), BusAttachment.RemoteMessage.Receive);
                    mBus.registerBusListener(new BusListener());

                    Status status = mBus.registerBusObject(mYMService, "/YamateService");
                    logStatus("BusAttachment.registerBusObject()", status);
                    if (status != Status.OK) {
//                    finish();
                        return;
                    }

                    status = mBus.connect();
                    logStatus("BusAttachment.connect()", status);
                    if (status != Status.OK) {
//                    finish();
                        return;
                    }

                    Mutable.ShortValue contactPort = new Mutable.ShortValue(CONTACT_PORT);

                    SessionOpts sessionOpts = new SessionOpts();
                    sessionOpts.traffic = SessionOpts.TRAFFIC_MESSAGES;
                    sessionOpts.isMultipoint = false;
                    sessionOpts.proximity = SessionOpts.PROXIMITY_ANY;

                    sessionOpts.transports = SessionOpts.TRANSPORT_ANY + SessionOpts.TRANSPORT_WFD;
                    status = mBus.bindSessionPort(contactPort, sessionOpts, new SessionPortListener() {
                        @Override
                        public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
                            Log.i(TAG, "acceptSessionJoiner sessionPort:" + sessionPort);
                            Log.i(TAG, "acceptSessionJoiner joiner:" + joiner);
                            if (sessionPort == CONTACT_PORT) {
                                return true;
                            } else {
                                return false;
                            }
                        }
                    });
                    logStatus(String.format("BusAttachment.bindSessionPort(%d, %s)",
                            contactPort.value, sessionOpts.toString()), status);
                    if (status != Status.OK) {
//                    finish();
                        return;
                    }

                /*
                 * request a well-known name from the bus
                 */
                    int flag = BusAttachment.ALLJOYN_REQUESTNAME_FLAG_REPLACE_EXISTING | BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE;

                    status = mBus.requestName(SERVICE_NAME, flag);
                    logStatus(String.format("BusAttachment.requestName(%s, 0x%08x)", SERVICE_NAME, flag), status);
                    if (status == Status.OK) {
                    /*
                     * If we successfully obtain a well-known name from the bus
                     * advertise the same well-known name
                     */
                        status = mBus.advertiseName(SERVICE_NAME, sessionOpts.transports);
                        logStatus(String.format("BusAttachement.advertiseName(%s)", SERVICE_NAME), status);
                        if (status != Status.OK) {
                        /*
                         * If we are unable to advertise the name, release
                         * the well-known name from the local bus.
                         */
                            status = mBus.releaseName(SERVICE_NAME);
                            logStatus(String.format("BusAttachment.releaseName(%s)", SERVICE_NAME), status);
//                        finish();
                            return;
                        }
                    }

                    break;
                }

            /* Release all resources acquired in connect. */
                case DISCONNECT: {
                /*
                 * It is important to unregister the BusObject before disconnecting from the bus.
                 * Failing to do so could result in a resource leak.
                 */
                    mBus.unregisterBusObject(mYMService);
                    mBus.disconnect();
                    mBusHandler.getLooper().quit();
                    break;
                }

                default:
                    break;
            }
        }
    }

    class YMService implements SpotifyInterface, BusObject {

        public void PlayCurrentTrack(String trackUri, int positionInMs) {
            Log.i(TAG, "Server PlayCurrentTrack");
            playPlaylistUri(trackUri, positionInMs);
        }

        private void playPlaylistUri(final String playlistUri, final int positionInMs) {
            Log.e(TAG, "Service playPlaylistUri mAccessToken:" + mAccessToken);
            Config playerConfig = new Config(YamateService.this, mAccessToken, YamateServer.CLIENT_ID);
            mPlayer = Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
                @Override
                public void onInitialized(Player player) {
                    mPlayer.addConnectionStateCallback(YamateService.this);
                    mPlayer.addPlayerNotificationCallback(YamateService.this);
                    Log.e(TAG, "mPlayer.play:" + playlistUri);
                    Log.e(TAG, "mPlayer.positionInMs:" + positionInMs);
                    mNewSeekPosition = positionInMs;
                    mPlayer.play(playlistUri);
                }

                @Override
                public void onError(Throwable throwable) {
                    Log.e(TAG, "Could not initialize player: " + throwable.getMessage());
                }
            });
        }

        public void TogglePlay() {
            Log.i(TAG, "Server TogglePlay");
            if (mPlayer == null) {
                return;
            }

            mPlayer.getPlayerState(new PlayerStateCallback() {
                @Override
                public void onPlayerState(PlayerState playerState) {
                    if (playerState.playing) {
                        mPlayer.pause();
                    } else {
                        mPlayer.resume();
                    }
                }
            });
        }

        public String GetTrackInfo() {
            final Object lockObject = new Object();
            final TrackInfo trackInfo = new TrackInfo();
            if (mPlayer == null) {
                return toTrackInfoJsonString(trackInfo);
            }

            mPlayer.getPlayerState(new PlayerStateCallback() {
                @Override
                public void onPlayerState(PlayerState playerState) {
                    trackInfo.setPlayerState(playerState);

                    Log.i(TAG, "lockObject.notifyAll");
                    synchronized(lockObject) {
                        lockObject.notifyAll();
                    }
                }
            });

            try {
                Log.i(TAG, "lockObject.wait");
                synchronized(lockObject) {
                    lockObject.wait();
                }
            } catch (InterruptedException e) {
            }

            return toTrackInfoJsonString(trackInfo);
        }

        private String toTrackInfoJsonString(TrackInfo trackInfo) {
            String result = "";
            try {
                result = trackInfo.toJSONObject().toString();
            } catch (JSONException e) {
            }
            return result;
        }
    }

    private long getIntervalStartTime(long time, long interval) {
        long remainder = time % interval;
        return time - remainder;
    }

    private void logStatus(String msg, Status status) {
        String log = String.format("%s: %s", msg, status);
        Log.i(TAG, log);
    }

    @Override
    public void onLoggedIn() {
        Log.d(TAG, "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d(TAG, "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        Log.d(TAG, "Login failed error:" + error);
    }

    @Override
    public void onTemporaryError() {
        Log.d(TAG, "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d(TAG, "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        Log.d(TAG, "Playback event received: " + eventType.name());
        switch (eventType) {
            case AUDIO_FLUSH:
                if (Math.abs(mNewSeekPosition - playerState.positionInMs) > 200) {
                    try {
                        Log.d(TAG, "Player seekToPosition: " + mNewSeekPosition);
                        mPlayer.seekToPosition(mNewSeekPosition);
                    } catch (RejectedExecutionException e) {
                    }
                }
                break;

            default:
                break;
        }
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        Log.d(TAG, "Playback error received: " + errorType.name());
        switch (errorType) {
            // Handle error type as necessary
            default:
                break;
        }
    }
}

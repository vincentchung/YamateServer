package com.mojo.yamate.server;

import com.spotify.sdk.android.playback.PlayerState;

import org.json.JSONException;
import org.json.JSONObject;

public class TrackInfo {

    public static String JSON_TRACK_URI = "track_uri";
    public static String JSON_DURATION_IN_MS = "duration_in_ms";
    public static String JSON_PLAYING = "playing";
    public static String JSON_POSITION_IN_MS = "position_in_ms";

    private String mTrackUri;
    private int mDurationInMs = -1;
    private boolean mPlaying = false;
    private int mPositionInMs = -1;

    public TrackInfo() {
    }

    public void setPlayerState(PlayerState playerState) {
        mTrackUri = playerState.trackUri;
        mDurationInMs = playerState.durationInMs;
        mPlaying = playerState.playing;
        mPositionInMs = playerState.positionInMs;
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(JSON_TRACK_URI, mTrackUri);
        json.put(JSON_DURATION_IN_MS, mDurationInMs);
        json.put(JSON_PLAYING, mPlaying);
        json.put(JSON_POSITION_IN_MS, mPositionInMs);
        return json;
    }

}

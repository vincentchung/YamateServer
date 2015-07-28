package com.mojo.yamate.server;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;


public class YamateServer extends ActionBarActivity {

    public static final String CLIENT_ID = "8b01f1da31ce4b5fa4459819e6d2951d";
    public static final String EXTRA_KEY_ACCESS_TOKEN = "spotify_access_token";
    public static final String KEY_BACKGROUND_SERVICE_ENABLED = "background_service_enabled";
    public static final String REDIRECT_URI = "yourcustomprotocol://callback";
    private static final int REQUEST_CODE = 1337;
    private String mAccessToken;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_yamate_server);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_yamate_server, menu);
        return true;
    }


    private void launchSpotifyLogInPage() {
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN, REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "playlist-read", "playlist-read-private",
                "streaming"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
    }

    public SharedPreferences getDefaultPreference(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void setBackgroundServiceEnabled(Context context, boolean enabled) {
        SharedPreferences.Editor editor = getDefaultPreference(context).edit();
        editor.putBoolean(KEY_BACKGROUND_SERVICE_ENABLED, enabled);
        editor.apply();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.spotify_log_in:
                launchSpotifyLogInPage();
                return true;
            case R.id.enable_background_service:
                boolean isChecked = !item.isChecked();
                item.setChecked(isChecked);
                setBackgroundServiceEnabled(this, isChecked);
                if (isChecked) {
                    Intent intent = new Intent(this, YamateService.class);
                    intent.putExtra(EXTRA_KEY_ACCESS_TOKEN, mAccessToken);
                    startService(intent);
                } else {
                    stopService(new Intent(this, YamateService.class));
                }
                return true;

            case R.id.quit:
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);

            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                mAccessToken = response.getAccessToken();
            }
        }
    }


}

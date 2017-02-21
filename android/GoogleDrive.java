package gr.lexicon.googleDrive;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.MetadataChangeSet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

public class GoogleDrive extends CordovaPlugin implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "GoogleDrivePlugin";
    private static final int REQUEST_CODE_RESOLUTION = 3;
    private GoogleApiClient mGoogleApiClient;
    private String mAction = "";
    private String toLocalDest;
    private String fileid;
    private String localFPath;
    private CallbackContext mCallbackContext;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView){
        super.initialize(cordova, webView);
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(cordova.getActivity())
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        mGoogleApiClient.connect();
        Log.i(TAG,"Plugin initialized");
    }

    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        //JSONObject jobject = args.getJSONObject(0);
        mCallbackContext = callbackContext;
        mAction = action;
        if ("downloadFile".equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        toLocalDest = args.getString(0);
                        fileid = args.getString(1);
                        downloadFile(toLocalDest, fileid, callbackContext);
                    } catch (JSONException ex){ex.getLocalizedMessage();}
                }
            });
            return true;
        } else if("uploadFile".equals(action)){
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        localFPath = args.getString(0);
                        uploadFile(localFPath, callbackContext);
                    }catch(JSONException ex){ex.getLocalizedMessage();}
                }
            });
            return true;
        }
        return false;
    }

    private void downloadFile(String destPath,String fileid,final CallbackContext callbackContext) {
        /*Toast.makeText(webView.getContext(),"just writing something", Toast.LENGTH_LONG).show();
        //Toast.makeText(webView.getContext(),"downloadDB",Toast.LENGTH_LONG).show();
        Path path = Paths.get(destPath);
        String dbname = path.getFileName().toString();
        File dbfile = cordova.getActivity().getDatabasePath(dbname);
        if (dbfile != null){
            Toast.makeText(webView.getContext(),"file found" + dbfile.getName(), Toast.LENGTH_LONG).show();
            Log.i("test", "file found calling getResultsFromAPi");
            // Initialize credentials and service object.
            mCredential = GoogleAccountCredential.usingOAuth2(
                    cordova.getActivity(), Arrays.asList(SCOPES))
                    .setBackOff(new ExponentialBackOff());
            getResultsFromApi();
        }
        callbackContext.success("download called");*/
    }

    private void uploadFile(final String fpath, final CallbackContext callbackContext) {

        Drive.DriveApi.newDriveContents(mGoogleApiClient)
                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {

                    @Override
                    public void onResult(DriveApi.DriveContentsResult result) {
                        final DriveContents driveContents = result.getDriveContents();

                        // If the operation was not successful, we cannot do anything
                        // and must
                        // fail.
                        if (!result.getStatus().isSuccess()) {
                            Log.i(TAG, "Failed to create new contents.");
                            callbackContext.error(1);
                            return;
                            }

                        new Thread() {
                            @Override
                            public void run() {
                                // Otherwise, we can write our data to the new contents.
                                Log.i(TAG, "New contents created.");
                                // Get an output stream for the contents.
                                OutputStream outputStream = driveContents.getOutputStream();
                                Uri fPathURI = Uri.fromFile(new File(fpath));;
                                try{
                                    InputStream inputStream = cordova.getActivity().getContentResolver().openInputStream(fPathURI);
                                    if (inputStream != null) {
                                        byte[] data = new byte[1024];
                                        while (inputStream.read(data) != -1) {
                                            outputStream.write(data);
                                        }
                                        inputStream.close();
                                    }
                                    outputStream.close();
                                } catch (IOException e) {
                                    Log.e(TAG, e.getMessage());
                                }

                                String fname = fPathURI.getLastPathSegment();
                                Log.i(TAG,fname);

                                MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                                        .setMimeType("application/octet-stream").setTitle(fname).build();
                                Drive.DriveApi.getRootFolder(mGoogleApiClient)
                                        .createFile(mGoogleApiClient, metadataChangeSet, driveContents)
                                        .setResultCallback(fileCallback);

                            }
                        }.start();
                    }
                });
    }

    final private ResultCallback<DriveFolder.DriveFileResult> fileCallback = new ResultCallback<DriveFolder.DriveFileResult>() {
        @Override
        public void onResult(DriveFolder.DriveFileResult result) {
            if (result.getStatus().isSuccess()) {
                Log.i(TAG,result.getDriveFile().getDriveId()+"");
            }
            return;

        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_RESOLUTION && resultCode == RESULT_OK) {
            mGoogleApiClient.connect();
            if(mAction.equals("downloadFile")){
                downloadFile(toLocalDest,fileid,mCallbackContext);
            } else if(mAction.equals("uploadFile")){
                uploadFile(localFPath,mCallbackContext);
            }
        }
    }


    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Called whenever the API client fails to connect.
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(cordova.getActivity(), result.getErrorCode(), 0).show();
            return;
        }
        try {
            Log.i(TAG,"trying to resolve issue...");
            cordova.setActivityResultCallback(this);//
            result.startResolutionForResult(cordova.getActivity(), REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "API client connected.");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }
}
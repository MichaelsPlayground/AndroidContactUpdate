package de.androidcrypto.androidcontactupdate;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import android.Manifest;
import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    // Request code for the contact picker activity
    private static final int PICK_CONTACT_REQUEST = 1;

    static final int REQUEST_PERMISSION_READWRITE_CONTACTS = 103;

    private TextView mResult;
    private String mEmail;


    private String mRawContactId;
    private String mDataId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mResult = (TextView) findViewById(R.id.result);
        final EditText mEmailEditText = (EditText) findViewById(R.id.email);
        Button mAttach = (Button) findViewById(R.id.attach);

        mAttach.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                mEmail = mEmailEditText.getText().toString();
                verifyPermissions();
            }
        });
    }

    private void verifyPermissions() {
        String[] permissions = {Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS};
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                permissions[0]) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this.getApplicationContext(),
                permissions[1]) == PackageManager.PERMISSION_GRANTED) {
            editContact();
        } else {
            ActivityCompat.requestPermissions(this,
                    permissions,
                    REQUEST_PERMISSION_READWRITE_CONTACTS);
        }
    }


    void editContact() {
        System.out.println("editContact started");
        startActivityForResult(new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI), PICK_CONTACT_REQUEST);
    }

    // if the user clicks ALLOW in dialog this method gets called.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_READWRITE_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                editContact();
            } else {
                Toast.makeText(this, "Read Contacts Permission is required to read contacts.", Toast.LENGTH_SHORT).show();
            }
        }
    }


    /**
     * Invoked when the contact picker activity is finished. The {@code contactUri} parameter
     * will contain a reference to the contact selected by the user. We will treat it as
     * an opaque URI and allow the SDK-specific ContactAccessor to handle the URI accordingly.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            loadContactInfo(data.getData());
        }
    }

    /**
     * Load contact information on a background thread.
     */
    private void loadContactInfo(Uri contactUri) {

        /*
         * We should always run database queries on a background thread. The database may be
         * locked by some process for a long time.  If we locked up the UI thread while waiting
         * for the query to come back, we might get an "Application Not Responding" dialog.
         */
        // original AsyncTask shows: This AsyncTask class should be static or leaks might occur
        // (anonymous android.os.AsyncTask)
        // Default constructor in android.os.AsyncTask is deprecated
        // solution: https://stackoverflow.com/a/46166223/8166854

        AsyncTask<Uri, Void, Boolean> task = new AsyncTask<Uri, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(Uri... uris) {
                Log.v("Retreived ContactURI", uris[0].toString());

                return doesContactContainHomeEmail(uris[0]);
            }

            @Override
            protected void onPostExecute(Boolean exists) {
                if (exists) {
                    Log.v("", "Updating...");
                    updateContact();
                } else {
                    Log.v("", "Inserting...");
                    insertEmailContact();
                }
            }
        };
        task.execute(contactUri);
    }

    private Boolean doesContactContainHomeEmail(Uri contactUri) {
        boolean returnValue = false;
        Cursor mContactCursor = getContentResolver().query(contactUri, null, null, null, null);
        Log.v("Contact", "Got Contact Cursor");

        try {
            if (mContactCursor.moveToFirst()) {
                String mContactId = getCursorString(mContactCursor,
                        ContactsContract.Contacts._ID);

                Cursor mRawContactCursor = getContentResolver().query(
                        RawContacts.CONTENT_URI,
                        null,
                        Data.CONTACT_ID + " = ?",
                        new String[]{mContactId},
                        null);

                Log.v("RawContact", "Got RawContact Cursor");

                try {
                    ArrayList<String> mRawContactIds = new ArrayList<String>();
                    while (mRawContactCursor.moveToNext()) {
                        String rawId = getCursorString(mRawContactCursor, RawContacts._ID);
                        Log.v("RawContact", "ID: " + rawId);
                        mRawContactIds.add(rawId);
                    }

                    for (String rawId : mRawContactIds) {
                        // Make sure the "last checked" RawContactId is set locally for use in insert & update.
                        mRawContactId = rawId;
                        Cursor mDataCursor = getContentResolver().query(
                                Data.CONTENT_URI,
                                null,
                                Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ? AND " + Email.TYPE + " = ?",
                                new String[]{mRawContactId, Email.CONTENT_ITEM_TYPE, String.valueOf(Email.TYPE_HOME)},
                                null);

                        if (mDataCursor.getCount() > 0) {
                            mDataCursor.moveToFirst();
                            mDataId = getCursorString(mDataCursor, Data._ID);
                            Log.v("Data", "Found data item with MIMETYPE and EMAIL.TYPE");
                            mDataCursor.close();
                            returnValue = true;
                            break;
                        } else {
                            Log.v("Data", "Data doesn't contain MIMETYPE and EMAIL.TYPE");
                            mDataCursor.close();
                        }
                        returnValue = false;
                    }
                } finally {
                    mRawContactCursor.close();
                }
            }
        } catch (Exception e) {
            Log.w("UpdateContact", e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                Log.w("UpdateContact", "\t" + ste.toString());
            }
            throw new RuntimeException();
        } finally {
            mContactCursor.close();
        }

        return returnValue;
    }

    private static String getCursorString(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        if (index != -1) return cursor.getString(index);
        return null;
    }

    public void insertEmailContact() {
        try {
            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, mRawContactId)
                    .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                    .withValue(Data.DATA1, mEmail)
                    .withValue(Email.TYPE, Email.TYPE_HOME)
                    .withValue(Email.DISPLAY_NAME, "Email")
                    .build());
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            mResult.setText("inserted");
        } catch (Exception e) {
            // Display warning
            Log.w("UpdateContact", e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                Log.w("UpdateContact", "\t" + ste.toString());
            }
            Context ctx = getApplicationContext();
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(ctx, "Update failed", duration);
            e.printStackTrace();
            toast.show();
        }
    }


    public void updateContact() {
        try {
            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

            ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                    .withSelection(Data.RAW_CONTACT_ID + " = ?", new String[]{mRawContactId})
                    .withSelection(Data._ID + " = ?", new String[]{mDataId})
                    .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                    .withValue(Data.DATA1, mEmail)
                    .withValue(Email.TYPE, Email.TYPE_HOME)
                    .build());
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            mResult.setText("Updated");
        } catch (Exception e) {
            // Display warning
            Log.w("UpdateContact", e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                Log.w("UpdateContact", "\t" + ste.toString());
            }
            Context ctx = getApplicationContext();
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(ctx, "Update failed", duration);
            toast.show();
        }
    }

}
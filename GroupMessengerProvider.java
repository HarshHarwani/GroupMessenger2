package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
//import android.os.DropBoxManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
//import java.io.ObjectOutputStream;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.Map;
//import java.util.Set;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 *
 * Please read:
 *
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 *
 * before you start to get yourself familiarized with ContentProvider.
 *
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 *
 * @author stevko
 *
 */
public class GroupMessengerProvider extends ContentProvider {
    private static final int TEST_CNT = 50;
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private final ContentValues[] mContentValues = new ContentValues[50];
    private ContentResolver mContentResolver=null;
    private  Uri mUri=null;

    static final String TAG = GroupMessengerProvider.class.getSimpleName();

    public GroupMessengerProvider(ContentResolver _cr) {
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
        mContentResolver = _cr;
    }
    public GroupMessengerProvider() {
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    static Context context;

    @Override
    public String getType(Uri uri) {
        return null;
    }
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }
    @Override
    public Uri insert(Uri uri, ContentValues values) {

        //The method that takes care of the creation and insertion of file usih values passed in values
        String filename = (String) values.get("key");
        String key = filename;
        String value = (String) values.get("value");
        FileOutputStream outputStream;
        try {
            outputStream = context.openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(value.getBytes());
            outputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "File write failed");
        }
        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        context = getContext();
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {

        FileInputStream inputStream;
        MatrixCursor matrixCursor = null;
        BufferedReader bufferedReader = null;
        InputStreamReader inputStreamReader = null;

        try {
            //Took the code to read from a file --->http://stackoverflow.com/questions/9095610/android-fileinputstream-read-txt-file-to-string
            inputStream = getContext().openFileInput(selection);
            inputStreamReader = new InputStreamReader(inputStream);
            bufferedReader = new BufferedReader(inputStreamReader);
            String key = selection;
            String value = bufferedReader.readLine();
            matrixCursor = new MatrixCursor(new String[]{"key", "value"});
            matrixCursor.newRow().add(key).add(value);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Exception Occured in reading Files");
        }
        Log.v("query", selection);
        return matrixCursor;
    }

    public boolean testQuery(int seq) {
        try {
            for (int i = 0; i <= seq; i++) {
            /*    String key = (String) mContentValues[i].get(KEY_FIELD);
                String val = (String) mContentValues[i].get(VALUE_FIELD);*/
                String key=String.valueOf(i);
                Cursor resultCursor = mContentResolver.query(mUri, null, key, null, null);
                if (resultCursor == null) {
                    Log.e(TAG, "Result null");
                    throw new Exception();
                }

                int keyIndex = resultCursor.getColumnIndex(KEY_FIELD);
                int valueIndex = resultCursor.getColumnIndex(VALUE_FIELD);
                if (keyIndex == -1 || valueIndex == -1) {
                    Log.e(TAG, "Wrong columns");
                    resultCursor.close();
                    throw new Exception();
                }

                resultCursor.moveToFirst();

                if (!(resultCursor.isFirst() && resultCursor.isLast())) {
                    Log.e(TAG, "Wrong number of rows");
                    resultCursor.close();
                    throw new Exception();
                }

                String returnKey = resultCursor.getString(keyIndex);
                String returnValue = resultCursor.getString(valueIndex);
                Log.e("returnKey-->",returnKey);
                Log.e("returnValue--->",returnValue);
              /*  if (!(returnKey.equals(key) && returnValue.equals(val))) {
                    Log.e(TAG, "(key, value) pairs don't match\n");
                    resultCursor.close();
                    throw new Exception();
                }*/

                resultCursor.close();
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}

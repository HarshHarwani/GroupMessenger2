package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
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

    static final String TAG = GroupMessengerProvider.class.getSimpleName();
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }
    static Context context;
    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        //The method that takes care of the creation and insertion of file usih values passed in values
        String filename = (String) values.get("key");
        String key=filename;
        String value= (String) values.get("value");
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
        BufferedReader bufferedReader=null;
        InputStreamReader inputStreamReader=null;

        try{
            //Took the code to read from a file --->http://stackoverflow.com/questions/9095610/android-fileinputstream-read-txt-file-to-string
            inputStream=getContext().openFileInput(selection);
            inputStreamReader = new InputStreamReader ( inputStream ) ;
            bufferedReader = new BufferedReader ( inputStreamReader ) ;
            String key=selection;
            String value=bufferedReader.readLine();
            matrixCursor=new MatrixCursor(new String[]{"key","value"});
            matrixCursor.newRow().add(key).add(value);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Log.e(TAG,"Exception Occured in reading Files");
        }
        Log.v("query", selection);
        return  matrixCursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
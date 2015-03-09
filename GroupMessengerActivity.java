package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewDebug;
import android.widget.EditText;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    private String[] portNumbers={"11108","11112","11116","11120","11124"};
    static final int SERVER_PORT = 10000;
    static final String KEY_FIELD = "key";
    static final String VALUE_FIELD = "value";
    public Uri mUri=null;
    static int messageIdNo=0;
    public static Queue<HoldBackQueueObject> holdBackQueue=new PriorityQueue<HoldBackQueueObject>();
    public static Map<String, String>  inputMessageMap = new HashMap<String, String>();
    public static Map<String,ArrayList<Float>> proposedSeqNoMap=new HashMap<String,ArrayList<Float>>();
    public static int proposedSeqNo=0;
    public static int deliverySeqNo=-1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        final EditText editText = (EditText) findViewById(R.id.editText1);
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {



            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString();
                editText.setText("");
                tv.append(msg + "\t\n");
                tv.append("\n");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ObjectOutputStream oos=null;
            ServerSocket serverSocket = sockets[0];
            Socket client=null;
            BufferedReader in=null;
            MessageObject messageObject=null;
            float maxCount=0f;
            while(true) {
                try {
                    client = serverSocket.accept();
                    if (client.isConnected()) {
                        Log.d("server", "resumed");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG,"Exception occurred in accepting Connection");
                }
                //http://stackoverflow.com/questions/16608878/read-data-from-a-java-socket-Taken the code to read from a socket.
                //Three modes
                //First mode for getting all the proposals
                //Second mode for getting the agreement
                //Third mode for actually delivering the message in the content provider using the holdback Queue
                if (client != null) {
                    try {
                        ObjectInputStream ois = new ObjectInputStream(client.getInputStream());
                        try {
                            messageObject = (MessageObject) ois.readObject();

                            //In case of agreedMuticast we are replacing the proposed value with the agreed value and then checking the queue for any deliverable message
                            if(messageObject.agreedmulticastFlag)
                            {
                                HoldBackQueueObject holdobj=null;
                                HoldBackQueueObject finalobj=null,finalObjToBeDelivered=null;
                                String id=messageObject.messageId;
                                float agreed=messageObject.agreedSeqNo;
                                Iterator itr=holdBackQueue.iterator();
                                while(itr.hasNext())
                                {
                                     holdobj= (HoldBackQueueObject) itr.next();
                                    if(id.equals(holdobj.messageId))
                                            break;
                                }
                                holdBackQueue.remove(holdobj);
                                holdBackQueue.add(new HoldBackQueueObject(id,agreed,true));
                                while(itr.hasNext())
                                {
                                    finalobj= (HoldBackQueueObject) itr.next();
                                    if(finalobj.isDeliverable)
                                    {
                                        finalObjToBeDelivered=holdBackQueue.poll();
                                        String msgId=finalObjToBeDelivered.messageId;
                                        String msg=inputMessageMap.get(msgId);
                                        onProgressUpdate(msg);
                                    }
                                }

                            }
                            //In case of First stage multicast when
                            if (messageObject != null && messageObject.basicMulticastFlag) {
                                //messageId of the form 11108.0(PortNo.sequence)
                                String messageId = messageObject.messageId;
                                String portNo = messageObject.sender;
                                //proposedNoinQueue(0.11108)
                                float proposedNoInQueue = Float.valueOf(proposedSeqNo + "." + portNo);
                                //unicasting the proposed sequence number back to the sender
                                Socket socket = null;
                                try {
                                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(portNo));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                if (socket.isConnected()) {
                                    oos = new ObjectOutputStream(socket.getOutputStream());
                                    MessageObject messageObjectUnicast=new MessageObject(true,proposedSeqNo);
                                    Log.d(TAG, "Client process started");
                                    oos.writeObject(messageObjectUnicast);
                                }
                                oos.close();
                                socket.close();
                                {
                                    try {
                                        socket.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                //adding the holdback object in the holdbackqueue
                                holdBackQueue.add(new HoldBackQueueObject(messageId,proposedNoInQueue,false));
                                proposedSeqNo++;
                                //adding propsedNoinQueue to the proposed list
                                if(messageObject.unicastFlag) {
                                    ArrayList<Float> proposedSeqNoList = proposedSeqNoMap.get(messageId);
                                    proposedSeqNoList.add(proposedNoInQueue);
                                    float max = 0.0f;
                                    if (proposedSeqNoList.size() == 5) {

                                        for (float f : proposedSeqNoList) {
                                            if (f > max)
                                                max = f;
                                        }
                                        //For multicasting the proposedValue and Id to all others.
                                        for (String s : portNumbers) {
                                            Socket clientsocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(s));
                                            if (clientsocket.isConnected()) {
                                                Log.d(TAG, "Client process started");
                                                MessageObject messageObjectMulticast = new MessageObject(messageId, max, true);
                                                oos = new ObjectOutputStream(socket.getOutputStream());
                                                oos.writeObject(messageObjectMulticast);
                                            }
                                            socket.close();
                                        }
                                    }
                                }
                            }
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Exception occurred in creating Inputstream");
                        e.printStackTrace();
                    }
                }
                if (messageObject != null) {
                    try {

                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e(TAG,"Exception occurred in reading Inputstream");
                    }

                    /*Log.d(TAG, strings);
                    publishProgress(strings);*/
                }
            }
        }
        protected void onProgressUpdate(String...strings) {

          /*   * The following code displays what is received in doInBackground().*/
            //The string received from the server and the sequence number are sent to the content provider fo creating the file.
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            deliverySeqNo++;
            ContentValues values = new ContentValues();
            values.put(KEY_FIELD, String.valueOf(deliverySeqNo));
            values.put(VALUE_FIELD, strReceived);
            new GroupMessengerProvider().insert(mUri,values);
        }
    }
    private class ClientTask extends AsyncTask<String, Void, Void> {
        // I used the PrintStream code from :http://www.tutorialspoint.com/javaexamples/net_singleuser.htm
        ObjectOutputStream oos=null;
        @Override
        protected Void doInBackground(String... msgs) {
            try {
                String messageValue = msgs[0];
                String portNo=msgs[1];
                int messageNo=messageIdNo;
                String messageId=portNo+"."+String.valueOf(messageNo); //MessageId--->PortNo.sequenceNo(11108.0,"msg")
                messageIdNo++;
                inputMessageMap.put(messageId,messageValue);
                MessageObject messageObject=new MessageObject(messageId,messageValue,portNo,true,false,false);
                ArrayList<Float> proposedSeqNoList=new ArrayList<Float>();
                proposedSeqNoMap.put(messageId,proposedSeqNoList);
                //As there is Multicast we iterate over all the ports including the one from which the message is sent.
                for(String s:portNumbers) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(s));
                    if (socket.isConnected()) {
                        oos = new ObjectOutputStream(socket.getOutputStream());
                        Log.d(TAG, "Client process started");
                        oos.writeObject(messageObject);
                    }
                    oos.close();
                    socket.close();
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }


    public static class MessageObject implements Serializable
    {
        String messageValue;
        String sender;
        String messageId;
        boolean basicMulticastFlag;
        boolean unicastFlag;
        boolean agreedmulticastFlag;
        float proposedSeqNo;
        float agreedSeqNo;

       MessageObject(String messageId,String messageValue,String sender,boolean basicMulticastFlag,boolean unicastFlag,boolean agreedmulticastFlag)
       {
           this.messageValue=messageValue;
           this.sender=sender;
           this.messageId=messageId;
           this.basicMulticastFlag=basicMulticastFlag;
           this.unicastFlag=unicastFlag;
           this.agreedmulticastFlag=agreedmulticastFlag;
       }
        MessageObject(boolean unicastFlag,float proposedSeqNo)
        {

            this.unicastFlag=unicastFlag;
            this.proposedSeqNo=proposedSeqNo;
        }
        MessageObject(String messageId,float agreedSeqNo,boolean agreedmulticastFlag)
        {

            this.messageId=messageId;
            this.agreedSeqNo=agreedSeqNo;
            this.agreedmulticastFlag=agreedmulticastFlag;
        }

    }

    public static class HoldBackQueueObject implements Serializable,Comparable
    {
        String messageId;
        float agreedSeqNo;
        boolean isDeliverable;
        HoldBackQueueObject(String messageId,float agreedSeqNo,boolean isDeliverable)
        {
            this.messageId=messageId;
            this.agreedSeqNo=agreedSeqNo;
            this.isDeliverable=isDeliverable;
        }

        @Override
        public int compareTo(Object another) {
            Float f=((HoldBackQueueObject)another).agreedSeqNo;
            return Float.compare(this.agreedSeqNo,f);
        }
    }

}
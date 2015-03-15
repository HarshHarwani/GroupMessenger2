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
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = "harsh";
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
    public static int deliverySeqNo=0;
    public static int agreedmax=0;
    public static int proposedmax=0;
    public static List<Float> MaxProposalList=new ArrayList<Float>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.GroupMessengerProvider");
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
        Log.d("myPort-->",myPort);

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
               /* tv.append(msg + "\t\n");
                tv.append("\n");*/
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
            //MessageObject messageObject=null;
            BufferedReader in=null;
            String id=null;
            String messageValue=null;
            String sender=null;
            String portNo=null;
            String agreed=null;
            float max = 0.0f;
            float maxinList=0.0f;
            float proposedNoInQueue=0.0f;
            int maxIntPart=0;
            while(true) {
                try {
                    client = serverSocket.accept();
                    Log.d("server", "Accept method called");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG,"Exception occurred in accepting Connection");
                }
                //http://stackoverflow.com/questions/16608878/read-data-from-a-java-socket-Taken the code to read from a socket.
                //Three modes
                //First mode for getting all the proposals
                //Second mode for getting the agreement
                //Third mode for actually delivering the message in the content provider using the holdback Queue
                    try {
                        in = new BufferedReader(
                                new InputStreamReader(client.getInputStream()));
                        String messageData=in.readLine();
                        String[] inputs=messageData.split("%");
                        if(messageData.contains("basicMultiCast"))
                        {
                            id=inputs[0];
                            messageValue=inputs[1];
                            sender=inputs[2];
                            portNo=inputs[3];
                        }
                        if(messageData.contains("UniCast"))
                        {
                            id=inputs[0];
                            proposedNoInQueue=Float.valueOf(inputs[1]);
                            sender=inputs[2];
                            messageValue=inputs[3];
                        }
                        if(messageData.contains("AgreedMulticast"))
                        {
                            id=inputs[0];
                            max=Float.valueOf(inputs[1]);
                            messageValue=inputs[2];
                            maxIntPart=Integer.parseInt(inputs[1].substring(0,inputs[1].indexOf('.')));
                        }
                        // ObjectInputStream ois = new ObjectInputStream(client.getInputStream());
                        try {
                           // messageObject = (MessageObject) ois.readObject();

                            /*if(messageObject.portNo!=null)
                                Log.d("Port no-->",messageObject.portNo);
                            Log.d("Message id-->",messageObject.messageId);*/
                            //In case of agreedMuticast we are replacing the proposed value with the agreed value and then checking the queue for any deliverable message

                            if(messageData.contains("AgreedMulticast")) {

                                Log.d(TAG, "Inside agreedmulticastFlag");
                                HoldBackQueueObject holdobj = null, holdobj1 = null, holdobj2 = null;
                                Iterator itr = holdBackQueue.iterator();
                                while (itr.hasNext()) {
                                    holdobj = (HoldBackQueueObject) itr.next();
                                    if (id.equals(holdobj.messageId))
                                        break;
                                }
                                synchronized (this) {
                                    if(maxIntPart>agreedmax) {
                                        agreedmax = maxIntPart;
                                    }
                                    holdBackQueue.remove(holdobj);
                                    holdBackQueue.add(new HoldBackQueueObject(id, messageValue, max, true));

                                    Log.d("AgreedMax-->", String.valueOf(agreedmax));
                                    Log.d("Queue value In agreed stage-->", HoldBackQueueObject.toString(holdBackQueue));
                                    while (true) {
                                        holdobj1 = holdBackQueue.peek();
                                        if (holdobj1 != null) {
                                            boolean flag = holdobj1.isDeliverable;
                                            if (flag) {
                                                holdobj2 = holdBackQueue.poll();
                                                String msgId = holdobj2.messageId;
                                                String finalMessage = holdobj2.messageValue;
                                                publishProgress(finalMessage);
                                            } else {
                                                break;
                                            }
                                        } else {
                                            break;
                                        }
                                    }
                                }
                            }
                            //In case of First stage multicast when we get the first message
                            if(messageData.contains("basicMultiCast")) {
                                //messageId of the form 11108.0(PortNo.sequence)
                                //proposedNoinQueue(0.11108)
                               // float proposedNoInQueueInBasic = Float.valueOf(proposedSeqNo + "." + portNo);
                               // Log.d("proposedNoInQueue-->",String.valueOf(proposedNoInQueue));
                                Log.d(TAG, "After adding the object in holdbackqueue");
                               // float maxProposedValueInQueue=HoldBackQueueObject.getMax(holdBackQueue);
                                proposedmax=Math.max(proposedmax,agreedmax)+1;
                                Log.d("proposedValue", String.valueOf(proposedmax));
                                float proposedNoInQueueInBasic = Float.valueOf(proposedmax + "." + portNo);
                                holdBackQueue.add(new HoldBackQueueObject(id,messageValue,proposedNoInQueueInBasic,false));
                                Log.d("Queue value  in first stage-->",HoldBackQueueObject.toString(holdBackQueue));
                                String messageDataUnicast=id+"%"+String.valueOf(proposedNoInQueueInBasic)+"%"+sender+"%"+messageValue+"%"+"UniCast";
                               // MessageObject messageObjectUnicast=new MessageObject(true,false,false,id,proposedNoInQueue,sender,messageValue);
                                proposedSeqNo=proposedSeqNo+1;
                                Socket socket = null;
                                try {
                                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(sender));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                    try {
                                        PrintStream ps = new PrintStream
                                                (socket.getOutputStream());
                                        ps.println(messageDataUnicast);
                                        ps.flush();
                                        ps.close();
                                        socket.close();
                                          }
                                    catch (IOException e1) {
                                        e1.printStackTrace();
                                    }
                                    Log.d(TAG,"Proposed sequence no-->"+ String.valueOf(proposedSeqNo));
                                    Log.d(TAG, "Inside ClientTaskUniTask");
                              //  new ClientTaskUniTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,messageObjectUnicast);
                            }
                            if(messageData.contains("UniCast")) {
                                ArrayList<Float> proposedSeqNoList = proposedSeqNoMap.get(id);
                                proposedSeqNoList.add(proposedNoInQueue);
                                proposedSeqNoMap.put(id, proposedSeqNoList);
                                Log.d("List value-->",proposedSeqNoList.toString());
                                Log.d(TAG, "Inside UnicastFlag");
                                    if (proposedSeqNoList.size() == 5) {
                                        Log.d("List-->", proposedSeqNoList.toString());
                                        for (float f : proposedSeqNoList) {
                                            if (f > maxinList)
                                                maxinList = f;
                                        }
                                        Log.d("FinalAgreedNo-->", String.valueOf(maxinList));
                                        String messageDataAgreed=id+"%"+String.valueOf(maxinList)+"%"+messageValue+"%"+"AgreedMulticast";
                                        //MessageObject messageObjectMulticast = new MessageObject(msg, messageId, max, true, false, false);
                                        for (String s : portNumbers) {
                                            Socket clientsocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(s));
                                            Log.d(TAG, "Agreed Multicast");
                                            PrintStream ps = new PrintStream
                                                    (clientsocket.getOutputStream());
                                            ps.println(messageDataAgreed);
                                            ps.flush();
                                            ps.close();
                                          /*  oos = new ObjectOutputStream(clientsocket.getOutputStream());
                                            oos.writeObject(messageObjectMulticast);
                                            oos.close();*/
                                            clientsocket.close();
                                        }
                                        // new AgreedMulticast().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, messageObjectMulticast);
                                    }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    catch (IOException e) {
                        Log.e(TAG, "Exception occurred in creating Inputstream");
                        e.printStackTrace();
                    }
                try{client.close();}
                catch (IOException e){
                    e.printStackTrace();
                }

            }

        }
        public float getMax(List<Float> maxProposalList)
        {
            float max=0.0f;
            for(float f: maxProposalList)
            {
                if(f>max)
                    max=f;
            }
            return max;
        }
        protected void onProgressUpdate(String...strings) {

          /*   * The following code displays what is received in doInBackground().*/
            //The string received from the server and the sequence number are sent to the content provider fo creating the file.
            String strReceived = strings[0];
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            String sequence=String.valueOf(deliverySeqNo);
            remoteTextView.append(sequence + "\t\n");
            ContentValues values = new ContentValues();
            values.put(KEY_FIELD, String.valueOf(deliverySeqNo));
            values.put(VALUE_FIELD, strReceived);
            deliverySeqNo=deliverySeqNo+1;
            new GroupMessengerProvider().insert(mUri,values);
          //  new GroupMessengerProvider(getContentResolver()).testQuery(deliverySeqNo);

        }
    }
    private class ClientTask extends AsyncTask<String, Void, Void> {
        // I used the PrintStream code from :http://www.tutorialspoint.com/javaexamples/net_singleuser.htm
        ObjectOutputStream oos=null;
        @Override
        protected  Void doInBackground(String... msgs) {
            try {
                String messageValue = msgs[0];
                String sender=msgs[1];
                int messageNo=messageIdNo;
                String messageId=sender+"."+String.valueOf(messageNo); //MessageId--->PortNo.sequenceNo(11108.0,"msg")
                messageIdNo=messageIdNo+1;
                inputMessageMap.put(messageId,messageValue);
                proposedSeqNoMap.put(messageId,new ArrayList<Float>());
                //As there is Multicast we iterate over all the ports including the one from which the message is sent.
                for(String s:portNumbers) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(s));
                    if (socket.isConnected()) {
                        String messageData=messageId+"%"+messageValue+"%"+sender+"%"+s+"%"+"basicMultiCast";
                       /* MessageObject messageObject=new MessageObject(messageId,messageValue,sender,s,true,false,false);
                        oos = new ObjectOutputStream(socket.getOutputStream());*/
                        PrintStream ps = new PrintStream
                                (socket.getOutputStream());
                        ps.println(messageData);
                        ps.flush();
                        ps.close();
                        Log.d(TAG, "Client process started");
                      //  oos.writeObject(messageObject);
                        //oos.close();
                        socket.close();
                    }
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

/*    public static class MessageObject implements Serializable
    {
        public String messageValue;
        public String sender;
        public String messageId;
        public String portNo;
        public boolean basicMulticastFlag;
        public boolean unicastFlag;
        public boolean agreedmulticastFlag;
        public float proposedSeqNo;
        public float agreedSeqNo;

        MessageObject(String messageId,String messageValue,String sender,String portNo,boolean basicMulticastFlag,boolean unicastFlag,boolean agreedmulticastFlag)
        {
            this.messageValue=messageValue;
            this.portNo=portNo;
            this.sender=sender;
            this.messageId=messageId;
            this.basicMulticastFlag=basicMulticastFlag;
            this.unicastFlag=unicastFlag;
            this.agreedmulticastFlag=agreedmulticastFlag;
        }

        MessageObject(boolean unicastFlag,boolean basicMulticastFlag,boolean agreedmulticastFlag,String messageId,float proposedSeqNo,String sender,String messageValue)
        {

            this.messageValue=messageValue;
            this.unicastFlag=unicastFlag;
            this.agreedmulticastFlag=agreedmulticastFlag;
            this.basicMulticastFlag=basicMulticastFlag;
            this.messageId=messageId;
            this.proposedSeqNo=proposedSeqNo;
            this.sender=sender;
        }
        MessageObject(String messageValue,String messageId,float agreedSeqNo,boolean agreedmulticastFlag,boolean basicMulticastFlag,boolean unicastFlag)
        {

            this.messageValue=messageValue;
            this.basicMulticastFlag=basicMulticastFlag;
            this.unicastFlag=unicastFlag;
            this.messageId=messageId;
            this.agreedSeqNo=agreedSeqNo;
            this.agreedmulticastFlag=agreedmulticastFlag;
        }

    }*/

    public static class HoldBackQueueObject implements Serializable,Comparable
    {
        String messageId;
        float agreedSeqNo;
        boolean isDeliverable;
        String messageValue;
        static float max=0.0f;
        HoldBackQueueObject(String messageId,String messageValue,float agreedSeqNo,boolean isDeliverable)
        {
            this.messageValue=messageValue;
            this.messageId=messageId;
            this.agreedSeqNo=agreedSeqNo;
            this.isDeliverable=isDeliverable;
        }

        @Override
        public int compareTo(Object another) {
            Float f=((HoldBackQueueObject)another).agreedSeqNo;
            return Float.compare(this.agreedSeqNo,f);
        }

        public static String toString(Queue holdBackQueue)
        {
            String list="[";
            Iterator iterator=holdBackQueue.iterator();
            while (iterator.hasNext())
            {
                HoldBackQueueObject holdBackQueueObject= (HoldBackQueueObject) iterator.next();
                list+="{"+String.valueOf(holdBackQueueObject.agreedSeqNo)+","+String.valueOf(holdBackQueueObject.isDeliverable)+","+"}";
            }
            return list+"]";
        }
        public static float getMax(Queue holdBackQueue)
        {
            Iterator iterator=holdBackQueue.iterator();

            while (iterator.hasNext())
            {
               HoldBackQueueObject holdBackQueueObject= (HoldBackQueueObject) iterator.next();
               if(max<holdBackQueueObject.agreedSeqNo)
               {
                   max=holdBackQueueObject.agreedSeqNo;
               }
            }
            return max;
        }
    }

}
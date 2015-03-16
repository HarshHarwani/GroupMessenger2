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
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = "harsh";
    //private String[] portNumbers={"11108","11112","11116","11120","11124"};
    public static volatile ArrayList<String> portNumberList=new ArrayList<String>();
    static final int SERVER_PORT = 10000;
    static final String KEY_FIELD = "key";
    static final String VALUE_FIELD = "value";
    public Uri mUri=null;
    static int messageIdNo=0;
    public static volatile Queue<HoldBackQueueObject> holdBackQueue=new PriorityQueue<HoldBackQueueObject>();
    public static Map<String, String>  inputMessageMap = new HashMap<String, String>();
    public static Map<String,ArrayList<LiveNodeDetails>> proposedSeqNoMap=new HashMap<String,ArrayList<LiveNodeDetails>>();
    public static volatile Map<String,Long> HeartBeatMap=new HashMap<String,Long>();
    //   public static Map<String,ArrayList<Float>> senderMessageMapping=new HashMap<String,ArrayList<Float>>();
    public static int proposedSeqNo=0,proposedmax=0,deliverySeqNo=0,agreedmax=0;
    public static boolean failureFlag=false;
    public final long TimeOut=5521;
    public String failedSender="";
    ServerSocket serverSocket=null;
    public String myPort=null;
    Timer timer=null;
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
        portNumberList.add("11108");
        portNumberList.add("11112");
        portNumberList.add("11116");
        portNumberList.add("11120");
        portNumberList.add("11124");
        //new FailureDetector().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,"Failure Detector Started");
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
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        Log.d("myPort-->",myPort);

        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            timer = new Timer("HeartBeatTimer");
            TimerTask taskToExecute = new HeartBeatSender();
            timer.scheduleAtFixedRate(taskToExecute,0,1000);
            /*new FailureDetector().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,"Failure Detector");*/
            new FailureDetector().start();
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
   protected void onDestroy(){
        super.onDestroy();
        timer.cancel();
    }
    protected void onPause(){
        super.onPause();
        timer.cancel();
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
            String id=null,messageValue=null,sender=null,portNo=null;
            float max = 0.0f,maxinList=0.0f,proposedNoInQueue=0.0f;
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
                       /* if(failureFlag)
                        {
                            Log.d("Failure Detected","Finally");
                        }*/
                    if(messageData.contains("heartbeat"))
                    {
                        String sender13=inputs[1];
                        Log.d("HeartBeat Received from-->",sender13);
                        HeartBeatMap.put(sender13, Calendar.getInstance().getTimeInMillis());
                    }
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
                        sender=inputs[3];
                        maxIntPart=Integer.parseInt(inputs[1].substring(0,inputs[1].indexOf('.')));
                    }
                    try {
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
                                holdBackQueue.add(new HoldBackQueueObject(id, messageValue, max, true,sender));
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
                            Log.d(TAG, "After adding the object in holdbackqueue");
                            // float maxProposedValueInQueue=HoldBackQueueObject.getMax(holdBackQueue);
                            proposedmax=Math.max(proposedmax,agreedmax)+1;
                            Log.d("proposedValue", String.valueOf(proposedmax));
                            float proposedNoInQueueInBasic = Float.valueOf(proposedmax + "." + portNo);
                            holdBackQueue.add(new HoldBackQueueObject(id,messageValue,proposedNoInQueueInBasic,false,sender));
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
                        }
                        if(messageData.contains("UniCast")) {
                            ArrayList<LiveNodeDetails> proposedSeqNoList = proposedSeqNoMap.get(id);
                            proposedSeqNoList.add(new LiveNodeDetails(proposedNoInQueue,sender));
                            proposedSeqNoMap.put(id, proposedSeqNoList);
                            Log.d("List value-->",proposedSeqNoList.toString());
                            int count=0;
                            for(int i=0;i<portNumberList.size();i++)
                            {
                                String liveNode=portNumberList.get(i);
                                for(int j=0;j<proposedSeqNoList.size();j++)
                                {
                                    if(liveNode.equals(proposedSeqNoList.get(i).sender));
                                    count++;
                                }
                            }
                            if (count>=4) {
                                Log.d("List-->", proposedSeqNoList.toString());
                                for (int i=0;i<proposedSeqNoList.size();i++) {
                                    float f= proposedSeqNoList.get(i).proposedSeqno;
                                    if (f > maxinList)
                                        maxinList = f;
                                }
                                Log.d("FinalAgreedNo-->", String.valueOf(maxinList));
                                String messageDataAgreed=id+"%"+String.valueOf(maxinList)+"%"+messageValue+"%"+sender+"%"+"AgreedMulticast";
                                //MessageObject messageObjectMulticast = new MessageObject(msg, messageId, max, true, false, false);
                                for (String s : portNumberList) {
                                    Socket clientsocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(s));
                                    Log.d(TAG, "Agreed Multicast");
                                    PrintStream ps = new PrintStream
                                            (clientsocket.getOutputStream());
                                    ps.println(messageDataAgreed);
                                    ps.flush();
                                    ps.close();
                                    clientsocket.close();
                                }
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

        }
    }
    public class FailureDetector extends Thread
    {
        Socket socket=null;
        public FailureDetector() {
            super("FailureDetector");
        }

        public void run() {
            while(true) {

                for (String s : HeartBeatMap.keySet()) {
                    if (Calendar.getInstance().getTimeInMillis() - HeartBeatMap.get(s) > TimeOut) {
                        portNumberList.remove(s);
                        failureFlag = true;
                        failedSender = s;
                        break;
                    }

                }
                if (failureFlag) {
                    try {
                     socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(myPort));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        String messageData="failureDetected";
                        PrintStream ps = new PrintStream
                                (socket.getOutputStream());
                        ps.println(messageData);
                        ps.flush();
                        ps.close();
                        Log.d(TAG, "Client process started");
                        socket.close();

                    }
                    catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    //Case where the process sending the agreed sequence number dies after sending it to some processes.
                    Iterator iterator = holdBackQueue.iterator();
                    while (iterator.hasNext()) {
                        HoldBackQueueObject holdBackQueueObject = (HoldBackQueueObject) iterator.next();
                        if (holdBackQueueObject.sender.equals(failedSender) && !holdBackQueueObject.isDeliverable) {
                            holdBackQueue.remove(holdBackQueueObject);
                        }
                    }
                    break;
                }

            }
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
                proposedSeqNoMap.put(messageId,new ArrayList<LiveNodeDetails>());
                //As there is Multicast we iterate over all the ports including the one from which the message is sent.
                for(String s:portNumberList) {
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

    public class HeartBeatSender extends TimerTask
    {
        public void run() {
            try {
                for(String s:portNumberList) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(s));
                    if (socket.isConnected()) {
                        String messageData="heartbeat"+"%"+myPort;
                        PrintStream ps = new PrintStream
                                (socket.getOutputStream());
                        ps.println(messageData);
                        ps.flush();
                        ps.close();
                        Log.d(TAG, "Client process started");
                        socket.close();
                    }
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "ClientTask socket IOException");
            }

        }
    }
    public static class HoldBackQueueObject implements Serializable,Comparable
    {
        String messageId;
        float agreedSeqNo;
        boolean isDeliverable;
        String messageValue;
        static float max=0.0f;
        String sender=null;
        HoldBackQueueObject(String messageId,String messageValue,float agreedSeqNo,boolean isDeliverable,String sender)
        {
            this.messageValue=messageValue;
            this.messageId=messageId;
            this.agreedSeqNo=agreedSeqNo;
            this.isDeliverable=isDeliverable;
            this.sender=sender;
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
    public class LiveNodeDetails
    {
        public float proposedSeqno;
        public String sender;
        LiveNodeDetails(float proposedSeqno,String sender)
        {
            this.proposedSeqno=proposedSeqno;
            this.sender=sender;
        }

    }

}



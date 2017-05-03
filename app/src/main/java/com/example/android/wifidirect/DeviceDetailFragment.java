/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wifidirect;

import android.app.Fragment;
import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import com.example.android.wifidirect.DeviceListFragment.DeviceActionListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends ListFragment implements ConnectionInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    Switch sw;
    String msg=null;
    String deviceName =null;
    ProgressDialog progressDialog = null;
    ListView list;
     Handler handle=new Handler();
     ChatAdapter adp= new ChatAdapter();
     public static ArrayList<Conversation> cList=new ArrayList<Conversation>();


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mContentView.findViewById(R.id.tv).setVisibility(View.VISIBLE);
        sw.setVisibility(View.VISIBLE);
        ((DeviceActionListener) getActivity()).disconnect();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.setListAdapter(adp);
        list.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        list.setStackFromBottom(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.device_detail, null);
        list= (ListView) mContentView.findViewById(android.R.id.list);
        sw= (Switch) mContentView.findViewById(R.id.switch1);
        sw.setVisibility(View.VISIBLE);
        mContentView.findViewById(R.id.tv).setVisibility(View.VISIBLE);
        mContentView.findViewById(R.id.btnSend).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView tview=(TextView)mContentView.findViewById(R.id.txt);
                msg= tview.getText().toString();
                tview.setText("");
            }
        });
        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                if(sw.isChecked()) {
                    System.out.print("owner:"+sw.isChecked());
                    config.groupOwnerIntent = 15;
                }
                else {
                    System.out.print("owner:"+sw.isChecked());
                    config.groupOwnerIntent = 0;
                }

                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
//                        new DialogInterface.OnCancelListener() {
//
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
//                            }
//                        }
                        );
                ((DeviceActionListener) getActivity()).connect(config);

            }
        });

        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        mContentView.findViewById(R.id.tv).setVisibility(View.VISIBLE);
                        sw.setVisibility(View.VISIBLE);
                        ((DeviceActionListener) getActivity()).disconnect();
                        getView().setVisibility(View.GONE);
                        ((WiFiDirectActivity)getActivity()).showList();
                    }
                });

        mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
                new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*video/*");
                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                    }
                });

        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        // User has picked an image. Transfer it to group owner i.e peer using
        // FileTransferService.
        Uri uri = data.getData();
        TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
        statusText.setText("Sending: " + uri);
        Log.d(WiFiDirectActivity.TAG, "Intent----------- " + uri);
        Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                info.groupOwnerAddress.getHostAddress());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
        getActivity().startService(serviceIntent);
    }
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);
        mContentView.findViewById(R.id.tv).setVisibility(View.GONE);
        sw.setVisibility(View.GONE);
        ((WiFiDirectActivity)getActivity()).hideList();
        // The owner IP is now known.
        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(getResources().getString(R.string.group_owner_text)
                + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
                        : getResources().getString(R.string.no)));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {
            new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text)).execute();
            new ChatSocketServer().createSocket();
        } else if (info.groupFormed) {
            // The device acts as the client. In this case, we enable the
            // get file button.
            new ChatSocketClient().createSocket();
            mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
            ((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
                    .getString(R.string.client_text));
        }

        // hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
    }

    /**
     * Updates the UI with device data
     * 
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        deviceName=device.deviceName;
        getActivity().getActionBar().setTitle(deviceName);
        System.out.println("devicename:"+deviceName);
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(device.deviceAddress);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(device.toString());

    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;

        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                ServerSocket serverSocket = new ServerSocket(8988);
                Log.d(WiFiDirectActivity.TAG, "Server: Socket opened");
                Socket client = serverSocket.accept();
                Log.d(WiFiDirectActivity.TAG, "Server: connection done");
                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();

                Log.d(WiFiDirectActivity.TAG, "server: copying files " + f.toString());
                InputStream inputstream = client.getInputStream();
                copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
                return f.getAbsolutePath();
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                statusText.setText("File copied - " + result);
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + result), "image/*");
                context.startActivity(intent);
            }

        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a server socket");
        }

    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        long startTime=System.currentTimeMillis();
        
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
            long endTime=System.currentTimeMillis()-startTime;
            Log.v("","Time taken to transfer all bytes is : "+endTime);
            
        } catch (IOException e) {
            Log.d(WiFiDirectActivity.TAG, e.toString());
            return false;
        }
        return true;
    }
    private class ChatAdapter extends BaseAdapter
    {

        /* (non-Javadoc)
         * @see android.widget.Adapter#getCount()
         */
        @Override
        public int getCount()
        {
            return cList.size();
        }

        /* (non-Javadoc)
         * @see android.widget.Adapter#getItem(int)
         */
        @Override
        public Conversation getItem(int arg0)
        {
            return cList.get(arg0);
        }

        /* (non-Javadoc)
         * @see android.widget.Adapter#getItemId(int)
         */
        @Override
        public long getItemId(int arg0)
        {
            return arg0;
        }

        /* (non-Javadoc)
         * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
         */
        @Override
        public View getView(int pos, View v, ViewGroup arg2)
        {
            Conversation c = getItem(pos);
            if (c.isSent())
                v = getActivity().getLayoutInflater().inflate(R.layout.chat_item_sent, null);
            else
                v = getActivity().getLayoutInflater().inflate(R.layout.chat_item_rcv, null);

            TextView lbl = (TextView) v.findViewById(R.id.lbl1);
            lbl.setText(DateUtils.getRelativeDateTimeString(getActivity(), c
                            .getDate().getTime(), DateUtils.SECOND_IN_MILLIS,
                    DateUtils.DAY_IN_MILLIS, 0));

            lbl = (TextView) v.findViewById(R.id.lbl2);
            lbl.setText(c.getMsg());

            lbl = (TextView) v.findViewById(R.id.lbl3);
            if (c.isSent())
                    lbl.setText("Delivered");
            else
                lbl.setText("");

            return v;
        }

    }

    public class ChatSocketServer {
        private ServerSocket severSocket = null;
        private Socket socket = null;
        private InputStream inStream = null;
        private OutputStream outStream = null;

        public ChatSocketServer() {

        }

        public void createSocket() {

                new Thread(){
                    public void run() {
                        try {
                        ServerSocket serverSocket = new ServerSocket(3339);
//while (true) {
                        socket = serverSocket.accept();
                        inStream = socket.getInputStream();
                        outStream = socket.getOutputStream();
                        System.out.println("Connected");
                        createReadThread();
                        createWriteThread();
                        } catch (IOException io) {
                            io.printStackTrace();
                        }
                    }
                }.start();

//}
        }

        public void createReadThread() {
            Thread readThread = new Thread() {
                public void run() {
                    while (socket.isConnected()) {
                        try {
                            byte[] readBuffer = new byte[200];
                            int num = inStream.read(readBuffer);
                            if (num > 0) {
                                byte[] arrayBytes = new byte[num];
                                System.arraycopy(readBuffer, 0, arrayBytes, 0, num);
                                String recvedMessage = new String(arrayBytes, "UTF-8");
                                System.out.println(recvedMessage);
                                //System.out.println(device.toString());
                                cList.add(new Conversation(recvedMessage,new Date(),deviceName));
                                handle.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        adp.notifyDataSetChanged();
                                    }
                                });
                                System.out.println("Received message :" + recvedMessage);
                            }/* else {
notify();
}*/
                            ;
//System.arraycopy();

                        } catch (SocketException se) {
                            //System.exit(0);

                        } catch (IOException i) {
                            i.printStackTrace();
                        }

                    }
                }
            };
            readThread.setPriority(Thread.MAX_PRIORITY);
            readThread.start();
        }

        public void createWriteThread() {
            Thread writeThread = new Thread() {
                public void run() {

                    while (socket.isConnected()) {
                        try {

                            sleep(100);
                            if (msg != null && msg.length() > 0) {
                                synchronized (socket) {
                                    outStream.write(msg.getBytes("UTF-8"));
                                    cList.add(new Conversation(msg,new Date(),DeviceListFragment.device.deviceName));
                                    msg=null;
                                    handle.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            adp.notifyDataSetChanged();
                                        }
                                    });
                                    sleep(100);
                                }
                            }/* else {
notify();
}*/
                            ;
//System.arraycopy();

                        } catch (IOException i) {
                            i.printStackTrace();
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }

                    }
                }
            };
            writeThread.setPriority(Thread.MAX_PRIORITY);
            writeThread.start();

        }

/*public static void main(String[] args) {
ChatSocketServer chatServer = new ChatSocketServer();
chatServer.createSocket();

}*/
    }
    public class ChatSocketClient {
        private Socket socket = null;
        private InputStream inStream = null;
        private OutputStream outStream = null;
    public ChatSocketClient() {

    }

    public void createSocket() {
        new Thread() {
            public void run() {
                try {sleep(2000);
                    socket = new Socket();
                    socket.bind(null);
                    socket.connect(new InetSocketAddress(info.groupOwnerAddress.getHostAddress(), 3339), 5000);
                    System.out.println("Connected");
                    inStream = socket.getInputStream();
                    outStream = socket.getOutputStream();
                    createReadThread();
                    createWriteThread();
                } catch (UnknownHostException u) {
                    u.printStackTrace();
                } catch (IOException io) {
                    io.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
    public void createReadThread() {
        Thread readThread = new Thread() {
            public void run() {
                while (socket.isConnected()) {

                    try {
                        byte[] readBuffer = new byte[200];
                        int num = inStream.read(readBuffer);

                        if (num > 0) {
                            byte[] arrayBytes = new byte[num];
                            System.arraycopy(readBuffer, 0, arrayBytes, 0, num);
                            String recvedMessage = new String(arrayBytes, "UTF-8");
                            cList.add(new Conversation(recvedMessage,new Date(),deviceName));
                            handle.post(new Runnable() {
                                @Override
                                public void run() {
                                    adp.notifyDataSetChanged();
                                }
                            });
                            System.out.println("Received message :" + recvedMessage);
                        }/* else {
// notify();
}*/
                        ;
//System.arraycopy();
                    }catch (SocketException se){
                        //System.exit(0);

                    } catch (IOException i) {
                        i.printStackTrace();
                    }

                }
            }
        };
        readThread.setPriority(Thread.MAX_PRIORITY);
        readThread.start();
    }

        public void createWriteThread() {
            Thread writeThread = new Thread() {
                public void run() {

                    while (socket.isConnected()) {
                        try {

                            sleep(100);
                            if (msg != null && msg.length() > 0) {
                                synchronized (socket) {
                                    outStream.write(msg.getBytes("UTF-8"));
                                    cList.add(new Conversation(msg,new Date(),DeviceListFragment.device.deviceName));
                                    msg=null;
                                    handle.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            adp.notifyDataSetChanged();
                                        }
                                    });
                                    sleep(100);
                                }
                            }/* else {
notify();
}*/
                            ;
//System.arraycopy();

                        } catch (IOException i) {
                            i.printStackTrace();
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }

                    }
                }
            };
            writeThread.setPriority(Thread.MAX_PRIORITY);
            writeThread.start();

        }
/*
public static void main(String[] args) throws Exception {
ChatSocketClient myChatClient = new ChatSocketClient();
myChatClient.createSocket();
}*/
}

}

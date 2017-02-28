/*
 * This file is part of MultipathControl.
 *
 * Copyright 2012 UCLouvain - Gregory Detal <first.last@uclouvain.be>
 * Copyright 2015 UCLouvain - Matthieu Baerts <first.last@student.uclouvain.be>
 *
 * MultipathControl is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package be.uclouvain.multipathcontrol.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.security.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import be.uclouvain.multipathcontrol.MPCtrl;
import be.uclouvain.multipathcontrol.global.Config;
import be.uclouvain.multipathcontrol.global.Manager;
import be.uclouvain.multipathcontrol.system.Cmd;

import static be.uclouvain.multipathcontrol.utils.JSONUtils.getJsonObjectFromMap;

public class MainService extends Service {
    public static final String CONFIG_FILE = "mptcp_ctrl.conf";
    private static final String TCPDUMP_TRACE_FOLDER = "mptcp_ctrl_traces";
    private static final String SERVER_IP = "PUT YOUR IP HERE"; // Nothing better found now...
    public static final String COLLECT_SERVER_IP = "PUT YOUR COLLECTOR IP HERE";
    public static final int COLLECT_SERVER_PORT = 80;
    private static final int NB_CONFIGS = 4;
    private static final boolean[] DATA_BACKUP = {false, true, true, true};
    private static final String[] TCP_RETRIES3 = {"16", "16", "16", "16"};
    private static final String[] MPTCP_ACTIVE_BK = {"0", "0", "0", "1"};
    private static final String[] MPTCP_OLD_BK = {"0", "0", "1", "0"};
    private static final String[] OPEN_BUP = {"1", "1", "1", "0"};
    private static final String[] SFS_PER_INTF = {"1", "1", "1", "1"};
    private static final String[] SLOSS_THRESHOLD = {"0", "0", "0", "250"};
    private static final String[] SRETRANS_THRESHOLD = {"0", "0", "0", "500"};
    private static final String[] RTO_MS_THRESHOLD = {"0", "0", "0", "1500"};
    private static final String[] IDLE_PERIODS_THRESHOLD = {"0", "0", "0", "0"};
    private static final String[] TIMER_PERIOD_MS = {"500", "500", "500", "500"};
    //private static final String[] KEEPALIVE_INTVL = {"75", "75", "75", "0"};
    //private static final String[] KEEPALIVE_INTVL_MS = {"0", "0", "0", "500"};
    //private static final String[] KEEPALIVE_PROBES_FASTJOIN = {"10", "10", "10", "2"};

    private static final long THREE_HOURS_MS = 3 * 60 * 60 * 1000;

    private MPCtrl mpctrl;
    private Random generator;
    private Timer timer;
    private int configId;

    private static MainService currentService;

    @Override
    public IBinder onBind(Intent intent) {
        return null; // not allow binding
    }

    public void onCreate() {
        super.onCreate();
        mpctrl = Manager.create(getApplicationContext());
        Log.i(Manager.TAG, "Create service");
        if (mpctrl == null) {
            Toast.makeText(this,
                    "MPControl: It seems this is not a rooted device",
                    Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        // Configure!
        // Schedule timer (in configure)
        generator = new Random();
        currentService = this;
        configureAndReschedule();
    }

    class ConfigureTask extends TimerTask {
        public void run() { configureAndReschedule(); };
    }


    public String getDeviceId() {
        return Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public static MainService getService() {
        return currentService;
    }

    public int getConfigNumber() {
        return configId;
    }

    public Date getDateCurrentTimeZone(long timestamp) {
        try{
            Calendar calendar = Calendar.getInstance();
            TimeZone tz = TimeZone.getDefault();
            calendar.setTimeInMillis(timestamp * 1000);
            calendar.add(Calendar.MILLISECOND, tz.getOffset(calendar.getTimeInMillis()));
            return calendar.getTime();
        }catch (Exception e) {
        }
        return null;
    }

    private boolean checkConfigFile() {
		/* Return true if the configuration can be changed */
        final File file = new File(Environment.getExternalStorageDirectory()
                .getAbsolutePath(), CONFIG_FILE);
        if (!file.exists()) {
            Log.d("MAINSERVICE", "Config file not found");
            return true;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            configId = Integer.parseInt(br.readLine());
            String serverIp = br.readLine();
            Date lastModified = getDateCurrentTimeZone(Long.parseLong(br.readLine()) / 1000);

            // If less than 3 hours, don't change the config
            if (getDateDiff(lastModified, Calendar.getInstance().getTime(), TimeUnit.MILLISECONDS) <= THREE_HOURS_MS)
                return false;

        } catch (FileNotFoundException e) {
            Log.e("MAINSERVICE", "Config file not found but file exists...");
            return true;
        } catch (IOException e) {
            Log.e("MAINSERVICE", "IOException: " + e);
            return true;
        } catch (NumberFormatException e) {
            Log.e("MAINSERVICE", "NumberFormatException: " + e);
            return true;
        } finally {
            try {
                // Don't forget to close the file!
                if (br != null)
                    br.close();
            } catch (IOException e) {}
        }
        return true;
    }

    private void configure(boolean random) {
        // Select random number if no configId
        if (random) {
            configId = generator.nextInt(NB_CONFIGS);
            Log.d("MAINSERVICE", "Selected config " + configId);
        } else {
            Log.d("MAINSERVICE", "Enforce config " + configId);
        }
        mpctrl.setDataBackup(DATA_BACKUP[configId]);
        Config.dataBackup = DATA_BACKUP[configId];
        Config.saveStatus(this);
        try {
            Cmd.runAsRootSafe("sysctl -w net.ipv4.tcp_retries3=" + TCP_RETRIES3[configId]);
            Cmd.runAsRootSafe("sysctl -w net.mptcp.mptcp_active_bk=" + MPTCP_ACTIVE_BK[configId]);
            Cmd.runAsRootSafe("sysctl -w net.mptcp.mptcp_old_bk=" + MPTCP_OLD_BK[configId]);
            Cmd.runAsRootSafe("echo " + OPEN_BUP[configId] + " | tee /sys/module/mptcp_fullmesh/parameters/open_bup");
            Cmd.runAsRootSafe("echo " + SFS_PER_INTF[configId] + " | tee /sys/module/mptcp_fullmesh/parameters/sfs_per_intf");
            // Don't forget to disable the oracle if config is not 3!
            Cmd.runAsRootSafe("echo " + SLOSS_THRESHOLD[configId] + " | tee /sys/module/mptcp_oracle/parameters/sloss_threshold");
            Cmd.runAsRootSafe("echo " + SRETRANS_THRESHOLD[configId] + " | tee /sys/module/mptcp_oracle/parameters/sretrans_threshold");
            Cmd.runAsRootSafe("echo " + RTO_MS_THRESHOLD[configId] + " | tee /sys/module/mptcp_oracle/parameters/rto_ms_threshold");
            Cmd.runAsRootSafe("echo " + IDLE_PERIODS_THRESHOLD[configId] + " | tee /sys/module/mptcp_oracle/parameters/idle_periods_threshold");
            Cmd.runAsRootSafe("echo " + TIMER_PERIOD_MS[configId] + " | tee /sys/module/mptcp_oracle/parameters/timer_period_ms\"");
            //Cmd.runAsRootSafe("sysctl -w net.ipv4.tcp_keepalive_intvl=" + KEEPALIVE_INTVL[configId]);
            //Cmd.runAsRootSafe("sysctl -w net.ipv4.tcp_keepalive_intvl_ms=" + KEEPALIVE_INTVL_MS[configId]);
            //Cmd.runAsRootSafe("sysctl -w net.mptcp.mptcp_keepalive_probes_fastjoin=" + KEEPALIVE_PROBES_FASTJOIN[configId]);
        } catch (Exception e) {
            Log.e("MAINSERVICE", "Holy shit: " + e.toString());
        }
    }

    private void writeConfigFile() {
        File file = new File(Environment.getExternalStorageDirectory()
                .getAbsolutePath(), CONFIG_FILE);
        try {
            PrintWriter pw = new PrintWriter(new FileWriter(file, false));
            pw.println(configId);
            pw.println(SERVER_IP);
            pw.println(System.currentTimeMillis());
            pw.close();
        } catch(IOException e) {
            Log.e("MAINSERVICE", "IOException: " + e);
        }

    }

    /**
     * Get a diff between two dates
     * @param date1 the oldest date
     * @param date2 the newest date
     * @param timeUnit the unit in which you want the diff
     * @return the diff value, in the provided unit
     */
    public static long getDateDiff(Date date1, Date date2, TimeUnit timeUnit) {
        long diffInMillies = Math.abs(date2.getTime() - date1.getTime());
        return timeUnit.convert(diffInMillies, TimeUnit.MILLISECONDS);
    }

    public static Date addDays(Date date, int days)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days); //minus number would decrement the days
        return cal.getTime();
    }

    private void reschedule() {
        // Reschedule to reconfigure at 3 o'clock, except if device is up since less than 3 hours
        timer = new Timer(true);
        Date now = Calendar.getInstance().getTime();
        long toAdd = 0;
        if (now.getHours() < 3)
            toAdd = 86400000;
        Date next = addDays(now, 1);
        next.setHours(3);
        next.setMinutes(0);
        long diff = getDateDiff(now, next, TimeUnit.MILLISECONDS);
        timer.schedule(new ConfigureTask(), diff + toAdd);
    }

    private void killTcpdump() {
        String cmd = "pkill tcpdump; sleep 1";
        try {
            Cmd.runAsRootSafe(cmd);
        } catch (Exception e) {
            Log.e("MAINSERVICE", cmd + " failed; " + e);
        }
    }

    private String getTcpdumpFileName() {
        Date now = Calendar.getInstance().getTime();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        return SERVER_IP + "_" + getDeviceId() + "_" + configId + "_passive-" + df.format(now) + ".pcap";
    }

    private void launchTcpdump() {
        String newTcpdumpFileName = getTcpdumpFileName();
        String cmd = "( " + getTcpdumpCmd(newTcpdumpFileName) + " &)" ;
        try {
            Cmd.runAsRoot(cmd);
        } catch (Exception e) {
            Log.e("MAINSERVICE", cmd + " failed; " + e);
        }
    }

    private void killAndRelaunchTcpdump() {
        killTcpdump();
        launchTcpdump();
    }

    private static String getTcpdumpCmd(String outfile) {
        File traceFolder = new File(Environment.getExternalStorageDirectory()
                .getAbsolutePath(), TCPDUMP_TRACE_FOLDER);
        if (!traceFolder.exists()) {
            traceFolder.mkdir();
        }

        if (!traceFolder.isDirectory()) {
            Log.e("MAINSERVICE", "A nasty thing occured! traceFolder is not a directory! " + traceFolder.getAbsolutePath());
        }

        File traceFile = new File(traceFolder, outfile);
        if (traceFile.exists()) {
            Log.e("MAINSERVICE", "A nasty thing occured! traceFile exists! " + traceFile.getAbsolutePath());
        }

        return "tcpdump -i any -w " + traceFile.getAbsolutePath() + " -s 120 'tcp and not ip host 127.0.0.1 and not ip host 10.0.0.2 and not ip host 10.0.1.2 and not ip host 26.26.26.1'";
    }

    private File[] getReadyTraces() {
        File traceFolder = new File(Environment.getExternalStorageDirectory()
                .getAbsolutePath(), TCPDUMP_TRACE_FOLDER);
        if (!traceFolder.exists() || !traceFolder.isDirectory()) {
            return null;
        }
        return traceFolder.listFiles();
    }

    public boolean post(String path, File file) {
        HttpURLConnection urlConnection;
        String result = null;
        int length = 4096;
        int read, bytesAvailable, bufferSize;
        int totalRead = 0;
        byte[] bytes = new byte[length];
        // Ensure sending when WiFi is available
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        while (!mWifi.isConnected()) {
            try {
                Thread.sleep(5000);
            }
            catch (InterruptedException e) {}
            mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        }
        try {
            FileInputStream fis = new FileInputStream(file);
            Log.i("FILE", "" + fis.getChannel().size());
            //Connect
            urlConnection = (HttpURLConnection) ((new URL("http://" + COLLECT_SERVER_IP + ":" + COLLECT_SERVER_PORT + "/PATH/TO/UPLOAD/YOUR/SMARTPHONE/TRACE/" + path + "/").openConnection()));
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);
            urlConnection.setUseCaches(false);
            urlConnection.setFixedLengthStreamingMode(fis.getChannel().size());
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/octet-stream");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.addRequestProperty("Content-length", fis.getChannel().size()+"");
            urlConnection.setRequestProperty("Connection", "Keep-Alive");

            OutputStream os = urlConnection.getOutputStream();
            bytesAvailable = fis.available();
            bufferSize = Math.min(bytesAvailable, length);
            while ((read = fis.read(bytes, 0, bufferSize)) > 0) {
                totalRead += read;
                try {
                    os.write(bytes, 0, bufferSize);
                } catch (ProtocolException e) {
                    Log.d("ProtocolException1", e.getMessage());
                }
                bytesAvailable = fis.available();
                bufferSize = Math.min(bytesAvailable, length);
            }
            Log.i("TOTALREAD", totalRead + " bytes");
            try {
                os.flush();
                os.close();
            } catch (ProtocolException e) {
                Log.d("ProtocolException2", e.getMessage());
            }
            //urlConnection.connect();


            int status = urlConnection.getResponseCode();
            Log.d("TCPClient", "status is " + status);

            //Read
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));

            String line = null;
            StringBuilder sb = new StringBuilder();

            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }

            bufferedReader.close();
            result = sb.toString();

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException g) {}
            return post(path, file);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException g) {}
            return post(path, file);
        }
        Log.d("TCPClient", "result is " + result);
        return true;
    }

    private Map getMetadata(File file) {
        Map map = new HashMap();
        String[] elems = file.getName().split("_");
        map.put("trace_user_name", file.getName());
        map.put("uploader_email", elems[2] + "@" + elems[1] + "." + elems[0]);
        map.put("smartphone", true);
        return map;
    }

    public static String makeRequest(String uri, String json) {
        HttpURLConnection urlConnection;
        String url;
        String data = json;
        String result = null;
        try {
            //Connect
            urlConnection = (HttpURLConnection) ((new URL(uri).openConnection()));
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Accept", "application/json");
            //urlConnection.connect();

            //Write
            OutputStream outputStream = urlConnection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8"));
            writer.write(data);
            writer.close();
            outputStream.close();

            int status = urlConnection.getResponseCode();
            Log.d("TCPClient", "status is " + status);

            //Read
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));

            String line = null;
            StringBuilder sb = new StringBuilder();

            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }

            bufferedReader.close();
            result = sb.toString();

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException f) {}
            return makeRequest(uri, json);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException g) {}
            return makeRequest(uri, json);
        }
        Log.d("TCPClient", "result is " + result);
        return result;
    }

    private String sendMetadata(File file) {
        Map params = getMetadata(file);
        String returnedJson = null;
        try {
            JSONObject holder = getJsonObjectFromMap(params);
            Log.d("HOLDER", holder.toString());
            returnedJson = makeRequest("http://" + COLLECT_SERVER_IP + ":" + COLLECT_SERVER_PORT + "/collect/save_test/", holder.toString());
        } catch (JSONException e) {

        }
        return returnedJson;
    }

    public static void fixTcpdumpFile(File f) {
        File fixFile = new File(Environment.getExternalStorageDirectory()
                .getAbsolutePath(), "fixed_passive.pcap");
        String cmd = "pcapfix -t 113 -o " + fixFile.getAbsolutePath() + " " + f.getAbsolutePath();
        try {
            Cmd.runAsRootSafe(cmd);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String cmd2 = "cp " + fixFile.getAbsolutePath() + " " + f.getAbsolutePath();
        try {
            Cmd.runAsRootSafe(cmd2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendTrace(File file) throws JSONException {
        // First fix the trace!
        // Don't fix it if it's a passive trace!
        // fixTcpdumpFile(file);
        String jsonResponse = sendMetadata(file);
        JSONObject mainObject = new JSONObject(jsonResponse);
        String path = mainObject.getString("store_path");
        post(path, file);
    }

    private void sendTraces(File[] tracesToSend) {
        if (tracesToSend == null)
            return;
        for (int i = 0; i < tracesToSend.length; i++) {
            if (tracesToSend[i].isFile()) {
                try{
                    sendTrace(tracesToSend[i]);
                    tracesToSend[i].delete();
                } catch (JSONException e) {}
            } else if (tracesToSend[i].isDirectory()) {
                Log.d("MAINSERVICE", "Directory " + tracesToSend[i].getName());
            }
        }
        Log.d("MAINSERVICE", "Good job!");
    }

    public void configureAndReschedule() {
        Log.d("MAINSERVICE", "In configureAndReschedule");
        if (checkConfigFile()) {
            Log.d("MAINSERVICE", "ConfigFile must change!");
            new Thread(new Runnable() {
                public void run() {
                    configure(true);
                    writeConfigFile();
                    File[] tracesToSend = getReadyTraces();
                    killAndRelaunchTcpdump();
                    try  {
                        // Wait for full config
                        Thread.sleep(20000);
                    } catch (InterruptedException e) {}
                    reschedule();
                    sendTraces(tracesToSend);
                }
            }).start();
        } else {
            Log.d("MAINSERVICE", "ConfigFile should not be changed, but enforce it!");
            new Thread(new Runnable() {
                public void run() {
                    configure(false);
                    // Check if pid of tcpdump is still alive
                    // If not, launch a new instance of tcpdump
                    // First check if the file exists
                    // Notice that in this particular case, it's very difficult to send the traces...
                    File[] tracesToSend = getReadyTraces();
                    String newTcpdumpFileName = getTcpdumpFileName();
                    String cmd = "PROCESS_NUM=$(ps | grep tcpdump | grep -v grep | wc -l); " +
                            "if [ $PROCESS_NUM -eq 0 ]; then (" + getTcpdumpCmd(newTcpdumpFileName) + "&) ;" +
                            " fi";
                    try {
                        Cmd.runAsRoot(cmd);
                    } catch (Exception e) {
                        Log.e("MAINSERVICE", cmd + " failed; " + e);
                    }
                    reschedule();
                    // Check if a new file exist; if so, we can send previous traces!
                    File[] tracesNow = getReadyTraces();
                    if (tracesNow.length != tracesToSend.length && tracesToSend.length > 0) {
                        // Ok, we can send traces!
                        sendTraces(tracesToSend);
                    }}
            }).start();
        }
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_REDELIVER_INTENT;
    }

    public void onDestroy() {
        super.onDestroy();
        if (mpctrl != null) {
            Manager.destroy(getApplicationContext());
            Log.i(Manager.TAG, "Destroy service");
        }
    }
}

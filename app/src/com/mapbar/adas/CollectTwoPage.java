package com.mapbar.adas;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.mapbar.adas.anno.PageSetting;
import com.mapbar.adas.anno.ViewInject;
import com.mapbar.adas.utils.AlarmManager;
import com.mapbar.adas.utils.URLUtils;
import com.mapbar.hamster.BleCallBackListener;
import com.mapbar.hamster.BlueManager;
import com.mapbar.hamster.OBDEvent;
import com.mapbar.hamster.core.HexUtils;
import com.mapbar.hamster.core.ProtocolUtils;
import com.mapbar.hamster.log.Log;
import com.miyuan.obd.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@PageSetting(contentViewId = R.layout.collect_two_layout, toHistory = false)
public class CollectTwoPage extends AppBasePage implements AMapLocationListener, BleCallBackListener, View.OnClickListener {

    @ViewInject(R.id.title)
    private TextView title;
    @ViewInject(R.id.back)
    private View back;
    @ViewInject(R.id.report)
    private View reportV;
    private Timer heartTimer;
    private LocationManager locationManager;
    private List<String> list20 = new ArrayList();
    private List<String> list2060 = new ArrayList();
    private List<String> list60 = new ArrayList();
    private List<String> locationList = new ArrayList<>();
    private int currentSpeed;
    private long lastLocationTime;
    private long firstLocationTime;
    private long turnLocationTime;
    private LinkedList<Float> bears = new LinkedList<>();
    private LinkedList<Long> bearsTime = new LinkedList<>();

    private Map<Integer, List<String>> collecData = new HashMap<>();
    private volatile double maxSpeed = 0;

    private boolean startCollect;

    private boolean uploadSuccess;

    private long startTime;

    private volatile boolean hasTrun = false;

    private StringBuilder sb = new StringBuilder();

    private boolean isCollect;

    public AMapLocationClient mlocationClient;

    public AMapLocationClientOption mLocationOption = null;

    @Override
    public void onResume() {
        super.onResume();
        MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        back.setVisibility(View.GONE);
        reportV.setOnClickListener(this);
        title.setText("开始校准");
        if (!isCollect) {
            isCollect = true;
            locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                return;
            }
            mlocationClient = new AMapLocationClient(GlobalUtil.getContext());
//初始化定位参数
            mLocationOption = new AMapLocationClientOption();
//设置定位监听
            mlocationClient.setLocationListener(this);
//设置定位模式为高精度模式，Battery_Saving为低功耗模式，Device_Sensors是仅设备模式
            mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
//设置定位间隔,单位毫秒,默认为2000ms
            mLocationOption.setInterval(1000);
//设置定位参数
            mlocationClient.setLocationOption(mLocationOption);
// 此方法为每隔固定时间会发起一次定位请求，为了减少电量消耗或网络流量消耗，
// 注意设置合适的定位时间的间隔（最小间隔支持为1000ms），并且在合适时间调用stopLocation()方法来取消定位请求
// 在定位结束后，在合适的生命周期调用onDestroy()方法
// 在单次定位情况下，定位无论成功与否，都无需调用stopLocation()方法移除请求，定位sdk内部会移除
//启动定位
            mlocationClient.startLocation();
            GlobalUtil.getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    AlarmManager.getInstance().play(R.raw.start_adjust);
                }
            }, 2000);
            heartTimer = new Timer();
            heartTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    BlueManager.getInstance().send(ProtocolUtils.sentHeart());
                }
            }, 1000, 60 * 1000);

            collecData.put(20, list20);
            collecData.put(2060, list2060);
            collecData.put(60, list60);
            startTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onStart() {
        BlueManager.getInstance().addBleCallBackListener(this);
        super.onStart();
    }

    @Override
    public void onStop() {
        BlueManager.getInstance().removeCallBackListener(this);
        super.onStop();
    }

    @Override
    public boolean onBackPressed() {
        PageManager.finishActivity(MainActivity.getInstance());
        return true;
    }

    @Override
    public void onDestroy() {
        isCollect = false;
        mlocationClient.stopLocation();
        if (null != heartTimer) {
            heartTimer.cancel();
            heartTimer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if ("gps".equals(aMapLocation.getProvider())) {
            lastLocationTime = System.currentTimeMillis();
            currentSpeed = (int) (aMapLocation.getSpeed() * 3.6);
            maxSpeed = currentSpeed > maxSpeed ? currentSpeed : maxSpeed;
            if (!startCollect && currentSpeed > 15) {
                startCollect = true;
                firstLocationTime = System.currentTimeMillis();
                BlueManager.getInstance().send(ProtocolUtils.startCollect());
            }
            sb = new StringBuilder();
            sb.append(lastLocationTime).append("#")
                    .append(currentSpeed).append("#")
                    .append(aMapLocation.getLongitude()).append("#")
                    .append(aMapLocation.getLatitude()).append("#");
            locationList.add(sb.toString());

            if (!startCollect && startTime != 0 && System.currentTimeMillis() - startTime > 40 * 1000) {
                Log.d("提示加速到20迈");
                AlarmManager.getInstance().play(R.raw.speed_20);
                startTime = System.currentTimeMillis();
                return;
            }

            Log.d("location.getBearing() " + aMapLocation.getBearing() + "     currentSpeed  " + currentSpeed);

            if (startCollect) {
                if (currentSpeed > 10) {
                    if (bears.size() > 9) {
                        bears.pollFirst();
                        bearsTime.pollFirst();
                    }
                    bears.addLast(aMapLocation.getBearing());
                    bearsTime.addLast(aMapLocation.getTime());
                }
                if (!hasTrun) {
                    if (hasTurn()) {
                        turnLocationTime = System.currentTimeMillis();
                        hasTrun = true;
                    }
                }
                if (hasTrun && maxSpeed >= 50 && (list20.size() > 0 || list2060.size() > 0 || list60.size() > 0)) {
                    if (!uploadSuccess) {
                        uploadSuccess = true;
                        stopCollect();
                    }
                } else {
                    if (!hasTrun) {
                        if (System.currentTimeMillis() - firstLocationTime >= 1000 * 40) {
                            // 提示请完成掉头操作
                            Log.d("提示请完成掉头操作");
                            if (BackStackManager.getInstance().getCurrent() instanceof CollectTwoPage) {
                                AlarmManager.getInstance().play(R.raw.trun);
                            }
                            firstLocationTime = System.currentTimeMillis();
                        }
                    } else {
                        if (System.currentTimeMillis() - turnLocationTime >= 5 || System.currentTimeMillis() - firstLocationTime >= 1000 * 40) {
                            // 提示请完成加速
                            turnLocationTime = Long.MAX_VALUE;
                            Log.d("提示请完成加速");
                            if (BackStackManager.getInstance().getCurrent() instanceof CollectTwoPage) {
                                AlarmManager.getInstance().play(R.raw.speed_60);
                            }
                            firstLocationTime = System.currentTimeMillis();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onEvent(int event, Object data) {
        switch (event) {
            case OBDEvent.COLLECT_DATA:
                byte[] onePackage = (byte[]) data;
                if (System.currentTimeMillis() - lastLocationTime > 5000) { // 超过5s没获取到定位，数据丢弃
                    return;
                }
                byte[] speed = new byte[]{(byte) currentSpeed};
                byte[] time = HexUtils.longToByte(lastLocationTime);
                byte[] pack = new byte[speed.length + time.length + onePackage.length];
                System.arraycopy(speed, 0, pack, 0, speed.length);
                System.arraycopy(time, 0, pack, speed.length, time.length);
                System.arraycopy(onePackage, 0, pack, speed.length + time.length, onePackage.length);
                if (currentSpeed < 20) {
                    list20.add(HexUtils.byte2HexStr(pack));
                } else if (currentSpeed >= 20 || currentSpeed <= 60) {
                    list2060.add(HexUtils.byte2HexStr(pack));
                } else {
                    list60.add(HexUtils.byte2HexStr(pack));
                }
                break;
        }
    }

    private void stopCollect() {
        mlocationClient.stopLocation();
        BlueManager.getInstance().send(ProtocolUtils.stopCollect());
        if (null != heartTimer) {
            heartTimer.cancel();
            heartTimer = null;
        }
        new Thread(new FileRunnable("Normal2050")).start();
        new Thread(new LocationRunnable("location")).start();
        updateCollectStauts();
    }

    private void updateCollectStauts() {
        boolean matching = getDate().getBoolean("matching");
        if (matching) {
            CollectPage collectPage = new CollectPage();
            Bundle bundle = new Bundle();
            bundle.putString("sn", getDate().getString("sn"));
            collectPage.setDate(bundle);
            PageManager.go(collectPage);
        } else {
            CollectFinish collectFinish = new CollectFinish();
            Bundle bundle = new Bundle();
            bundle.putString("sn", getDate().getString("sn"));
            bundle.putString("pVersion", getDate().getString("pVersion"));
            bundle.putString("bVersion", getDate().getString("bVersion"));
            bundle.putBoolean("success", false);
            collectFinish.setDate(bundle);
            PageManager.go(collectFinish);
        }

    }

    private boolean hasTurn() {
        if (bears.size() > 2) {
            float last = bears.getLast();
            long lastTime = bearsTime.getLast();
            // 最后一个和之前的比较
            for (int i = 0; i < bears.size() - 2; i++) {
                if (150 <= Math.abs(last - bears.get(i)) && Math.abs(last - bears.get(i)) <= 210 && lastTime - bearsTime.get(i) <= 10000) {
                    return true;
                }
            }
        }
        return false;
    }


    private void uploadCollectData(String filePath) {
        final File file = new File(filePath);

        Log.d("uploadCollectData input ");

        MediaType type = MediaType.parse("application/octet-stream");//"text/xml;charset=utf-8"
        RequestBody fileBody = RequestBody.create(type, file);

        RequestBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.ALTERNATIVE)
                //一样的效果
                .addPart(MultipartBody.Part.createFormData("serialNumber", getDate().getString("sn")))
                .addPart(MultipartBody.Part.createFormData("type", "2"))
                .addPart(Headers.of(
                        "Content-Disposition",
                        "form-data; name=\"file\"; filename=\"Normal2050\"")
                        , fileBody).build();

        Request request = new Request.Builder()
                .url(URLUtils.UPDATE_ERROR_FILE)
                .post(multipartBody)
                .build();

        GlobalUtil.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("uploadCollectData onFailure " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responese = response.body().string();
                Log.d("uploadCollectData success " + responese);
                try {
                    final JSONObject result = new JSONObject(responese);
                    if ("000".equals(result.optString("status"))) {
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                } catch (JSONException e) {
                    Log.d("uploadCollectData failure " + e.getMessage());
                }
            }
        });
    }

    private void uploadLocationData(String filePath) {

        final File file = new File(filePath);

        Log.d("uploadLocationData input ");

        MediaType type = MediaType.parse("application/octet-stream");//"text/xml;charset=utf-8"
        RequestBody fileBody = RequestBody.create(type, file);

        RequestBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.ALTERNATIVE)
                //一样的效果
                .addPart(MultipartBody.Part.createFormData("serialNumber", getDate().getString("sn")))
                .addPart(MultipartBody.Part.createFormData("type", "3"))
                .addPart(Headers.of(
                        "Content-Disposition",
                        "form-data; name=\"file\"; filename=\"location\"")
                        , fileBody).build();


        Request request = new Request.Builder()
                .url(URLUtils.UPDATE_ERROR_FILE)
                .post(multipartBody)
                .build();

        GlobalUtil.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("uploadCollectData onFailure " + e.getMessage());

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responese = response.body().string();
                Log.d("uploadCollectData success " + responese);
                try {
                    final JSONObject result = new JSONObject(responese);
                    if ("000".equals(result.optString("status"))) {
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                } catch (JSONException e) {
                    Log.d("uploadCollectData failure " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.report:
                uploadLog();
                break;
        }
    }

    private void uploadLog() {
        Log.d("CollectTwoPage uploadLog ");
        final File dir = new File(Environment.getExternalStorageDirectory().getPath() + File.separator + "obd");
        final File[] logs = dir.listFiles();

        if (null != logs && logs.length > 0) {
            MultipartBody.Builder builder = new MultipartBody.Builder();
            builder.addPart(MultipartBody.Part.createFormData("serialNumber", getDate().getString("sn")))
                    .addPart(MultipartBody.Part.createFormData("type", "1"));
            for (File file : logs) {
                builder.addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("application/octet-stream"), file));
            }
            Request request = new Request.Builder()
                    .url(URLUtils.UPDATE_ERROR_FILE)
                    .post(builder.build())
                    .build();

            GlobalUtil.getOkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.d("CollectTwoPage uploadLog onFailure " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responese = response.body().string();
                    Log.d("CollectTwoPage uploadLog success " + responese);
                    try {
                        final JSONObject result = new JSONObject(responese);
                        if ("000".equals(result.optString("status"))) {
                            GlobalUtil.getHandler().post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getContext(), "上报成功", Toast.LENGTH_SHORT).show();
                                }
                            });
                            for (File delete : logs) {
                                delete.delete();
                            }
                        }
                    } catch (JSONException e) {
                        Log.d("OBDAuthPage uploadLog failure " + e.getMessage());
                    }
                }
            });
        }
    }

    private class FileRunnable implements Runnable {

        private String fileName;

        public FileRunnable(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void run() {
            try {
                File dir = new File(Environment.getExternalStorageDirectory() + File.separator + "obd_collect" + File.separator);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File file = new File(dir, fileName);
                try {
                    FileOutputStream fos = new FileOutputStream(file);
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

                    for (String str : list20) {
                        bw.write(str);
                        bw.newLine();
                        bw.flush();
                    }
                    for (String str : list2060) {
                        bw.write(str);
                        bw.newLine();
                        bw.flush();
                    }
                    for (String str : list60) {
                        bw.write(str);
                        bw.newLine();
                        bw.flush();
                    }
                    bw.close();
                    fos.close();
                    // 上传
                    uploadCollectData(file.getPath());
                } catch (FileNotFoundException e) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class LocationRunnable implements Runnable {

        private String fileName;

        public LocationRunnable(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void run() {
            try {
                File dir = new File(Environment.getExternalStorageDirectory() + File.separator + "obd_collect" + File.separator);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File file = new File(dir, fileName);
                try {
                    FileOutputStream fos = new FileOutputStream(file);
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

                    for (String str : locationList) {
                        bw.write(str);
                        bw.newLine();
                        bw.flush();
                    }
                    bw.close();
                    fos.close();
                    // 上传
                    uploadLocationData(file.getPath());
                } catch (FileNotFoundException e) {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

package com.mapbar.adas;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.text.Html;
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
import com.mapbar.hamster.core.ProtocolUtils;
import com.mapbar.hamster.log.Log;
import com.miyuan.obd.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


@PageSetting(contentViewId = R.layout.collect_layout)
public class CollectPage extends AppBasePage implements View.OnClickListener, BleCallBackListener, AMapLocationListener {

    @ViewInject(R.id.title)
    private TextView title;
    @ViewInject(R.id.back)
    private View back;
    @ViewInject(R.id.report)
    private View reportV;
    @ViewInject(R.id.status)
    private View statusV;
    @ViewInject(R.id.content)
    private TextView contentTV;

    public AMapLocationClient mlocationClient;

    public AMapLocationClientOption mLocationOption = null;


    private int currentSpeed;

    private boolean isStudy;

    private LinkedList<Integer> adjustSpeed = new LinkedList<>();

    private LocationManager locationManager;

    private AnimationDrawable animationDrawable;

    @Override
    public void onResume() {
        super.onResume();
        MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        title.setText("校准即将完成-6");
        back.setVisibility(View.GONE);
        reportV.setOnClickListener(this);
        statusV.setBackgroundResource(R.drawable.check_status_bg);
        animationDrawable = (AnimationDrawable) statusV.getBackground();
        animationDrawable.start();
        contentTV.setText(Html.fromHtml("<font color='#4A4A4A'>请您保持</font><font color='#009488'>直行</font><font color='#4A4A4A'>,并提速至</font><font color='#009488'>60km/h以上</font><font color='#4A4A4A'>,路面尽量平整、须直行。</font><br><br><font color='#009488'>当道路不够直、或者路面不平时，请您提前减速至40km/h以下。</font>"));
        if (!isStudy) {
            isStudy = true;
            GlobalUtil.getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    AlarmManager.getInstance().play(R.raw.adjust_last);
                }
            }, 2000);
            GlobalUtil.getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    BlueManager.getInstance().send(ProtocolUtils.study());
                }
            }, 5000);
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
//            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000l, 0, this);
        }
    }

    @Override
    public boolean onBackPressed() {
        PageManager.finishActivity(MainActivity.getInstance());
        return true;
    }

    @Override
    public void onStart() {
        BlueManager.getInstance().addBleCallBackListener(this);
        super.onStart();
    }


    @Override
    public void onDestroy() {
        isStudy = false;
        mlocationClient.stopLocation();
        super.onDestroy();
    }

    @Override
    public void onStop() {
        BlueManager.getInstance().removeCallBackListener(this);
        animationDrawable.stop();
        super.onStop();
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
        Log.d("CollectPage uploadLog ");
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
                    Log.d("CollectPage uploadLog onFailure " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responese = response.body().string();
                    Log.d("CollectPage uploadLog success " + responese);
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
                        Log.d("CollectPage uploadLog failure " + e.getMessage());
                    }
                }
            });
        }
    }

    @Override
    public void onEvent(int event, Object data) {
        switch (event) {
            case OBDEvent.ADJUST_SUCCESS:
                mlocationClient.stopLocation();
                CollectFinish collectFinish = new CollectFinish();
                Bundle bundle = new Bundle();
                bundle.putBoolean("success", true);
                collectFinish.setDate(bundle);
                PageManager.go(collectFinish);
                break;
        }
    }

    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (aMapLocation.getErrorCode() == 0) {
            if ("gps".equals(aMapLocation.getProvider())) {
                currentSpeed = (int) (aMapLocation.getSpeed() * 3.6);
                if (currentSpeed < 50) {
                    adjustSpeed.addLast(currentSpeed);
                    if (adjustSpeed.size() >= 70) {
                        Log.d("校准即将完成 play");
                        AlarmManager.getInstance().play(R.raw.adjust_last);
                        adjustSpeed.clear();
                    }
                } else {
                    adjustSpeed.clear();
                }
            }
        }
    }
}

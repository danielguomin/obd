package com.mapbar.adas;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbar.adas.anno.PageSetting;
import com.mapbar.adas.anno.ViewInject;
import com.mapbar.adas.utils.URLUtils;
import com.mapbar.hamster.log.Log;
import com.miyuan.obd.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@PageSetting(contentViewId = R.layout.confirm_car_layout)
public class ConfirmCarPage extends AppBasePage implements View.OnClickListener {

    String carName = "";
    @ViewInject(R.id.title)
    private TextView title;
    @ViewInject(R.id.back)
    private View back;
    @ViewInject(R.id.report)
    private View reportV;
    @ViewInject(R.id.next)
    private TextView next;
    @ViewInject(R.id.car_info)
    private TextView carInfo;
    @ViewInject(R.id.goback)
    private View goBack;
    private int times = 1;

    @Override
    public void onResume() {
        super.onResume();
        MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        title.setText("确认车型");
        next.setOnClickListener(this);
        back.setOnClickListener(this);
        reportV.setOnClickListener(this);
        goBack.setOnClickListener(this);
        if (null != getDate()) {
            carName = getDate().get("carName").toString();
            carInfo.setText(carName);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.back:
            case R.id.goback:
                PageManager.back();
                break;
            case R.id.report:
                uploadLog();
                break;
            case R.id.next:
                if (times >= 2) {
                    OBDActivatePage page = new OBDActivatePage();
                    Bundle bundle = new Bundle();
                    bundle.putString("boxId", getDate().getString("boxId"));
                    bundle.putString("phone", getDate().getString("phone"));
                    bundle.putString("code", getDate().getString("code"));
                    bundle.putString("sn", getDate().getString("sn").toString());
                    bundle.putString("carId", getDate().getString("carId").toString());
                    bundle.putString("carName", getDate().get("carName").toString());
                    page.setDate(bundle);
                    PageManager.go(page);
                } else {
                    title.setText("再次确认");
                }
                times++;
                break;
        }
    }

    private void uploadLog() {
        Log.d("ConfirmCarPage uploadLog ");
        final File dir = new File(Environment.getExternalStorageDirectory().getPath() + File.separator + "obd");
        final File[] logs = dir.listFiles();

        if (null != logs && logs.length > 0) {
            MultipartBody.Builder builder = new MultipartBody.Builder();
            builder.addPart(MultipartBody.Part.createFormData("serialNumber", getDate().getString("sn").toString()))
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
                    Log.d("ConfirmCarPage uploadLog onFailure " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responese = response.body().string();
                    Log.d("ConfirmCarPage uploadLog success " + responese);
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
                        Log.d("ConfirmCarPage uploadLog failure " + e.getMessage());
                    }
                }
            });
        }
    }
}

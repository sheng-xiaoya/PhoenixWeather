package wanghaisheng.com.phoenixweather.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import wanghaisheng.com.phoenixweather.R;
import wanghaisheng.com.phoenixweather.db.WeatherDB;
import wanghaisheng.com.phoenixweather.model.City;
import wanghaisheng.com.phoenixweather.model.County;
import wanghaisheng.com.phoenixweather.model.Province;
import wanghaisheng.com.phoenixweather.util.HttpCallbackListener;
import wanghaisheng.com.phoenixweather.util.HttpUtil;
import wanghaisheng.com.phoenixweather.util.MyApplication;
import wanghaisheng.com.phoenixweather.util.Utility;


public class ChooseAreaActivity extends Activity {
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;

    private ProgressDialog progressDialog;
    private TextView titleView;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private WeatherDB yakerWeatherDB;

    /**
     * ListView data
     */
    private List<String> dataList = new ArrayList<>();

    /**
     * 省列表
     */
    private List<Province> provinceList;
    /**
     * 市列表
     */
    private List<City> cityList;
    /**
     * 县列表
     */
    private List<County> countyList;

    /**
     * 选中的省
     */
    private Province selectedProvince;
    /**
     * 选中的市
     */
    private City selectedCity;

    /**
     * 当前选中的级别
     * @param savedInstanceState
     */
    private int currentLevel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MyApplication.getContext());
        if(prefs.getBoolean("city_selected",false)) {
            Intent intent = new Intent(ChooseAreaActivity.this,WeatherActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.choose_area);

        listView = (ListView) findViewById(R.id.list_view);
        titleView = (TextView) findViewById(R.id.title_text);
        adapter = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,dataList);

        listView.setAdapter(adapter);

        yakerWeatherDB = WeatherDB.getInstance(this);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(currentLevel == LEVEL_PROVINCE) {
                    selectedProvince = provinceList.get(position);
                    queryCities();
                } else if(currentLevel == LEVEL_CITY) {
                    selectedCity = cityList.get(position);
                    queryCounties();
                } else if(currentLevel == LEVEL_COUNTY) {
                    String countyCode = countyList.get(position).getCountyCode();
                    Intent intent = new Intent(ChooseAreaActivity.this,WeatherActivity.class);
                    intent.putExtra("county_code",countyCode);
                    startActivity(intent);
                    finish();
                }
            }
        });

        queryProvinces();// 加载省级数据
    }

    /**
     * 查询全国所有的省，优先从数据库查询，如果没有查询到再去服务器上查询。
     */
    private void queryProvinces() {
        provinceList = yakerWeatherDB.loadProvinces();
        if(provinceList.size()>0) {
            dataList.clear();
            for(Province p:provinceList) {
                dataList.add(p.getProvinceName());
            }

            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleView.setText("中国");
            currentLevel = LEVEL_PROVINCE;
        } else {
            queryFromServer(null, "province");
        }
    }

    /**
     * 查询City数据
     */
    private void queryCities() {
        cityList = yakerWeatherDB.loadCities(selectedProvince.getId());
        if(cityList.size()>0) {
            dataList.clear();
            for(City city:cityList) {
                dataList.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleView.setText(selectedProvince.getProvinceName());
            currentLevel = LEVEL_CITY;
        } else {
            queryFromServer(selectedProvince.getProvinceCode(),"city");
        }
    }

    /**
     * 查询county数据
     */
    private void queryCounties() {
        countyList = yakerWeatherDB.loadCounties(selectedCity.getId());
        if(countyList.size()>0) {
            dataList.clear();
            for(County county:countyList) {
                dataList.add(county.getCountyName());
            }

            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleView.setText(selectedCity.getCityName());
            currentLevel = LEVEL_COUNTY;
        } else {
            queryFromServer(selectedCity.getCityCode(),"county");
        }
    }

    /**
     * 根据传入的代号和类型从服务器上查询省市县数据。
     * @param code
     * @param type
     */
    private void queryFromServer(final String code,final String type) {
        String address = null;
        if(!TextUtils.isEmpty(code)) {
            address = "http://www.weather.com.cn/data/list3/city" + code + ".xml";
        } else {
            address = "http://www.weather.com.cn/data/list3/city.xml";
        }

        showProgressDialog();

        HttpUtil.sendHttpRequest(address, new HttpCallbackListener() {
            @Override
            public void onFinish(String response) {
                boolean result = false;
                if ("province".equals(type)) {
                    result = Utility.handleProvincesResponse(yakerWeatherDB, response);
                } else if ("city".equals(type)) {
                    result = Utility.handleCitiesResponse(yakerWeatherDB, response, selectedProvince.getId());
                } else if ("county".equals(type)) {
                    result = Utility.handleCountiesResponse(yakerWeatherDB, response, selectedCity.getId());
                }

                if (result) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals(type)) {
                                queryProvinces();
                            } else if ("city".equals(type)) {
                                queryCities();
                            } else if ("county".equals(type)) {
                                queryCounties();
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                // 通过runOnUiThread()方法回到主线程处理逻辑
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(ChooseAreaActivity.this, "加载失败", Toast.LENGTH_SHORT);
                    }
                });
            }
        });
    }

    /**
     * 显示进度对话框
     */
    private void showProgressDialog() {
        if(null == progressDialog) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("正在加载中....");
            progressDialog.setCanceledOnTouchOutside(false);
        }

        progressDialog.show();
    }

    /**
     * 关闭进度对话框
     */
    private void closeProgressDialog() {
        if(null != progressDialog) {
            progressDialog.dismiss();
        }
    }

    /**
     * 捕获Back按键，根据当前的级别来判断，此时应该返回市列表、省列表、还是直接退出。
     */
    @Override
    public void onBackPressed() {
        if(currentLevel==LEVEL_COUNTY) {
            queryCities();
        } else if(currentLevel==LEVEL_CITY) {
            queryProvinces();
        } else {
            finish();
        }
    }
}
package com.mittytomorrow.locationindoor;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapBaseIndoorMapInfo;
import com.baidu.mapapi.map.MapBaseIndoorMapInfo.SwitchFloorError;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationConfiguration.LocationMode;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.mittytomorrow.locationindoor.MyOrientationListener.OnOrientationListener;

//import com.mittytomorrow.locationindoor.MyOrientationListener;



public class MainActivity extends AppCompatActivity implements BDLocationListener{

    private TextView mTextMessage;
    private MapView mMapView = null;
    private BaiduMap mBaiduMap=null;
    public LocationClient mLocationClient=null;
    public LocationMode mCurrentMode=null;
    private  boolean isFirstLoc=true;
    private MyOrientationListener mOrientationListener;
    private float mCurrentX;
    private MapBaseIndoorMapInfo myMapBaseIndoorMapInfo;

    private FrameLayout preview;
    private CameraPreview mPreview;
    private Button buttonSettings;
    private Button buttonStartPreview;
    private Button buttonStopPreview;
    private LinearLayout buttonPreview;
    private SettingsFragment mySettingsFragment;
    private Boolean setNotShow=true;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    mTextMessage.setText(R.string.title_home);
                    mMapView.setVisibility(View.VISIBLE);//室外图
                    stopPreview();//3D视图下关闭摄像头
                    return true;
                case R.id.navigation_dashboard:
                    mTextMessage.setText(R.string.title_dashboard);
                    mMapView.setVisibility(View.INVISIBLE);
                    startPreview();
                    return true;
                case R.id.navigation_notifications:
                    mTextMessage.setText(R.string.title_notifications);
                    mMapView.setVisibility(View.INVISIBLE);
                    stopPreview();
                    return true;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //在使用SDK各组件之前初始化context信息，传入ApplicationContext
        //注意该方法要再setContentView方法之前实现
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);
        //获取地图控件引用
        mMapView = (MapView) findViewById(R.id.bmapView);
        initOutdoorLocation();

//----------------------------------------------------------------------------------------------------
        mTextMessage = (TextView) findViewById(R.id.message);
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        //---------------------------------------------------------------------------------------------
        buttonPreview=(LinearLayout)findViewById(R.id.bottom_preview);

//        buttonStopPreview = (Button) findViewById(R.id.button_stop_preview);
//        buttonStopPreview.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                stopPreview();
//            }
//        });
//        SurfaceView mySurfaceView = (SurfaceView) findViewById(R.id.surface_view);

        preview = (FrameLayout) findViewById(R.id.camera_preview);

        buttonSettings = (Button) findViewById(R.id.button_settings);


        //-----------------------------------------------------------------------------------------------
        setSystemUIVisible(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBaiduMap.setMyLocationEnabled(true);
        if (!mLocationClient.isStarted()) {
            // 开启定位
            mLocationClient.start();
            // 开启方向传感器
            mOrientationListener.start();
        }
        setSystemUIVisible(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mBaiduMap.setMyLocationEnabled(false);
        // 停止定位
        mLocationClient.stop();
        // 停止方向传感器
        mOrientationListener.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        setSystemUIVisible(false);
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        mMapView.onPause();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        mMapView.onDestroy();
    }

    private void initLocation() {
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy );
        //可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        option.setCoorType("bd09ll");
        //可选，默认gcj02，设置返回的定位结果坐标系
        int span = 1000;
        option.setScanSpan(span);//可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的
        option.setIsNeedAddress(true);
        //可选，设置是否需要地址信息，默认不需要
//        option.setPriority(LocationClientOption.NetWorkFirst); // 设置网络优先

//        option.setPriority(LocationClientOption.GpsFirst); //设置gps优先
        option.setOpenGps(true);//可选，默认false,设置是否使用gps
        option.setLocationNotify(true);//可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        option.setIsNeedLocationDescribe(true);//可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
        option.setIsNeedLocationPoiList(true);//可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
        option.setIgnoreKillProcess(false); //可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
        option.SetIgnoreCacheException(false);//可选，默认false，设置是否收集CRASH信息，默认收集
        option.setEnableSimulateGps(false);//可选，默认false，设置是否需要过滤gps仿真结果，默认需要
        mLocationClient.setLocOption(option);

        mOrientationListener = new MyOrientationListener(this);

        mOrientationListener
                .setmOnOrientationListener(new OnOrientationListener() {

                    @Override
                    public void onOrientationChanged(float x) {
                        mCurrentX = x;
                    }
                });

        mBaiduMap.setOnBaseIndoorMapListener(new BaiduMap.OnBaseIndoorMapListener() {

            @Override
            public void onBaseIndoorMapMode(boolean b, MapBaseIndoorMapInfo mapBaseIndoorMapInfo) {
                if (mapBaseIndoorMapInfo!=null) {
                    // 进入室内图
                    // 通过获取回调参数 mapBaseIndoorMapInfo 便可获取室内图信息，包含楼层信息，室内ID等
                    //MapBaseIndoorMapInfo mymapBaseIndoorMapInfo=mBaiduMap.getFocusedBaseIndoorMapInfo();
                    myMapBaseIndoorMapInfo=mapBaseIndoorMapInfo;
                    String indoorID=mapBaseIndoorMapInfo.getID();
                    Log.v("indoorID",indoorID);
                    String indoorCurFloor=mapBaseIndoorMapInfo.getCurFloor();
                    Log.v("indoorCurFloor",indoorCurFloor);

//                    //get indoorFloors 之心城：B2，F1，F3，F5，F7
//                    ArrayList<String> indoorFloors=mapBaseIndoorMapInfo.getFloors();
//                    Iterator<String> indoorFloor=indoorFloors.iterator();
//                    while(indoorFloor.hasNext()){
//                        Log.v("indoorFloors"+indoorFloor.next(),indoorFloor.next());
//                        //get indoorFloors 之心城：B2，F1，F3，F5，F7
////                        out.println(i.next());
//                    }

//                    Log.d(String tag, String msg);
//                    Log.i(String tag, String msg);
//                    Log.w(String tag, String msg);
//                    Log.e(String tag, String msg);
                    //分别对应 Verbose, Debug, Info, Warning,Error.

                } else {
                    // 移除室内图
                }
            }
        });
    }

    @Override
    public void onReceiveLocation(BDLocation location) {

        MyLocationData locData = new MyLocationData.Builder()
                .accuracy(location.getRadius())
                // 此处设置开发者获取到的方向信息，顺时针0-360
                .direction(mCurrentX)
                .latitude(31.860291)//.latitude(location.getLatitude())
                .longitude(117.263888)//.longitude(location.getLongitude())
                .build(); // 设置定位数据
        //华润五彩城31.835828，117.257186
        //之心城31.860291，117.263888
        //accuracy
        //定位精度
        //float	direction
        //GPS定位时方向角度
        //double	latitude
        //百度纬度坐标
        //double	longitude
        //百度经度坐标
        //int	satellitesNum
        //GPS定位时卫星数目
        //float	speed
        //GPS定位时速度
        mBaiduMap.setMyLocationData(locData);
        if(myMapBaseIndoorMapInfo!=null){
            SwitchFloorError switchFloorError =
                    mBaiduMap.switchBaseIndoorMapFloor("F3",myMapBaseIndoorMapInfo.getID());// mapBaseIndoorMapInfo.getID()); // 切换楼层信息

            switch (switchFloorError) {
                case SWITCH_OK:
                    // 切换成功
                    break;
                case FLOOR_INFO_ERROR:
                    // 切换楼层, 室内ID信息错误
                    break;
                case FLOOR_OVERLFLOW:
                    // 切换楼层室内ID与当前聚焦室内ID不匹配
                    break;
                case FOCUSED_ID_ERROR:
                    // 切换楼层室内ID与当前聚焦室内ID不匹配
                    break;
                case SWITCH_ERROR:
                    // 切换楼层错误
                    break;
                default:
                    break;
            }
        }

        if (isFirstLoc) {
            LatLng ll = new LatLng(location.getLatitude(), location.getLongitude()); MapStatusUpdate update = MapStatusUpdateFactory.newLatLngZoom(ll, 16);//设置地图中心及缩放级别
            mBaiduMap.animateMapStatus(update);
            isFirstLoc = false;
            Toast.makeText(getApplicationContext(), location.getAddrStr(), Toast.LENGTH_SHORT ).show();
        }
        mMapView.refreshDrawableState();
    }
    private void initOutdoorLocation()
    {
        mBaiduMap = mMapView.getMap();

        mLocationClient = new LocationClient(getApplicationContext()); //声明LocationClient类
        mLocationClient.registerLocationListener(this);//注册监听函数
        initLocation(); // 开启定位图层
        mBaiduMap.setMyLocationEnabled(true);//显示定位层并且可以触发定位,默认是flase
//        mLocationClient.start();//开启定位
//        LocationMode mCurrentMode = LocationMode.FOLLOWING;//定位跟随态
//        mCurrentMode = LocationMode.NORMAL;   //默认为 LocationMode.NORMAL 普通态
        mCurrentMode = LocationMode.COMPASS;  //定位罗盘态
        BitmapDescriptor mCurrentMarker = BitmapDescriptorFactory .fromResource(R.drawable.icon_geo);
// 支持自定义定位图标样式，替换定位icon
        int accuracyCircleFillColor = 0xAAFFFF88;//自定义精度圈填充颜色
        int accuracyCircleStrokeColor = 0xAA00FF00;//自定义精度圈边框颜色
        mBaiduMap.setMyLocationConfiguration(new MyLocationConfiguration(
                mCurrentMode, true, mCurrentMarker,
                accuracyCircleFillColor, accuracyCircleStrokeColor));
        mBaiduMap.setIndoorEnable(true);//打开室内图，默认为关闭状态
        mBaiduMap.setCompassEnable(true);//打开方向罗盘
        mMapView.showZoomControls(false);// 不显示地图缩放控件（按钮控制栏）

        // 不显示地图上比例尺
//        mMapView.showScaleControl(false);

//        // 隐藏百度的LOGO
//        View child = mMapView.getChildAt(1);
//        if (child != null && (child instanceof ImageView || child instanceof ZoomControls)) {
//            child.setVisibility(View.INVISIBLE);
//        }

        MapStatusUpdate msu = MapStatusUpdateFactory.zoomTo(15.0f);// 设置地图放大比例
        mBaiduMap.setMapStatus(msu);
    }
    private void setSystemUIVisible(boolean show) {
        if (show) {
            int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            uiFlags |= 0x00001000;
            getWindow().getDecorView().setSystemUiVisibility(uiFlags);
        } else {
            int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
            uiFlags |= 0x00001000;
            getWindow().getDecorView().setSystemUiVisibility(uiFlags);
        }
    }


    public void startPreview() {//3D视图下打开摄像头

        buttonPreview.setVisibility(View.VISIBLE);
        preview.setVisibility(View.VISIBLE);
        mPreview = new CameraPreview(this);
//        mPreview.show();
        preview.addView(mPreview);

        SettingsFragment.passCamera(mPreview.getCameraInstance());
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        SettingsFragment.setDefault(PreferenceManager.getDefaultSharedPreferences(this));
        SettingsFragment.init(PreferenceManager.getDefaultSharedPreferences(this));

        setNotShow=true;
        mySettingsFragment=new SettingsFragment();
        buttonSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(setNotShow) {
                    setNotShow=false;
                    getFragmentManager().beginTransaction().add(R.id.camera_preview, mySettingsFragment).addToBackStack(null).commit();
                }
            }
        });
        buttonStartPreview = (Button) findViewById(R.id.button_start_preview);
        buttonStartPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setNotShow=true;
                getFragmentManager().beginTransaction().remove(mySettingsFragment).addToBackStack(null).commit();
            }
        });
    }

    public void stopPreview() {//3D视图下关闭摄像头
        buttonPreview.setVisibility(View.INVISIBLE);
        preview.setVisibility(View.INVISIBLE);
//        mPreview.hide();
        preview.removeAllViews();
    }
}



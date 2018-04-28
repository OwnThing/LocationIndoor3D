package com.mittytomorrow.locationindoor;

import android.app.Application;

import com.baidu.mapapi.SDKInitializer;

public class sdkApplication extends Application {

    @Override
    public void onCreate(){
        super.onCreate();
        //在使用SDK各组之前初始化context信息，传入ApplicationContext
        SDKInitializer.initialize(this);
    }
}

package com.bandwidwith.rw.mte;

import android.content.Context;
import android.util.Log;

import com.bandwidth.rw.WifiQuality;
import com.bandwidth.rw.WifiQualityChecker;

public class RunMte implements Runnable {
    
    public boolean run = true;
    private Context mContext;
    private MteListener mListener;
    
    public RunMte(Context context, MteListener listener) {
        mContext = context;
        mListener = listener;
    }
    
    public RunMte(MainActivity a) {
        this(a, a);
    }

    @Override
    public void run() {
        int num = 50;
        while(run) {
            Log.i("mte", "checking");
            WifiQuality q = new WifiQuality();
            WifiQualityChecker.checkBlocking(mContext,
                    num, 
                    q);
            mListener.onReuslt(q);
            try {
                if(q.unreachable()) {
                    Log.i("mte", "sleeping");
                    Thread.sleep(num*20);
                    num = Math.min(num+50, 150);
                }
            } catch (InterruptedException e) {
            }
        }
    }

}

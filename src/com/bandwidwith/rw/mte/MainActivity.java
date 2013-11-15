package com.bandwidwith.rw.mte;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.bandwidth.rw.Env;
import com.bandwidth.rw.WifiQuality;
import com.bandwidth.rw.WifiQualityChecker;
import com.bandwidth.rw.WifiQuality.Category;

import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

public class MainActivity extends Activity implements MteListener {
    
    private Button mStartButton;
    private Button mStopButton;
    private RunMte mRunMte;
    private MinIsBetter minIsBetter = new MinIsBetter();
    private MaxIsBetter maxIsBetter = new MaxIsBetter();
    private WakeLock mWakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mStartButton = (Button)findViewById(R.id.start_button);
        mStopButton = (Button)findViewById(R.id.stop_button);
        
        mStartButton.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View arg0) {
                onStartMte();
            }
        });
        
        mStopButton.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                onStopMte();
            }
        });
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        onStopMte();
    }
    
    private void onStartMte() {
        Env.setOverideEnv(getBaseContext(), Env.QA);
        
        mStartButton.setEnabled(false);
        mStopButton.setEnabled(true);
        
        TextView host = (TextView) findViewById(R.id.host);
        host.setText(WifiQualityChecker.getEchoSvr());
        
        TextView start = (TextView) findViewById(R.id.start);
        
        SimpleDateFormat format = new SimpleDateFormat("MMM dd,yyyy  hh:mm a");
        String date = format.format(new Date(System.currentTimeMillis()));
        start.setText(date);
        
        TextView count = (TextView) findViewById(R.id.count);
        count.setText("0");
        
        TextView failed = (TextView) findViewById(R.id.failed);
        failed.setText("0");
        
        Chronometer elapsed = (Chronometer) findViewById(R.id.elapsed);
        elapsed.setBase(SystemClock.elapsedRealtime());
        elapsed.start();
        
        mResultMap.clear();
        for(Category c : Category.values()) {
            setView(lastId(c), "");
            setView(avgId(c), "");
            setView(bestId(c), "");
            setView(worstId(c), "");
            setView(stdevId(c), "");
        }
        
        if(mRunMte != null) {
            mRunMte.run = false;
        }
        mRunMte = new RunMte(this);
        new Thread(mRunMte).start();
        
        if(mWakeLock != null) {
            mWakeLock.release();
        }
        PowerManager powman = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powman.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "mte");
        mWakeLock.acquire();
    }
    
    private void onStopMte() {
        mStartButton.setEnabled(true);
        mStopButton.setEnabled(false);
        
        Chronometer elapsed = (Chronometer) findViewById(R.id.elapsed);
        elapsed.stop();
        
        if(mRunMte != null) {
            mRunMte.run = false;
            mRunMte = null;
        }
        
        if(mWakeLock != null) {
            if(mWakeLock.isHeld())
                mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    private static class Result {
        double last;
        double avg;
        double best;
        double worst;
        double stdev;
        double count = 0;
        Sorter sorter;
        
        public Result(Sorter sorter) {
            this.sorter = sorter;
        }
        
        public static Result newValue(double v, Sorter sorter) {
            Result r = new Result(sorter);
            r.last = v;
            r.avg = v;
            r.best = v;
            r.worst = v;
            r.stdev = -1.0;
            r.count = 1;
            return r;
        }
        
        private Result updateWith(Result n) {
            this.last = n.last;
            this.avg = ((this.avg*count)+n.last)/(this.count+1);
            this.count++;
            this.best = sorter.getBest(this.best, n.best);
            this.worst = sorter.getWorst(this.worst, n.worst);
            double m = this.count;
            if(m-1>0) {
                double var = (m-2)/(m-1)*(this.stdev*this.stdev) +
                        (1/m)*(n.last-this.avg)*(n.last-this.avg);
                this.stdev = Math.sqrt(var);
            } else {
                this.stdev = -1.0;
            }
            return this;
        }
    }
    
    private Map<Category, Result> mResultMap = new HashMap<Category, Result>();

    @Override
    public void onReuslt(final WifiQuality result) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Log.i("mte", "got result");
                for(Category c : WifiQuality.Category.values()) {
                    if(result.unreachable()) {
                        updateViewsUnreachable(c);
                        continue;
                    }
                    Result r = updateResult(c,
                            Result.newValue(getValue(c, result), getSorter(c)));
                    updateViews(c, r);
                }
                if(result.unreachable()) incrementFailed();
                else incrementCount();
                Log.i("mte", "finished result");
            }
        });
    }

    private Result updateResult(Category c, Result r) {
        if(!mResultMap.containsKey(c)) {
            mResultMap.put(c, r);
            return r;
        }
        Result o = mResultMap.get(c);
        return o.updateWith(r);
    }
    
    private double getValue(Category c, WifiQuality q) {
        switch(c) {
        case SIGNAL:
            return q.rssi;
        case LINK_SPEED:
            return q.linkSpeed;
        case LATENCY:
            return q.latency;
        case LOSS:
            return q.loss;
        case JITTER:
            return q.jitter;
        }
        return 0;
    }
    
    private Sorter getSorter(Category c) {
        switch(c) {
        case SIGNAL:
        case LINK_SPEED:
            return maxIsBetter;
        case LATENCY:
        case LOSS:
        case JITTER:
            return minIsBetter;
        }
        return maxIsBetter;
    }
    
    private void updateViews(Category c, Result r) {
        int lastId = lastId(c);
        int avgId = avgId(c);
        int bestId = bestId(c);
        int worstId = worstId(c);
        int stdevId = stdevId(c);
        setView(lastId, r.last);
        setView(avgId, r.avg);
        setView(bestId, r.best);
        setView(worstId, r.worst);
        setView(stdevId, r.stdev);
    }
    
    private void updateViewsUnreachable(Category c) {
        setView(lastId(c), "[u]");
    }
    
    private void incrementCount() {
        TextView v = (TextView)findViewById(R.id.count);
        int i = Integer.parseInt((String) v.getText());
        v.setText(Integer.toString(i+1));
    }
    
    private void incrementFailed() {
        TextView v = (TextView)findViewById(R.id.failed);
        int i = Integer.parseInt((String) v.getText());
        v.setText(Integer.toString(i+1));
    }
    
    private void setView(int id, double v) {
        TextView view = (TextView)findViewById(id);
        view.setText(Integer.toString((int)v));
    }
    
    private void setView(int id, String s) {
        TextView view = (TextView)findViewById(id);
        view.setText(s);
    }
    
    private int lastId(Category c) {
        switch(c) {
        case SIGNAL:
            return R.id.rssi_last;
        case LINK_SPEED:
            return R.id.link_last;
        case LATENCY:
            return R.id.latency_last;
        case LOSS:
            return R.id.loss_last;
        case JITTER:
            return R.id.jitter_last;
        }
        return 0;
    }
    
    private int avgId(Category c) {
        switch(c) {
        case SIGNAL:
            return R.id.rssi_avg;
        case LINK_SPEED:
            return R.id.link_avg;
        case LATENCY:
            return R.id.latency_avg;
        case LOSS:
            return R.id.loss_avg;
        case JITTER:
            return R.id.jitter_avg;
        }
        return 0;
    }
    
    private int bestId(Category c) {
        switch(c) {
        case SIGNAL:
            return R.id.rssi_best;
        case LINK_SPEED:
            return R.id.link_best;
        case LATENCY:
            return R.id.latency_best;
        case LOSS:
            return R.id.loss_best;
        case JITTER:
            return R.id.jitter_best;
        }
        return 0;
    }
    
    private int worstId(Category c) {
        switch(c) {
        case SIGNAL:
            return R.id.rssi_worst;
        case LINK_SPEED:
            return R.id.link_worst;
        case LATENCY:
            return R.id.latency_worst;
        case LOSS:
            return R.id.loss_worst;
        case JITTER:
            return R.id.jitter_worst;
        }
        return 0;
    }
    
    private int stdevId(Category c) {
        switch(c) {
        case SIGNAL:
            return R.id.rssi_stdev;
        case LINK_SPEED:
            return R.id.link_stdev;
        case LATENCY:
            return R.id.latency_stdev;
        case LOSS:
            return R.id.loss_stdev;
        case JITTER:
            return R.id.jitter_stdev;
        }
        return 0;
    }
}

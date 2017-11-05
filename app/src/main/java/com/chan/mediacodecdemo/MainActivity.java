package com.chan.mediacodecdemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surface);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		surfaceHolder.setKeepScreenOn(true);
		surfaceHolder.addCallback(this);
	}

	private Thread mThread;

	@Override
	public void surfaceCreated(SurfaceHolder surfaceHolder) {
		//mThread = new Thread(new VideoWorkRunnable(this, surfaceHolder.getSurface()));
		mThread = new Thread(new AudioWorkRunnable(this));
	}

	@Override
	public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
		mThread.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
		if (mThread != null) {
			mThread.interrupt();
			mThread = null;
		}
	}
}

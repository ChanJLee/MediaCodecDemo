package com.chan.mediacodecdemo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by chan on 2017/10/25.
 */

public class VideoWorkRunnable implements Runnable {
	private Surface mSurface;
	private MediaExtractor mMediaExtractor;
	private Context mContext;
	private MediaCodec mMediaCodec;


	public VideoWorkRunnable(Context context, Surface surface) {
		mContext = context;
		mSurface = surface;
		mMediaExtractor = new MediaExtractor();
	}

	@Override
	public void run() {
		try {
			start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void start() throws IOException {
		AssetFileDescriptor assetFileDescriptor = mContext.getAssets().openFd("默.mp3");
		mMediaExtractor.setDataSource(assetFileDescriptor);

		for (int i = 0; i < mMediaExtractor.getTrackCount(); ++i) {
			MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(i);
			String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
			d("format: index " + i + " " + mime);
			if (mime.startsWith("video")) {
				mMediaExtractor.selectTrack(i);
				mMediaCodec = MediaCodec.createDecoderByType(mime);
				mMediaCodec.configure(mediaFormat, mSurface, null, 0);
				break;
			}
		}

		if (mMediaCodec == null) {
			return;
		}

		mMediaCodec.start();
		ByteBuffer[] input = mMediaCodec.getInputBuffers();
		ByteBuffer[] output = mMediaCodec.getOutputBuffers();

		MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
		boolean EOF = false;
		long startMs = SystemClock.elapsedRealtime();
		while (!Thread.interrupted()) {
			if (!EOF) {
				int index = mMediaCodec.dequeueInputBuffer(10000);
				if (index >= 0) {
					ByteBuffer buffer = input[index];
					int size = mMediaExtractor.readSampleData(buffer, 0);
					if (size < 0) {
						mMediaCodec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
						EOF = true;
					} else {
						mMediaCodec.queueInputBuffer(index, 0, size, mMediaExtractor.getSampleTime(), 0);
						mMediaExtractor.advance();
					}
				}
			}

			int index = mMediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
			switch (index) {
				case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
				case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
					output = mMediaCodec.getOutputBuffers();
					break;
				case MediaCodec.INFO_TRY_AGAIN_LATER:
					break;
				default:
					while (bufferInfo.presentationTimeUs / 1000 > SystemClock.elapsedRealtime() - startMs) {
						try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					mMediaCodec.releaseOutputBuffer(index, true);
					break;
			}

			if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
				break;
			}
		}
		mMediaCodec.stop();
		mMediaCodec.release();
		mMediaExtractor.release();
	}

	private void d(String message) {
		Log.d("VideoWorkRunnable", message);
	}
}

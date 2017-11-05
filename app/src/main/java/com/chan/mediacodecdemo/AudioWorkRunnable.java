package com.chan.mediacodecdemo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by chan on 2017/11/5.
 */

public class AudioWorkRunnable implements Runnable {
	private Context mContext;
	private MediaExtractor mMediaExtractor;
	private MediaCodec mMediaCodec;
	private int mSampleRate;

	public AudioWorkRunnable(Context context) {
		mContext = context;
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
			if (mime.startsWith("audio")) {
				mMediaExtractor.selectTrack(i);
				mMediaCodec = MediaCodec.createDecoderByType(mime);
				mMediaCodec.configure(mediaFormat, null, null, 0);
				mSampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
				if (mSampleRate <= 0) {
					mSampleRate = 44100;
				}
				d("channel count: " + mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
				d("sample rate in hz: " + mediaFormat.getInteger(MediaFormat.KEY_BIT_RATE));
				break;
			}
		}

		if (mMediaCodec == null) {
			d("media codec is null");
			return;
		}

		mMediaCodec.start();
		ByteBuffer[] input = mMediaCodec.getInputBuffers();
		ByteBuffer[] output = mMediaCodec.getOutputBuffers();

		MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

		int bufferSize = AudioTrack.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
		AudioTrack audioTrack = new AudioTrack(
				AudioManager.STREAM_MUSIC,
				mSampleRate,
				AudioFormat.CHANNEL_OUT_STEREO,
				AudioFormat.ENCODING_PCM_16BIT,
				bufferSize,
				AudioTrack.MODE_STREAM);
		audioTrack.play();

		boolean EOF = false;
		long startMs = SystemClock.elapsedRealtime();
		while (!Thread.interrupted() && !EOF) {
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

				index = mMediaCodec.dequeueOutputBuffer(info, 10000);
				switch (index) {
					case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
					case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
						output = mMediaCodec.getOutputBuffers();
						break;
					case MediaCodec.INFO_TRY_AGAIN_LATER:
						break;
					default:
						while (info.presentationTimeUs / 1000 > SystemClock.elapsedRealtime() - startMs) {
							try {
								Thread.sleep(10);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						buffer = output[index];
						byte[] bytes = new byte[info.size];
						buffer.get(bytes);
						audioTrack.write(bytes, info.offset, info.size + info.offset);
						buffer.clear();
						mMediaCodec.releaseOutputBuffer(index, true);
						break;
				}

				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					break;
				}
			}
		}

		mMediaCodec.stop();
		mMediaCodec.release();

		mMediaExtractor.release();

		audioTrack.stop();
		audioTrack.release();
	}

	private static void d(String msg) {
		Log.d("AudioWorkRunnable", msg);
	}
}

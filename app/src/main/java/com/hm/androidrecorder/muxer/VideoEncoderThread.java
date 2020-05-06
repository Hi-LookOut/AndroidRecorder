package com.hm.androidrecorder.muxer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import com.hm.androidrecorder.utils.MyPrintLog;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Vector;

/**
 * 视频编码线程
 */
public class VideoEncoderThread extends Thread {

    public static final int IMAGE_HEIGHT = 1080;
    public static final int IMAGE_WIDTH = 1920;

    private static final String TAG = "VideoEncoderThread";

    // 编码相关参数
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private static final int FRAME_RATE = 25; // 帧率
    private static final int IFRAME_INTERVAL = 10; // I帧间隔（GOP）
    private static final int TIMEOUT_USEC = 10000; // 编码超时时间

    // 视频宽高参数
    private int mWidth;
    private int mHeight;

    // 存储每一帧的数据 Vector 自增数组
    private Vector<byte[]> frameBytes;
    //n21数据转为I420的输出缓存
    private byte[] mFrameData;
    //自定义的压缩比
    private static final int COMPRESS_RATIO = 256;
    //RGB24 一帧=一个像素为3字节 ，COMPRESS_RATIO
    private static final int BIT_RATE = IMAGE_HEIGHT * IMAGE_WIDTH * 3 * 8 * FRAME_RATE / COMPRESS_RATIO; // bit rate CameraWrapper.

    private final Object lock = new Object();

    //解码器信息
    private MediaCodecInfo mCodecInfo;
    private MediaCodec mMediaCodec;  // Android压缩编码器，能够对Surface内容进行编码
    private MediaCodec.BufferInfo mBufferInfo; //  编解码Buffer相关信息

    private WeakReference<MediaMuxerThread> mediaMuxer; // 音视频混合器
    private MediaFormat mediaFormat; // 音视频格式
    private boolean isRunning = false;
    private volatile boolean isPause = false;

    public VideoEncoderThread(int mWidth, int mHeight, WeakReference<MediaMuxerThread> mediaMuxer) {
        // 初始化相关对象和参数
        this.mWidth = mWidth;
        this.mHeight = mHeight;
        this.mediaMuxer = mediaMuxer;

        prepare();
    }

    // 执行相关准备工作
    private void prepare() {

        frameBytes = new Vector<byte[]>();
        mFrameData = new byte[this.mWidth * this.mHeight * 3 / 2];

        //获取缓存信息
        mBufferInfo = new MediaCodec.BufferInfo();

        //选择视频编码器--H.264
        mCodecInfo = selectCodec(MIME_TYPE);
        if (mCodecInfo == null) {
            MyPrintLog.LogProcess(VideoEncoderThread.class, "没有找到需要的视频解码器信息");
            return;
        }
        mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, this.mWidth, this.mHeight);
        //设置比特率
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        //设置帧率
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
//        mediaFormat.setInteger(MediaFormat.KEY_ROTATION,90);
        //编辑器输入的颜色，确保所有硬件平台都支持的颜色
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        //I帧间隔时间
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);


        //创建解码器
        try {
            mMediaCodec = MediaCodec.createByCodecName(mCodecInfo.getName());
        } catch (IOException e) {
            MyPrintLog.LogErr("视频解码器创建失败", e);
            e.printStackTrace();
        }
        //配置解码器参数
        // MediaCodec.CONFIGURE_FLAG_ENCOD如果该解码器已被占用时，返回该值。
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);


        MyPrintLog.LogProcess(VideoEncoderThread.class, "视频准备完成");
    }

    //获得内置h.264解码器信息
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }


    public void add(byte[] data) {
        if (frameBytes != null) {
            frameBytes.add(data);
        }

        if (isRunning && !isPause) {
            synchronized (lock) {
                lock.notify();
            }
        }
    }


    @Override
    public void run() {
        super.run();

        MyPrintLog.LogProcess(VideoEncoderThread.class, "视频循环：isRunning:" + isRunning);
        while (isRunning) {

            if (frameBytes.isEmpty() || isPause) {
                synchronized (lock) {
                    try {
                        MyPrintLog.LogProcess(VideoEncoderThread.class, "视频线程睡眠等待");
                        lock.wait();
                        MyPrintLog.LogProcess(VideoEncoderThread.class, "视频线程被唤醒");
                    } catch (InterruptedException e) {
                    }
                }
            } else {
                MyPrintLog.LogProcess("视频一次解码");
                try {
                    encodeFrame(frameBytes.remove(0));
                } catch (Exception e) {
                    MyPrintLog.LogErr("视频线程错误",e,160);
                    e.printStackTrace();
                }
            }

        }


        MyPrintLog.LogProcess(VideoEncoderThread.class, "Video 录制线程 退出...");
    }


    /**
     * 编码每一帧的数据
     *
     * @param input 每一帧的数据
     */
    private void encodeFrame(byte[] input) throws Exception{
        Log.w(TAG, "VideoEncoderThread.encodeFrame()");

        // 将原始的N21数据转为I420
        NV21toI420SemiPlanar(input, mFrameData, this.mWidth, this.mHeight);

        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();

        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        if (inputBufferIndex >= 0) {

            if (mFrameData.length>0){
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(mFrameData);
                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, mFrameData.length, System.nanoTime() / 1000, 0);
            }else {
                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
        } else {
            //？？？？？如果不可用怎么办？？？
            Log.e(TAG, "input buffer not available");
            return;
        }

        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        int outputBufferIndex;
        do {
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                //标明执行函数超时，将不会继续等待。
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaCodec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mMediaCodec.getOutputFormat();
                MediaMuxerThread mediaMuxerRunnable = this.mediaMuxer.get();
                if (mediaMuxerRunnable != null) {
                    mediaMuxerRunnable.addTrackIndex(MediaMuxerThread.TRACK_VIDEO, newFormat);
                }
            } else if (outputBufferIndex < 0) {
                Log.e(TAG, "outputBufferIndex < 0");

            } else if (outputBufferIndex>=0){
                Log.d(TAG, "perform encoding");
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                if (outputBuffer == null) {
                    throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex + " was null");
                }
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }
                if (mBufferInfo.size != 0) {
                    MediaMuxerThread mediaMuxer = this.mediaMuxer.get();

//                    if (mediaMuxer != null && !mediaMuxer.isVideoTrackAdd()) {
//                        MediaFormat newFormat = mMediaCodec.getOutputFormat();
//                        mediaMuxer.addTrackIndex(MediaMuxerThread.TRACK_VIDEO, newFormat);
//                    }
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);


                    if (mediaMuxer != null) {
                        mediaMuxer.addMuxerData(new MediaMuxerThread.MuxerData(MediaMuxerThread.TRACK_VIDEO, outputBuffer, mBufferInfo));
                    }

                    Log.d(TAG, "sent " + mBufferInfo.size + " frameBytes to muxer");
                }
                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
        } while (outputBufferIndex >= 0);
    }

    /**
     * 开始视频编码
     */
    public void startVideo() {
        if (mMediaCodec != null) {
            mMediaCodec.start();
            isRunning = true;
            start();
        }
    }

    /**
     * 停止视频编码
     */
    public void stopVideo() {
        isRunning = false;
        isPause = false;

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        if (frameBytes != null) {
            frameBytes.clear();
        }
        mBufferInfo = null;
        mFrameData = null;
        mediaMuxer = null;
        MyPrintLog.LogProcess(VideoEncoderThread.class, "视频退出.");
    }


    public void pauseVideo() {
        isPause = true;
    }

    public void rePlayVideo() {
        isPause = false;
        if (isRunning) {
            synchronized (lock) {
                lock.notify();
            }
        }
    }

    private static void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes, int width, int height) {
        System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height);
        for (int i = width * height; i < nv21bytes.length; i += 2) {
            i420bytes[i] = nv21bytes[i + 1];
            i420bytes[i + 1] = nv21bytes[i];
        }
    }

}

package com.hm.androidrecorder.muxer;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.hm.androidrecorder.constant.GlobalConfig;
import com.hm.androidrecorder.utils.FileUtil;
import com.hm.androidrecorder.utils.MyPrintLog;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Vector;

/**
 * 音视频混合线程
 */
public class MediaMuxerThread extends Thread {

    private static final String TAG = "MediaMuxerThread";
    //视频存储目录
    private String mediaFilePathDir = FileUtil.getSDPath() + "/hm_muxer/media/";
    private String BASE_EXT = ".mp4";
    private String currentMediaFilePath = "";
    public static final int TRACK_VIDEO = 0;
    public static final int TRACK_AUDIO = 1;

    private final Object lock = new Object();

    private static MediaMuxerThread mediaMuxerThread;

    private AudioEncoderThread audioThread = null;
    private VideoEncoderThread videoThread = null;

    private MediaMuxer mediaMuxer;
    //缓存数组
    private Vector<MuxerData> muxerDatas = null;

    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;


    private boolean isRunning = false;
    //标记混合器运行状态
    private int muxerState = MUXER_STATE_UNINITIALIZED;


    private static final int MUXER_STATE_UNINITIALIZED = -1;

    //混合器开始
    private static final int MUXER_STATE_STARTED = 1;
    //结束
    private static final int MUXER_STATE_STOPPED = 2;

    private MediaMuxerThread() {
        initMuxer();
    }

    // 初始化混合器
    private void initMuxer() {
        //创建缓存数组
        muxerDatas = new Vector<>();

        //支持用户自定义set
        if (audioThread == null) {
            //创建了音频编码器和规定了音频格式
            audioThread = new AudioEncoderThread((new WeakReference<MediaMuxerThread>(this)));
        }

        if (videoThread == null) {
            //创建视频编码器和规定了视频格式
            videoThread = new VideoEncoderThread(1920, 1080, new WeakReference<MediaMuxerThread>(this));
        }

        //创建视频混合器
        createMediaMuxer();
    }


    /**
     * 给开发者自定义音频格式
     *
     * @param at
     */
    public void setAudioThread(AudioEncoderThread at) {
        audioThread = at;
    }

    /**
     * 给开发者自定义视频频格式
     *
     * @param vt
     */
    public void setVideoThread(VideoEncoderThread vt) {
        videoThread = vt;
    }


    @Override
    public void run() {
        super.run();
        Log.e(GlobalConfig.LOG_PROCESS, "run--------isRunning：" + isRunning);
        Log.e(GlobalConfig.LOG_PROCESS, "混合器是否启动：isMuxerStart" + isStartMuxer());
        Log.e(GlobalConfig.LOG_PROCESS, "混合器缓存是否有数据：" + muxerDatas.isEmpty());
        while (isRunning) {
            if (!isStartMuxer() || muxerDatas.isEmpty()) {
                //没有音视频轨或者没有数据
                synchronized (lock) {
                    try {
                        MyPrintLog.LogProcess(MediaMuxerThread.class,"混合器线程睡眠等待");
                        lock.wait();
                        MyPrintLog.LogProcess(MediaMuxerThread.class,"混合器线程被唤醒");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                MuxerData data = muxerDatas.remove(0);
                int track;
                //？？？？？videoTrackIndex和audioTrackIndex都是-1
                if (data.trackIndex == TRACK_VIDEO) {
                    track = videoTrackIndex;
                } else {
                    track = audioTrackIndex;
                }
                MyPrintLog.LogProcess(MediaMuxerThread.class.getName(), "向混合器写入数据：" + data.bufferInfo.size);
                try {
                    mediaMuxer.writeSampleData(track, data.byteBuf, data.bufferInfo);
                } catch (Exception e) {
                    MyPrintLog.LogErr("混合器写入数据失败", e);
                }
            }
        }

        MyPrintLog.LogProcess(MediaMuxerThread.class, "MediaMuxerThread停止！！！！！");
    }

    // 开始音视频混合任务
    public static MediaMuxerThread getInstance() {

        if (mediaMuxerThread == null) {
            mediaMuxerThread = new MediaMuxerThread();
            Log.e(GlobalConfig.LOG_PROCESS, "获取音视频混合器单例");
        }

        return mediaMuxerThread;
    }


    private String getNewFullPath() {
        return mediaFilePathDir + FileUtil.getSystemTime() + BASE_EXT;
    }

    private void createMediaMuxer() {
        //创建存储文件
        currentMediaFilePath = FileUtil.createNewFile(getNewFullPath());

        //创建混合器
        try {
            mediaMuxer = new MediaMuxer(currentMediaFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            MyPrintLog.LogErr("创建MediaMuxer失败", e);
            e.printStackTrace();
        }

        MyPrintLog.LogProcess("创建音视频线程完成");
    }


    // 添加视频数据
    public void addVideoData(byte[] data) {
        if (videoThread != null) {
            videoThread.add(data);
        }
    }


    public void addMuxerData(MuxerData data) {
//        if (!checkMuxerStacks()) {
//            return;
//        }
        if (muxerDatas!=null){
            muxerDatas.add(data);
            if (isRunning&&checkMuxerStacks()){
                synchronized (lock){
                    lock.notify();
                }
            }
        }

    }

    /**
     * 添加视频／音频轨
     *
     * @param index
     * @param newFormat
     */
    public synchronized void addTrackIndex(int index, MediaFormat newFormat) {


        if (mediaMuxer != null) {
            int track;
            try {
                //由于格式改变，所以需要添加该格式的音频或视频轨，返回添加轨道的索引，在writeSampleData时，会被用到
                track = mediaMuxer.addTrack(newFormat);
            } catch (Exception e) {
                MyPrintLog.LogErr("音视频轨添加错误：", e);
                return;
            }

            if (index == TRACK_VIDEO) {
                videoTrackIndex = track;
                Log.e(TAG, "添加视频轨完成");
            } else {
                audioTrackIndex = track;
                Log.e(TAG, "添加音轨完成");
            }


            if (checkMuxerStacks()) {
                //启动Muxer
                if (!isStartMuxer()){
                    requestStartMuxer();
                }

                synchronized (lock){
                    lock.notify();
                }
            }

        }
    }

    /**
     * 请求混合器开始启动
     */
    private void requestStartMuxer() {
        synchronized (lock) {
            //启动条件-----必须要有音视频轨，才能启动
            if (checkMuxerStacks()&&mediaMuxer!=null) {
                mediaMuxer.start();
                muxerState = MUXER_STATE_STARTED;
                MyPrintLog.LogProcess(MediaMuxerThread.class.getName(), "Muxer已启动！！！！！！！");

            }
        }
    }

    /**
     * 判断Muxer是否已经启动
     *
     * @return
     */
    public boolean isStartMuxer() {
        if (muxerState == MUXER_STATE_STARTED) {
            return true;
        }
        return false;
    }


    /**
     * 检查muxer是否已经添加了音轨和视频轨
     *
     * @return
     */
    public boolean checkMuxerStacks() {
        return videoTrackIndex != -1 && audioTrackIndex != -1;
    }


    public void startMuxer() {
        isRunning = true;
        if (videoThread != null) {
            videoThread.startVideo();
        }
        if (audioThread != null) {
            audioThread.startAudio();
        }
        start();
    }

    /**
     * 重置参数
     */
    private void resetParameters() {
        audioTrackIndex = -1;
        videoTrackIndex = -1;
        isRunning = false;
        mediaMuxer = null;
        muxerDatas.clear();
        muxerDatas = null;
        mediaMuxerThread=null;
        currentMediaFilePath = "";
        muxerState = MUXER_STATE_UNINITIALIZED;
    }


    /**
     * 完成混合，关闭muxer
     */
    public void stopMuxer() {
        if (isRunning&&mediaMuxer!=null) {
            if (videoThread!=null){
                videoThread.stopVideo();
                videoThread=null;
            }
           if (audioThread!=null){
               audioThread.stopAudio();
               audioThread=null;
           }


            try {
                //等待muxerDatas缓存中的数据全部写完
                Thread.sleep(100);
                //release会帮我们调用muxer.stop
                mediaMuxer.release();

                MyPrintLog.LogProcess(MediaMuxerThread.class, "mediaMuxer关闭成功");
            } catch (Exception e) {
                MyPrintLog.LogErr("Muxer.release() 异常：", e);
            }

            resetParameters();
        }
    }

    /**
     * 停止Muxer
     */
    public void pauseMuxer() {
        if (muxerState != MUXER_STATE_STARTED) {
            MyPrintLog.LogProcess(MediaMuxerThread.class, "Muxer还开始，你就想暂停了");
            return;
        }
        if (videoThread!=null){
            videoThread.pauseVideo();
        }
        if (audioThread!=null){
            audioThread.pauseAudio();
        }
        if (mediaMuxer!=null){
            try {
                mediaMuxer.stop();
                muxerState = MUXER_STATE_STOPPED;
                MyPrintLog.LogProcess(MediaMuxerThread.class, "Muxer已暂停");
            } catch (IllegalStateException e) {
                MyPrintLog.LogErr("Muxer.stop() 异常:", e);
            }
        }


    }


    private void rePlayMuexr() {
        if (videoThread != null) {
            videoThread.rePlayVideo();
        }
        if (audioThread != null) {
            audioThread.rePlayAudio();
        }

        requestStartMuxer();
        //唤醒MediaMuxerThread
        if (isRunning){
            synchronized (lock){
                lock.notify();
            }
        }

    }

    /**
     * 封装需要传输的数据类型
     */
    public static class MuxerData {

        // 数据类别： 视频或者音频
        int trackIndex;

        //数据
        ByteBuffer byteBuf;

        //数据信息描述类
        MediaCodec.BufferInfo bufferInfo;

        public MuxerData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
            this.trackIndex = trackIndex;
            this.byteBuf = byteBuf;
            this.bufferInfo = bufferInfo;
        }
    }


}

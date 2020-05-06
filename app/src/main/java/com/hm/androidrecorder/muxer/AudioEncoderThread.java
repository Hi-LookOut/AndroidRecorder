package com.hm.androidrecorder.muxer;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import com.hm.androidrecorder.constant.GlobalConfig;
import com.hm.androidrecorder.utils.MyPrintLog;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * 音频编码线程
 * Created by renhui on 2017/9/25.
 */
public class AudioEncoderThread extends Thread {

    public static final String TAG = "AudioEncoderThread";

    public int min_buffer_size = 0;
//    public static final int FRAMES_PER_BUFFER = 25;
    private static final int TIMEOUT_USEC = 10000;
    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int SAMPLE_RATE = 16000;
    private static final int BIT_RATE = 64000;


    private final Object lock = new Object();
    private MediaCodec mMediaCodec = null;                // API >= 16(Android4.1.2)
    private WeakReference<MediaMuxerThread> mediaMuxerRunnable;
    private AudioRecord audioRecord = null;
    private MediaCodec.BufferInfo mBufferInfo;        // API >= 16(Android4.1.2)
    private boolean isRecording = false;
    private volatile boolean isPause = false;
    private ByteBuffer bufferAudioRecoder = null;

    //前一个解码音频时间
    private long prevOutputPTSUs = 0;


    public AudioEncoderThread(WeakReference<MediaMuxerThread> mediaMuxerRunnable) {
        this.mediaMuxerRunnable = mediaMuxerRunnable;


        init();
        MyPrintLog.LogProcess(AudioEncoderThread.class, "音频准备工作完成");
    }


    /**
     * 规定音频格式，其中包括：
     * <p>
     * 1. 格式----mp4
     * 2. 采样率--- 16000
     * 3. 声道数 ----- 1
     * 4. 比特率----传输速度
     */
    private void init() {
        //选择对应格式的编码器
        MediaCodecInfo audioCodecInfo = selectAudioCodec(MIME_TYPE);
        if (audioCodecInfo == null) {
            MyPrintLog.LogProcess(AudioEncoderThread.class, "没有找到对应的音频解码器信息");
            return;
        }


        MyPrintLog.LogProcess(AudioEncoderThread.class, "获得音频解码器信息");
        MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        //声道
//        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        //设置采样率
//        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        try {
            mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            MyPrintLog.LogErr("创建音频解码器失败", e);
        }
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        MyPrintLog.LogProcess(AudioEncoderThread.class, "音频解码器创建成功");


        //获取缓冲信息
        //每个缓冲区元数据包括一个偏移量和大小，指定关联编解码器（输出）缓冲区中有效数据的范围。
        mBufferInfo = new MediaCodec.BufferInfo();

        //创建录音
        createAudioRecord();
    }


    @Override
    public void run() {

        int readBytes;
        MyPrintLog.LogProcess(AudioEncoderThread.class, "isRecording:" + isRecording);
        while (isRecording) {

            if (isPause) {
                //暂停
                synchronized (lock) {
                    try {
                        Log.e(GlobalConfig.LOG_PROCESS, "等待录音...");
                        MyPrintLog.LogProcess(AudioEncoderThread.class, "音频线程睡眠等待");
                        lock.wait();
                        Log.e(GlobalConfig.LOG_PROCESS, "录音线程被唤醒...");
                        MyPrintLog.LogProcess(AudioEncoderThread.class, "音频线程被唤醒");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else if (audioRecord != null) {

                readBytes = audioRecord.read(bufferAudioRecoder, min_buffer_size);
                if (readBytes > 0) {
                    // set audio data to encoder
                    bufferAudioRecoder.position(readBytes);
                    //ByteBuffer取数据前调用
                    bufferAudioRecoder.flip();
                    MyPrintLog.LogProcess(AudioEncoderThread.class.getName(), "音频解码一次数据");
                    encode(bufferAudioRecoder, readBytes, getPTSUs());
                    bufferAudioRecoder.clear();
                }
            }

        }

        MyPrintLog.LogProcess(AudioEncoderThread.class, "Audio 录制线程 退出...");
    }


    /**
     * 选择解码器
     *
     * @param mimeType
     * @return
     */
    private MediaCodecInfo selectAudioCodec(final String mimeType) {
        MediaCodecInfo result = null;
        // get the list of available codecs

        //获得可获得的解码器数
        final int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            //查询编解码器是否为编码器
            if (!codecInfo.isEncoder()) {    // skipp decoder
                continue;
            }
            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                Log.i(TAG, "supportedType:" + codecInfo.getName() + ",MIME=" + types[j]);
                if (types[j].equalsIgnoreCase(mimeType)) {
                    if (result == null) {
                        result = codecInfo;
                        break;
                    }
                }
            }
        }
        return result;
    }

    private void createAudioRecord() {

        min_buffer_size = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
//            int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
//            if (buffer_size < min_buffer_size)
//                buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
        //(min_buffer_size+SAMPLES_PER_FRAME)*2
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min_buffer_size);
        } catch (IllegalArgumentException e) {
            MyPrintLog.LogErr("创建audioRecord失败", e);
        }

        bufferAudioRecoder = ByteBuffer.allocateDirect(min_buffer_size);

        MyPrintLog.LogProcess(AudioEncoderThread.class.getName(), "创建audioRecord成功");

    }


    //使用MediaCodec硬解码
    private void encode(final ByteBuffer buffer, int length, long presentationTimeUs) {

        //获得输入缓存区数组
        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        //返回有效的输入缓冲区的索引。
        // TIMEOUT_USEC<0,则无限期等待输入缓冲区的可用性；
        // TIMEOUT_USEC=0，立即返回。
        // 如果超时时间>0，则最多等待“超时”微秒
        // 当前没有此类缓冲区，返回-1。
        // 单位为微秒
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        /*向编码器输入数据*/
        if (inputBufferIndex >= 0) {
            //拿到可有输入缓存区对象
            final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            //清空缓存区里的数据
            inputBuffer.clear();
            if (buffer != null) {
                inputBuffer.put(buffer);
            }

            //如果有内容就去解码
            if (length <= 0) {
                //发送结束标示
//                Log.i(TAG, "send BUFFER_FLAG_END_OF_STREAM");
                MyPrintLog.LogProcess(AudioEncoderThread.class.getName(), "音频解码结束", 297);
                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                //让缓存区去排队
                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, length, presentationTimeUs, 0);
            }
        } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            //如果在对{@link#dequeueOutputBuffer}的调用中指定了非负超时，则表示调用超时。
            MyPrintLog.LogProcess(AudioEncoderThread.class.getName(), "音频解码超时", 305);
            return;
        }

        /*获取解码后的数据*/

        //获取混合器
        MediaMuxerThread WeakReferenceMediaMuxerThread = mediaMuxerRunnable.get();
        if (WeakReferenceMediaMuxerThread == null) {
            Log.w(TAG, "MediaMuxerRunnable is unexpectedly null");
            return;
        }
        ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();
        int encoderStatus;

        do {
            //返回已成功解码的输出缓冲区的索引或一个信息常数。
            //mBufferInfo为一个容器，传入后，会被填充缓存数据信息eg，输出数据大小等
            encoderStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus >= 0) {
                //获得输出数据
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                //表示该数据不是媒体数据
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    mBufferInfo.size = 0;
                }

                //待确认
                //if (mBufferInfo.size != 0  && WeakReferenceMediaMuxerThread.checkMuxerStacks()) {
                if (mBufferInfo.size != 0) {
                    mBufferInfo.presentationTimeUs = getPTSUs();
                    MyPrintLog.LogProcess(AudioEncoderThread.class.getName(), "向混合器中添加一次音频数据");
                    WeakReferenceMediaMuxerThread.addMuxerData(new MediaMuxerThread.MuxerData(MediaMuxerThread.TRACK_AUDIO, encodedData, mBufferInfo));
                    prevOutputPTSUs = mBufferInfo.presentationTimeUs;
                }
                //释放资源
                mMediaCodec.releaseOutputBuffer(encoderStatus, false);
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                //输出缓冲区已更改，客户端必须引用{@link#getOutputBuffers}从此点返回的新输出缓冲区集。
                encoderOutputBuffers = mMediaCodec.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                //输出格式已更改，后续数据将遵循新格式。
                //MediaCodec在一开始调用dequeueOutputBuffer()时会返回一次INFO_OUTPUT_FORMAT_CHANGED消息。
                // 我们只需在这里获取该MediaCodec的format，并注册到MediaMuxer里
                final MediaFormat format = mMediaCodec.getOutputFormat(); // API >= 16
                MyPrintLog.LogProcess(AudioEncoderThread.class.getName(), "获得新的音频输出格式", 320);
                WeakReferenceMediaMuxerThread.addTrackIndex(MediaMuxerThread.TRACK_AUDIO, format);

            } else if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                //超时
                MyPrintLog.LogProcess(AudioEncoderThread.class, "编码超时！");
            } else if (encoderStatus < 0) {
                //没成功解码，或者结束
                MyPrintLog.LogProcess(AudioEncoderThread.class, "没有成功编码或者结束");
            }

        } while (encoderStatus >= 0 );
    }

    /**
     * get next encoding presentationTimeUs
     *
     * @return
     */
    private long getPTSUs() {
        //获得纳秒时间
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }


    public void startAudio() {
        if (audioRecord != null) {
            audioRecord.startRecording();
        }
        if (mMediaCodec != null) {
            mMediaCodec.start();
        }
        isRecording = true;
        start();
        MyPrintLog.LogProcess("音频线程已开启");
    }

    public void pauseAudio() {
        if (audioRecord != null) {
            audioRecord.stop();
        }
        isPause = true;
        MyPrintLog.LogProcess("音频线程已暂停");
    }

    public void rePlayAudio() {
        if (audioRecord != null) {
            audioRecord.startRecording();
            if (isRecording && isPause) {
                isPause = false;
                synchronized (lock) {
                    lock.notify();
                }
            }
        }

    }

    public void stopAudio() {
        isRecording = false;
        isPause = false;
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }

        //等待已采集数据处理完毕在关
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
        bufferAudioRecoder = null;
        mediaMuxerRunnable = null;
        MyPrintLog.LogProcess("音频线程已关闭");
    }
}

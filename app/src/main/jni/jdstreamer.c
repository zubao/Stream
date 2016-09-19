//
// Created by lipeilong on 16/9/10.
//

#include <jni.h>
#include <time.h>
#include <stdio.h>
#include <android/log.h>


#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libswscale/swscale.h"
#include "libavutil/log.h"

#ifdef ANDROID
#include <jni.h>
#include <android/log.h>
#define LOGE(format, ...)  __android_log_print(ANDROID_LOG_ERROR, "(>_<)", format, ##__VA_ARGS__)
#define LOGI(format, ...)  __android_log_print(ANDROID_LOG_INFO,  "(=_=)", format, ##__VA_ARGS__)
#else
#define LOGE(format, ...)  printf("(>_<) " format "\n", ##__VA_ARGS__)
#define LOGI(format, ...)  printf("(^_^) " format "\n", ##__VA_ARGS__)
#endif

AVFormatContext *ofmt_ctx;
AVStream* video_st;
AVCodecContext* pCodecCtx;
AVCodec* pCodec;
AVPacket enc_pkt;
AVFrame *pFrameYUV;

int framecnt = 0;
int yuv_width;
int yuv_height;
int y_length;
int uv_length;
int64_t start_time;


//Output FFmpeg's av_log()
void custom_log(void *ptr, int level, const char* fmt, va_list vl){
    FILE *fp=fopen("/storage/emulated/0/av_log.txt","a+");
    if(fp){
        vfprintf(fp,fmt,vl);
        fflush(fp);
        fclose(fp);
    }
}

/*
 * Class:     com_lipeilong_jdstreamer_jni_JniStreamer
 * Method:    getTag
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_lipeilong_jdstreamer_jni_JniStreamer_getTag
  (JNIEnv *env, jclass obj){
       return (*env)->NewStringUTF(env,"This just a test for Android Studio NDK JNI developer! lpl");


  }

JNIEXPORT jint JNICALL
Java_com_lipeilong_jdstreamer_jni_JniStreamer_initial(JNIEnv *env, jobject instance, jint width, jint height) {

    // TODO

    //const char* out_path = "rtmp://localhost/publishlive/livestream";
    const char* out_path = "rtmp://192.168.0.163:1935/publishlive/livestream";
    yuv_width=width;
    yuv_height=height;
    y_length=width*height;
    uv_length=width*height/4;

    //FFmpeg av_log() callback
    av_log_set_callback(custom_log);

    // 注册所有的编解码器
    av_register_all();

    // 初始化网络
    avformat_network_init();

    //output initialize, PS:初始化输出配置
    avformat_alloc_output_context2(&ofmt_ctx, NULL, "flv", out_path);
    if(!ofmt_ctx){
        printf("Could not create output context\n");
        return -1;
    }

    //output encoder initialize PS: 从链表中找出h264的编码器
    pCodec = avcodec_find_encoder(AV_CODEC_ID_H264);
    if (!pCodec){
        LOGE("Can not find encoder!\n");
        return -1;
    }

    // 分配编码器context
    pCodecCtx = avcodec_alloc_context3(pCodec);
    pCodecCtx->pix_fmt = PIX_FMT_YUV420P;
    pCodecCtx->width = width;
    pCodecCtx->height = height;
    pCodecCtx->time_base.num = 1;
    pCodecCtx->time_base.den = 30;
    pCodecCtx->bit_rate = 1280*720*3/2;
    pCodecCtx->gop_size = 300;
    /* Some formats want stream headers to be separate. */
    if (ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER)
        pCodecCtx->flags |= CODEC_FLAG_GLOBAL_HEADER;

    //H264 codec param
    //pCodecCtx->me_range = 16;
    //pCodecCtx->max_qdiff = 4;
    //pCodecCtx->qcompress = 0.6;
    pCodecCtx->qmin = 10;
    pCodecCtx->qmax = 51;
    //Optional Param
    pCodecCtx->max_b_frames = 3;

    // 初始化元数据; PS: 设置零延时
    // Set H264 preset and tune
    AVDictionary *param = 0;
    av_dict_set(&param, "preset", "ultrafast", 0);
    av_dict_set(&param, "tune", "zerolatency", 0);

    // 设置旋转角度 或者设置再输出流
    //int ret = av_dict_set(&param, "rotate", "90", 0);


    // 打开编码器
    if (avcodec_open2(pCodecCtx, pCodec, &param) < 0){
        LOGE("Failed to open encoder!\n");
        return -1;
    }

    //Add a new stream to output,should be called by the user before avformat_write_header() for muxing
    video_st = avformat_new_stream(ofmt_ctx, pCodec);
    if (video_st == NULL){
        return -1;
    }
    video_st->time_base.num = 1;
    video_st->time_base.den = 30;
    video_st->codec = pCodecCtx;

    if(av_dict_set(&video_st->metadata, "rotate", "90", 0)<0){
        LOGE("roatate fail");
    }

    // Dump format 好像是日志相关的,可以删除
    //av_dump_format(ofmt_ctx, 0, out_path, 1);

    //Open output URL,set before avformat_write_header() for muxing
    if (avio_open(&ofmt_ctx->pb, out_path, AVIO_FLAG_READ_WRITE) < 0){
        LOGE("Failed to open output file!\n");
        return -1;
    }

    //Write File Header
    avformat_write_header(ofmt_ctx, NULL);

    // 获取时间
    start_time = av_gettime();
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_lipeilong_jdstreamer_jni_JniStreamer_encode(JNIEnv *env, jobject instance, jbyteArray yuv) {

    int ret;
    int enc_got_frame=0;
    int i=0;

    pFrameYUV = avcodec_alloc_frame();
    uint8_t *out_buffer = (uint8_t *)av_malloc(avpicture_get_size(PIX_FMT_YUV420P, pCodecCtx->width, pCodecCtx->height));
    avpicture_fill((AVPicture *)pFrameYUV, out_buffer, PIX_FMT_YUV420P, pCodecCtx->width, pCodecCtx->height);


    jbyte* in= (jbyte*)(*env)->GetByteArrayElements(env,yuv,0);
    // copy y分量
    memcpy(pFrameYUV->data[0],in,y_length);
    // copy u、v分量
    for(i=0;i<uv_length;i++)
    {
        *(pFrameYUV->data[2]+i)=*(in+y_length+i*2);
        *(pFrameYUV->data[1]+i)=*(in+y_length+i*2+1);
    }


    pFrameYUV->format = AV_PIX_FMT_YUV420P;
    pFrameYUV->width = yuv_width;
    pFrameYUV->height = yuv_height;

    enc_pkt.data = NULL;
    enc_pkt.size = 0;
    av_init_packet(&enc_pkt);
    ret = avcodec_encode_video2(pCodecCtx, &enc_pkt, pFrameYUV, &enc_got_frame);
    av_frame_free(&pFrameYUV);


    if (enc_got_frame == 1){
        LOGI("Succeed to encode frame: %5d\tsize:%5d\twidth:%5d\theight:%5d\n", framecnt, enc_pkt.size, pCodecCtx->width, pCodecCtx->height);
        framecnt++;
        enc_pkt.stream_index = video_st->index;

        //Write PTS
        AVRational time_base = ofmt_ctx->streams[0]->time_base;
        AVRational r_framerate1 = {60, 2 };
        AVRational time_base_q = { 1, AV_TIME_BASE };
        //Duration between 2 frames (us)
        int64_t calc_duration = (double)(AV_TIME_BASE)*(1 / av_q2d(r_framerate1));	//�ڲ�ʱ���
        //Parameters

        enc_pkt.pts = av_rescale_q(framecnt*calc_duration, time_base_q, time_base);
        enc_pkt.dts = enc_pkt.pts;
        enc_pkt.duration = av_rescale_q(calc_duration, time_base_q, time_base);
        enc_pkt.pos = -1;

        //Delay
        int64_t pts_time = av_rescale_q(enc_pkt.dts, time_base, time_base_q);
        int64_t now_time = av_gettime() - start_time;
        if (pts_time > now_time){
            av_usleep(pts_time - now_time);
            LOGI("sleep : %5l\n ",(pts_time -now_time));
        }

        ret = av_interleaved_write_frame(ofmt_ctx, &enc_pkt);
        if(ret < 0){
            LOGI("write error code : %5d\n", ret);
        }
        av_free_packet(&enc_pkt);
    }else{
        LOGI("encode error code : %5d\n", enc_got_frame);
    }

    // 释放内存
    (*env)->ReleaseByteArrayElements(env, yuv, in, 0);
    av_free(out_buffer);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_lipeilong_jdstreamer_jni_JniStreamer_flush(JNIEnv *env, jobject instance) {

    // TODO

    int ret;
    int got_frame;
    AVPacket enc_pkt;
    if (!(ofmt_ctx->streams[0]->codec->codec->capabilities &
          CODEC_CAP_DELAY))
        return 0;
    while (1) {
        enc_pkt.data = NULL;
        enc_pkt.size = 0;
        av_init_packet(&enc_pkt);
        ret = avcodec_encode_video2(ofmt_ctx->streams[0]->codec, &enc_pkt,
                                    NULL, &got_frame);
        if (ret < 0)
            break;
        if (!got_frame){
            ret = 0;
            break;
        }
        LOGI("Flush Encoder: Succeed to encode 1 frame!\tsize:%5d\n", enc_pkt.size);

        //Write PTS
        AVRational time_base = ofmt_ctx->streams[0]->time_base;//{ 1, 1000 };
        AVRational r_framerate1 = { 60, 2 };
        AVRational time_base_q = { 1, AV_TIME_BASE };
        //Duration between 2 frames (us)
        int64_t calc_duration = (double)(AV_TIME_BASE)*(1 / av_q2d(r_framerate1));	//�ڲ�ʱ���
        //Parameters
        enc_pkt.pts = av_rescale_q(framecnt*calc_duration, time_base_q, time_base);
        enc_pkt.dts = enc_pkt.pts;
        enc_pkt.duration = av_rescale_q(calc_duration, time_base_q, time_base);

        //ת��PTS/DTS��Convert PTS/DTS��
        enc_pkt.pos = -1;
        framecnt++;
        ofmt_ctx->duration = enc_pkt.duration * framecnt;

        /* mux encoded frame */
        ret = av_interleaved_write_frame(ofmt_ctx, &enc_pkt);
        if (ret < 0)
            break;
    }
    //Write file trailer
    av_write_trailer(ofmt_ctx);
    return 0;


}

JNIEXPORT jint JNICALL
Java_com_lipeilong_jdstreamer_jni_JniStreamer_close(JNIEnv *env, jobject instance) {

    // TODO

    if (video_st)
        avcodec_close(video_st->codec);
    avio_close(ofmt_ctx->pb);
    avformat_free_context(ofmt_ctx);
    return 0;


}
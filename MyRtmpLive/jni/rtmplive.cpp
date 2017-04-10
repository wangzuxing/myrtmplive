#include <stdio.h>
#include <stdlib.h>
#include <jni.h>
#include <malloc.h>
#include <string.h>
#include <time.h>
#include <android/log.h>
#include "librtmp/rtmp.h"
#include "librtmp/rtmp_sys.h"
#include "librtmp/amf.h"

#ifdef __cplusplus
extern "C" {
#endif

#define LOG_TAG "rtmplive"
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))

#define NAL_SLICE_IDR  0x65

//RTMP_MAX_HEADER_SIZE=18
#define RTMP_HEAD_SIZE   (sizeof(RTMPPacket)+RTMP_MAX_HEADER_SIZE)

// _RTMPMetadata
//内部结构体。该结构体主要用于存储和传递元数据信息
typedef struct _RTMPMetadata  
{   // video, must be h264 type
	unsigned int    nWidth;  
	unsigned int    nHeight;  
	unsigned int    nFrameRate;      
	unsigned int    nSpsLen;  
	unsigned char   *Sps;  
	unsigned int    nPpsLen;  
	unsigned char   *Pps;   
} RTMPMetadata;

enum  {
	VIDEO_CODECID_H264 = 7,
};  

RTMP* m_pRtmp;  
RTMPMetadata metaData;

unsigned int tick, tick0;
unsigned int tick_gap, tick_gap0;

//初始化并连接到服务器
int RTMP264_Connect(const char* url)  
{  
	m_pRtmp = RTMP_Alloc();
	RTMP_Init(m_pRtmp);
	LOGI("            RTMP264_Connect %s             ", url);

	/*设置URL*/
	if (RTMP_SetupURL(m_pRtmp,(char*)url) == FALSE)
	{
		RTMP_Free(m_pRtmp);
		return false;
	}
	LOGI("             RTMP_SetupURL ok              ");

	/*设置可写,即发布流,这个函数必须在连接前使用,否则无效*/
	RTMP_EnableWrite(m_pRtmp);

	/*连接服务器*/
	if (RTMP_Connect(m_pRtmp, NULL) == FALSE) // NetConnection  struct sockaddr_in service  (struct sockaddr*)&service
	{
		RTMP_Free(m_pRtmp);
		return false;
	} 
	LOGI("             RTMP_Connect ok              ");

	/*连接流*/
	if (RTMP_ConnectStream(m_pRtmp,0) == FALSE) // NetStream
	{
		RTMP_Close(m_pRtmp);
		RTMP_Free(m_pRtmp);
		return false;
	}
	LOGI("              RTMP_ConnectStream ok             ");
	return true;  
}  

// 断开连接，释放相关的资源。
void RTMP264_Close()  
{  
	if(m_pRtmp) {
		RTMP_Close(m_pRtmp);  
		RTMP_Free(m_pRtmp);  
		m_pRtmp = NULL;  
	}  
	LOGI("             RTMP264_Close            ");
} 

// 发送RTMP数据包
int SendPacket(unsigned int nPacketType,unsigned char *data,unsigned int size,unsigned int nTimestamp)  
{  
	RTMPPacket* packet;

	/*分配包内存和初始化,len为包体长度*/
	packet = (RTMPPacket *)malloc(RTMP_HEAD_SIZE+size);
	memset(packet,0,RTMP_HEAD_SIZE);

	/*包体内存*/
	packet->m_body = (char *)packet + RTMP_HEAD_SIZE;
	packet->m_nBodySize = size;
	memcpy(packet->m_body,data,size);
	packet->m_hasAbsTimestamp = 0;
	packet->m_packetType = nPacketType; /*此处为类型有两种一种是音频,一种是视频*/
	packet->m_nInfoField2 = m_pRtmp->m_stream_id;
	packet->m_nChannel = 0x04;

	packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
	if (RTMP_PACKET_TYPE_AUDIO ==nPacketType && size !=4)
	{
		packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
	}
	packet->m_nTimeStamp = nTimestamp;
	/*发送*/
	int nRet =0;
	if (RTMP_IsConnected(m_pRtmp))
	{
		nRet = RTMP_SendPacket(m_pRtmp,packet,TRUE); /*TRUE为放进发送队列,FALSE是不放进发送队列,直接发送*/
	}
	/*释放内存*/
	free(packet);
	return nRet;  
}  

//发送视频的sps和pps信息
int SendVideoSpsPps(unsigned char *sps,int sps_len, unsigned char *pps,int pps_len,unsigned int nTimeStamp)
{
	RTMPPacket * packet=NULL;//rtmp包结构
	unsigned char * body=NULL;
	int i;
	packet = (RTMPPacket *)malloc(RTMP_HEAD_SIZE+1024);
	//RTMPPacket_Reset(packet);//重置packet状态
	memset(packet,0,RTMP_HEAD_SIZE+1024);

	packet->m_body = (char *)packet + RTMP_HEAD_SIZE;
	body = (unsigned char *)packet->m_body;

	i = 0;
	body[i++] = 0x17; //FrameType: 1(key frame) + CodeID: 7(AVC)
	body[i++] = 0x00; //AVCPackType == 0, AVC sequence header

	//Composition Time
	body[i++] = 0x00;
	body[i++] = 0x00;
	body[i++] = 0x00;

	/*AVCDecoderConfigurationRecord*/
	body[i++] = 0x01;
	body[i++] = sps[1];
	body[i++] = sps[2];
	body[i++] = sps[3];
	body[i++] = 0xff;

	/*sps*/
	body[i++]   = 0xe1;
	body[i++] = (sps_len >> 8) & 0xff;
	body[i++] = sps_len & 0xff;
	memcpy(&body[i],sps,sps_len);
	i +=  sps_len;

	/*pps*/
	body[i++]   = 0x01;
	body[i++] = (pps_len >> 8) & 0xff;
	body[i++] = (pps_len) & 0xff;
	memcpy(&body[i],pps,pps_len);
	i +=  pps_len;

	packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
	packet->m_nBodySize = i;
	packet->m_nChannel = 0x04;
	packet->m_nTimeStamp = nTimeStamp;//0;
	packet->m_hasAbsTimestamp = 0;
	packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
	packet->m_nInfoField2 = m_pRtmp->m_stream_id;

	/*调用发送接口*/
	int nRet = RTMP_SendPacket(m_pRtmp,packet,TRUE);
	free(packet);    //释放内存
	return nRet;
}

/**
 * 发送H264数据帧
 *
 * @param data 存储数据帧内容
 * @param size 数据帧的大小
 * @param bIsKeyFrame 记录该帧是否为关键帧
 * @param nTimeStamp 当前帧的时间戳
 *
 * @成功则返回 1 , 失败则返回0
 */
int SendH264Packet(unsigned char *data,unsigned int size,int bIsKeyFrame,unsigned int nTimeStamp)  
{  
	if(data == NULL && size<11){  
		LOGI("             SendH264Packet datat NULL, %d           ",size);
		return false;  
	}  
	unsigned char *body = (unsigned char*)malloc(size+9);  
	memset(body,0,size+9);

	int i = 0; 
	if(bIsKeyFrame){  
		body[i++] = 0x17;// 1:Iframe  7:AVC   
		body[i++] = 0x01;// AVC NALU   
		body[i++] = 0x00;  
		body[i++] = 0x00;  
		body[i++] = 0x00;  

		// NALU size   
		body[i++] = size>>24 &0xff;  
		body[i++] = size>>16 &0xff;  
		body[i++] = size>>8 &0xff;  
		body[i++] = size&0xff;
		// NALU data   
		memcpy(&body[i],data,size);  
		SendVideoSpsPps(metaData.Sps, metaData.nSpsLen, metaData.Pps, metaData.nPpsLen, nTimeStamp);
		tick +=tick_gap;
		nTimeStamp = tick;
	}else{  
		body[i++] = 0x27;// 2:Pframe  7:AVC   
		body[i++] = 0x01;// AVCPackType == 0, AVC NALU
		body[i++] = 0x00;  
		body[i++] = 0x00;  
		body[i++] = 0x00;  

		// NALU size   
		body[i++] = size>>24 &0xff;  
		body[i++] = size>>16 &0xff;  
		body[i++] = size>>8 &0xff;  
		body[i++] = size&0xff;
		// NALU data   
		memcpy(&body[i],data,size);  
	}  
	int bRet = SendPacket(RTMP_PACKET_TYPE_VIDEO,body,i+size,nTimeStamp);  
	free(body);  
	return bRet;  
}

int send_rtmp_video(unsigned char * buf,int len, unsigned int nTimeStamp)
{
    int type;
    long timeoffset;
    RTMPPacket * packet;
    unsigned char * body;

    //timeoffset = GetTickCount() - start_time;  /*start_time为开始直播时的时间戳*/

    /*去掉帧界定符*/
    if (buf[2] == 0x00) { /*00 00 00 01*/
        buf += 4;
        len -= 4;
    } else if (buf[2] == 0x01){ /*00 00 01*/
        buf += 3;
        len -= 3;
    }
    type = buf[0]&0x1f;

    packet = (RTMPPacket *)malloc(RTMP_HEAD_SIZE+len+9);
    memset(packet,0,RTMP_HEAD_SIZE);

    packet->m_body = (char *)packet + RTMP_HEAD_SIZE;
    packet->m_nBodySize = len + 9;

    /*send video packet*/
    body = (unsigned char *)packet->m_body;
    memset(body,0,len+9);

    /*key frame*/
    body[0] = 0x27;
    if (type == NAL_SLICE_IDR) {
        body[0] = 0x17;
    }

    body[1] = 0x01;   /*nal unit*/
    body[2] = 0x00;
    body[3] = 0x00;
    body[4] = 0x00;

    body[5] = (len >> 24) & 0xff;
    body[6] = (len >> 16) & 0xff;
    body[7] = (len >>  8) & 0xff;
    body[8] = (len ) & 0xff;

    /*copy data*/
    memcpy(&body[9],buf,len);

    packet->m_hasAbsTimestamp = 0;
    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nInfoField2 = m_pRtmp->m_stream_id;
    packet->m_nChannel = 0x04;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nTimeStamp = timeoffset;

    /*调用发送接口*/
    RTMP_SendPacket(m_pRtmp,packet,TRUE);
    free(packet);
}

int GetAacSequenceHeader(unsigned char *spec_buf,int spec_len){
	char *buf;
	int len;
	//faacEncGetDecoderSpecificInfo(fh,&buf,&len);
	memcpy(spec_buf,buf,len);
	spec_len = len;
	/*释放系统内存*/
	free(buf);
}

//AACDecoderSpecificInfo
int SendAacSequenceHderPacket0(unsigned char *spec_buf,int spec_len)
{
    RTMPPacket * packet;
    unsigned char * body;
    //int audio_specific_config = 0;
    int len;
    len = spec_len;  /*spec data长度,一般是2*/

    packet = (RTMPPacket *)malloc(RTMP_HEAD_SIZE+len+2);
    memset(packet,0,RTMP_HEAD_SIZE);

    packet->m_body = (char *)packet + RTMP_HEAD_SIZE;
    body = (unsigned char *)packet->m_body;

    /*AF 00 + AAC sequence header*/
    body[0] = 0xAF;
    body[1] = 0x00; //AACPackType == 0x00 (AAC sequence header)
    memcpy(&body[2],spec_buf,len); /*spec_buf是AAC sequence header数据*/

    /*
    audio_specific_config |= ((2<<11)&0xF800); // 2:AAC:LC
    audio_specific_config |= ((4<<7)&0x0780);  // 4:44.1khz
    audio_specific_config |= ((2<<3)&0x78);    // 2:stereo
    audio_specific_config |= 0&0x07; //padding:000

    body[3] = (audio_specific_config>>8)&0xFF;
    body[4] = audio_specific_config&0xFF;
    */

    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_nBodySize = len+2; // 4
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = m_pRtmp->m_stream_id;

    /*调用发送接口*/
    RTMP_SendPacket(m_pRtmp,packet,TRUE);
    return TRUE;
}

int SendAacSequenceHderPacket()
{
    LOGI("              SendAacSequenceHderPacket            ");
    RTMPPacket * packet;
    unsigned char * body;
    int audio_specific_config = 0;
    int len;
    len = 2;

    packet = (RTMPPacket *)malloc(RTMP_HEAD_SIZE+len+2);
    memset(packet,0,RTMP_HEAD_SIZE);

    packet->m_body = (char *)packet + RTMP_HEAD_SIZE;
    body = (unsigned char *)packet->m_body;

    /*AF 00 + AAC sequence header*/
    body[0] = 0xAF;
    body[1] = 0x00; //AACPackType == 0x00 (AAC sequence header)

    /*
    //method 1
    audio_specific_config |= ((2<<11)&0xF800); // 2:AAC:LC
    audio_specific_config |= ((4<<7)&0x0780);  // 4:44.1khz
    audio_specific_config |= ((2<<3)&0x78);    // 2:stereo
    audio_specific_config |= 0&0x07;           //padding:000

    body[3] = (audio_specific_config>>8)&0xFF;
    body[4] = audio_specific_config&0xFF;
    */
    //method 2
    body[3] = 0x12;
    body[4] = 0x10;

    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_nBodySize = len+2; // 4
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = m_pRtmp->m_stream_id;

    /*调用发送接口*/
    RTMP_SendPacket(m_pRtmp,packet,TRUE);
    return TRUE;
}

int count_aac;

int SendAacRawPacket(unsigned char *buf, int len, unsigned int nTimeStamp)
{
    //long timeoffset;
    //timeoffset = GetTickCount() - start_time;
	//for ADTS data packet
    //buf += 7;
    //len -= 7;
    if (len > 0) {
    	if(++count_aac>200){
    	   count_aac = 0;
    	   LOGI("       SendAacRawPacket %d, %d        ", len, nTimeStamp);
    	}
        RTMPPacket * packet;
        unsigned char * body;

        packet = (RTMPPacket *)malloc(RTMP_HEAD_SIZE+len+2);
        memset(packet,0,RTMP_HEAD_SIZE);

        packet->m_body = (char *)packet + RTMP_HEAD_SIZE;
        body = (unsigned char *)packet->m_body;

        /*AF 01 + AAC RAW data*/
        body[0] = 0xAF;
        body[1] = 0x01;
        //aac raw data
        memcpy(&body[2],buf,len);

        packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
        packet->m_nBodySize = len+2;
        packet->m_nChannel = 0x04;
        packet->m_nTimeStamp = nTimeStamp;//timeoffset;
        packet->m_hasAbsTimestamp = 0;
        packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
        packet->m_nInfoField2 = m_pRtmp->m_stream_id;

        /*调用发送接口*/
        RTMP_SendPacket(m_pRtmp,packet,TRUE);
        free(packet);
    }
    return 0;
}

JNIEXPORT void JNICALL Java_com_example_myrtmplive_RtmpActivity_RtmpLiveSpsAndPps(JNIEnv *env, jclass clz,
		jbyteArray data, jint size, jbyteArray data0, jint size0)
{
	LOGI("              RtmpLiveSpsAndPps               ");
	unsigned char *sps = (unsigned char *)env->GetByteArrayElements(data, JNI_FALSE);
    unsigned char *pps = (unsigned char *)env->GetByteArrayElements(data0, JNI_FALSE);

    metaData.nSpsLen = size;
    metaData.Sps=(unsigned char*)malloc(size);
    memcpy(metaData.Sps, sps, size);

    metaData.nPpsLen = size0;
    metaData.Pps=(unsigned char*)malloc(size0);
    memcpy(metaData.Pps, pps, size0);

	env->ReleaseByteArrayElements(data, (jbyte *)sps, 0);
	env->ReleaseByteArrayElements(data0, (jbyte *)pps, 0);
}

//typedef long time_t; /* time value */
time_t GetTimeStamp( )
{
	//time_t timer;//time_t就是long int 类型
	//struct tm *tblock;
	//timer = time(NULL);//这一句也可以改成time(&timer);
	//time(&timer);
	//tblock = localtime(&timer);
    static time_t t0 = time(NULL);
    time_t tnow = time(NULL);
    time_t diff = tnow - t0;
    t0 = tnow;
    return diff;
}

JNIEXPORT void JNICALL Java_com_example_myrtmplive_RtmpActivity_RtmpLiveWHFreqR(JNIEnv *env, jclass clz,
	jint w, jint h, jbyte freq)
{
	LOGI("              RtmpLiveWHFreqR               ");
	metaData.nWidth  = w;
	metaData.nHeight = h;
	metaData.nFrameRate = freq;

	tick = 0;
	tick_gap = 1000/metaData.nFrameRate;
	//tick_gap = inc++ * 90000/fps;

	//视频帧根据帧率，在同一时间基上累加，
	//如，25帧每秒，则按毫秒计，1000/25=40ms,在首帧pts上进行累加

	//音频根据采样率及样本个数，在同一时间基上累加,
	//如，1024个样本(1024个采样为一帧)，44100采样率（即1秒钟有44100个采样），
	//以毫秒计，1000*1024/44100=23.21995464852607709750566893424 ms
}

int count;
int first_s=1;

JNIEXPORT void JNICALL Java_com_example_myrtmplive_RtmpActivity_RtmpLiveSendNalU(JNIEnv *env, jclass clz,
	jbyteArray data, jint size, jboolean iskeyframe)
{
	unsigned char *nalu = (unsigned char *)env->GetByteArrayElements(data, JNI_FALSE);
	if(++count>15){
	   count = 0;
	   LOGI("              RtmpLiveSendNalU %d           ",size);
	}
	SendH264Packet(nalu, size, iskeyframe, tick);
	tick +=tick_gap;

	env->ReleaseByteArrayElements(data, (jbyte *)nalu, 0);
}


JNIEXPORT void JNICALL Java_com_example_myrtmplive_RtmpActivity_RtmpLiveSendAacSequnceHder(JNIEnv *env, jclass clz,
	jbyteArray data, jint size)
{
	unsigned char *aac = (unsigned char *)env->GetByteArrayElements(data, JNI_FALSE);
	if(++count>15){
	   count = 0;
	   //LOGI("              RtmpLiveSendAacD %d           ",size);
	}

	tick0 = 0;
	tick_gap0 = 23;//1000*1024/44100;
	SendAacSequenceHderPacket0(aac, size);

	env->ReleaseByteArrayElements(data, (jbyte *)aac, 0);
}


JNIEXPORT void JNICALL Java_com_example_myrtmplive_RtmpActivity_RtmpLiveSendAacD(JNIEnv *env, jclass clz,
	jbyteArray data, jint size)
{
	unsigned char *aac = (unsigned char *)env->GetByteArrayElements(data, JNI_FALSE);
	if(++count>15){
	   count = 0;
	   //LOGI("              RtmpLiveSendAacD %d           ",size);
	}
	/*
	if(first_s==1){
	   first_s = 0;
	   tick0 = 0;
	   tick_gap0 = 23;//1000*1024/44100;
	   SendAacSequenceHderPacket();
	   tick0 +=tick_gap0;
	}
	*/
	SendAacRawPacket(aac, size, tick0);
	tick0 +=tick_gap0;

	env->ReleaseByteArrayElements(data, (jbyte *)aac, 0);
}

JNIEXPORT void JNICALL Java_com_example_myrtmplive_RtmpActivity_RtmpLiveS(JNIEnv *env, jclass clz, jstring url_s)
{
	char const* url = env->GetStringUTFChars(url_s, NULL);
    LOGI("              RtmpLive start              ");
    LOGI("              url %s              ", url);

    first_s = 1;

	RTMP264_Connect("rtmp://192.168.10.113:1935/live/livestream");

	env->ReleaseStringUTFChars(url_s, url);
}

JNIEXPORT void JNICALL Java_com_example_myrtmplive_RtmpActivity_RtmpLiveE(JNIEnv *env, jclass clz)
{
	//断开连接并释放相关资源
	RTMP264_Close();

	if(metaData.Sps != NULL){
	   free(metaData.Sps);
	}
	if(metaData.Pps != NULL){
       free(metaData.Pps);
	}

	LOGI("              RtmpLive end              ");
}

#ifdef __cplusplus
}
#endif

#include <stdio.h>
#include <jni.h>
#include <malloc.h>
#include <string.h>
#include <android/log.h>
#include <string.h>
#include "mp4v2/mp4v2.h"

#define LOG_TAG "libmp4"
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))

typedef struct _MP4ENC_NaluUnit
{
   int type;
   int size;
   unsigned char *data;
}MP4ENC_NaluUnit;

int m_nWidth;
int m_nHeight;
int m_nFrameRate;
int m_nTimeScale;
MP4TrackId m_videoId;

#define BUFFER_SIZE				(50*1024)
#define MP4_DETAILS_ALL		    0xFFFFFFFF

/**
 * 返回值 char* 这个代表char数组的首地址
 *  Jstring2CStr 把java中的jstring的类型转化成一个c语言中的char 字符串
 */
char* Jstring2CStr(JNIEnv* env, jstring jstr) {
	char* rtn = NULL;
	jclass clsstring = (*env)->FindClass(env, "java/lang/String"); //String
	jstring strencode = (*env)->NewStringUTF(env, "GB2312"); // 得到一个java字符串 "GB2312"
	jmethodID mid = (*env)->GetMethodID(env, clsstring, "getBytes",
			"(Ljava/lang/String;)[B"); //[ String.getBytes("gb2312");
	jbyteArray barr = (jbyteArray)(*env)->CallObjectMethod(env, jstr, mid,
			strencode); // String .getByte("GB2312");
	jsize alen = (*env)->GetArrayLength(env, barr); // byte数组的长度
	jbyte* ba = (*env)->GetByteArrayElements(env, barr, JNI_FALSE);
	if (alen > 0) {
		rtn = (char*) malloc(alen + 1); //"\0"
		memcpy(rtn, ba, alen);
		rtn[alen] = 0;
	}
	(*env)->ReleaseByteArrayElements(env, barr, ba, 0); //
	return rtn;
}

MP4TrackId video;
MP4TrackId audio;
MP4FileHandle fileHandle;
unsigned char sps_pps[17] = {0x67, 0x42, 0x40, 0x1F, 0x96 ,0x54, 0x05,
		0x01, 0xED, 0x00, 0xF3, 0x9E, 0xA0, 0x68, 0xCE, 0x38, 0x80}; //存储sps和pps

unsigned char sps[] = {0x67, 0x42, 0x80, 0x1e, 0xe9, 0x01, 0x40, 0x7b, 0x20};
unsigned char pps[] = {0x68, 0xce, 0x06, 0xe2};

int video_width = 640;
int video_height = 480;
int video_type;

int m_nVideoFrameRate=20;
int	m_nMp4TimeScale=90000;
MP4Timestamp m_lastTime=0;
MP4Timestamp m_thisTime=0;
MP4SampleId  m_samplesWritten=0;

#define NUM_ADTS_SAMPLING_RATES	16

uint32_t AdtsSamplingRates[NUM_ADTS_SAMPLING_RATES] = {
	96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
	16000, 12000, 11025, 8000, 7350, 0, 0, 0
};

uint8_t MP4AdtsFindSamplingRateIndex(uint32_t samplingRate)
{
	uint8_t i;
	for(i = 0; i < NUM_ADTS_SAMPLING_RATES; i++) {
		if (samplingRate == AdtsSamplingRates[i]) {
			return i;
		}
	}
	return NUM_ADTS_SAMPLING_RATES - 1;
}


bool MP4AacGetConfiguration(uint8_t** ppConfig,
					     uint32_t* pConfigLength,
					     uint8_t profile,
						 uint32_t samplingRate,
						 uint8_t channels)
{
	/* create the appropriate decoder config */

	uint8_t* pConfig = (uint8_t*)malloc(2);

	if (pConfig == NULL) {
		return false;
	}

	uint8_t samplingRateIndex = MP4AdtsFindSamplingRateIndex(samplingRate);

	pConfig[0] = ((profile + 1) << 3) | ((samplingRateIndex & 0xe) >> 1);
	pConfig[1] = ((samplingRateIndex & 0x1) << 7) | (channels << 3);

	/* LATER this option is not currently used in MPEG4IP
	 if (samplesPerFrame == 960) {
	 pConfig[1] |= (1 << 2);
	 }
	 */

	*ppConfig = pConfig;
	*pConfigLength = 2;

	return true;
}

int ReadOneNaluFromBuf(const unsigned char *buffer,
		unsigned int nBufferSize,
		unsigned int offSet,
		MP4ENC_NaluUnit *nalu)
{
	int i = offSet;
	while(i<nBufferSize)
	{
		if(buffer[i++] == 0x00 &&
			buffer[i++] == 0x00 &&
			buffer[i++] == 0x00 &&
			buffer[i++] == 0x01
			)
		{
			int pos = i;
			while (pos<nBufferSize)
			{
				if(buffer[pos++] == 0x00 &&
					buffer[pos++] == 0x00 &&
					buffer[pos++] == 0x00 &&
					buffer[pos++] == 0x01
					)
				{
					break;
				}
			}
			if(pos == nBufferSize)
			{
				nalu->size = pos-i;
			}
			else
			{
				nalu->size = (pos-4)-i;
			}

			nalu->type = buffer[i]&0x1f;
			nalu->data =(unsigned char*)&buffer[i];
			LOGI("     nalu type = %d, size = %d    ", nalu->type, nalu->size);
			return (nalu->size+i-offSet);
		}
	}
	return 0;
}

int WriteH264Data(MP4FileHandle hMp4File,const unsigned char* pData,int size)
{
	if(hMp4File == NULL)
	{
		return -1;
	}
	if(pData == NULL)
	{
		return -1;
	}
	MP4ENC_NaluUnit nalu;
	int pos = 0, len = 0;
	while (len = ReadOneNaluFromBuf(pData, size, pos, &nalu))
	{
		if(nalu.type == 0x07) // sps
		{
			// track
			m_videoId = MP4AddH264VideoTrack
				(hMp4File,
				m_nTimeScale,
				m_nTimeScale / m_nFrameRate,
				m_nWidth,     // width
				m_nHeight,    // height
				nalu.data[1], // sps[1] AVCProfileIndication
				nalu.data[2], // sps[2] profile_compat
				nalu.data[3], // sps[3] AVCLevelIndication
				3);           // 4 bytes length before each NAL unit
			if (m_videoId == MP4_INVALID_TRACK_ID)
			{
				printf("add video track failed.\n");
				//MP4Close(mMp4File, 0);
				return 0;
			}
			MP4SetVideoProfileLevel(hMp4File, 1); //  Simple Profile @ Level 3

			MP4AddH264SequenceParameterSet(hMp4File,m_videoId,nalu.data,nalu.size);
			LOGI("              write sps                ");
		}
		else if(nalu.type == 0x08) // pps
		{
			MP4AddH264PictureParameterSet(hMp4File,m_videoId,nalu.data,nalu.size);
			LOGI("              write pps                ");
		}
		else
		{
			int datalen = nalu.size+4;
			unsigned char data[datalen];
			// MP4 Nalu
			data[0] = nalu.size>>24;
			data[1] = nalu.size>>16;
			data[2] = nalu.size>>8;
			data[3] = nalu.size&0xff;
			memcpy(data+4, nalu.data, nalu.size);
			if(!MP4WriteSample(hMp4File, m_videoId, data, datalen,MP4_INVALID_DURATION, 0, 1))
			{
				LOGI("              MP4_INVALID_TRACK_ID = %d               ",m_samplesWritten);
				// MP4DeleteTrack(mMp4File, video);
				return 0;
			}
		}

		pos += len;
	}
	return pos;
}

MP4FileHandle CreateMP4File(const char *pFileName,int width,int height)//int timeScale/* = 90000*/, int frameRate/* = 25*/)
{
	if(pFileName == NULL)
	{
		return false;
	}
	// create mp4 file
	MP4FileHandle hMp4file = MP4Create(pFileName, 0);
	if (hMp4file == MP4_INVALID_FILE_HANDLE)
	{
		//printf("ERROR:Open file fialed.\n");
		LOGI("              MP4_INVALID_FILE_HANDLE                ");
		return false;
	}
	m_nWidth = width;
	m_nHeight = height;
	m_nTimeScale = 90000;
	m_nFrameRate = 15;
	MP4SetTimeScale(hMp4file, m_nTimeScale);
	return hMp4file;
}

void CloseMP4File(MP4FileHandle hMp4File)
{
	if(hMp4File)
	{
		MP4Close(hMp4File,0);
		hMp4File = NULL;
	}
}

bool WriteH264File(const char* pFile264,const char* pFileMp4)
{
	if(pFile264 == NULL || pFileMp4 == NULL)
	{
		return false;
	}

	MP4FileHandle hMp4File = CreateMP4File(pFileMp4, 640, 480);//240,320);

	if(hMp4File == NULL)
	{
		//printf("ERROR:Create file failed!");
		LOGI("              MP4_INVALID_FILE_HANDLE                ");
		return false;
	}

	FILE *fp = fopen(pFile264, "rb");
	if(!fp)
	{
		//printf("ERROR:open file failed!");
		LOGI("              h264 fopen error                ");
		return false;
	}
	LOGI("              h264 fopen                 ");

	fseek(fp, 0, SEEK_SET);

	unsigned char buffer[BUFFER_SIZE];
	int pos = 0;
	LOGI("       mp4Encoder start %s      ",pFile264);
	while(1)
	{
		int readlen = fread(buffer+pos, sizeof(unsigned char), BUFFER_SIZE-pos, fp);
		if(readlen<=0)
		{
			break;
		}
		readlen += pos;

		int writelen = 0;
		int i;
		for(i = readlen-1; i>=0; i--)
		{
			if(buffer[i--] == 0x01 &&
				buffer[i--] == 0x00 &&
				buffer[i--] == 0x00 &&
				buffer[i--] == 0x00
				)
			{
				writelen = i+5;
				break;
			}
		}
		LOGI("          mp4Encoder writelen = %d     ",writelen);
		writelen = WriteH264Data(hMp4File,buffer,writelen);
		if(writelen<=0)
		{
			break;
		}
		memcpy(buffer,buffer+writelen,readlen-writelen+1);
		pos = readlen-writelen+1;
	}
	fclose(fp);
	CloseMP4File(hMp4File);
	LOGI("              mp4Encoder end                ");
	LOGI("              mp4Encoder end                ");
	return true;
}

JNIEXPORT bool JNICALL Java_com_example_mymp4v2h264_Mp4Activity_Mp4Encode
 (JNIEnv *env, jclass clz, jstring h264, jstring mp4)
 {
 	const char* h264_title = (*env)->GetStringUTFChars(env,h264, NULL);
 	const char* mp4_title = (*env)->GetStringUTFChars(env,mp4, NULL);

    bool ret = WriteH264File(h264_title, mp4_title);

 	(*env)->ReleaseStringUTFChars(env, h264, h264_title);
 	(*env)->ReleaseStringUTFChars(env, mp4, mp4_title);
 }

JNIEXPORT bool JNICALL Java_com_example_mymp4v2h264_MainActivity0_Mp4Start
 (JNIEnv *env, jclass clz, jstring mp4)
{
	const char* mp4_title = (*env)->GetStringUTFChars(env,mp4, NULL);
    if(mp4_title == NULL)
	{
		return false;
	}

    fileHandle = CreateMP4File(mp4_title, 640, 480);//240,320);

	if(fileHandle == NULL)
	{
		//printf("ERROR:Create file failed!");
		LOGI("              MP4_INVALID_FILE_HANDLE NULL             ");
		(*env)->ReleaseStringUTFChars(env, mp4, mp4_title);
		return false;
	}

	uint32_t samplesPerSecond;
	uint8_t profile;
	uint8_t channelConfig;

	samplesPerSecond = 44100;
	profile = 2; // AAC LC

	/*
	0: Null
	1: AAC Main
	2: AAC LC (Low Complexity)
	3: AAC SSR (Scalable Sample Rate)
	4: AAC LTP (Long Term Prediction)
	5: SBR (Spectral Band Replication)
	6: AAC Scalable
    */
	channelConfig = 1;

	uint8_t* pConfig = NULL;
	uint32_t configLength = 0;

    //m_audio = MP4AddAudioTrack(m_file, 44100, 1024, MP4_MPEG2_AAC_MAIN_AUDIO_TYPE );
	audio = MP4AddAudioTrack(fileHandle, 44100, 1024, MP4_MPEG2_AAC_LC_AUDIO_TYPE);//MP4_MPEG4_AUDIO_TYPE);//MP4_MPEG2_AAC_LC_AUDIO_TYPE
	//MP4_MPEG2_AAC_LC_AUDIO_TYPE);//16000 1024
	if(audio == MP4_INVALID_TRACK_ID)
	{
		MP4Close(fileHandle, 0);
		return false;
	}
	MP4SetAudioProfileLevel(fileHandle, 0x02);
	LOGI("              MP4AddAudioTrack ok                ");

	MP4AacGetConfiguration(&pConfig, &configLength, profile, samplesPerSecond, channelConfig);
	//free(pConfig);
	MP4SetTrackESConfiguration(fileHandle, audio, pConfig, configLength);

	(*env)->ReleaseStringUTFChars(env, mp4, mp4_title);
	return true;
}

//添加视频帧的方法
JNIEXPORT void JNICALL Java_com_example_mymp4v2h264_MainActivity0_Mp4PackV
(JNIEnv *env, jclass clz, jbyteArray data, jint size, jint keyframe)
{
	unsigned char *buf = (unsigned char *)(*env)->GetByteArrayElements(env, data, JNI_FALSE);

	unsigned char type;
	type = buf[4]&0x1f;
	//LOGI(" 0x%x 0x%x 0x%x 0x%x 0x%x ",buf[0],buf[1],buf[2],buf[3], type);
	if(type == 0x07) // sps
	{
				// track
				m_videoId = MP4AddH264VideoTrack(fileHandle,
					m_nTimeScale,
					m_nTimeScale / m_nFrameRate,
					m_nWidth,     // width
					m_nHeight,    // height
					buf[5], // sps[1] AVCProfileIndication
					buf[6], // sps[2] profile_compat
					buf[7], // sps[3] AVCLevelIndication
					3);           // 4 bytes length before each NAL unit
				if (m_videoId == MP4_INVALID_TRACK_ID)
				{
					printf("add video track failed.\n");
					//MP4Close(mMp4File, 0);
					//return 0;
				}else{
				    MP4SetVideoProfileLevel(fileHandle, 0x7F); //  Simple Profile @ Level 3 = 2

				    MP4AddH264SequenceParameterSet(fileHandle, m_videoId, &buf[4], size-4);
				    LOGI("              write sps                ");
				}
	}
	else if(type == 0x08) // pps
	{
				MP4AddH264PictureParameterSet(fileHandle, m_videoId, &buf[4], size-4);
				m_samplesWritten = 0;
				m_lastTime = 0;
				LOGI("              write pps                ");
    }
    else
    {
		        int nalsize = size-4;
		 		bool ret = false;
		 		/*
		        buf[0] = (nalsize >> 24) & 0xff;
		 		buf[1] = (nalsize >> 16) & 0xff;
		 		buf[2] = (nalsize >> 8)& 0xff;
		 		buf[3] =  nalsize & 0xff;
                */
		 		//LOGI(" 0x%02x 0x%02x 0x%02x 0x%02x %d ", buf[0],buf[1],buf[2],buf[3],nalsize);
		 		buf[0] = (nalsize&0xff000000)>>24;
		 		buf[1] = (nalsize&0x00ff0000)>>16;
		 		buf[2] = (nalsize&0x0000ff00)>>8;
		 		buf[3] = nalsize&0x000000ff;

		 		/*
		 		m_samplesWritten++;
		 		double thiscalc;
		 		thiscalc = m_samplesWritten;
		 		thiscalc *= m_nTimeScale;
		 		thiscalc /= m_nFrameRate;

		 		m_thisTime = (MP4Duration)thiscalc;
		 		MP4Duration dur;
		 		dur = m_thisTime - m_lastTime;
                */

		 		//ret = MP4WriteSample(fileHandle, video, buf, size, dur, 0, keyframe); //MP4_INVALID_DURATION keyframe

                if(keyframe){
		 	     	LOGI("       type = %d, size = %d, %d       ",type, size, keyframe);
                }
		 		ret = MP4WriteSample(fileHandle, m_videoId, buf, size, MP4_INVALID_DURATION, 0, keyframe);
		 		//ret = MP4WriteSample(fileHandle, m_videoId, buf, size, dur, 0, keyframe);
		 		//m_lastTime = m_thisTime;
		 		if(!ret){
		 			//fprintf(stderr,	"can't write video frame %u\n",	m_samplesWritten );
		 			LOGI("              MP4_INVALID_TRACK_ID = %d               ",ret);
		 			//MP4DeleteTrack(fileHandle, m_videoId);
		 			//return MP4_INVALID_TRACK_ID;
		 		}
	}
	(*env)->ReleaseByteArrayElements(env, data, (jbyte *)buf, 0);
}

//添加音频帧的方法
JNIEXPORT void JNICALL Java_com_example_mymp4v2h264_MainActivity0_Mp4PackA
(JNIEnv *env, jclass clz, jbyteArray data, jint size)
{
	uint8_t *bufaudio = (uint8_t *)(*env)->GetByteArrayElements(env, data, JNI_FALSE);
	//LOGI("       Mp4PackA = %d       ", size);
	//MP4WriteSample(fileHandle, audio, &bufaudio[7], size-7);
	MP4WriteSample(fileHandle, audio, &bufaudio[7], size-7, MP4_INVALID_DURATION, 0, 1);
	/*
	bool MP4WriteSample(
		MP4FileHandle hFile,
		MP4TrackId trackId,
		const u_int8_t* pBytes,
		u_int32_t numBytes,
		MP4Duration duration DEFAULT(MP4_INVALID_DURATION),
		MP4Duration renderingOffset DEFAULT(0),
		bool isSyncSample DEFAULT(true));
		*/
    //减去7为了删除adts头部的7个字节
	(*env)->ReleaseByteArrayElements(env, data, (jbyte *)bufaudio, 0);
}


//视频录制结束调用
JNIEXPORT void JNICALL Java_com_example_mymp4v2h264_MainActivity0_Mp4End
(JNIEnv *env, jclass clz)
{
	MP4Close(fileHandle, 0);
	fileHandle = NULL;
	LOGI("              mp4close              ");
	LOGI("              mp4close              ");
}

//视频录制的调用,实现初始化
JNIEXPORT bool JNICALL Java_com_example_mymp4v2faac_MainActivity0_mp4init
(JNIEnv *env, jclass clz, jstring title)
{
	const char* local_title = (*env)->GetStringUTFChars(env,title, NULL);
	uint32_t samplesPerSecond;
	uint8_t profile;
	uint8_t channelConfig;

	samplesPerSecond = 44100;
	profile = 1; // AAC LC
    /*
0: Null
1: AAC Main
2: AAC LC (Low Complexity)
3: AAC SSR (Scalable Sample Rate)
4: AAC LTP (Long Term Prediction)
5: SBR (Spectral Band Replication)
6: AAC Scalable
     */
	channelConfig = 2;
	/*
	MP4V2_EXPORT MP4FileHandle MP4CreateEx(
	    const char* fileName,
	    uint32_t    flags DEFAULT(0),
	    int         add_ftyp DEFAULT(1),
	    int         add_iods DEFAULT(1),
	    char*       majorBrand DEFAULT(0),
	    uint32_t    minorVersion DEFAULT(0),
	    char**      compatibleBrands DEFAULT(0),
	    uint32_t    compatibleBrandsCount DEFAULT(0) );
    MP4V2_EXPORT MP4FileHandle MP4CreateEx(
    const char* fileName,
    uint32_t    flags DEFAULT(0),
    int         add_ftyp DEFAULT(1),
    int         add_iods DEFAULT(1),
    char*       majorBrand DEFAULT(0),
    uint32_t    minorVersion DEFAULT(0),
    char**      compatibleBrands DEFAULT(0),
    uint32_t    compatibleBrandsCount DEFAULT(0) );

   trak / mdia / minf / stbl
   - stsd: 编码器CODEC信息
   - stsz: 用于sample的划分，通常一个sample可以对应于frame。
   - stsc: 多个sample组成一个trunk，不过实际操作中可以让一个sample直接构成一个trunk
   - stco: trunk在文件中的位置，用于定位。
   - stts / ctts: 指定每个sample的PTS, DTS
    */

	//创建mp4文件
	//fileHandle = MP4Create(local_title, 0);
	//m_mp4file = MP4CreateEx(".\\Data\\2.mp4", MP4_DETAILS_ALL, 0, 1, (char*)1, 0, 0, 0);
	//m_mp4file = MP4CreateEx(".\\Data\\2.mp4", MP4_DETAILS_ALL, 0, 1, 1, 0, 0, 0);
	//fileHandle = MP4CreateEx(local_title, MP4_DETAILS_ALL, 0, 1, (char*)1, 0, 0, 0);

	char *p[4];
	p[0] = "isom";
	p[1] = "iso2";
	p[2] = "avc1";
	p[3] = "mp41";
	//fileHandle = MP4CreateEx(local_title, 0, 1, 1, "isom", 0x00000200, p, 4);
	fileHandle = MP4CreateEx(local_title, 9, 0, 1, (char*)1, 0, 0, 0);

	if(fileHandle == MP4_INVALID_FILE_HANDLE)
	{
		return false;
	}
	LOGI("              MP4CreateEx ok                ");

	//memcpy(sps_pps, sps_pps_640, 17);

	video_width = 640;
	video_height = 480;

	//设置mp4文件的时间单位
	MP4SetTimeScale(fileHandle, 90000);

	//创建视频track //根据ISO/IEC 14496-10 可知sps的第二个，第三个，第四个字节分别是 AVCProfileIndication,profile_compat,AVCLevelIndication     其中90000/20  中的20>是fps
	video = MP4AddH264VideoTrack(fileHandle,
			m_nMp4TimeScale,
			m_nMp4TimeScale/m_nVideoFrameRate,
			video_width,
			video_height,
			0x42,//sps_pps[1],
			0x80,//sps_pps[2],
			0x1e,//sps_pps[3],
			3);
	if(video == MP4_INVALID_TRACK_ID)
	{
		MP4Close(fileHandle, 0);
		return false;
	}
	MP4SetVideoProfileLevel(fileHandle, 0x7F);
	//设置sps和pps
	//67 42 80 1e e9 01 40 7b 20 sps
	//68 ce 06 e2 pps

	//uint8_t  ubuffer[2];
	//ubuffer = 0x1220;
	//ubuffer[0] = 0x12;
	//ubuffer[1] = 0x20;
	//MP4SetTrackESConfiguration(fileHandle, audio, ubuffer, 2);

	MP4AddH264SequenceParameterSet(fileHandle, video, sps, 9);//sps_pps, 13);
	MP4AddH264PictureParameterSet(fileHandle, video, pps, 4);//sps_pps+13, 4);

	LOGI("              MP4AddH264VideoTrack ok                ");
    /*
	uint32_t m_nVerbosity=1;
	if (MP4GetNumberOfTracks(fileHandle, MP4_VIDEO_TRACK_TYPE) == 1)
	{
		uint32_t new_verb = m_nVerbosity & ~(MP4_DETAILS_ERROR);
		MP4SetVerbosity(fileHandle, new_verb);
		MP4SetVideoProfileLevel(fileHandle, 0x7f);
		MP4SetVerbosity(fileHandle, m_nVerbosity);

		LOGI("              MP4_VIDEO_TRACK_TYPE                ");
	}
    */

	//ADTS = Audio Data Transport Stream 7 bytes:
	//adts_fixed_header();
	//adts_variable_header();
	audio = MP4AddAudioTrack(fileHandle, samplesPerSecond, 2048, MP4_MPEG2_AAC_LC_AUDIO_TYPE);//16000 1024
	if(audio == MP4_INVALID_TRACK_ID)
	{
		MP4Close(fileHandle, 0);
		return false;
	}
	MP4SetAudioProfileLevel(fileHandle, 0x02);
	LOGI("              MP4AddAudioTrack ok                ");
	/*
	if (MP4GetNumberOfTracks(fileHandle, MP4_AUDIO_TRACK_TYPE) == 1)
	{
		MP4SetAudioProfileLevel(fileHandle, 0x0F);
	}
	*/
	uint8_t* pConfig = NULL;
	uint32_t configLength = 0;

	MP4AacGetConfiguration(&pConfig, &configLength, profile, samplesPerSecond, channelConfig);
	//free(pConfig);
	MP4SetTrackESConfiguration(fileHandle, audio, pConfig, configLength);

	video_type = 1;
	(*env)->ReleaseStringUTFChars(env, title, local_title);

	LOGI("              mp4init                ");
	LOGI("              mp4init                ");

	return true;
}

uint16_t len_rec;

//添加视频帧的方法
JNIEXPORT void JNICALL Java_com_example_mymp4v2faac_MainActivity0_mp4packVideo
(JNIEnv *env, jclass clz, jbyteArray data, jint size, jint keyframe)
{
	unsigned char *buf = (unsigned char *)(*env)->GetByteArrayElements(env, data, JNI_FALSE);
	if(video_type == 1){
		int nalsize = size;
		bool ret = false;
		if(len_rec<5){
			len_rec++;
		    LOGI("              nalsize = %d, %d , %d              ",nalsize, size, keyframe);
	    }
		/*
		buf[0] = (nalsize & 0xff000000) >> 24;
		buf[1] = (nalsize & 0x00ff0000) >> 16;
		buf[2] = (nalsize & 0x0000ff00) >> 8;
		buf[3] =  nalsize & 0x000000ff;
		*/
		buf[0] = (nalsize >> 24) & 0xff;
		buf[1] = (nalsize >> 16) & 0xff;
		buf[2] = (nalsize >> 8)& 0xff;
		buf[3] =  nalsize & 0xff;

		m_samplesWritten++;
		double thiscalc;
		thiscalc = m_samplesWritten;
		thiscalc *= m_nMp4TimeScale;
		thiscalc /= m_nVideoFrameRate;

		m_thisTime = (MP4Duration)thiscalc;
		MP4Duration dur;
		dur = m_thisTime - m_lastTime;

		//ret = MP4WriteSample(fileHandle, video, buf, size, dur, 0, keyframe); //MP4_INVALID_DURATION keyframe
		ret = MP4WriteSample(fileHandle, video, buf, size, MP4_INVALID_DURATION, 0, 1);
		m_lastTime = m_thisTime;
		if(!ret)
		{
			//fprintf(stderr,	"can't write video frame %u\n",	m_samplesWritten );
			LOGI("              MP4_INVALID_TRACK_ID = %d               ",m_samplesWritten);
			MP4DeleteTrack(fileHandle, video);
			//return MP4_INVALID_TRACK_ID;
		}
	}
	(*env)->ReleaseByteArrayElements(env, data, (jbyte *)buf, 0);
}

//添加音频帧的方法
JNIEXPORT void JNICALL Java_com_example_mymp4v2faac_MainActivity0_mp4packAudio
(JNIEnv *env, jclass clz, jbyteArray data, jint size)
{
	uint8_t *bufaudio = (uint8_t *)(*env)->GetByteArrayElements(env, data, JNI_FALSE);
	//MP4WriteSample(fileHandle, audio, &bufaudio[7], size-7);
	MP4WriteSample(fileHandle, audio, &bufaudio[7], size-7, MP4_INVALID_DURATION, 0, 1);
	/*
	bool MP4WriteSample(
		MP4FileHandle hFile,
		MP4TrackId trackId,
		const u_int8_t* pBytes,
		u_int32_t numBytes,
		MP4Duration duration DEFAULT(MP4_INVALID_DURATION),
		MP4Duration renderingOffset DEFAULT(0),
		bool isSyncSample DEFAULT(true));
		*/
    //减去7为了删除adts头部的7个字节
	(*env)->ReleaseByteArrayElements(env, data, (jbyte *)bufaudio, 0);
}

//视频录制结束调用
JNIEXPORT void JNICALL Java_com_example_mymp4v2faac_MainActivity0_mp4close
(JNIEnv *env, jclass clz)
{
	MP4Close(fileHandle, 0);
	fileHandle = NULL;
	LOGI("              mp4close              ");
	LOGI("              mp4close              ");
}

/*
static void* writeThread(void* arg)
{
     rtp_s* p_rtp = (rtp_s*) arg;
     if (p_rtp == NULL)
     {
         printf("ERROR!\n");
         return;
     }

     MP4FileHandle file = MP4CreateEx("test.mp4", MP4_DETAILS_ALL, 0, 1, 1, 0, 0, 0, 0);
     if (file == MP4_INVALID_FILE_HANDLE)
     {
         printf("open file fialed.\n");
         return;
     }

     MP4SetTimeScale(file, 90000);

     //添加h264 track
     MP4TrackId video = MP4AddH264VideoTrack(file, 90000, 90000/25, 320, 240,
          0x64, //sps[1] AVCProfileIndication
          0x00, //sps[2] profile_compat
          0x1f, //sps[3] AVCLevelIndication
             3); // 4 bytes length before each NAL unit
     if (video == MP4_INVALID_TRACK_ID)
     {
         printf("add video track failed.\n");
         return;
     }
     MP4SetVideoProfileLevel(file, 0x7F);

     //添加aac音频
     MP4TrackId audio = MP4AddAudioTrack(file, 48000, 1024, MP4_MPEG4_AUDIO_TYPE);
     if (video == MP4_INVALID_TRACK_ID)
     {
         printf("add audio track failed.\n");
         return;
     }
     MP4SetAudioProfileLevel(file, 0x2);


     int ncount = 0;
     while (1)
     {
         frame_t* pf = NULL; //frame
         pthread_mutex_lock(&p_rtp->mutex);
         pf = p_rtp->p_frame_header;
         if (pf != NULL)
         {
             if (pf->i_type == 1)//video
             {
             //(1)h264流中的NAL，头四个字节是0x00000001;
             //(2)mp4中的h264track，头四个字节要求是NAL的长度，并且是大端顺序；
             // 将每个sample(也就是NAL)的头四个字节内容改成NAL的长度
                 if(pf->i_frame_size >= 4)
                 {
                    uint32_t* p = (&pf->p_frame[0]);
                    *p = htonl(pf->i_frame_size -4);//大端,去掉头部四个字节
                 }
                 MP4WriteSample(file, video, pf->p_frame, pf->i_frame_size, MP4_INVALID_DURATION, 0, 1);
             }
             else if (pf->i_type == 2)//audio
             {
                 MP4WriteSample(file, audio, pf->p_frame, pf->i_frame_size , MP4_INVALID_DURATION, 0, 1);
             }

             ncount++;

             //clear frame.
             p_rtp->i_buf_num--;
             p_rtp->p_frame_header = pf->p_next;
             if (p_rtp->i_buf_num <= 0)
             {
                 p_rtp->p_frame_buf = p_rtp->p_frame_header;
             }
             free_frame(&pf);
             pf = NULL;

             if (ncount >= 1000)
             {
                 break;
             }
         }
         else
         {
             //printf("BUFF EMPTY, p_rtp->i_buf_num:%d\n", p_rtp->i_buf_num);
         }
         pthread_mutex_unlock(&p_rtp->mutex);
         usleep(10000);
     }
     MP4Close(file);
}
*/


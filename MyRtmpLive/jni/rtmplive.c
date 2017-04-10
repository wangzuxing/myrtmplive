int RTMP264_Connect(const char* url);
int RTMP264_Send(int (*read_buffer)(unsigned char *buf, int buf_size));
void RTMP264_Close();

/*分配与初始化*/
rtmp = RTMP_Alloc();
RTMP_Init(rtmp);

/*设置URL*/
if (RTMP_SetupURL(rtmp,rtmp_url) == FALSE) {
    log(LOG_ERR,"RTMP_SetupURL() failed!");
    RTMP_Free(rtmp);
    return -1;
}

/*设置可写,即发布流,这个函数必须在连接前使用,否则无效*/
RTMP_EnableWrite(rtmp);

/*连接服务器*/
if (RTMP_Connect(rtmp, NULL) == FALSE) {
    log(LOG_ERR,"RTMP_Connect() failed!");
    RTMP_Free(rtmp);
    return -1;
}

/*连接流*/
if (RTMP_ConnectStream(rtmp,0) == FALSE) {
    log(LOG_ERR,"RTMP_ConnectStream() failed!");
    RTMP_Close(rtmp);
    RTMP_Free(rtmp);
    return -1;
}

/*定义包头长度,RTMP_MAX_HEADER_SIZE为rtmp.h中定义值为18*/

#define RTMP_HEAD_SIZE   (sizeof(RTMPPacket)+RTMP_MAX_HEADER_SIZE)

RTMPPacket * packet;
unsigned char * body;

/*分配包内存和初始化,len为包体长度*/
packet = (RTMPPacket *)malloc(RTMP_HEAD_SIZE+len);
memset(packet,0,RTMP_HEAD_SIZE);

/*包体内存*/
packet->m_body = (char *)packet + RTMP_HEAD_SIZE;
body = (unsigned char *)packet->m_body;
packet->m_nBodySize = len;

/*
 * 此处省略包体填充
 */
packet->m_hasAbsTimestamp = 0;
packet->m_packetType = RTMP_PACKET_TYPE_VIDEO; /*此处为类型有两种一种是音频,一种是视频*/
packet->m_nInfoField2 = rtmp->m_stream_id;
packet->m_nChannel = 0x04;
packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
packet->m_nTimeStamp = timeoffset;

/*发送*/
if (RTMP_IsConnected(rtmp)) {
    ret = RTMP_SendPacket(rtmp,packet,TRUE); /*TRUE为放进发送队列,FALSE是不放进发送队列,直接发送*/
}

/*释放内存*/
free(packet);

/*关闭与释放*/
RTMP_Close(rtmp);
RTMP_Free(rtmp);

//AVC sequence header

int send_video_sps_pps()
{
    RTMPPacket * packet;
    unsigned char * body;
    int i;

    packet = (RTMPPacket *)malloc(RTMP_HEAD_SIZE+1024);
    memset(packet,0,RTMP_HEAD_SIZE);

    packet->m_body = (char *)packet + RTMP_HEAD_SIZE;
    body = (unsigned char *)packet->m_body;

    memcpy(winsys->pps,buf,len);
    winsys->pps_len = len;

    i = 0;
    body[i++] = 0x17;
    body[i++] = 0x00;

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
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet->m_nInfoField2 = rtmp->m_stream_id;

    /*调用发送接口*/
    RTMP_SendPacket(rtmp,packet,TRUE);
    free(packet);

    return 0;
}

size = x264_encoder_encode(cx->hd,&nal,&n,pic,&pout);

int i,last;
for (i = 0,last = 0;i < n;i++) {
    if (nal[i].i_type == NAL_SPS) {

        sps_len = nal[i].i_payload-4;
        memcpy(sps,nal[i].p_payload+4,sps_len);

    } else if (nal[i].i_type == NAL_PPS) {

        pps_len = nal[i].i_payload-4;
        memcpy(pps,nal[i].p_payload+4,pps_len);

        /*发送sps pps*/
        send_video_sps_pps();

    } else {

        /*发送普通帧*/
        send_rtmp_video(nal[i].p_payload,nal[i].i_payload);
    }
    last += nal[i].i_payload;
}

int send_rtmp_video(unsigned char * buf,int len)
{
    int type;
    long timeoffset;
    RTMPPacket * packet;
    unsigned char * body;

    timeoffset = GetTickCount() - start_time;  /*start_time为开始直播时的时间戳*/

    /*去掉帧界定符*/
    if (buf[2] == 0x00) { /*00 00 00 01*/
            buf += 4;
            len -= 4;
    } else if (buf[2] == 0x01){ /*00 00 01*/
            buf += 3;
            len -= 3;
    }
    type = buf[0]&0x1f;

    packet = (RTMPPacket *)base_malloc(RTMP_HEAD_SIZE+len+9);
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
    packet->m_nInfoField2 = winsys->rtmp->m_stream_id;
    packet->m_nChannel = 0x04;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nTimeStamp = timeoffset;

    /*调用发送接口*/
    RTMP_SendPacket(rtmp,packet,TRUE);
    free(packet);
}

int cap_rtmp_sendaac_spec(unsigned char *spec_buf,int spec_len)
{
    RTMPPacket * packet;
    unsigned char * body;
    int len;

    len = spec_len;  /*spec data长度,一般是2*/

    packet = (RTMPPacket *)base_malloc(RTMP_HEAD_SIZE+len+2);
    memset(packet,0,RTMP_HEAD_SIZE);

    packet->m_body = (char *)packet + RTMP_HEAD_SIZE;
    body = (unsigned char *)packet->m_body;

    /*AF 00 + AAC RAW data*/
    body[0] = 0xAF;
    body[1] = 0x00;
    memcpy(&body[2],spec_buf,len); /*spec_buf是AAC sequence header数据*/

    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_nBodySize = len+2;
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = rtmp->m_stream_id;

    /*调用发送接口*/
    RTMP_SendPacket(rtmp,packet,TRUE);

    return TRUE;
}

//AAC sequence header
char *buf;
int len;
faacEncGetDecoderSpecificInfo(fh,&buf,&len);

memcpy(spec_buf,buf,len);
spec_len = len;

/*释放系统内存*/
free(buf);

void * cap_dialog_send_audio(unsigned char * buf,int len)
{
    long timeoffset;
    timeoffset = GetTickCount() - start_time;

    buf += 7;
    len += 7;

    if (len > 0) {
        RTMPPacket * packet;
        unsigned char * body;

        packet = (RTMPPacket *)malloc(RTMP_HEAD_SIZE+len+2);
        memset(packet,0,RTMP_HEAD_SIZE);

        packet->m_body = (char *)packet + RTMP_HEAD_SIZE;
        body = (unsigned char *)packet->m_body;

        /*AF 01 + AAC RAW data*/
        body[0] = 0xAF;
        body[1] = 0x01;
        memcpy(&body[2],buf,len);

        packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
        packet->m_nBodySize = len+2;
        packet->m_nChannel = 0x04;
        packet->m_nTimeStamp = timeoffset;
        packet->m_hasAbsTimestamp = 0;
        packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
        packet->m_nInfoField2 = rtmp->m_stream_id;

        /*调用发送接口*/
        RTMP_SendPacket(rtmp,packet,TRUE);
        free(packet);
    }
    return 0;
}

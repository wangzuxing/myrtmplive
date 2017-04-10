#include "H264VideoServerMediaSubsession.h"

H264VideoServerMediaSubsession::H264VideoServerMediaSubsession(UsageEnvironment & env, FramedSource * source) : OnDemandServerMediaSubsession(env, True)
{
	m_pSource = source;
	m_pSDPLine = 0;

	//FRAME_PER_SEC = H264VideoSource::getFrameRate();
	FRAME_PER_SEC = 15; //frame_rate;
}

H264VideoServerMediaSubsession::~H264VideoServerMediaSubsession(void)
{
	if (m_pSDPLine)
	{
		free(m_pSDPLine);
	}
}

H264VideoServerMediaSubsession * H264VideoServerMediaSubsession::createNew(UsageEnvironment & env, FramedSource * source)
{
	return new H264VideoServerMediaSubsession(env, source);
}

FramedSource * H264VideoServerMediaSubsession::createNewStreamSource(unsigned clientSessionId, unsigned & estBitrate)
{
	return H264VideoStreamFramer::createNew(envir(), new H264VideoSource(envir()));
}

RTPSink * H264VideoServerMediaSubsession::createNewRTPSink(Groupsock * rtpGroupsock, unsigned char rtpPayloadTypeIfDynamic, FramedSource * inputSource)
{
	return H264VideoRTPSink::createNew(envir(), rtpGroupsock, rtpPayloadTypeIfDynamic);
}

char const * H264VideoServerMediaSubsession::getAuxSDPLine(RTPSink * rtpSink, FramedSource * inputSource)
{
	if (m_pSDPLine)
	{
		return m_pSDPLine;
	}

	m_pDummyRTPSink = rtpSink;

	//mp_dummy_rtpsink->startPlaying(*source, afterPlayingDummy, this);
	m_pDummyRTPSink->startPlaying(*inputSource, 0, 0);

	chkForAuxSDPLine(this);

	m_done = 0;

	envir().taskScheduler().doEventLoop(&m_done);

	m_pSDPLine = strdup(m_pDummyRTPSink->auxSDPLine());

	m_pDummyRTPSink->stopPlaying();

	return m_pSDPLine;
}

void H264VideoServerMediaSubsession::afterPlayingDummy(void * ptr)
{
	H264VideoServerMediaSubsession * This = (H264VideoServerMediaSubsession *)ptr;

	This->m_done = 0xff;
}

void H264VideoServerMediaSubsession::chkForAuxSDPLine(void * ptr)
{
	H264VideoServerMediaSubsession * This = (H264VideoServerMediaSubsession *)ptr;

	This->chkForAuxSDPLine1();
}

void H264VideoServerMediaSubsession::chkForAuxSDPLine1()
{
	if (m_pDummyRTPSink->auxSDPLine())
	{
		m_done = 0xff;
	}
	else
	{
		double delay = 1000.0 / (FRAME_PER_SEC);  // ms
		int to_delay = delay * 1000;  // us

		nextTask() = envir().taskScheduler().scheduleDelayedTask(to_delay, chkForAuxSDPLine, this);
	}
}

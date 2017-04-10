package com.example.myrtmplive;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class RtmpActivity extends Activity implements SurfaceHolder.Callback, PreviewCallback {
	 private int width=320, height=240; 
	 private byte[] buf;
	 private Surface surface;
	 private String TAG = "RtmpActivity";
	 private SurfaceView surfaceView;
	 
	 boolean playAAC;
	 boolean playRtmp;
	 boolean playRtmp0;
	 
	 Camera camera = null;
	 MediaCodec mediaCodec, mediaCodecd;
     
	 AssetFileDescriptor assetFileDescriptor;// = getResources().openRawResourceFd(R.raw.live);
	 private static final String SAMPLE = Environment.getExternalStorageDirectory() + "/live.mp4";

	 static {
		System.loadLibrary("rtmp");
		System.loadLibrary("rtmplive");
	 }
	
	 public static native void RtmpLiveS(String url_s);
	 public static native void RtmpLiveE();
	 public static native void RtmpLiveSpsAndPps(byte[] sps, int sps_len, byte[] pps, int pps_len);
	 public static native void RtmpLiveWHFreqR(int video_w, int video_h, int video_fps);
	 public static native void RtmpLiveSendNalU(byte[] nalu, int nalu_size, boolean isKeyFrame);
	 public static native void RtmpLiveSendAacD(byte[] aac, int aac_size);
	 public static native void RtmpLiveSendAacSequnceHder(byte[] aac, int aac_size);
	 
	 @Override
	 protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.rtmp_main);
		
		surfaceView = (SurfaceView) findViewById(R.id.mSurfaceview);
		SurfaceHolder mSurfaceHolder = surfaceView.getHolder();
		//mSurfaceHolder.setFormat(PixelFormat.TRANSPARENT);//translucent
		mSurfaceHolder.addCallback(this);
		surface = surfaceView.getHolder().getSurface();
		
		playRtmp = false;
		playRtmp0= false;
		
		Button playUdp = (Button) findViewById(R.id.playUdp);
		playUdp.setOnClickListener(new OnClickListener() {
			@Override
		    public void onClick(View v){
				// Do something in response to button click
				playRtmp = !playRtmp;
				if(playRtmp){
					if(pps != null){
					   RtmpLiveE();
					   playRtmp0 = true;
					   Toast.makeText(RtmpActivity.this, "Push Start "+idx_number, Toast.LENGTH_SHORT).show();
				       RtmpLiveS("rtmp://192.168.1.9:1935/live/livestream");
				       RtmpLiveWHFreqR(width, height, 15);
				       RtmpLiveSpsAndPps(sps, sps.length, pps,  pps.length);
					}
				}else{
					Toast.makeText(RtmpActivity.this, "Push End", Toast.LENGTH_SHORT).show();
					RtmpLiveE();
				}
		    }
		});
		
		Button playAac = (Button) findViewById(R.id.playAac);
		playAac.setOnClickListener(new OnClickListener() {
			@Override
		    public void onClick(View v){
				// Do something in response to button click
				playAAC = !playAAC;
				if(playAAC){
					RtmpLiveE();
					Toast.makeText(RtmpActivity.this, "AAC Start ", Toast.LENGTH_SHORT).show();
					try {
						RtmpLiveS("rtmp://192.168.1.9:1935/live/livestream");
						AudioEncoder();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else{
					Toast.makeText(RtmpActivity.this, "AAC Stop", Toast.LENGTH_SHORT).show();
					RtmpLiveE();
					AACClose();
				}
		    }
		});
		//assetFileDescriptor = getResources().openRawResourceFd(R.raw.live);
	 }

	 @Override
	 public void surfaceCreated(SurfaceHolder holder) {
	 	Log.i("Encoder", "--------------surfaceCreated--------------");
		MediaCodecEncodeInit();
		//MediaCodecDecodeInit();
		openCamera(holder);
	 }

	 @Override
	 public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
	 }

	 @Override
	 public void surfaceDestroyed(SurfaceHolder holder) {
		Log.i("Encoder", "--------------surfaceDestroyed--------------");
		releaseCamera();
		close();
		RtmpLiveE();
		AACClose();
	 }

	 @Override
	 public boolean onKeyDown(int keyCode, KeyEvent event)  {
	    if (keyCode == KeyEvent.KEYCODE_BACK) { 
	      //do something here
	      finish();   
	      return true;
	    }
	    return super.onKeyDown(keyCode, event);
	 }
	
	 private Camera getCamera(int cameraType) {
	    Camera camera = null;
	    try {
	    	Log.i("Encoder", "--------------getCamera start--------------");
	        camera = Camera.open(Camera.getNumberOfCameras()-1);
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return camera; // returns null if camera is unavailable
	 }
	
	 private void openCamera(SurfaceHolder holder) {
	    releaseCamera();
	    try {
	            camera = getCamera(Camera.CameraInfo.CAMERA_FACING_BACK); 
	        } catch (Exception e) {
	            camera = null;
	            e.printStackTrace();
	        }   
	    if(camera != null){
	    	/*
	        try {
				camera.setPreviewDisplay(holder);// 
			} catch (IOException e) {  
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        */
	    	Log.i("Encoder", "--------------openCamera 00--------------");
			camera.setDisplayOrientation(90);
			
			Camera.Parameters parameters = camera.getParameters();
			
			parameters.setPreviewSize(width, height); 
			//parameters.getSupportedPreviewSizes();
			parameters.setFlashMode("off");
			parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO); 
			parameters.setPreviewFormat(ImageFormat.YV12); //NV21 / YV12
			parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);  
			//parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); 
			//parameters.set("orientation", "portrait");
			//parameters.set("orientation", "landscape");
			Log.i("Encoder", "--------------openCamera 11--------------");
			camera.setParameters(parameters);
			buf = new byte[width*height*3/2];
			camera.addCallbackBuffer(buf);
			camera.setPreviewCallbackWithBuffer(this);
			
			List<int[]> fpsRange = parameters.getSupportedPreviewFpsRange();
			for (int[] temp3 : fpsRange) {
			     System.out.println(Arrays.toString(temp3));
			}

			//parameters.setPreviewFpsRange(4000,60000);
			parameters.setPreviewFpsRange(15000, 15000);    
			//parameters.setPreviewFpsRange(4000,60000);//this one results fast playback when I use the FRONT CAMERA 
			
			camera.startPreview();
			
			Log.i("Encoder", "--------------openCamera--------------");
	    }
	 }
	
	 private synchronized void releaseCamera() {
	    if (camera != null) {
	        try {
	            camera.setPreviewCallback(null);
	            camera.stopPreview();
	            camera.release();
	            camera = null;
	            Log.i("Encoder", "--------------releaseCamera--------------");
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }
	 }

     private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
            	Log.i("Encoder", "selectColorFormat = "+colorFormat);
                return colorFormat;
            }
        }
        Log.e("Encoder","couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
     }

     private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
     }

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
                	Log.i("Encoder", "selectCodec = "+codecInfo.getName());
                    return codecInfo;
                }
            }
        }
        return null;
     }
    
	 public void MediaCodecEncodeInit(){
		String type = "video/avc";
	    int colorFormat = selectColorFormat(selectCodec("video/avc"), "video/avc");
	    
		mediaCodec = MediaCodec.createEncoderByType(type);  
		MediaFormat mediaFormat = MediaFormat.createVideoFormat(type, width, height);  
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 125000);//125kbps  
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);  
		//mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, 
		//		MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);  //COLOR_FormatYUV420Planar
		
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5); 
		mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);  
		mediaCodec.start();		
		
		Log.i("Encoder", "--------------MediaCodecEncodeInit--------------");
	 }

	 public void MediaCodecDecodeInit(){
		String type = "video/avc";
		mediaCodecd = MediaCodec.createDecoderByType(type);  
		MediaFormat mediaFormat = MediaFormat.createVideoFormat(type, width, height);  
		mediaCodecd.configure(mediaFormat, surface, null, 0);  
		mediaCodecd.start(); 
	 }	

	 public void close() {
	    try {
	        mediaCodec.stop();
	        mediaCodec.release();
	        
	        //mediaCodecd.stop(); 
	        //mediaCodecd.release(); 
	        Log.i("Encoder", "--------------close--------------");
	    } catch (Exception e){ 
	        e.printStackTrace();
	    }
	 }
	
	 private byte[] m_info = null; 
	 private byte[] yuv420 = new byte[width*height*3/2];
	 private byte[] h264 = new byte[width*height*3/2]; 
	
	 /*
    I420: YYYYYYYY UU VV    =>YUV420P
    YV12: YYYYYYYY VV UU    =>YUV420P
    NV12: YYYYYYYY UVUV     =>YUV420SP
    NV21: YYYYYYYY VUVU     =>YUV420SP
    */
	 //yv12 ת yuv420p  yvu -> yuv  
     private void swapYV12toI420(byte[] yv12bytes, byte[] i420bytes, int width, int height)   
     {        
        System.arraycopy(yv12bytes, 0, i420bytes, 0,width*height);  
        System.arraycopy(yv12bytes, width*height+width*height/4, i420bytes, width*height,width*height/4);  
        System.arraycopy(yv12bytes, width*height, i420bytes, width*height+width*height/4,width*height/4);    
     } 

     public int offerEncoder(byte[] input, byte[] output)   
     {     
        int pos = 0;  
        swapYV12toI420(input, output, width, height);  
        try {  
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();  
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();  
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);  
            if (inputBufferIndex >= 0) {  
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];  
                inputBuffer.clear();  
                inputBuffer.put(output);  //yuv420
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, output.length, 0, 0); // yuv420.length 
            }  
  
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();  
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);  
             
            while (outputBufferIndex >= 0)   
            {  
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];  
                byte[] outData = new byte[bufferInfo.size];  
                outputBuffer.get(outData);  
                  
                if(m_info != null)  {                 
                    System.arraycopy(outData, 0,  output, pos, outData.length);  
                    pos += outData.length;    
                }else { //
                     ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);    
                     if (spsPpsBuffer.getInt() == 0x00000001) {   
                    	 Log.i("Encoder", "--------- pps sps found = "+outData.length+"---------");
                         m_info = new byte[outData.length];  
                         System.arraycopy(outData, 0, m_info, 0, outData.length);  
                     }else {    
                    	 Log.i("Encoder", "--------- no pps sps detect---------");
                         return -1;  
                     }        
                }  
                  
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);  
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);  
            }  
  
            if(output[4] == 0x65) //key frame 
            {  
            	Log.i("Encoder", "-----------idr frame: "+output[4]+"-----------");
                System.arraycopy(output, 0,  yuv420, 0, pos);  
                System.arraycopy(m_info, 0,  output, 0, m_info.length);  
                System.arraycopy(yuv420, 0,  output, m_info.length, pos);  
                pos += m_info.length;  
            }  
              
        } catch (Throwable t) {  
            t.printStackTrace();  
        }  
  
        return pos;  
     }  
     
     boolean isKeyFrame;
     boolean getSpsPpsFlag = true;
     boolean firstFlag = true;
     byte[]  outData0 = new byte[20]; 
     byte[]  sps;
     byte[]  pps;
 	 int     idx_number;
     // encode
 	 public void onFrame(byte[] buf, int length) {	
 		    swapYV12toI420(buf, h264, width, height); 
		 
		    ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
		    ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
		    int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
		    if (inputBufferIndex >= 0) {
		        ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
		        inputBuffer.clear();
		        //inputBuffer.put(buf, offset, length);
		        inputBuffer.put(h264, 0, length);
		        mediaCodec.queueInputBuffer(inputBufferIndex, 0, length, 0, 0);
		    }
		    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
		    int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);

		    while (outputBufferIndex >= 0) {
		        ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

	            byte[] outData = new byte[bufferInfo.size];
	            outputBuffer.get(outData);

	            if(getSpsPpsFlag){
	                /*去掉帧界定符*/
	            	getSpsPpsFlag = false;
	            	if (outData[2] == 0x00) { /*00 00 00 01*/
		            	idx_number = 4;
		            } else if (outData[2] == 0x01){ /*00 00 01*/
		            	idx_number = 3;
		            }
	            	System.out.println("idx_number:" + idx_number);
	            	System.out.println("type:" + outData[4]);
	            
				    if ((outData[idx_number] & 0x1f) == 7) 
				    { // MediaCodec会在编码第一帧之前一起输出sps+pps
				    	Log.i(TAG, "   onFrame pps sps  ");
		              	for (int ix = 0; ix < outData.length; ++ix) {
		        			System.out.printf("%02x ", outData[ix]);
		        	    }
		        	    System.out.println("\n----------"); 
						Log.d(TAG, "sps pps:" + Arrays.toString(outData));
						
						if(idx_number==4){
							for (int i = 0; i < outData.length; i++) {
								if (i + 4 < outData.length) { // 保证不越界
									if (outData[i] == 0x00 
									  && outData[i+1] == 0x00
									  && outData[i+2] == 0x00
									  && outData[i+3] == 0x01) {
										//sps pps数据如下: 0x00 0x00 0x00 0x01 7 sps 0x00 0x00 0x00 0x01 8 pps
										if ((outData[i + 4] & 0x1f) == 8) {// & 0x1f =8 pps
											//去掉界定符
											sps = new byte[i - 4];
											System.arraycopy(outData, 4, sps, 0, sps.length);
											pps = new byte[outData.length - (4 + sps.length) - 4];
											System.arraycopy(outData, 4 + sps.length + 4, pps, 0, pps.length);
											break;
										}
									}
								}
							}
						 }else if(idx_number==3){
							for (int i = 0; i < outData.length; i++) {
								if (i + 3 < outData.length) { // 保证不越界
									if (outData[i] == 0x00 
									  && outData[i+1] == 0x00
									  && outData[i+2] == 0x01) {
										//sps pps数据如下: 0x00 0x00 0x00 0x01 7 sps 0x00 0x00 0x00 0x01 8 pps
										if ((outData[i + 3] & 0x1f) == 8) {// & 0x1f =8 pps
											//去掉界定符
											sps = new byte[i - 3];
											System.arraycopy(outData, 3, sps, 0, sps.length);
											pps = new byte[outData.length - (3 + sps.length) - 3];
											System.arraycopy(outData, 3 + sps.length + 3, pps, 0, pps.length);
											break;
										}
									}
								}
							}
						}
						Log.d(TAG, "sps :" + Arrays.toString(sps));
						Log.d(TAG, "sps :" + Arrays.toString(pps));
				    }
				}	            
	            if(playRtmp) {
					isKeyFrame = false;
					//Log.d(TAG, "           frame         ");
					if(outData[idx_number] == 0x65) {//& 0x1f) == 5){ //关键帧 outData[4] == 0x65
                        isKeyFrame = true;
                        Log.d(TAG, "          key frame         ");
                        if(playRtmp0){
                           Log.d(TAG, "          playRtmp0         ");
                           playRtmp0 = false;
                        }
					}
					if(!playRtmp0){
				       System.arraycopy(outData, idx_number, h264, 0, outData.length-idx_number);
				       //Log.d(TAG, "push nalu :" + (outData.length-idx_number));
				       RtmpLiveSendNalU(h264, outData.length-idx_number, isKeyFrame);
					}
				}  

		        mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
		        outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
		        //Log.d(TAG, "encode: " + outputBufferIndex);
		    }    
     }
 	
	 private int mCount;
	 private final static int FRAME_RATE = 15;
	 // decoder
	 public void onFrame0(byte[] buf, int offset, int length, int flag) {  
	        ByteBuffer[] inputBuffers = mediaCodecd.getInputBuffers();  
	        int inputBufferIndex = mediaCodecd.dequeueInputBuffer(-1);  
	        if (inputBufferIndex >= 0) {  
	            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];  
	            inputBuffer.clear();  
	            inputBuffer.put(buf, offset, length);  
	            mediaCodecd.queueInputBuffer(inputBufferIndex, 0, length, mCount * 1000000 / FRAME_RATE, 0);  
	            mCount++;  
	        }  
	  
	       MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();  
	       int outputBufferIndex = mediaCodecd.dequeueOutputBuffer(bufferInfo,0);  
	       while (outputBufferIndex >= 0) {  
	           mediaCodecd.releaseOutputBuffer(outputBufferIndex, true);  
	           outputBufferIndex = mediaCodecd.dequeueOutputBuffer(bufferInfo, 0);  
	       }  
	}
	 
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		// TODO Auto-generated method stub
		 //int ret = offerEncoder(data,h264);  
		 onFrame(data, data.length); 
		 camera.addCallbackBuffer(buf);	
	}
	
	//
	private MediaCodec mediaCodecAAC;
	private BufferedOutputStream outputStreamAAC;
	private String mediaTypeAAC = "audio/mp4a-latm";
	private AudioRecord recorder;
	private int bufferSize;
	private boolean isRunning,isRecording;
	
	public void AudioEncoder() throws IOException {
		 File f = new File(Environment.getExternalStorageDirectory(), "audioencoded_1.aac");
		 try {
			 if(!f.exists()){
				Log.e("AudioEncoder", "       outputStreamAAC file      ");
			    f.createNewFile();
			 }else{
				if(f.delete()){
				   Log.e("AudioEncoder", "       outputStreamAAC file created again      ");
				   f.createNewFile();
				}
			 }
		 } catch (IOException e) {
			 e.printStackTrace();
		 }
		 
		 try {
		      outputStreamAAC = new BufferedOutputStream(new FileOutputStream(f));
		      Log.e("AudioEncoder", "outputStream initialized");
		 } catch (Exception e){
		      e.printStackTrace();
		 }
		 isRunning = false;
		 count_tt0 = 0;
		 aacFlag   = true;
		 bufferSize = AudioRecord.getMinBufferSize(44100,
				 AudioFormat.CHANNEL_IN_STEREO, 
				 AudioFormat.ENCODING_PCM_16BIT);
		 
		 recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 
				 44100, 
				 AudioFormat.CHANNEL_IN_STEREO,
				 AudioFormat.ENCODING_PCM_16BIT, //AudioFormat.ENCODING_DEFAULT
				 bufferSize);
		 
		 /*
		 bufferSize = AudioRecord.getMinBufferSize(44100,
				 AudioFormat.CHANNEL_IN_MONO, 
				 AudioFormat.ENCODING_PCM_16BIT)*2;
		 recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 
		    		44100,
		    		AudioFormat.CHANNEL_IN_MONO,
		    		AudioFormat.ENCODING_PCM_16BIT, 
		    		bufferSize);		   
		 */
		 Log.e("AudioEncoder", "bufferSize = "+bufferSize);
		 
		 /*
		 mediaCodecAAC = MediaCodec.createEncoderByType(mediaTypeAAC);
		 final int kSampleRates[] = { 8000, 11025, 22050, 44100, 48000 };
		 final int kBitRates[] = { 64000, 128000 }; //96000
		 
		 MediaFormat mediaFormat = MediaFormat.createAudioFormat(mediaTypeAAC,kSampleRates[3],1);
		 mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		 mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, kBitRates[1]);
		 mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
		 mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);//2
		 mediaFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
		 mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
		 mediaCodecAAC.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		 mediaCodecAAC.start();
		 */
		 
		 Log.i("Encoder", "--------------AudioEncoder--------------");
		 //doMediaRecordEncode();
		 
		 mediaCodecAAC = MediaCodec.createEncoderByType("audio/mp4a-latm");
		 MediaFormat format = new MediaFormat();
		 format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
		 format.setInteger(MediaFormat.KEY_BIT_RATE, 96000); //64 * 1024
		 format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
		 format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
		 format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
		 format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);//AACObjectHE
		 mediaCodecAAC.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		 mediaCodecAAC.start();

		 isRecording = true;
		 recorder.startRecording();
		 new Thread() {
		        public void run() {
		            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);
		            int read = 0;
		            while (isRecording) {
		            	try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
		                read = recorder.read(byteBuffer, bufferSize);
		                if(AudioRecord.ERROR_INVALID_OPERATION != read){
		                	AACEncoder(byteBuffer.array(), read);
		                }
		            }
		            recorder.stop();
		            mediaCodecAAC.stop();
		 		    mediaCodecAAC.release();
		 		    try {
						outputStreamAAC.flush();
						outputStreamAAC.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		            Log.i("Encoder", "--------------end 22--------------");
		        }
		 }.start();
	}
	
	public void AACClose() {
		try {
		   Log.i("Encoder", "--------------AACClose--------------");
		   isRecording = false;
		} catch (Exception e){
		   e.printStackTrace();
		}
    }
	
	/**
	* Add ADTS header at the beginning of each and every AAC packet.
	* This is needed as MediaCodec encoder generates a packet of raw
	* AAC data.
	*
	* Note the packetLen must count in the ADTS header itself.
	**/
	private void addADTStoPacket(byte[] packet, int packetLen) {
		int profile = 2; //AAC LC
		//39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
		int freqIdx = 4; //44.1KHz
		int chanCfg = 2; //CPE

		// fill in ADTS data
		packet[0] = (byte)0xFF;
		packet[1] = (byte)0xF9;
		packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
		packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
		packet[4] = (byte)((packetLen&0x7FF) >> 3);
		packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
		packet[6] = (byte)0xFC;
	}
	
	int count_tt;
	int count_tt0;
	boolean  aacFlag = true;
	
	// called AudioRecord's read
	public synchronized void AACEncoder(byte[] input, int length) {
	    //Log.e("AudioEncoder", input.length + " is coming");
	
	    try {
	    	ByteBuffer[] inputBuffers = mediaCodecAAC.getInputBuffers();
	 	    ByteBuffer[] outputBuffers = mediaCodecAAC.getOutputBuffers();
	 	    int inputBufferIndex = mediaCodecAAC.dequeueInputBuffer(-1); //-1, 0
	 	    if (inputBufferIndex >= 0) {
	 		    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
	 		    inputBuffer.clear();
	 		    inputBuffer.put(input);

	 		    mediaCodecAAC.queueInputBuffer(inputBufferIndex, 0, length, 0, 0); //input.length
	 	    }
	 	    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
	 	    int outputBufferIndex = mediaCodecAAC.dequeueOutputBuffer(bufferInfo,0);

	 	    //trying to add a ADTS
		    while (outputBufferIndex >= 0) {
		    	
		    	int outBitsSize = bufferInfo.size;
			    int outPacketSize = outBitsSize + 7; // 7 is ADTS size
			    
			    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
    
			    //Without ADTS header
			    byte[] outData = new byte[bufferInfo.size];
			    outputBuffer.get(outData);
			    //outputStreamAAC.write(outData, 0, outData.length);
			    //Log.e("AudioEncoder", "AAC = "+outData.length );
			    if(aacFlag){
			    	if(count_tt0==0){
			    		count_tt0 = 1;
			    		Log.e("AudioEncoder", "length "+bufferInfo.size);
			    		Log.d(TAG, " " + Arrays.toString(outData));
			    		for (int ix = 0; ix < outData.length; ++ix) {
		        			System.out.printf("%02x ", outData[ix]);
		        	    }
		        	    System.out.println("\n----------"); 
			    	}else if(count_tt0==1){
			    		count_tt0 = 2;
			    		Log.e("AudioEncoder", "length "+bufferInfo.size);
			    		Log.d(TAG, " " + Arrays.toString(outData));
			    		for (int ix = 0; ix < outData.length; ++ix) {
		        			System.out.printf("%02x ", outData[ix]);
		        	    }
		        	    System.out.println("\n----------"); 
			    	}else if(count_tt0<6){
			    		count_tt0++;
			    		Log.e("AudioEncoder", "length "+bufferInfo.size);
			    	}else{
			    		aacFlag = false; 
			    	}
			    }
			    if(bufferInfo.size == 2){
			    	System.out.println("        aac sequence header    ");  
			    	System.out.println("        aac sequence header    "); 
			    	RtmpLiveSendAacSequnceHder(outData, bufferInfo.size);
			    }else{
			        RtmpLiveSendAacD(outData, bufferInfo.size);
			    }
			    /*
			    outputBuffer.position(bufferInfo.offset);
			    outputBuffer.limit(bufferInfo.offset + outBitsSize);
			
			    byte[] outData = new byte[outPacketSize];
			    addADTStoPacket(outData, outPacketSize);
			
			    outputBuffer.get(outData, 7, outBitsSize);
			    outputBuffer.position(bufferInfo.offset);
			    
			    outputStreamAAC.write(outData, 0, outData.length);
			    count_tt++;
		    	if(count_tt>=10){
		    		count_tt = 0;
		    		Log.e("AudioEncoder", length + " inputing, "+ outData.length + " bytes written");
		    	}
			    */
			    outputBuffer.clear();
			
			    mediaCodecAAC.releaseOutputBuffer(outputBufferIndex, false);
			    outputBufferIndex = mediaCodecAAC.dequeueOutputBuffer(bufferInfo, 0);
	        }
		    
		    /*
		    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();  
	        int index = codec.dequeueOutputBuffer(info,0);	        
			if (index >= 0) {
				int outBitsSize = info.size;
				int outPacketSize = outBitsSize + 7; // 7 is ADTS size
				ByteBuffer outBuf = codecOutputBuffers[index];
				outBuf.position(info.offset);
				outBuf.limit(info.offset + outBitsSize);
				try {
					byte[] data = new byte[outPacketSize]; //space for ADTS header included					
					addADTStoPacket(data, outPacketSize);
					outBuf.get(data, 7, outBitsSize);
					outBuf.position(info.offset);				
					mFileStream.write(data, 0, outPacketSize); //open FileOutputStream beforehand
				} catch (IOException e) {
				    Log.e(TAG, "failed writing bitstream data to file");
				    e.printStackTrace();
				}
				numBytesDequeued += info.size;
				outBuf.clear();
				codec.releaseOutputBuffer(index, false);// render
				Log.d(TAG, " dequeued " + outBitsSize + " bytes of output data.");
				Log.d(TAG, " write " + outPacketSize + " bytes into output file.");
			}
			else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
			}
			else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
		    }
			*/
	    } catch (Throwable t) {
	        t.printStackTrace();
	    }
	}
	
	// Frame smooth timestamp generator
	class TimeStampEstimator {
		final int durationHistoryLength = 2048;
		private long durationHistory[];
		int durationHistoryIndex = 0;
		long durationHistorySum = 0;
		long lastFrameTiming = 0;
		long sequenceDuration = 0;

		public void update() {
			long currentFrameTiming = SystemClock.elapsedRealtime();
			long newDuration = currentFrameTiming - lastFrameTiming;
			lastFrameTiming = currentFrameTiming;

			durationHistorySum -= durationHistory[durationHistoryIndex];
			durationHistorySum += newDuration;
			durationHistory[durationHistoryIndex] = newDuration;
			durationHistoryIndex++;
			if (durationHistoryIndex >= durationHistoryLength)
				durationHistoryIndex = 0;
			sequenceDuration += (int) ((1.0 * durationHistorySum / durationHistoryLength));
		}

		public void setFirstFrameTiming() {
			lastFrameTiming = SystemClock.elapsedRealtime()
					- durationHistorySum / durationHistoryLength;
			sequenceDuration = 0;
		}

		public long getSequenceTimeStamp() {
			return sequenceDuration;
		}

		public void reset(long frameDuration) {
			if (durationHistory == null)
				durationHistory = new long[durationHistoryLength];
			durationHistorySum = 0;
			for (int i = 0; i < durationHistoryLength; i++) {
				durationHistory[i] = frameDuration; // us
				durationHistorySum += frameDuration;
			}
			lastFrameTiming = 0;
			sequenceDuration = 0;
			durationHistoryIndex = 0;
		}

		public TimeStampEstimator(long frameDuration) {
			reset(frameDuration);
		}
	}
	
	//
	public void getSPSAndPPS(String fileName) throws IOException {
		File file = new File(fileName);
		FileInputStream fis = new FileInputStream(file);

		int fileLength = (int) file.length();
		byte[] fileData = new byte[fileLength];
		fis.read(fileData);

		// 'a'=0x61, 'v'=0x76, 'c'=0x63, 'C'=0x43
		byte[] avcC = new byte[] { 0x61, 0x76, 0x63, 0x43 };

		// avcC的起始位置
		int avcRecord = 0;
		for (int ix = 0; ix < fileLength; ++ix) {
			if (fileData[ix] == avcC[0] && fileData[ix + 1] == avcC[1]
					&& fileData[ix + 2] == avcC[2]
					&& fileData[ix + 3] == avcC[3]) {
				// 找到avcC，则记录avcRecord起始位置，然后退出循环。
				avcRecord = ix + 4;
				break;
			}
		}
		if (0 == avcRecord) {
			System.out.println("没有找到avcC，请检查文件格式是否正确");
			return;
		}

		// 加7的目的是为了跳过
		// (1)8字节的 configurationVersion
		// (2)8字节的 AVCProfileIndication
		// (3)8字节的 profile_compatibility
		// (4)8 字节的 AVCLevelIndication
		// (5)6 bit 的 reserved
		// (6)2 bit 的 lengthSizeMinusOne
		// (7)3 bit 的 reserved
		// (8)5 bit 的numOfSequenceParameterSets
		// 共6个字节，然后到达sequenceParameterSetLength的位置
		int spsStartPos = avcRecord + 6;
		byte[] spsbt = new byte[] { fileData[spsStartPos],
				fileData[spsStartPos + 1] };
		int spsLength = bytes2Int(spsbt);
		byte[] SPS = new byte[spsLength];
		// 跳过2个字节的 sequenceParameterSetLength
		spsStartPos += 2;
		System.arraycopy(fileData, spsStartPos, SPS, 0, spsLength);
		printResult("SPS", SPS, spsLength);

		// 底下部分为获取PPS
		// spsStartPos + spsLength 可以跳到pps位置
		// 再加1的目的是跳过1字节的 numOfPictureParameterSets
		int ppsStartPos = spsStartPos + spsLength + 1;
		byte[] ppsbt = new byte[] { fileData[ppsStartPos],
				fileData[ppsStartPos + 1] };
		int ppsLength = bytes2Int(ppsbt);
		byte[] PPS = new byte[ppsLength];
		ppsStartPos += 2;
		System.arraycopy(fileData, ppsStartPos, PPS, 0, ppsLength);
		printResult("PPS", PPS, ppsLength);
	}

	private int bytes2Int(byte[] bt) {
		int ret = bt[0];
		ret <<= 8;
		ret |= bt[1];
		return ret;
	}

	private void printResult(String type, byte[] bt, int len) {
		System.out.println(type + "长度为：" + len);
		String cont = type + "的内容为：";
		System.out.print(cont);
		for (int ix = 0; ix < len; ++ix) {
			System.out.printf("%02x ", bt[ix]);
		}
		System.out.println("\n----------");
	}
}

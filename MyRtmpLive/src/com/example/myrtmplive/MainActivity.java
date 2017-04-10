package com.example.myrtmplive;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.net.Uri;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.MediaCodec.BufferInfo;
import android.os.Environment;
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
import android.widget.VideoView;

public class MainActivity extends Activity implements SurfaceHolder.Callback, PreviewCallback {
	private static final String TAG = "MainActivity"; 
	private int width=640, height=480;
	private byte[] buf;
	private Surface surface;
	private SurfaceView surfaceView;
	Camera camera = null;
	MediaCodec mediaCodec, mediaCodecd;
	
	byte keyFrame;
	boolean mp4fFlag;
	private byte[] m_info = null; 
	private byte[] yuv420 = new byte[width*height*3/2];
	private byte[] h264 = new byte[width*height*3/2]; 
	
	protected static AudioDecoderThread mAudioDecoder;
	
	private BufferedOutputStream outputStream;
	private static final String SAMPLE = Environment.getExternalStorageDirectory() + "/live.mp4";
	
	final String mp4Path = Environment.getExternalStorageDirectory() + "/H264AAC_001.mp4";
	
	String h264Path = Environment.getExternalStorageDirectory() + "/brazil-bq.h264";//testfly
	
	static {
		System.loadLibrary("rtmp");
		System.loadLibrary("rtmplive");
	}

	/*
	public native boolean Mp4Start(String pcm);
	public native void Mp4PackV(byte[] array, int length, int keyframe);
	public native void Mp4PackA(byte[] array, int length);
	public native void Mp4End();
	*/
	
	public static native void RtmpLive(String h264path);
	
	VideoView  videoView;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
    	setContentView(R.layout.activity_main);
    	 
        surfaceView = (SurfaceView)findViewById(R.id.mSurfaceview);
        //surfaceView = new SurfaceView(this);
		SurfaceHolder mSurfaceHolder = surfaceView.getHolder();
		//mSurfaceHolder.setFormat(PixelFormat.TRANSPARENT);//translucent半透明 transparent透明  
		mSurfaceHolder.addCallback(this);
		surface = surfaceView.getHolder().getSurface();
		//mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		
		//getActionBar().hide();
		//setContentView(surfaceView);
		
		if (Build.VERSION.SDK_INT < 20){ //Build.VERSION_CODES.LOLLIPOP) {
            // your code using Camera API here - is between 1-20
        } else if(Build.VERSION.SDK_INT >= 21){//Build.VERSION_CODES.LOLLIPOP) {
            // your code using Camera2 API here - is api 21 or higher
        }

		videoView = (VideoView)findViewById(R.id.mVideoView);
		
		Button playBtn = (Button) findViewById(R.id.playRtsp);
		playBtn.setOnClickListener(new OnClickListener() {
			@Override
		    public void onClick(View v){
				// Do something in response to button click
				//PlayRtspStream();
				
				new Thread() {
					@Override
					public void run() {
						Log.w(TAG, " isPlaying = "+h264Path);
						RtmpLive(h264Path);
					}

				}.start();
		    }
		});
            

        isSupportMediaCodecHardDecoder();
        getCodecs();
        /*
	    MediaCodecInfo[] mediaCodecInfo = getCodecs();
	    if(mediaCodecInfo.length>0){
	    	for(int i=0; i<mediaCodecInfo.length; i++){
	    		Log.i("Encoder", "selectCodec = "+ mediaCodecInfo[i].getName());
	    	}
	    } 
	    */
	}
    
	public static MediaCodecInfo[] getCodecs() {

	    //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
	    //    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
	    //    return mediaCodecList.getCodecInfos();
	    //} else {
	        int numCodecs = MediaCodecList.getCodecCount();
	        MediaCodecInfo[] mediaCodecInfo = new MediaCodecInfo[numCodecs];

	        for (int i = 0; i < numCodecs; i++) {
	            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
	            mediaCodecInfo[i] = codecInfo;
	            Log.i("Encoder", "selectCodec = "+ mediaCodecInfo[i].getName());
	        }

	        return mediaCodecInfo;
	    //}       
	}
    
    public boolean isSupportMediaCodecHardDecoder(){
	    boolean isHardcode = false;
	    //读取系统配置文件/system/etc/media_codecc.xml
	    File file = new File("/system/etc/media_codecs.xml");
	    InputStream inFile = null;
	    try {
	      inFile = new FileInputStream(file);
	    } catch (Exception e) {
	        // TODO: handle exception
	    }

	    if(inFile != null) { 
	        XmlPullParserFactory pullFactory;
	        try {
	            pullFactory = XmlPullParserFactory.newInstance();
	            XmlPullParser xmlPullParser = pullFactory.newPullParser();
	            xmlPullParser.setInput(inFile, "UTF-8");
	            int eventType = xmlPullParser.getEventType();
	            while (eventType != XmlPullParser.END_DOCUMENT) {
	                String tagName = xmlPullParser.getName();
	                switch (eventType) {
	                case XmlPullParser.START_TAG:
	                    //if ("MediaCodec".equals(tagName)) {
	                        String componentName = xmlPullParser.getAttributeValue(0);
	                        
	                        Log.i("MediaCodec", "MediaCodec = "+componentName);
	                        
	                        if(componentName.startsWith("OMX."))
	                        {
	                            if(!componentName.startsWith("OMX.google."))
	                            {
	                                isHardcode = true;
	                            }
	                        }
	                    //}
	                }
	                eventType = xmlPullParser.next();
	            }
	        } catch (Exception e) {
	            // TODO: handle exception
	        }
	    }
	    return isHardcode;
    }
    
    public void RtspPlayH264() {
		final String h264Path = Environment.getExternalStorageDirectory() + "/butterfly.h264";
		File file = new File(h264Path);
		if(file.exists()){
		    Log.w("MainActivity", "      h264 file is exists!   ");
		}

		int size = (int) file.length();

		System.out.println("h264Path =  " + size);
		if ("".equals(h264Path)) {
			Toast.makeText(this, "路径不能为空", 1).show();
			return;
		}

		new Thread() {
			@Override
			public void run() {
				Log.w("StreamerActivity", " isPlaying = "+h264Path);
				//RtspServer(h264Path);
				//pd.dismiss();
			}

		}.start();
	}
    
    public void PlayRtspStream(){
		//String rtspUrl = "rtsp://218.204.223.237:554/live/1/67A7572844E51A64/f68g2mj7wjua3la7.sdp";
		
		String sdp = "rtsp://192.168.1.5:8554/live";
		if(sdp != null){
		 //Create media controller
        //mMediaController = new MediaController(MainActivity.this);
        //videoView.setMediaController(mMediaController);
        
		videoView.setVideoURI(Uri.parse(sdp));
		videoView.requestFocus();
		
		Log.w("StreamerActivity2", "PlayRtspStream sdp = "+sdp);
		Toast.makeText(this, "sdp = "+sdp, 1).show();
		
		videoView.start();
		}
	}
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.i("Encoder", "--------------surfaceCreated--------------");
		//MediaCodecEncodeInit();
		//MediaCodecDecodeInit();
		
		//mp4fFlag = Mp4Start(mp4Path); //mp4init(mp4Path,0);
		//openCamera(holder);
		
		//mAudioDecoder = new AudioDecoderThread();
		//mAudioDecoder.startPlay(SAMPLE);
		
		//AudioEncoder();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		//releaseCamera();
		
		//mAudioDecoder.stop();
		//close();
		if(mp4fFlag){
		   //Mp4End();//mp4close();
		}
		
		//AACClose();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)  {
	    if (keyCode == KeyEvent.KEYCODE_BACK) { //按下的如果是BACK，同时没有重复
	      //do something here
	      finish();   
          //System.exit(0); //凡是非零都表示异常退出!0表示正常退出! 
	      return true;
	    }
	    return super.onKeyDown(keyCode, event);
	} 
	
	private MediaCodec mediaCodecAAC;
	private BufferedOutputStream outputStreamAAC;
	private String mediaTypeAAC = "audio/mp4a-latm";
	private AudioRecord recorder;
	private int bufferSize;
	private boolean isRunning, tmpBufferClear, isRecording;
	private ByteBuffer tmpInputBuffer;
	
	private boolean isRunning00, isRunning11;
	
	public void AudioEncoder() {
		 File f = new File(Environment.getExternalStorageDirectory(), "audioencoded712tt.aac");
		 try {
			 if(!f.exists()){
			    f.createNewFile();
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
		 
		 /*
		 bufferSize = AudioRecord.getMinBufferSize(44100,
				 AudioFormat.CHANNEL_IN_STEREO, 
				 AudioFormat.ENCODING_PCM_16BIT);
		 Log.e("AudioEncoder", "bufferSize = "+bufferSize);
		 Log.e("AudioEncoder", "bufferSize = "+bufferSize);
		 
		 recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 
				 44100, 
				 AudioFormat.CHANNEL_IN_STEREO,
				 AudioFormat.ENCODING_DEFAULT, 
				 bufferSize);
		 */
		 bufferSize = AudioRecord.getMinBufferSize(44100,
				 AudioFormat.CHANNEL_IN_MONO, 
				 AudioFormat.ENCODING_PCM_16BIT)*2;
		 recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 
		    		44100,
		    		AudioFormat.CHANNEL_IN_MONO,
		    		AudioFormat.ENCODING_PCM_16BIT, 
		    		bufferSize);		   
		 
		 Log.e("AudioEncoder", "bufferSize = "+bufferSize);
		 Log.e("AudioEncoder", "bufferSize = "+bufferSize);
		 
		 mediaCodecAAC = MediaCodec.createEncoderByType(mediaTypeAAC);
		 final int kSampleRates[] = { 8000, 11025, 22050, 44100, 48000 };
		 final int kBitRates[] = { 64000, 128000 };
		    
		 MediaFormat mediaFormat = MediaFormat.createAudioFormat(mediaTypeAAC,kSampleRates[3],1);
		 mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		 mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, kBitRates[1]);
		 mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
		 mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);//2
		 mediaFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
		 mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
		 mediaCodecAAC.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		 mediaCodecAAC.start();
		 
		 Log.i("Encoder", "--------------AudioEncoder--------------");
		 //doMediaRecordEncode();
		 /*
		 mediaCodecAAC = MediaCodec.createEncoderByType("audio/mp4a-latm");
		 MediaFormat format = new MediaFormat();
		 format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
		 format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024);
		 format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
		 format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
		 format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE);
		 mediaCodecAAC.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		 mediaCodecAAC.start();
		 */
		 
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
	public synchronized void AACEncoder(byte[] input, int length) {
	    try {
	    	ByteBuffer[] inputBuffers = mediaCodecAAC.getInputBuffers();
	 	    ByteBuffer[] outputBuffers = mediaCodecAAC.getOutputBuffers();
	 	    int inputBufferIndex = mediaCodecAAC.dequeueInputBuffer(0); //-1
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
		    	count_tt++;
		    	if(count_tt>=50){
		    		count_tt = 0;
		    	   Log.e("AudioEncoder", input.length + " is coming");
		    	}
		    	int outBitsSize = bufferInfo.size;
			    int outPacketSize = outBitsSize + 7; // 7 is ADTS size
			    
			    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
			
			    outputBuffer.position(bufferInfo.offset);
			    outputBuffer.limit(bufferInfo.offset + outBitsSize);
			
			    byte[] outData = new byte[outPacketSize];
			    addADTStoPacket(outData, outPacketSize);
			
			    outputBuffer.get(outData, 7, outBitsSize);
			    outputBuffer.position(bufferInfo.offset);
			    
			    //outputStreamAAC.write(outData, 0, outData.length);
			    //Log.e("AudioEncoder", outData.length + " bytes written");
			    
			    //Log.e("AudioEncoder", "AAC = "+outData.length );
			    if(mp4fFlag){
					//Mp4PackA(outData, outData.length);
				   //mp4packAudio(chunk, chunk.length);
				}
			
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
				Log.d(TAG, " wrote " + outPacketSize + " bytes into output file.");
			}
			else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
			}
			else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				codecOutputBuffers = codec.getOutputBuffers();
		    }
			*/

		    /*
		    //Without ADTS header
		    while (outputBufferIndex >= 0) {
			    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
			    byte[] outData = new byte[bufferInfo.size];
			    outputBuffer.get(outData);
			    outputStreamAAC.write(outData, 0, outData.length);
			    
			    Log.e("AudioEncoder", outData.length + " bytes written");
			    mediaCodecAAC.releaseOutputBuffer(outputBufferIndex, false);
			    outputBufferIndex = mediaCodecAAC.dequeueOutputBuffer(bufferInfo, 0);
		    }
		    */
	    } catch (Throwable t) {
	        t.printStackTrace();
	    }
	}
	
	private Camera getCamera(int cameraType) {
	    Camera camera = null;
	    try {
	    	Log.i("Encoder", "--------------getCamera start--------------");
	        camera = Camera.open(Camera.getNumberOfCameras()-1);
	        Log.i("Encoder", "--------------getCamera end--------------");	        
	        //printSupportPreviewSize(camera.getParameters());
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return camera; // returns null if camera is unavailable
	}
	
	private void openCamera(SurfaceHolder holder) {
	    releaseCamera();
	    try {
	            camera = getCamera(Camera.CameraInfo.CAMERA_FACING_BACK); // 根据需求选择前/后置摄像头
	        } catch (Exception e) {
	            camera = null;
	            e.printStackTrace();
	        }
	    if(camera != null){
	        try {
				camera.setPreviewDisplay(holder);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			camera.setDisplayOrientation(90); // 此方法为官方提供的旋转显示部分的方法，并不会影响onPreviewFrame方法中的原始数据；
			
			Camera.Parameters parameters = camera.getParameters();
			
			parameters.setPreviewSize(width, height); // 还可以设置很多相机的参数，但是建议先遍历当前相机是否支持该配置，不然可能会导致出错
			//parameters.getSupportedPreviewSizes();
			parameters.setFlashMode("off"); // 无闪光灯  
			parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO); 
			parameters.setPreviewFormat(ImageFormat.YV12); // 常用格式：NV21 / YV12
			parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);  
			//parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); 
			//parameters.set("orientation", "portrait");
			//parameters.set("orientation", "landscape");
			camera.setParameters(parameters);

			buf = new byte[width*height*3/2];
			camera.addCallbackBuffer(buf);
			camera.setPreviewCallbackWithBuffer(this);
			
			List<int[]> fpsRange = parameters.getSupportedPreviewFpsRange();
			for (int[] temp3 : fpsRange) {
			     System.out.println(Arrays.toString(temp3));
			}
			
			parameters.setPreviewFpsRange(10000, 30000);    
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

    /**
     * Returns a color format that is supported by the codec and by this test code.  If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     */
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

    /**
     * Returns true if this is a color format that this test code understands (i.e. we know how
     * to read and generate frames in this format).
     */
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
    
    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
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
		
		File f = new File(Environment.getExternalStorageDirectory(), "mediacodec_r0.264");
	    if(!f.exists()){	
	    	try {
				f.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }
	    try {
	        outputStream = new BufferedOutputStream(new FileOutputStream(f));
	        Log.i("Encoder", "outputStream initialized");
	    } catch (Exception e){ 
	        e.printStackTrace();
	    }
	    
	    int colorFormat = selectColorFormat(selectCodec("video/avc"), "video/avc");
		mediaCodec = MediaCodec.createEncoderByType(type);  
		MediaFormat mediaFormat = MediaFormat.createVideoFormat(type, width, height);  
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 250000);//125kbps  
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);  
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
		//mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, 
		//		MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);  //COLOR_FormatYUV420Planar
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5); //关键帧间隔时间 单位s  
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
	        outputStream.flush();
	        outputStream.close();
	        Log.i("Encoder", "--------------close--------------");
	    } catch (Exception e){ 
	        e.printStackTrace();
	    }
	}

	 //yv12 转 yuv420p  yvu -> yuv  
    private void swapYV12toI420(byte[] yv12bytes, byte[] i420bytes, int width, int height)   
    {        
        System.arraycopy(yv12bytes, 0, i420bytes, 0,width*height);  
        System.arraycopy(yv12bytes, width*height+width*height/4, i420bytes, width*height,width*height/4);  
        System.arraycopy(yv12bytes, width*height, i420bytes, width*height+width*height/4,width*height/4);    
    } 
    
    public int offerEncoder(byte[] input, byte[] output)   
    {     
        int pos = 0;
        keyFrame = 0;
        swapYV12toI420(input, output, width, height);  
        try {  
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();  
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();  
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);  
            if (inputBufferIndex >= 0)   
            {  
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
                  
                if(m_info != null) {                 
                    System.arraycopy(outData, 0,  output, pos, outData.length);  
                    pos += outData.length;    
                }  
                else //保存pps sps 只有开始时 第一个帧里有， 保存起来后面用  
                {  
                     ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);    
                     if (spsPpsBuffer.getInt() == 0x00000001)   
                     {   
                    	 Log.i("Encoder", "--------- pps sps found = "+outData.length+"---------");
                         m_info = new byte[outData.length];  
                         System.arraycopy(outData, 0, m_info, 0, outData.length); 
                         
                         int length = outData.length;
                         for (int ix = 0; ix < length; ++ix) {
                 			System.out.printf("%02x ", outData[ix]);
                 		 }
                 		 System.out.println("\n----------");
                 		 pos += outData.length;  
                     }   
                     else   
                     {    
                    	 Log.i("Encoder", "--------- no pps sps detect---------");
                         return -1;  
                     }        
                }  
                  
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);  
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);  
            }  
  
            if(output[4] == 0x65) //key frame   编码器生成关键帧时只有 00 00 00 01 65 没有pps sps， 要加上  
            {  
            	Log.i("Encoder", "-----------idr frame: "+output[4]+"-----------");
                System.arraycopy(output, 0,  yuv420, 0, pos);  
                System.arraycopy(m_info, 0,  output, 0, m_info.length);  
                System.arraycopy(yuv420, 0,  output, m_info.length, pos);  
                pos += m_info.length;  
                
                keyFrame = 1;
            }
            
              
        } catch (Throwable t) {  
            t.printStackTrace();  
        }  
  
        return pos;  
    }  
    
	// encode
	 public void onFrame(byte[] buf, int offset, int length, int flag) {	
		    swapYV12toI420(buf, h264, width, height); 
		    
		    ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
		    ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
		    int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
		    if (inputBufferIndex >= 0) {
		        ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
		        inputBuffer.clear();
		        //inputBuffer.put(buf, offset, length);
		        inputBuffer.put(h264, offset, length);
		        mediaCodec.queueInputBuffer(inputBufferIndex, 0, length, 0, 0);
		    }
		    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
		    int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
		    while (outputBufferIndex >= 0) {
		        ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

	            byte[] outData = new byte[bufferInfo.size];
	            outputBuffer.get(outData);
	            
	            keyFrame = 0;
	            if(mp4fFlag){
	            	  if(outData.length==21){
		    	    	   Log.i("Encoder", "--------- pps sps set---------");
		    	    	   //int length = outData.length;
	                       for (int ix = 0; ix < 21; ++ix) {
	                 			System.out.printf("%02x ", outData[ix]);
	                 	   }
	                 	   System.out.println("\n----------");
                            //00 00 00 01 67 42 80 1e e9 01 40 7b 20 00 00 00 01 68 ce 06 e2 
	                 		 
		    	    	   byte[] outData0 = new byte[13]; 
		    	    	   byte[] outData1 = new byte[8]; 
		                   System.arraycopy(outData, 0,  outData0, 0, 13);  
		                   System.arraycopy(outData, 13, outData1, 0, 8); 
		                   
		                   //Mp4PackV(outData0, 13, keyFrame);
		                   //Mp4PackV(outData1, 8, keyFrame);
		    	      }else{
		    	    	   if(outData[4] == 0x65) //key frame   编码器生成关键帧时只有 00 00 00 01 65 没有pps sps， 要加上  
		    	           { 
		    	    		   keyFrame = 1;
		    	    		   // Log.i("Encoder", "--------- key frame---------");
		    	           }
		    	    	   //Mp4PackV(outData, outData.length, keyFrame);
		    	      }
	            }
	            /*
	            try {
					outputStream.write(outData, 0, outData.length);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				*/ 
				// write into h264 file
	            //Log.i("Encoder", outData.length + " bytes written");
		        
	            //onFrame0(outData, 0, outData.length, flag);
		        //onFrame0(outputBuffer.array(), 0, bufferInfo.size, flag);
		        
		        mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
		        outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
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
	            mediaCodecd.queueInputBuffer(inputBufferIndex, 0, length,
	            		mCount * 1000000 / FRAME_RATE, 0);  
	            mCount++;  
	        }  
	  
	       MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();  
	       int outputBufferIndex = mediaCodecd.dequeueOutputBuffer(bufferInfo,0);  
	       while (outputBufferIndex >= 0) {  
	    	   /*
	    	   ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];  
	    	   byte[] outData = new byte[bufferInfo.size + 3];  
	    		        outputBuffer.get(outData, 3, bufferInfo.size);  
	    	   if (frameListener != null) {  
	    		     if ((outData[3]==0 && outData[4]==0 && outData[5]==1)  
	    		     || (outData[3]==0 && outData[4]==0 && outData[5]==0 && outData[6]==1))  
	    		     {  
	    		         frameListener.onFrame(outData, 3, outData.length-3, bufferInfo.flags);  
	    		     }  
	    		     else  
	    		     {  
	    		      outData[0] = 0;  
	    		      outData[1] = 0;  
	    		      outData[2] = 1;  
	    		         frameListener.onFrame(outData, 0, outData.length, bufferInfo.flags);  
	    		     }  
	    	   } 
	    		 */
	           mediaCodecd.releaseOutputBuffer(outputBufferIndex, true);  
	           outputBufferIndex = mediaCodecd.dequeueOutputBuffer(bufferInfo, 0);  
	       }  
	}
	
	 boolean firstFlag = true;
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		// TODO Auto-generated method stub
		 /*
		 int ret = offerEncoder(data,h264);  
         
	     if(ret > 0)  
	     {   	 
	    	 //try {
	    	    if(mp4fFlag){
	    	       if(firstFlag){
	    	    	  firstFlag = false;
	    	    	  Log.i("Encoder", "first frame = "+ret);
	    	       }
	    	       if(ret==21){
	    	    	   Log.i("Encoder", "--------- pps sps set---------");
	    	    	   byte[] outData0 = new byte[13]; 
	    	    	   byte[] outData1 = new byte[8]; 
	                   System.arraycopy(h264, 0,  outData0, 0, 13);  
	                   System.arraycopy(h264, 13, outData1, 0, 8); 
		               mp4packVideo(outData0, 13, keyFrame);
		               mp4packVideo(outData1, 8, keyFrame); 
	    	       }else{
	    	    	   mp4packVideo(h264, ret, keyFrame); 
	    	       }
		        }
	    	    
	    	    
				//outputStream.write(h264, 0, ret);
			 } catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			 } // write into h264 file
	         //Log.i("Encoder", ret + " bytes written");
	          
	     }
	     */
		
		 //onFrame(data, 0, data.length, 0); 
		 camera.addCallbackBuffer(buf);	
		 //Log.i("Encoder", "--------------------onPreviewFrame-----------------"+data.length);
	}
	
	public class AudioDecoderThread {
		private static final int TIMEOUT_US = 1000;
		private MediaExtractor mExtractor;
		private MediaCodec mDecoder;
		
		private boolean eosReceived;
		private int mSampleRate = 0;
		
		private BufferedOutputStream outputStreamAACp;
		
		/**
		 * 
		 * @param filePath
		 */
		public void startPlay(String path) {
			eosReceived = false;
			mExtractor = new MediaExtractor();
			mExtractor.setDataSource(path);

			int channel = 0;
			for (int i = 0; i < mExtractor.getTrackCount(); i++) {
				MediaFormat format = mExtractor.getTrackFormat(i);
				String mime = format.getString(MediaFormat.KEY_MIME);
				if (mime.startsWith("audio/")) {
					mExtractor.selectTrack(i);
					Log.d("TAG", "format : " + format);
					ByteBuffer csd = format.getByteBuffer("csd-0");
					
					if(csd != null){
					for (int k = 0; k < csd.capacity(); ++k) {
						Log.e("TAG", "00 csd : " + csd.array()[k]);
					}
					}
					mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
					channel = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
					break;
				}
			}
			//MediaCodecInfo.CodecProfileLevel.AACObjectLC  AACObjectHE
			MediaFormat format = makeAACCodecSpecificData(MediaCodecInfo.CodecProfileLevel.AACObjectLC, 
					mSampleRate, 
					channel);
			if (format == null)
				return;
			
			mDecoder = MediaCodec.createDecoderByType("audio/mp4a-latm");
			mDecoder.configure(format, null, null, 0);

			if (mDecoder == null) {
				Log.e("DecodeActivity", "Can't find video info!");
				return;
			}

			 File f = new File(Environment.getExternalStorageDirectory(), "aacpcm.pcm");
			 try {
				 if(!f.exists()){
				    f.createNewFile();
				 }
			 } catch (IOException e) {
				 e.printStackTrace();
			 }
			 
			 try {
			       outputStreamAACp = new BufferedOutputStream(new FileOutputStream(f));
			      Log.e("AudioEncoder", "outputStream initialized");
			 } catch (Exception e){
			      e.printStackTrace();
			 }
			 
			mDecoder.start();
		
			new Thread(AACDecoderAndPlayRunnable).start();
		}
		
		/**
		 * The code profile, Sample rate, channel Count is used to
		 * produce the AAC Codec SpecificData.
		 * Android 4.4.2/frameworks/av/media/libstagefright/avc_utils.cpp refer
		 * to the portion of the code written.
		 * 
		 * MPEG-4 Audio refer : http://wiki.multimedia.cx/index.php?title=MPEG-4_Audio#Audio_Specific_Config
		 * 
		 * @param audioProfile is MPEG-4 Audio Object Types
		 * @param sampleRate
		 * @param channelConfig
		 * @return MediaFormat
		 */
		private MediaFormat makeAACCodecSpecificData(int audioProfile, int sampleRate, int channelConfig) {
			MediaFormat format = new MediaFormat();
			format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
			format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
			format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelConfig);
			
		    int samplingFreq[] = {
		        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
		        16000, 12000, 11025, 8000
		    };
		    
		    // Search the Sampling Frequencies
		    int sampleIndex = -1;
		    for (int i = 0; i < samplingFreq.length; ++i) {
		    	if (samplingFreq[i] == sampleRate) {
		    		Log.d("TAG", "kSamplingFreq " + samplingFreq[i] + " i : " + i+" , channel = "+channelConfig);
		    		sampleIndex = i;
		    	}
		    }
		    
		    if (sampleIndex == -1) {
		    	return null;
		    }
		    
			ByteBuffer csd = ByteBuffer.allocate(2);
			csd.put((byte) ((audioProfile << 3) | (sampleIndex >> 1)));
			
			csd.position(1);
			csd.put((byte) ((byte) ((sampleIndex << 7) & 0x80) | (channelConfig << 3)));
			csd.flip();
			format.setByteBuffer("csd-0", csd); // add csd-0
			
			for (int k = 0; k < csd.capacity(); ++k) {
				Log.e("TAG", "11 csd : " + csd.array()[k]);
			}
			
			return format;
		}
		
		Runnable AACDecoderAndPlayRunnable = new Runnable() {
			
			@Override
			public void run() {
				AACDecoderAndPlay();
			}
		};

		/**
		 * After decoding AAC, Play using Audio Track.
		 */
		public void AACDecoderAndPlay() {
			ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();
			ByteBuffer[] outputBuffers = mDecoder.getOutputBuffers();
			
			BufferInfo info = new BufferInfo();
			
			int buffsize = AudioTrack.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
	        // create an audiotrack object
			AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate,
	                AudioFormat.CHANNEL_OUT_STEREO,
	                AudioFormat.ENCODING_PCM_16BIT,
	                buffsize,
	                AudioTrack.MODE_STREAM);
			audioTrack.play();
			
			while (!eosReceived) {
				int inIndex = mDecoder.dequeueInputBuffer(TIMEOUT_US);
				if (inIndex >= 0) {
					ByteBuffer buffer = inputBuffers[inIndex];
					int sampleSize = mExtractor.readSampleData(buffer, 0);
					if (sampleSize < 0) {
						// We shouldn't stop the playback at this point, just pass the EOS
						// flag to mDecoder, we will get it again from the
						// dequeueOutputBuffer
						Log.d("DecodeActivity", "InputBuffer BUFFER_FLAG_END_OF_STREAM");
						mDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
						
					} else {
						mDecoder.queueInputBuffer(inIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
						mExtractor.advance();
					}
					
					int outIndex = mDecoder.dequeueOutputBuffer(info, TIMEOUT_US);
					switch (outIndex) {
					case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
						Log.d("DecodeActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
						outputBuffers = mDecoder.getOutputBuffers();
						break;
						
					case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
						MediaFormat format = mDecoder.getOutputFormat();
						Log.d("DecodeActivity", "New format " + format);
						audioTrack.setPlaybackRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
						
						break;
					case MediaCodec.INFO_TRY_AGAIN_LATER:
						Log.d("DecodeActivity", "dequeueOutputBuffer timed out!");
						break;
						
					default:
						ByteBuffer outBuffer = outputBuffers[outIndex];
						//Log.v("DecodeActivity", "We can't use this buffer but render it due to the API limit, " + outBuffer);
						
						final byte[] chunk = new byte[info.size];
						outBuffer.get(chunk); // Read the buffer all at once
						outBuffer.clear(); // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN
						
						/*
						try {
	                    	outputStreamAACp.write(chunk, 0, chunk.length);
	                    } 
	                    catch (IOException e) {
	                        e.printStackTrace();
	                    }
						*/
						if(mp4fFlag){
							//Mp4PackA(chunk, chunk.length);
						   //mp4packAudio(chunk, chunk.length);
						}
						 
						audioTrack.write(chunk, info.offset, info.offset + info.size); // AudioTrack write data
						mDecoder.releaseOutputBuffer(outIndex, false);
						break;
					}
					
					// All decoded frames have been rendered, we can stop playing now
					if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
						Log.d("DecodeActivity", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
						
						try {
							outputStreamAACp.flush();
							outputStreamAACp.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						break;
					}
				}
			}
			
			mDecoder.stop();
			mDecoder.release();
			mDecoder = null;
			
			mExtractor.release();
			mExtractor = null;
			
			audioTrack.stop();
			audioTrack.release();
			audioTrack = null;
		}
		
		public void stop() {
			eosReceived = true;
		}
	}
	
	
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


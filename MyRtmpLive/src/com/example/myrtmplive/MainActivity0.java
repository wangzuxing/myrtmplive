package com.example.myrtmplive;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.AssetFileDescriptor;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.MediaCodec.BufferInfo;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.util.Pair;
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
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity0 extends Activity implements SurfaceHolder.Callback, PreviewCallback {

	private int width=320, height=240; 
	private byte[] buf;
	private Surface surface;
	private SurfaceView surfaceView;
	Camera camera = null;
	MediaCodec mediaCodec, mediaCodec0, mediaCodecd;
	
	//vlc:  udp://@:5000
	private UdpSendTask netSendTask;
	
	private BufferedOutputStream outputStream;
	AssetFileDescriptor assetFileDescriptor;// = getResources().openRawResourceFd(R.raw.live);
	private static final String SAMPLE = Environment.getExternalStorageDirectory() + "/live.mp4";
	private PlayerThread mPlayer = null;
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.activity_main0);
		
		//surfaceView = new SurfaceView(this);
		surfaceView = (SurfaceView) findViewById(R.id.mSurfaceview);
		SurfaceHolder mSurfaceHolder = surfaceView.getHolder();
		//mSurfaceHolder.setFormat(PixelFormat.TRANSPARENT);//translucent��͸�� transparent͸��  
		mSurfaceHolder.addCallback(this);
		surface = surfaceView.getHolder().getSurface();
		//mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		//requestWindowFeature(Window.FEATURE_NO_TITLE);
		//getActionBar().hide();
		//setContentView(surfaceView);
		
		Button playUdp = (Button) findViewById(R.id.playUdp);
		playUdp.setOnClickListener(new OnClickListener() {
			@Override
		    public void onClick(View v){
				// Do something in response to button click
				showAddrDialog(v);
		    }
		});
		
		//assetFileDescriptor = getResources().openRawResourceFd(R.raw.live);
	}
	
	static boolean runFlag = false;
	private void showAddrDialog(final View v) {
		if(netSendTask != null){
			netSendTask.end();
		}
		secondFlag = true;
        threeFlag  = false;
		runFlag = true;
		UdpSendTask.running = false;
        final EditText input = new EditText(this);
        input.setText("192.168.1.9");//SDP address: "
        new AlertDialog.Builder(this)
                .setTitle("Destination IP: *.*.*.*")
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String addr = input.getText().toString();
                        
                        Log.w("StreamerActivity", "ip = "+addr);
                        //addr = "192.168.1.102";
                        //if (addr.matches("^([1-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])){3}$")) 
                        if (addr.matches("^([1-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])){3}$")) 
                        {
                        	//Log.w("StreamerActivity", "00 ip = "+addr);
                        	MainActivity0.REMOTE_HOST = addr;
                        	//Toast.makeText(MainActivity.this, addr, Toast.LENGTH_SHORT).show();
                            //((Button) v).setText("Stop");
                        	//REMOTE_HOST = addr;
                        	netSendTask = new UdpSendTask();
                    		netSendTask.init();
                    		netSendTask.start();
                    		
                        } else {
                            Toast.makeText(MainActivity0.this, "Check IP!", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.i("Encoder", "--------------surfaceCreated--------------");
		showCodecCapabilities();
		
		selectCodecs();
		
		MediaCodecEncodeInit();
		MediaCodecDecodeInit();
		openCamera(holder);
		
		//encodeDecodeVideoFile();
		
		/*
		if (mPlayer == null) {
			mPlayer = new PlayerThread(holder.getSurface());
			mPlayer.start();
		}
		*/
		
		//AudioEncoder();
	}


    public boolean isSupportMediaCodecHardDecoder(){
    boolean isHardcode = false;
    //��ȡϵͳ�����ļ�/system/etc/media_codecc.xml
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
                    if ("MediaCodec".equals(tagName)) {
                        String componentName = xmlPullParser.getAttributeValue(0);
                        
                        Log.i("MediaCodec", "MediaCodec = "+componentName);
                        
                        if(componentName.startsWith("OMX."))
                        {
                            if(!componentName.startsWith("OMX.google."))
                            {
                                isHardcode = true;
                            }
                        }
                    }
                }
                eventType = xmlPullParser.next();
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
    return isHardcode;
    }

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.i("Encoder", "--------------surfaceDestroyed--------------");
		
		/*
		if (mPlayer != null) {
			mPlayer.interrupt();
		}
		*/
		
		releaseCamera();
		//AACClose();
		close();
		netSendTask.end();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)  {
	    if (keyCode == KeyEvent.KEYCODE_BACK) { //���µ������BACK��ͬʱû���ظ�
	      //do something here
	      finish();   
          //System.exit(0); //���Ƿ��㶼��ʾ�쳣�˳�!0��ʾ�����˳�! 
	      return true;
	    }
	    return super.onKeyDown(keyCode, event);
	}
	
	private static MediaCodecInfo selectCodecs() {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                Log.i("Encoder", "codec types = "+codecInfo.getName());
            }
        }
        return null;
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
	 
	public void AACClose_0() {
		try {
		   Log.i("Encoder", "--------------AACClose--------------");
		   isRecording = false;
		   isRunning = false;
		   new Thread() {
		        public void run() {
		        	while(!isRunning00 || !isRunning11){
		        		try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
		        	}
		        	mediaCodecAAC.stop();
		      		mediaCodecAAC.release();
		      		try {
						outputStreamAAC.flush();
						outputStreamAAC.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		      		
		            Log.i("Encoder", "--------------end end--------------");
		        }
		    }.start();
		   /*
		   mediaCodecAAC.stop();
		   mediaCodecAAC.release();
		   outputStreamAAC.flush();
		   outputStreamAAC.close();
		   */
		} catch (Exception e){
		   e.printStackTrace();
		}
    }
	
	public void add(ByteBuffer input) {
	    if (!isRunning)
	        return; 

	    if (tmpInputBuffer == null)
	        tmpInputBuffer = ByteBuffer.allocate(input.capacity());

	    if (!tmpBufferClear)
	        Log.e("audio encoder", "deadline missed"); //TODO lower bit rate

	    synchronized (tmpInputBuffer) {
	        tmpInputBuffer.clear();
	        tmpInputBuffer.put(input);
	        tmpInputBuffer.notifyAll();
	        Log.d("audio encoder", "pushed data into tmpInputBuffer");
	    }
	}
	
	public void doMediaRecordEncode(){
		isRunning = true;
		isRecording = true;
		isRunning00 = false;
		isRunning00 = false;
		
		new Thread() {
		    public void run() {
		        while (isRunning) {
		            if (tmpInputBuffer == null) continue;
		            synchronized (tmpInputBuffer) 
		            {
		                if (tmpBufferClear) {
		                    try {
		                        //Log.d("audio encoder", "falling asleep");
		                        tmpInputBuffer.wait(); //wait when no input is available
		                    } 
		                    catch (InterruptedException e) {
		                        e.printStackTrace();
		                    }
		                }

		                ByteBuffer[] inputBuffers = mediaCodecAAC.getInputBuffers();
		                int inputBufferIndex;
		                do
		                    inputBufferIndex = mediaCodecAAC.dequeueInputBuffer(-1);
		                while (inputBufferIndex < 0);
		                
		                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
		                inputBuffer.clear();
		                //Log.d("input buffer size", String.valueOf(inputBuffer.capacity()));
		                //Log.d("tmp input buffer size", String.valueOf(tmpInputBuffer.capacity()));
		                inputBuffer.put(tmpInputBuffer.array());
		                tmpInputBuffer.clear();
		                mediaCodecAAC.queueInputBuffer(inputBufferIndex, 0, tmpInputBuffer.capacity(), 0, 0);
		                tmpBufferClear = true;
		                //Log.d("audio encoder", "added to input buffer");
		            }
		        }
		        isRunning11 = true;
		        Log.i("Encoder", "--------------end 00--------------");
		    }
		}.start();
		
	    new Thread() {
	    	 public void run() {
		            while (isRunning) {
		                ByteBuffer[] outputBuffers = mediaCodecAAC.getOutputBuffers();
		                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
		                int outputBufferIndex = mediaCodecAAC.dequeueOutputBuffer(bufferInfo, -1);
		                
		                while (outputBufferIndex >= 0) {
		                	/*
		                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
		                    byte[] outData = new byte[bufferInfo.size];
		                    outputBuffer.get(outData);
                            */
		                    int outBitsSize = bufferInfo.size;
		    			    int outPacketSize = outBitsSize + 7; // 7 is ADTS size
		    			    
		    			    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
		    			
		    			    outputBuffer.position(bufferInfo.offset);
		    			    outputBuffer.limit(bufferInfo.offset + outBitsSize);
		    			
		    			    byte[] outData = new byte[outPacketSize];
		    			    addADTStoPacket(outData, outPacketSize);
		    			
		    			    outputBuffer.get(outData, 7, outBitsSize);
		    			    outputBuffer.position(bufferInfo.offset);
		    			    
		                    try {
		                    	outputStreamAAC.write(outData, 0, outData.length);
		                    } 
		                    catch (IOException e) {
		                        e.printStackTrace();
		                    }
		                    mediaCodecAAC.releaseOutputBuffer(outputBufferIndex, false);
		                    outputBufferIndex = mediaCodecAAC.dequeueOutputBuffer(bufferInfo, 0);
		                    //Log.d("audio encoder", "removed from output buffer");
		                }
		            }
		            isRunning00 = true;
		            /*
		            mediaCodecAAC.stop();
		            mediaCodecAAC.release();
		            try {
		            	outputStreamAAC.flush();
		            	outputStreamAAC.close();
		            } catch (IOException e) {
		                e.printStackTrace();
		            }
		            */
		            Log.i("Encoder", "--------------end 11--------------");
		     }   
		}.start();
		
		new Thread() {
	        public void run() {
	            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);
	            int read = 0;
	            while (isRecording) {
	                read = recorder.read(byteBuffer, bufferSize);
	                if(AudioRecord.ERROR_INVALID_OPERATION != read){
	                    add(byteBuffer);
	                }
	            }
	            recorder.stop();
	            Log.i("Encoder", "--------------end 22--------------");
	        }
	    }.start();
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
	// called AudioRecord's read
	public synchronized void AACEncoder(byte[] input, int length) {
	    //Log.e("AudioEncoder", input.length + " is coming");
	
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
			    
			    outputStreamAAC.write(outData, 0, outData.length);
			    Log.e("AudioEncoder", outData.length + " bytes written");
			
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
	 
	/*
	private static void testEncoder(String componentName, MediaFormat format, Context c) {
		String TAG = "Encoder";
		final boolean VERBOSE = true;
		MediaMuxer mMediaMuxer;
	    int trackIndex = 0;
	    boolean mMuxerStarted = false;
	    File f = FileUtils.createTempFileInRootAppStorage(c, "aac_test_" + new Date().getTime() + ".mp4");
	    MediaCodec codec = MediaCodec.createByCodecName(componentName);

	    try {
	        codec.configure(
	                format,
	                null // surface 
	                null // crypto 
	                MediaCodec.CONFIGURE_FLAG_ENCODE);
	    } catch (IllegalStateException e) {
	        Log.e(TAG, "codec '" + componentName + "' failed configuration.");

	    }

	    codec.start();

	    try {
	        mMediaMuxer = new MediaMuxer(f.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
	    } catch (IOException ioe) {
	        throw new RuntimeException("MediaMuxer creation failed", ioe);
	    }

	    ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
	    ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

	    int kNumInputBytes;
	    int kTimeoutUs;
	    int numBytesSubmitted = 0;
	    boolean doneSubmittingInput = false;
	    int numBytesDequeued = 0;

	    while (true) {
	        int index;

	        if (!doneSubmittingInput) {
	            index = codec.dequeueInputBuffer(kTimeoutUs); // timeoutUs 

	            if (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
	                if (numBytesSubmitted >= kNumInputBytes) {
	                    Log.i(TAG, "queueing EOS to inputBuffer");
	                    codec.queueInputBuffer(
	                            index,
	                            0 // offset
	                            0 // size 
	                            0 // timeUs 
	                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);

	                    if (VERBOSE) {
	                        Log.d(TAG, "queued input EOS.");
	                    }

	                    doneSubmittingInput = true;
	                } else {
	                    int size = queueInputBuffer(
	                            codec, codecInputBuffers, index);

	                    numBytesSubmitted += size;

	                    if (VERBOSE) {
	                        Log.d(TAG, "queued " + size + " bytes of input data.");
	                    }
	                }
	            }
	        }

	        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
	        index = codec.dequeueOutputBuffer(info, kTimeoutUs); // timeoutUs 

	        if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
	        } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
	            MediaFormat newFormat = codec.getOutputFormat();
	            trackIndex = mMediaMuxer.addTrack(newFormat);
	            mMediaMuxer.start();
	            mMuxerStarted = true;
	        } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
	            codecOutputBuffers = codec.getOutputBuffers();
	        } else {
	            // Write to muxer
	            ByteBuffer encodedData = codecOutputBuffers[index];
	            if (encodedData == null) {
	                throw new RuntimeException("encoderOutputBuffer " + index +
	                        " was null");
	            }

	            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
	                // The codec config data was pulled out and fed to the muxer when we got
	                // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
	                if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
	                info.size = 0;
	            }

	            if (info.size != 0) {
	                if (!mMuxerStarted) {
	                    throw new RuntimeException("muxer hasn't started");
	                }

	                // adjust the ByteBuffer values to match BufferInfo (not needed?)
	                encodedData.position(info.offset);
	                encodedData.limit(info.offset + info.size);

	                mMediaMuxer.writeSampleData(trackIndex, encodedData, info);
	                if (VERBOSE) Log.d(TAG, "sent " + info.size + " audio bytes to muxer with pts " + info.presentationTimeUs);
	            }

	            codec.releaseOutputBuffer(index, false);

	            // End write to muxer
	            numBytesDequeued += info.size;

	            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
	                if (VERBOSE) {
	                    Log.d(TAG, "dequeued output EOS.");
	                }
	                break;
	            }

	            if (VERBOSE) {
	                Log.d(TAG, "dequeued " + info.size + " bytes of output data.");
	            }
	        }
	    }

	    if (VERBOSE) {
	        Log.d(TAG, "queued a total of " + numBytesSubmitted + "bytes, "
	                + "dequeued " + numBytesDequeued + " bytes.");
	    }

	    int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
	    int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
	    int inBitrate = sampleRate * channelCount * 16;  // bit/sec
	    int outBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);

	    float desiredRatio = (float)outBitrate / (float)inBitrate;
	    float actualRatio = (float)numBytesDequeued / (float)numBytesSubmitted;

	    if (actualRatio < 0.9 * desiredRatio || actualRatio > 1.1 * desiredRatio) {
	        Log.w(TAG, "desiredRatio = " + desiredRatio
	                + ", actualRatio = " + actualRatio);
	    }


	    codec.release();
	    mMediaMuxer.stop();
	    mMediaMuxer.release();
	    codec = null;
	}
	*/

	public  void printSupportPreviewSize(Camera.Parameters params){  
        List<Size> previewSizes = params.getSupportedPreviewSizes();  
        for(int i=0; i< previewSizes.size(); i++){  
            Size size = previewSizes.get(i);  
            Log.i("Encoder", "previewSizes:width = "+size.width+" height = "+size.height);  
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
	            camera = getCamera(Camera.CameraInfo.CAMERA_FACING_BACK); // ��������ѡ��ǰ/��������ͷ
	        } catch (Exception e) {
	            camera = null;
	            e.printStackTrace();
	        }
	    if(camera != null){
	    	/*
	        try {
				camera.setPreviewDisplay(holder);// ʹ��UDP to VLC ʱ����ע�ͣ��Ա�Ӱ�촫������
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        */
	    	Log.i("Encoder", "--------------openCamera 00--------------");
			camera.setDisplayOrientation(90); // �˷���Ϊ�ٷ��ṩ����ת��ʾ���ֵķ�����������Ӱ��onPreviewFrame�����е�ԭʼ���ݣ�
			
			Camera.Parameters parameters = camera.getParameters();
			
			parameters.setPreviewSize(width, height); // ���������úܶ�����Ĳ��������ǽ����ȱ�����ǰ����Ƿ�֧�ָ����ã���Ȼ���ܻᵼ�³���
			//parameters.getSupportedPreviewSizes();
			parameters.setFlashMode("off"); // �������  
			parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO); 
			parameters.setPreviewFormat(ImageFormat.YV12); // ���ø�ʽ��NV21 / YV12
			parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);  
			//parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); 
			//parameters.set("orientation", "portrait");
			//parameters.set("orientation", "landscape");
			Log.i("Encoder", "--------------openCamera 11--------------");
			camera.setParameters(parameters);
			Log.i("Encoder", "--------------openCamera 22--------------");
			
            /*
			 camera.setPreviewDisplay(holder);  
			 Camera.Parameters parameters = camera.getParameters();  
			 parameters.setPreviewSize(width, height);  
			 // parameters.setPictureSize(width, height);  
			 parameters.setPreviewFormat(ImageFormat.YV12);  
			 camera.setParameters(parameters);   
			 camera.setPreviewCallback(this);  
			 camera.startPreview();  
         */
			
			buf = new byte[width*height*3/2];
			camera.addCallbackBuffer(buf);
			camera.setPreviewCallbackWithBuffer(this);
			Log.i("Encoder", "--------------openCamera 33--------------");
			
			List<int[]> fpsRange = parameters.getSupportedPreviewFpsRange();
			for (int[] temp3 : fpsRange) {
			     System.out.println(Arrays.toString(temp3));
			}
			Log.i("Encoder", "--------------openCamera 44--------------");
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
	
	/*
	private static synchronized Pair<MediaCodecInfo, CodecCapabilities> getMediaCodecInfo(
		    String mimeType) {
		  String mimeType0 = "video/avc";
		  Pair<MediaCodecInfo, CodecCapabilities> result = codecs.get(mimeType0);
		  if (result != null) {
		    return result;
		  }
		  int numberOfCodecs = MediaCodecList.getCodecCount();
		  // Note: MediaCodecList is sorted by the framework such that the best decoders come first.
		  for (int i = 0; i < numberOfCodecs; i++) {
		    MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
		    String codecName = info.getName();
		    if (!info.isEncoder() && isOmxCodec(codecName)) {
		      String[] supportedTypes = info.getSupportedTypes();
		      for (int j = 0; j < supportedTypes.length; j++) {
		        String supportedType = supportedTypes[j];
		        if (supportedType.equalsIgnoreCase(mimeType)) {
		          result = Pair.create(info, info.getCapabilitiesForType(supportedType));
		          codecs.put(mimeType, result);
		          return result;
		        }
		      }
		    }
		  }
		  return null;
	}
	*/
	
	public static void YUV420PtoYUV420PackedsemiPlanar_00(byte[] input, byte[] output, int width, int height) {
        /* 
         * COLOR_TI_FormatYUV420PackedSemiPlanar is NV12
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize/4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i*2] = input[frameSize + i]; // Cb (U)
            output[frameSize + i*2 + 1] = input[frameSize + i + qFrameSize]; // Cr (V)
        }
	}
	
	private byte[] h265;
	private byte[] outData_0;
	
	private class PlayerThread extends Thread {
		private MediaExtractor extractor;
		private MediaCodec decoder, mediaCodec;
		private Surface surface;
		private String type = "video/avc";
		private int count;
		// = new byte[width*height*3/2];
		
		public PlayerThread(Surface surface) {
			this.surface = surface;
			
			File f = new File(Environment.getExternalStorageDirectory(), "mediacodec_2.264");
		    if(!f.exists()){	
		    	try {
					f.createNewFile();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    }
		    //else{
		    //	f.delete();
		    //}
		    
		    try {
		        outputStream = new BufferedOutputStream(new FileOutputStream(f));
		        Log.i("Encoder", "outputStream initialized");
		    } catch (Exception e){ 
		        e.printStackTrace();
		    }
		
		}

		@Override
		public void run() {
			extractor = new MediaExtractor();
			//extractor.setDataSource(SAMPLE);
			
			extractor.setDataSource(assetFileDescriptor.getFileDescriptor(),
					assetFileDescriptor.getStartOffset(),
					assetFileDescriptor.getLength());

			for (int i = 0; i < extractor.getTrackCount(); i++) {
				MediaFormat format = extractor.getTrackFormat(i);
				String mime = format.getString(MediaFormat.KEY_MIME);
				//Log.e("DecodeActivity", "color format = "+format.getInteger(MediaFormat.KEY_COLOR_FORMAT));
				if (mime.startsWith("video/")) {
					width = format.getInteger(MediaFormat.KEY_WIDTH);
					height = format.getInteger(MediaFormat.KEY_HEIGHT);
					extractor.selectTrack(i);
					decoder = MediaCodec.createDecoderByType(mime);
					//
					decoder.configure(format, surface, null, 0);
					break;
				}
			}

			if (decoder == null) {
				Log.e("PlayerThread", "Can't find video info!");
				return;
			}

			mediaCodec = MediaCodec.createEncoderByType(type);  
			MediaFormat mediaFormat = MediaFormat.createVideoFormat(type, width, height);  
			mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 250000);//125kbps  
			mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);  
			mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, 
					MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);  //COLOR_FormatYUV420Planar
			mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5); //�ؼ�֡���ʱ�� ��λs  
			mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);  
			mediaCodec.start();	
			Log.i("PlayerThread", "--------------MediaCodecEncodeInit--------------");
			
			h265 = new byte[width*height*3/2];
			outData_0 = new byte[width*height*3/2];
			count = 0;
			decoder.start();

			ByteBuffer[] inputBuffers = decoder.getInputBuffers();
			ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
			BufferInfo info = new BufferInfo();
			boolean isEOS = false;
			long startMs = System.currentTimeMillis();

			while (!Thread.interrupted()) {
				if (!isEOS) {
					int inIndex = decoder.dequeueInputBuffer(10000);
					if (inIndex >= 0) {
						ByteBuffer buffer = inputBuffers[inIndex];
						int sampleSize = extractor.readSampleData(buffer, 0);
						if (sampleSize < 0) {
							// We shouldn't stop the playback at this point, just pass the EOS flag to decoder, we will get it again from the dequeueOutputBuffer
							Log.d("PlayerThread", "InputBuffer BUFFER_FLAG_END_OF_STREAM");
							decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
							isEOS = true;
						} else {
							decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
							extractor.advance();
						}
					}
				}

				int outIndex = decoder.dequeueOutputBuffer(info, 0); //10000
				switch (outIndex) {
				case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
					Log.d("PlayerThread", "INFO_OUTPUT_BUFFERS_CHANGED");
					outputBuffers = decoder.getOutputBuffers();
					break;
				case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
					Log.d("PlayerThread", "New format " + decoder.getOutputFormat());
					break;
				case MediaCodec.INFO_TRY_AGAIN_LATER:
					Log.d("PlayerThread", "dequeueOutputBuffer timed out!");
					break;
				default:
					ByteBuffer buffer = outputBuffers[outIndex];
					
					buffer.position(info.offset);
					buffer.limit(info.offset + info.size);
			            
					//byte[] outData = new byte[info.size];
					
				    Log.i("PlayerThread", "outIndex = "+outIndex+" info.size = "+info.size);

				    
					if(info.size > 0){
					        buffer.get(outData_0);
						    Log.i("PlayerThread", "--------------outData.length = "+outData_0.length+"--------------");
						    YUV420PtoYUV420PackedsemiPlanar_00(outData_0,h265,width,height);
							
							ByteBuffer[] inputBuffers0 = mediaCodec.getInputBuffers();
						    ByteBuffer[] outputBuffers0 = mediaCodec.getOutputBuffers();
						    int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
						    if (inputBufferIndex >= 0) {
						        ByteBuffer inputBuffer = inputBuffers0[inputBufferIndex];
						        inputBuffer.clear();
						        //inputBuffer.put(buffer.array());
						        inputBuffer.put(h264, 0, h264.length);
						        mediaCodec.queueInputBuffer(inputBufferIndex, 0, h264.length, 0, 0);// 
						    }
						    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
						    int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
						    while (outputBufferIndex >= 0) {
						        ByteBuffer outputBuffer = outputBuffers0[outputBufferIndex];

					            byte[] outData0 = new byte[bufferInfo.size];
					            outputBuffer.get(outData0);
					            
					            try {
									outputStream.write(outData0, 0, outData0.length);
									//outputStream.write(outputBuffer.array(), 0, bufferInfo.size);
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} // write into h264 file
					            
					            count++;
					            if(count > 5){
					                Log.i("PlayerThread", outData0.length + " bytes written");
					            }
				                
					            
						        //if (frameListener != null)//ȡѹ���õ�����ι��������
						        //    frameListener.onFrame(outputBuffer, 0, length, flag);
						        //onFrame0(outData, 0, outData.length, flag);
					            
						        mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
						        outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
						    }
					  
					}
					//Log.v("DecodeActivity", "We can't use this buffer but render it due to the API limit, " + buffer);

					// We use a very simple clock to keep the video FPS, or the video playback will be too fast
					while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
						try {
							sleep(10);
						} catch (InterruptedException e) {
							e.printStackTrace();
							break;
						}
					}
					decoder.releaseOutputBuffer(outIndex, true);
					break;
				}

				// All decoded frames have been rendered, we can stop playing now
				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					Log.d("PlayerThread", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
					break;
				}
			}
			
		    mediaCodec.stop();
		    mediaCodec.release();
		    try {
				outputStream.flush();
				outputStream.close();
		    } catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}   

			decoder.stop();
			decoder.release();
			extractor.release();
		}
	}
	//AssetFileDescriptor assetFileDescriptor
	public void encodeDecodeVideoFile() {
		AssetFileDescriptor assetFileDescriptor;// = this.getResources().openRawResourceFd(R.raw.live);
	    //FileInputStream in = new FileInputStream(afd.getFileDescriptor());
	    //in.skip(afd.getStartOffset());  
	        
        final String TAG = "EncodeVideo";
	    int bitRate = 500000;//500kbps
	    int frameRate = 15;
	    int width = 480;
	    int height = 368;
	    String mimeType = "video/avc";

	    MediaCodec encoder, decoder = null;
	    ByteBuffer[] encoderInputBuffers;
	    ByteBuffer[] encoderOutputBuffers;
	    ByteBuffer[] decoderInputBuffers = null;
	    ByteBuffer[] decoderOutputBuffers = null;

	    // Find a code that supports the mime type
	    int numCodecs = MediaCodecList.getCodecCount();
	    MediaCodecInfo codecInfo = null;
	    for (int i = 0; i < numCodecs && codecInfo == null; i++) {
	        MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
	        if (!info.isEncoder()) {
	            continue;
	        }
	        String[] types = info.getSupportedTypes();
	        Log.d(TAG, "codecInfo.getName() =  " + info.getName());
	        boolean found = false;
	        for (int j = 0; j < types.length && !found; j++) {
	            if (types[j].equals(mimeType)){
	                found = true;
	                codecInfo = info;
	                Log.d(TAG, "Found " + codecInfo.getName());
	            }
	        }
	        if (!found)
	            continue;
	        
	    }
	    Log.d(TAG, "Found " + codecInfo.getName() + " supporting " + mimeType);

	    // Find a color profile that the codec supports
	    int colorFormat = 0;
	    MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
	    for (int i = 0; i < capabilities.colorFormats.length && colorFormat == 0; i++) {
	        int format = capabilities.colorFormats[i];
	        switch (format) {
	        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
	        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
	        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
	        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
	        case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
	            colorFormat = format;
	            break;
	        default:
	            Log.i(TAG, "Skipping unsupported color format " + format);
	            break;
	        }
	    }
	    Log.i(TAG, "Using color format " + colorFormat);

	    // Determine width, height and slice sizes
	    if (codecInfo.getName().equals("OMX.TI.DUCATI1.VIDEO.H264E")) {
	        // This codec doesn't support a width not a multiple of 16,
	        // so round down.
	        width &= ~15;
	    }
	    int stride = width;
	    int sliceHeight = height;
	    if (codecInfo.getName().startsWith("OMX.Nvidia.")) {
	        stride = (stride + 15) / 16 * 16;
	        sliceHeight = (sliceHeight + 15) / 16 * 16;
	    }

	    // Used MediaExtractor to select the first track from the h.264 content
	    MediaExtractor extractor  = new MediaExtractor();
	    
		//extractor.setDataSource(assetFileDescriptor.getFileDescriptor(),
		//			assetFileDescriptor.getStartOffset(),
		//			assetFileDescriptor.getLength());
			
	    MediaFormat extractedFormat = extractor.getTrackFormat(0);
	    String mime = extractedFormat.getString(MediaFormat.KEY_MIME);
	    Log.d(TAG, "Extartced Mime " + mime);
	    extractor.selectTrack(0);

	    //createDecoderByType����������������PAD��API LEVEL 18�� Android version 4.3������ʱ���֣�
	    //���ø÷���������Ƶ������ʱ������ʸ�Ϊʹ��createByCodecName("OMX.SEC.aac.enc")������Ƶ������
	    
	    // Create an encoder
	    encoder = MediaCodec.createByCodecName(codecInfo.getName());
	    MediaFormat inputFormat = MediaFormat.createVideoFormat(mimeType, width, height);
	    inputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
	    inputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
	    inputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
	    inputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
	    inputFormat.setInteger("stride", stride);
	    inputFormat.setInteger("slice-height", sliceHeight);
	    Log.d(TAG, "Configuring encoder with input format " + inputFormat);
	    encoder.configure(inputFormat, null /* surface */, null /* crypto */, MediaCodec.CONFIGURE_FLAG_ENCODE);
	    encoder.start();
	    encoderInputBuffers = encoder.getInputBuffers();
	    encoderOutputBuffers = encoder.getOutputBuffers();

	    // start encoding + decoding
	    final long kTimeOutUs = 5000;
	    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
	    boolean sawInputEOS = false;
	    boolean sawOutputEOS = false;
	    MediaFormat oformat = null;
	    long startMs = System.currentTimeMillis();
	    while (!sawOutputEOS) {
	        if (!sawInputEOS) {
	            int inputBufIndex = encoder.dequeueInputBuffer(kTimeOutUs);

	            if (inputBufIndex >= 0) {

	                ByteBuffer dstBuf = encoderInputBuffers[inputBufIndex];

	                int sampleSize = extractor.readSampleData(dstBuf, 0 /* offset */);

	                long presentationTimeUs = 0;

	                if (sampleSize < 0) {
	                    Log.d(TAG, "saw input EOS.");
	                    sawInputEOS = true;
	                    sampleSize = 0;
	                } else {
	                    presentationTimeUs = extractor.getSampleTime();
	                }

	                encoder.queueInputBuffer(inputBufIndex, 0 /* offset */, sampleSize, presentationTimeUs, sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

	                if (!sawInputEOS) {
	                    extractor.advance();
	                }
	            }
	        }

	        int res = encoder.dequeueOutputBuffer(info, kTimeOutUs);

	        if (res >= 0) {
	            int outputBufIndex = res;
	            ByteBuffer buf = encoderOutputBuffers[outputBufIndex];

	            buf.position(info.offset);
	            buf.limit(info.offset + info.size);

	            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {

	                // create a decoder
	                decoder = MediaCodec.createDecoderByType(mimeType);
	                MediaFormat format = MediaFormat.createVideoFormat(mimeType, width, height);
	                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
	                format.setByteBuffer("csd-0", buf);
	                decoder.configure(format, surface /* surface */, null /* crypto */, 0 /* flags */);
	                decoder.start();
	                decoderInputBuffers = decoder.getInputBuffers();
	                decoderOutputBuffers = decoder.getOutputBuffers();
	            } else {
	                int decIndex = decoder.dequeueInputBuffer(-1);
	                decoderInputBuffers[decIndex].clear();
	                decoderInputBuffers[decIndex].put(buf);
	                decoder.queueInputBuffer(decIndex, 0, info.size, info.presentationTimeUs, info.flags);
	            }

	            encoder.releaseOutputBuffer(outputBufIndex, false /* render */);
	        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
	            encoderOutputBuffers = encoder.getOutputBuffers();

	            Log.d(TAG, "encoder output buffers have changed.");
	        } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
	            MediaFormat encformat = encoder.getOutputFormat();

	            Log.d(TAG, "encoder output format has changed to " + encformat);
	        }

	        if (decoder == null)
	            res = MediaCodec.INFO_TRY_AGAIN_LATER;
	        else
	            res = decoder.dequeueOutputBuffer(info, kTimeOutUs);

	        if (res >= 0) {
	            int outputBufIndex = res;
	            ByteBuffer buf = decoderOutputBuffers[outputBufIndex];

	            buf.position(info.offset);
	            buf.limit(info.offset + info.size);

	            // The worlds simplest FPS implementation
	            while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
	                try {
	                    Thread.sleep(10);
	                } catch (InterruptedException e) {
	                    e.printStackTrace();
	                    break;
	                }
	            }

	            decoder.releaseOutputBuffer(outputBufIndex, true /* render */);

	            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
	                Log.d(TAG, "saw output EOS.");
	                sawOutputEOS = true;
	            }
	        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
	            decoderOutputBuffers = decoder.getOutputBuffers();

	            Log.d(TAG, "decoder output buffers have changed.");
	        } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
	            oformat = decoder.getOutputFormat();

	            Log.d(TAG, "decoder output format has changed to " + oformat);
	        }

	    }

	    encoder.stop();
	    encoder.release();
	    decoder.stop();
	    decoder.release();
	}
	
    public void showCodecCapabilities(){
    	String mimeType = "video/avc";
    	
    	// Find a code that supports the mime type
	    int numCodecs = MediaCodecList.getCodecCount();
	    MediaCodecInfo codecInfo = null;
	    for (int i = 0; i < numCodecs && codecInfo == null; i++) {
	        MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
	        if (!info.isEncoder()) {
	            continue;
	        }
	        String[] types = info.getSupportedTypes();
	        boolean found = false;
	        for (int j = 0; j < types.length && !found; j++) {
	            if (types[j].equals(mimeType))
	                found = true;
	        }
	        if (!found)
	            continue;
	        codecInfo = info;
	    }
	    Log.i("Encoder", "Found " + codecInfo.getName() + " supporting " + mimeType);

	    // Find a color profile that the codec supports
	    int colorFormat = 0;
	    MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
	    Log.i("Encoder", "capabilities.colorFormats.length " + capabilities.colorFormats.length);
	    for (int i = 0; i < capabilities.colorFormats.length && colorFormat == 0; i++) {
	        int format = capabilities.colorFormats[i];
	        Log.i("Encoder", "color format " + format);
	        switch (format) {
	        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
	        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
	        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
	        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
	        case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
	            colorFormat = format;
	            break;
	        default:
	            Log.i("Encoder", "Skipping unsupported color format " + format);
	            break;
	        }
	    }
	    Log.i("Encoder", "Using color format " + colorFormat);
    	
    }
    
    //byte[]
    public static void YV12toYUV420PackedSemiPlanar_00(byte[] input, byte[] output, int width, int height) {
        /* 
         * COLOR_TI_FormatYUV420PackedSemiPlanar is NV12
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize/4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i*2] = input[frameSize + i + qFrameSize]; // Cb (U)
            output[frameSize + i*2 + 1] = input[frameSize + i]; // Cr (V)
        }
        
        /*
        //When set the resolution to 320x240, your color transform should looks like:
        System.arraycopy(input, 0, output, 0, frameSize);
        for (int i = 0; i < (qFrameSize); i++) {  
            output[frameSize + i*2] = (input[frameSize + qFrameSize + i - 32 - 320]);  
            output[frameSize + i*2 + 1] = (input[frameSize + i - 32 - 320]);            
        }
        
        //for resolution 640x480 and above
        System.arraycopy(input, 0, output, 0, frameSize);    
        for (int i = 0; i < (qFrameSize); i++) {  
            output[frameSize + i*2] = (input[frameSize + qFrameSize + i]);  
            output[frameSize + i*2 + 1] = (input[frameSize + i]);   
        } 
        */
        
        //return output;
    }

    public static byte[] YV12toYUV420Planar_00(byte[] input, byte[] output, int width, int height) {
        /* 
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize/4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr (V)
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb (U)

        return output;
    }

    public static byte[] swapYV12toI420_00(byte[] yv12bytes, int width, int height) {
        byte[] i420bytes = new byte[yv12bytes.length];
        for (int i = 0; i < width*height; i++)
            i420bytes[i] = yv12bytes[i];
        for (int i = width*height; i < width*height + (width/2*height/2); i++)
            i420bytes[i] = yv12bytes[i + (width/2*height/2)];
        for (int i = width*height + (width/2*height/2); i < width*height + 2*(width/2*height/2); i++)
            i420bytes[i] = yv12bytes[i - (width/2*height/2)];
        return i420bytes;
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
		
		File f = new File(Environment.getExternalStorageDirectory(), "mediacodec0712tt.264");
	    if(!f.exists()){	
	    	try {
				f.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }
	    //else{
	    //	f.delete();
	    //}
	    
	    try {
	        outputStream = new BufferedOutputStream(new FileOutputStream(f));
	        Log.i("Encoder", "outputStream initialized");
	    } catch (Exception e){ 
	        e.printStackTrace();
	    }
	    
	    int colorFormat = selectColorFormat(selectCodec("video/avc"), "video/avc");
	    
	    mediaCodec0 = MediaCodec.createEncoderByType(type);
	    
		mediaCodec = MediaCodec.createEncoderByType(type);  
		MediaFormat mediaFormat = MediaFormat.createVideoFormat(type, width, height);  
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 125000);//125kbps  
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);  
		//mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, 
		//		MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);  //COLOR_FormatYUV420Planar
		
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
		
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5); //�ؼ�֡���ʱ�� ��λs  
		mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);  
		mediaCodec.start();	
		
		mediaCodec0.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);  
		mediaCodec0.start();	
		
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
	        
	        mediaCodec0.stop();
	        mediaCodec0.release();
			
	        
	        mediaCodecd.stop(); 
	        mediaCodecd.release(); 
	        outputStream.flush();
	        outputStream.close();
	        Log.i("Encoder", "--------------close--------------");
	    } catch (Exception e){ 
	        e.printStackTrace();
	    }
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	public static byte[] YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {
        /* 
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize/4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr (V)
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb (U)

        return output;
    }
	
	private byte[] m_info = null; 
	private byte[] yuv420 = new byte[width*height*3/2];
	private byte[] h264 = new byte[width*height*3/2]; 
	
	 /*
    YUV420P��Y��U��V������������ƽ���ʽ����ΪI420��YV12��I420��ʽ��YV12��ʽ�Ĳ�ͬ����Uƽ���Vƽ���λ�ò�ͬ��
          ��I420��ʽ�У�Uƽ�������Yƽ��֮��Ȼ�����Vƽ�棨����YUV������YV12�����෴������YVU����
    YUV420SP, Y����ƽ���ʽ��UV�����ʽ, ��NV12�� NV12��NV21���ƣ�U �� V ��������,��ͬ����UV˳��
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
    
    private void swapYV12toI420_01(byte[] yv12bytes, byte[] i420bytes, int width, int height)   
    {        
        System.arraycopy(yv12bytes, 0, i420bytes, 0,width*height);
        System.arraycopy(yv12bytes, width*height+width*height/4, i420bytes, width*height,width*height/4);  
        System.arraycopy(yv12bytes, width*height, i420bytes, width*height+width*height/4,width*height/4);    
    } 
    
    public int offerEncoder(byte[] input, byte[] output)   
    {     
        int pos = 0;  
        swapYV12toI420(input, output, width, height);  
        //YV12toYUV420PackedSemiPlanar_00(input, output, width, height);
        
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
                  
                if(m_info != null)  
                {                 
                    System.arraycopy(outData, 0,  output, pos, outData.length);  
                    pos += outData.length;  
                      
                }  
                  
                else //����pps sps ֻ�п�ʼʱ ��һ��֡���У� ��������������  
                {  
                     ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);    
                     if (spsPpsBuffer.getInt() == 0x00000001)   
                     {   
                    	 Log.i("Encoder", "--------- pps sps found = "+outData.length+"---------");
                         m_info = new byte[outData.length];  
                         System.arraycopy(outData, 0, m_info, 0, outData.length);  
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
  
            if(output[4] == 0x65) //key frame   ���������ɹؼ�֡ʱֻ�� 00 00 00 01 65 û��pps sps�� Ҫ����  
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
    
    //static String REMOTE_HOST;
    private static String REMOTE_HOST= "192.168.1.9"; // the terminal of VLC play
	private static final short REMOTE_HOST_PORT = 5000;
    private static InetAddress address;
	private static DatagramSocket socket;
	
    static class UdpSendTask extends Thread{
		private ArrayList<ByteBuffer> mList;
		static  boolean running;
		
		public void init()
		{
			try {  
	            socket = new DatagramSocket();  
	            address = InetAddress.getByName(REMOTE_HOST); 
	            
	            Log.w("UdpSendTask", "      UdpSendTask start    " + REMOTE_HOST);
	            mList = new ArrayList<ByteBuffer>();
	            
	            try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
	            running = true;
	        } catch (SocketException e) {  
	            e.printStackTrace();  
	        } catch (UnknownHostException e) {
	            e.printStackTrace();  
	        }
		}
		
		public void end()
		{
			running = false;	
			runFlag = false;
		}
		
		public void pushBuf(byte[] buf,int len)
		{
			ByteBuffer buffer = ByteBuffer.allocate(len);
			buffer.put(buf,0,len);
			mList.add(buffer);
		}
		
		@Override  
	    public void run() {
			//Log.d("UdpSendTask","fall in udp send thread");
			while(running){
				if(mList.size() <= 0){
		        	try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
		        }
		        while(mList.size() > 0){
		        	ByteBuffer sendBuf = mList.get(0);
		        	try {         
		        		//Log.d("UdpSendTask","send udp packet len:"+sendBuf.capacity());
		                DatagramPacket packet=new DatagramPacket(sendBuf.array(),sendBuf.capacity(), address,REMOTE_HOST_PORT);  
		                socket.send(packet);  
		            } catch (Throwable t) {
		    	        t.printStackTrace();
		    	    }
		        	mList.remove(0);
		        	if(!running){
		        		Log.w("UdpSendTask", "      UdpSendTask pre-over    ");
		        		break;
		        	}
		        }	
			}
			mList.clear();
			Log.w("UdpSendTask", "      UdpSendTask over    ");
	    }  
	}
	
    int count_frame;
    boolean firstFlag = true;
    boolean secondFlag= true;
    boolean threeFlag = false;
    byte[] outData0 = new byte[20]; 

 // encode
 	public void onFrame2(byte[] buf, int offset, int length, int flag) {	
 		 
 		    swapYV12toI420(buf, h264, width, height); 
 		 
 		    ByteBuffer[] inputBuffers = mediaCodec0.getInputBuffers();
 		    ByteBuffer[] outputBuffers = mediaCodec0.getOutputBuffers();
 		    int inputBufferIndex = mediaCodec0.dequeueInputBuffer(-1);
 		    if (inputBufferIndex >= 0) {
 		        ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
 		        inputBuffer.clear();
 		        //inputBuffer.put(buf, offset, length);
 		        inputBuffer.put(h264, offset, length);
 		        mediaCodec0.queueInputBuffer(inputBufferIndex, 0, length, 0, 0);
 		    }
 		    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
 		    int outputBufferIndex = mediaCodec0.dequeueOutputBuffer(bufferInfo,0);

 		    while (outputBufferIndex >= 0) {
 		        ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

 	            byte[] outData = new byte[bufferInfo.size];
 	            outputBuffer.get(outData);
 	            
 	            if(++count_frame>100){
 			    	count_frame = 0;
 			        //Log.i("Encoder", " length = "+length+", inputBufferIndex = "+inputBufferIndex+", outputBufferIndex = "+outputBufferIndex);
 			    }
 	            
 	            /*
 	            try {
 					outputStream.write(outData, 0, outData.length);
 				} catch (IOException e) {
 					// TODO Auto-generated catch block
 					e.printStackTrace();
 				} // write into h264 file
 	            //Log.i("Encoder", outData.length + " bytes written");
                 */
 	            
 		        //if (frameListener != null)//ȡѹ���õ�����ι��������
 		        //    frameListener.onFrame(outputBuffer, 0, length, flag);
 	            
 	            
 		        if(!runFlag){
 	                onFrame0(outData, 0, outData.length, flag);
 	                if(outData.length==20 && firstFlag){
 	                	Log.i("Encoder", "   onFrame pps sps  ");
 	                	for (int ix = 0; ix < 20; ++ix) {
                  			System.out.printf("%02x ", outData[ix]);
                  	    }
                  	    System.out.println("\n----------");
 	                	System.arraycopy(outData, 0,  outData0, 0, outData.length);  
 	                    firstFlag = false;
 	                }
 		        }else{
 		        	if(netSendTask != null){
 					   if(UdpSendTask.running){
 						    if(secondFlag){
 		 		        		Log.i("Encoder", "   pushBuf outData0  ");
 		 		        		netSendTask.pushBuf(outData0, outData0.length);
 		 		        		secondFlag = false;
 		 		        	}
 		 		        	if(outData[4] == 0x65){
 		 		        	    threeFlag = true;
 		 		        	    //Log.i("Encoder", "   pushBuf key frame  ");
 		 		        	}
 		 		        	if(threeFlag){
 		 		        	    netSendTask.pushBuf(outData, outData.length);
 		 		        	}
 					   }
 		        	}
 		        }
 		        
 		        //onFrame0(outputBuffer.array(), 0, bufferInfo.size, flag);
 		        
 		        mediaCodec0.releaseOutputBuffer(outputBufferIndex, false);
 		        outputBufferIndex = mediaCodec0.dequeueOutputBuffer(bufferInfo, 0);
 		    }
 	 }  
 	
    // encode
 	public void onFrame1(byte[] buf, int offset, int length, int flag) {	
 		 
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
 	            
               if(outData.length==20 && firstFlag){
                 	Log.i("Encoder", "   onFrame pps sps  ");
                  	for (int ix = 0; ix < 20; ++ix) {
            			System.out.printf("%02x ", outData[ix]);
            	    }
            	    System.out.println("\n----------"); 
                    firstFlag = false;
               }
               
 		        netSendTask.pushBuf(outData, outData.length);
 		        
 		        mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
 		        outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
 		    }
 	 }
    
	// encode
	public void onFrame(byte[] buf, int offset, int length, int flag) {	
		 
		    swapYV12toI420(buf, h264, width, height); 
		 
		    ByteBuffer[] inputBuffers = mediaCodec0.getInputBuffers();
		    ByteBuffer[] outputBuffers = mediaCodec0.getOutputBuffers();
		    int inputBufferIndex = mediaCodec0.dequeueInputBuffer(-1);
		    if (inputBufferIndex >= 0) {
		        ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
		        inputBuffer.clear();
		        //inputBuffer.put(buf, offset, length);
		        inputBuffer.put(h264, offset, length);
		        mediaCodec0.queueInputBuffer(inputBufferIndex, 0, length, 0, 0);
		    }
		    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
		    int outputBufferIndex = mediaCodec0.dequeueOutputBuffer(bufferInfo,0);

		    while (outputBufferIndex >= 0) {
		        ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

	            byte[] outData = new byte[bufferInfo.size];
	            outputBuffer.get(outData);
	            
	            if(++count_frame>100){
			    	count_frame = 0;
			        //Log.i("Encoder", " length = "+length+", inputBufferIndex = "+inputBufferIndex+", outputBufferIndex = "+outputBufferIndex);
			    }
	            
	            /*
	            try {
					outputStream.write(outData, 0, outData.length);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} // write into h264 file
	            //Log.i("Encoder", outData.length + " bytes written");
                */
	            
		        //if (frameListener != null)//ȡѹ���õ�����ι��������
		        //    frameListener.onFrame(outputBuffer, 0, length, flag);
	            
	            /*
		        if(!runFlag){
	                onFrame0(outData, 0, outData.length, flag);
	                if(outData.length==20 && firstFlag){
	                	Log.i("Encoder", "   onFrame pps sps  ");
	                	for (int ix = 0; ix < 20; ++ix) {
                 			System.out.printf("%02x ", outData[ix]);
                 	    }
                 	    System.out.println("\n----------");
	                	System.arraycopy(outData, 0,  outData0, 0, outData.length);  
	                    firstFlag = false;
	                }
	                secondFlag = true;
	                threeFlag = false;
		        }else{
		        	if(secondFlag){
		        		Log.i("Encoder", "   pushBuf outData0  ");
		        		//netSendTask.pushBuf(outData0, outData0.length);
		        		secondFlag = false;
		        	}else{
		        		if(outData[4] == 0x65){
		        			threeFlag = true;
		        			Log.i("Encoder", "   pushBuf key frame  ");
		        		}
		        	}
		        	if(threeFlag){
		        	    netSendTask.pushBuf(outData, outData.length);
		        	}
		        }
		        */
	            if(!runFlag){
	               onFrame0(outData, 0, outData.length, flag);
	            }
		        //onFrame0(outputBuffer.array(), 0, bufferInfo.size, flag);
		        
		        mediaCodec0.releaseOutputBuffer(outputBufferIndex, false);
		        outputBufferIndex = mediaCodec0.dequeueOutputBuffer(bufferInfo, 0);
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
	
	 
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		// TODO Auto-generated method stub
		 //if (frameListener != null) {  
		 //       frameListener.onFrame(data, 0, data.length, 0);  
		 //} 
		 
		 //int ret = offerEncoder(data,h264);  
         
	     //if(ret > 0)  
	     //{   	 
	    	 //outputStream.write(h264, 0, ret);
			 //netSendTask.pushBuf(h264, ret);
	     //}
		 /*
		 if(!runFlag){
			onFrame(data, 0, data.length, 0); 
		 }else{
			if(netSendTask != null){
			   if(UdpSendTask.running){
		          onFrame1(data, 0, data.length, 0); 
			   }
			}
		 }
		 */
		 onFrame2(data, 0, data.length, 0); 
		 
		 camera.addCallbackBuffer(buf);	
		 //Log.i("Encoder", "--------------------onPreviewFrame-----------------"+data.length);
	}
}

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := rtmp
LOCAL_SRC_FILES := librtmp.so
include $(PREBUILT_SHARED_LIBRARY) 

include $(CLEAR_VARS)

LOCAL_C_INCLUDES += \
     $(LOCAL_PATH)\
     $(LOCAL_PATH)/librtmp
     
LOCAL_SHARED_LIBRARIES := rtmp
LOCAL_MODULE := rtmplive
LOCAL_SRC_FILES := rtmplive.cpp
LOCAL_LDLIBS    += -llog -lc -lz

include $(BUILD_SHARED_LIBRARY)

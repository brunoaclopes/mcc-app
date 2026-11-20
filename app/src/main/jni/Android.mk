LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE    := mccserial
LOCAL_SRC_FILES := mccserial.c
LOCAL_LDLIBS    := -llog
include $(BUILD_SHARED_LIBRARY)

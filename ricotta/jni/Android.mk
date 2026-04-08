# RicottaArch Android.mk
# Sets up the build context and includes RetroArch's Android.mk,
# then adds Ricotta-specific sources and flags.

LOCAL_PATH := $(call my-dir)

# Point to RetroArch's phoenix-common/jni directory for the real Android.mk
RA_JNI_DIR := $(LOCAL_PATH)/../../retroarch/pkg/android/phoenix-common/jni

# Include RetroArch's Android.mk from its own directory
include $(RA_JNI_DIR)/Android.mk

# Add Ricotta IGM bridge source and define
LOCAL_SRC_FILES += $(LOCAL_PATH)/ricotta_bridge.c
DEFINES += -DHAVE_RICOTTA_IGM

# Reapply DEFINES to CFLAGS since RetroArch's mk already set them
LOCAL_CFLAGS += -DHAVE_RICOTTA_IGM

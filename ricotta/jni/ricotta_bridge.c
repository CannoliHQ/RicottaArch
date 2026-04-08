/*
 * ricotta_bridge.c - JNI bridge for RicottaArch IGM (In-Game Menu)
 *
 * Provides native methods for dev.cannoli.ricotta.RicottaArchBridge
 * that dispatch RetroArch command events and query emulator state.
 */

#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <time.h>
#include <android/log.h>

static long long get_time_ms(void)
{
   struct timespec ts;
   clock_gettime(CLOCK_MONOTONIC, &ts);
   return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

#define RTAG "RicottaBridge"
#define RLOG(...) __android_log_print(ANDROID_LOG_DEBUG, RTAG, __VA_ARGS__)

#include "../../../../retroarch.h"
#include "../../../../command.h"
#include "../../../../configuration.h"
#include "../../../../menu/menu_driver.h"
#include "../../../../menu/menu_defines.h"
#include "../../../../runloop.h"

/* Cached JVM and bridge object refs for callbacks */
static JavaVM *g_jvm           = NULL;
static jobject g_bridge_obj    = NULL;
static jmethodID g_on_menu_closed_mid = NULL;
static jmethodID g_on_menu_button_mid = NULL;
static jmethodID g_on_debug_key_mid = NULL;

/* Flag set by Java side when IGM overlay is visible */
static volatile int g_igm_visible = 0;

/* Cached JNIEnv for the native runloop thread (attached once, never detached) */
static JNIEnv *g_native_env = NULL;

/* Timestamp of when the IGM was opened, to debounce the menu button */
static volatile long long g_igm_open_time = 0;

/* Menu close polling */
static pthread_t g_menu_poll_thread;
static volatile int g_menu_poll_active = 0;

static void *menu_close_poll_func(void *arg)
{
   JNIEnv *env = NULL;
   int attached = 0;

   (void)arg;

   /* Attach this thread to the JVM */
   if ((*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK)
   {
      if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK)
         return NULL;
      attached = 1;
   }

   /* Poll until menu closes */
   while (g_menu_poll_active)
   {
      struct menu_state *menu_st = menu_state_get_ptr();
      if (menu_st && !(menu_st->flags & MENU_ST_FLAG_ALIVE))
      {
         /* Menu has closed - notify Kotlin */
         if (g_bridge_obj && g_on_menu_closed_mid)
            (*env)->CallVoidMethod(env, g_bridge_obj, g_on_menu_closed_mid);
         break;
      }
      usleep(50000); /* 50ms */
   }

   g_menu_poll_active = 0;

   if (attached)
      (*g_jvm)->DetachCurrentThread(g_jvm);

   return NULL;
}

/*
 * Called from retroarch.c when CMD_EVENT_MENU_TOGGLE fires.
 * This replaces RetroArch's normal menu open/close with our IGM.
 */
void ricotta_bridge_on_menu_toggle(void)
{
   if (!g_jvm || !g_bridge_obj || !g_on_menu_button_mid)
      return;

   /* Attach the native runloop thread once and cache the env */
   if (!g_native_env)
   {
      if ((*g_jvm)->GetEnv(g_jvm, (void **)&g_native_env, JNI_VERSION_1_6) != JNI_OK)
      {
         if ((*g_jvm)->AttachCurrentThread(g_jvm, &g_native_env, NULL) != JNI_OK)
            return;
      }
   }

   (*g_native_env)->CallVoidMethod(g_native_env, g_bridge_obj, g_on_menu_button_mid);
}

/*
 * Called from android_input.c when a key event arrives.
 * Returns 1 if the event should be consumed (IGM wants it).
 */
int ricotta_bridge_intercept_key(int keycode, int action)
{
   /* When IGM is visible, consume all gamepad input
    * (input is handled through the Dialog's key listener instead) */
   if (g_igm_visible)
      return 1;

   return 0;
}

JNIEXPORT void JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativeSetIGMVisible(
      JNIEnv *env, jobject obj, jboolean visible)
{
   (void)env;
   (void)obj;
   g_igm_visible = visible ? 1 : 0;
}

JNIEXPORT void JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativeInit(
      JNIEnv *env, jobject obj)
{
   jclass cls;

   (*env)->GetJavaVM(env, &g_jvm);

   /* Clean up any previous global ref */
   if (g_bridge_obj)
   {
      (*env)->DeleteGlobalRef(env, g_bridge_obj);
      g_bridge_obj = NULL;
   }

   g_bridge_obj = (*env)->NewGlobalRef(env, obj);

   cls = (*env)->GetObjectClass(env, obj);
   g_on_menu_closed_mid = (*env)->GetMethodID(env, cls, "onNativeMenuClosed", "()V");
   g_on_menu_button_mid = (*env)->GetMethodID(env, cls, "onMenuButtonPressed", "()V");
   g_on_debug_key_mid = (*env)->GetMethodID(env, cls, "onDebugKey", "(I)V");
}

JNIEXPORT void JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativeDestroy(
      JNIEnv *env, jobject obj)
{
   (void)obj;

   g_menu_poll_active = 0;

   if (g_bridge_obj)
   {
      (*env)->DeleteGlobalRef(env, g_bridge_obj);
      g_bridge_obj = NULL;
   }

   g_on_menu_closed_mid = NULL;
}

JNIEXPORT void JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativeSaveState(
      JNIEnv *env, jobject obj, jint slot)
{
   settings_t *settings = config_get_ptr();
   (void)env;
   (void)obj;

   if (settings)
      settings->ints.state_slot = (int)slot;

   command_event(CMD_EVENT_SAVE_STATE, NULL);
}

JNIEXPORT void JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativeLoadState(
      JNIEnv *env, jobject obj, jint slot)
{
   settings_t *settings = config_get_ptr();
   (void)env;
   (void)obj;

   if (settings)
      settings->ints.state_slot = (int)slot;

   command_event(CMD_EVENT_LOAD_STATE, NULL);
}

JNIEXPORT void JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativeUndoSaveState(
      JNIEnv *env, jobject obj)
{
   (void)env;
   (void)obj;
   command_event(CMD_EVENT_UNDO_SAVE_STATE, NULL);
}

JNIEXPORT void JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativeUndoLoadState(
      JNIEnv *env, jobject obj)
{
   (void)env;
   (void)obj;
   command_event(CMD_EVENT_UNDO_LOAD_STATE, NULL);
}

JNIEXPORT void JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativeReset(
      JNIEnv *env, jobject obj)
{
   (void)env;
   (void)obj;
   command_event(CMD_EVENT_RESET, NULL);
}

JNIEXPORT void JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativeQuit(
      JNIEnv *env, jobject obj)
{
   (void)env;
   (void)obj;
   command_event(CMD_EVENT_QUIT, NULL);
}

JNIEXPORT void JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativePause(
      JNIEnv *env, jobject obj)
{
   (void)env;
   (void)obj;
   command_event(CMD_EVENT_PAUSE, NULL);
}

JNIEXPORT void JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativeUnpause(
      JNIEnv *env, jobject obj)
{
   (void)env;
   (void)obj;
   command_event(CMD_EVENT_UNPAUSE, NULL);
}

JNIEXPORT void JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativeMenuToggle(
      JNIEnv *env, jobject obj)
{
   (void)env;
   (void)obj;

   command_event(CMD_EVENT_MENU_TOGGLE, NULL);

   /* Start polling for menu close */
   if (!g_menu_poll_active)
   {
      g_menu_poll_active = 1;
      pthread_create(&g_menu_poll_thread, NULL, menu_close_poll_func, NULL);
      pthread_detach(g_menu_poll_thread);
   }
}

JNIEXPORT jboolean JNICALL
Java_dev_cannoli_ricotta_RicottaArchBridge_nativeIsPaused(
      JNIEnv *env, jobject obj)
{
   uint32_t flags;
   (void)env;
   (void)obj;

   flags = runloop_get_flags();
   return (flags & RUNLOOP_FLAG_PAUSED) ? JNI_TRUE : JNI_FALSE;
}

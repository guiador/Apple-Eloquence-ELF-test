/* SPDX-License-Identifier: MIT
 * JNI control + bounded IPC for a standalone, same-UID native renderer.
 * The converted Apple libraries stay outside ART's linker/runtime context.
 */
#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,"EloquenceJNI",__VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,"EloquenceJNI",__VA_ARGS__)
#define MAX_SAMPLES (16u * 1024u * 1024u)
extern char **environ;
typedef struct { char *data, *libraries; int dialect, values[8], abort; pid_t child; } Engine;
static int valid_dialect(int dialect) {
    const int codes[]={0x10000,0x10001,0x20000,0x20001,0x30000,0x30001,0x40000,0x50000,0x70000,0x90000};
    for(size_t i=0;i<sizeof(codes)/sizeof(codes[0]);++i) if(codes[i]==dialect)return 1;
    return 0;
}
JNIEXPORT jlong JNICALL Java_com_eloquence_tts_EloquenceNative_nativeInit(
        JNIEnv *env,jclass clazz,jstring data,jstring libraries,jint dialect) {
    (void)clazz;if(!data||!libraries||!valid_dialect(dialect))return 0;
    Engine *e=calloc(1,sizeof(*e));if(!e)return 0;
    const char *s=(*env)->GetStringUTFChars(env,data,NULL);
    if(s){e->data=strdup(s);(*env)->ReleaseStringUTFChars(env,data,s);}
    s=(*env)->GetStringUTFChars(env,libraries,NULL);
    if(s){e->libraries=strdup(s);(*env)->ReleaseStringUTFChars(env,libraries,s);}
    char path[4096];
    if(!e->data||!e->libraries||snprintf(path,sizeof(path),"%s/libeloquence_worker.so",e->libraries)>=(int)sizeof(path)||access(path,X_OK)) {
        free(e->data);free(e->libraries);free(e);LOGE("Native renderer unavailable");return 0;
    }
    const int defaults[]={0,50,65,30,0,0,50,92};memcpy(e->values,defaults,sizeof(defaults));e->dialect=dialect;
    return (jlong)(intptr_t)e;
}
JNIEXPORT jboolean JNICALL Java_com_eloquence_tts_EloquenceNative_nativeSetLanguage(
        JNIEnv *env,jclass clazz,jlong handle,jint dialect) {
    (void)env;(void)clazz;Engine *e=(Engine*)(intptr_t)handle;
    if(!e||!valid_dialect(dialect))return JNI_FALSE;e->dialect=dialect;return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_com_eloquence_tts_EloquenceNative_nativeSetVoice(
        JNIEnv *env,jclass clazz,jlong handle,jintArray array) {
    (void)clazz;Engine *e=(Engine*)(intptr_t)handle;
    if(!e||!array||(*env)->GetArrayLength(env,array)!=8)return JNI_FALSE;
    jint values[8];(*env)->GetIntArrayRegion(env,array,0,8,values);if((*env)->ExceptionCheck(env))return JNI_FALSE;
    for(int i=0;i<8;++i)if(values[i]<0||values[i]>(i==0?1:i==6?250:100))return JNI_FALSE;
    memcpy(e->values,values,sizeof(values));return JNI_TRUE;
}
static int send_all(int fd,const void *data,size_t size) {
    const char *p=data;
    while(size){ssize_t n=send(fd,p,size,MSG_NOSIGNAL);if(n<0&&errno==EINTR)continue;if(n<=0)return 0;p+=n;size-=n;}
    return 1;
}
static int receive_all(int fd,void *data,size_t size) {
    char *p=data;
    while(size){ssize_t n=read(fd,p,size);if(n<0&&errno==EINTR)continue;if(n<=0)return 0;p+=n;size-=n;}
    return 1;
}
JNIEXPORT jshortArray JNICALL Java_com_eloquence_tts_EloquenceNative_nativeSynthesize(
        JNIEnv *env,jclass clazz,jlong handle,jbyteArray array) {
    (void)clazz;Engine *e=(Engine*)(intptr_t)handle;if(!e||!array)return NULL;
    jsize length=(*env)->GetArrayLength(env,array);if(length<=0||length>=16384)return NULL;
    char *text=malloc((size_t)length);if(!text)return NULL;
    (*env)->GetByteArrayRegion(env,array,0,length,(jbyte*)text);
    if((*env)->ExceptionCheck(env)){free(text);return NULL;}
    char path[4096],dialect[24],values[128],search[4128];
    if(snprintf(path,sizeof(path),"%s/libeloquence_worker.so",e->libraries)>=(int)sizeof(path)){free(text);return NULL;}
    snprintf(dialect,sizeof(dialect),"%d",e->dialect);
    snprintf(values,sizeof(values),"%d,%d,%d,%d,%d,%d,%d,%d",e->values[0],e->values[1],e->values[2],e->values[3],e->values[4],e->values[5],e->values[6],e->values[7]);
    snprintf(search,sizeof(search),"LD_LIBRARY_PATH=%s",e->libraries);
    char *args[]={path,e->libraries,e->data,dialect,values,NULL};
    size_t count=0;while(environ[count])++count;
    char **child_env=calloc(count+2,sizeof(char*));if(!child_env){free(text);return NULL;}
    size_t copied=0;int env_ok=1;
    for(size_t i=0;i<count;++i)if(strncmp(environ[i],"LD_LIBRARY_PATH=",16)){
        child_env[copied]=strdup(environ[i]);if(!child_env[copied]){env_ok=0;break;}++copied;
    }
    child_env[copied]=strdup(search);if(!child_env[copied])env_ok=0;else ++copied;
    int sockets[2];
    if(!env_ok||socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,sockets)){
        for(size_t i=0;i<copied;++i)free(child_env[i]);free(child_env);free(text);return NULL;
    }
    e->abort=0;pid_t child=fork();
    if(child==0){
        close(sockets[0]);
        if(dup2(sockets[1],STDIN_FILENO)<0||dup2(sockets[1],STDOUT_FILENO)<0)_exit(125);
        if(sockets[1]!=STDIN_FILENO&&sockets[1]!=STDOUT_FILENO)close(sockets[1]);
        execve(path,args,child_env);_exit(126);
    }
    for(size_t i=0;i<copied;++i)free(child_env[i]);free(child_env);
    close(sockets[1]);
    if(child<0){close(sockets[0]);free(text);return NULL;}
    e->child=child;
    int ok=send_all(sockets[0],text,(size_t)length);free(text);shutdown(sockets[0],SHUT_WR);
    uint32_t header[4];short *pcm=NULL;
    if(ok)ok=receive_all(sockets[0],header,sizeof(header));
    if(ok)ok=header[0]==0x31514c45&&header[1]==1&&header[2]==11025&&header[3]>0&&header[3]<=MAX_SAMPLES;
    if(ok){pcm=malloc((size_t)header[3]*sizeof(short));ok=pcm&&receive_all(sockets[0],pcm,(size_t)header[3]*sizeof(short));}
    close(sockets[0]);if(!ok)kill(child,SIGTERM);
    int status=0;pid_t waited;do{waited=waitpid(child,&status,0);}while(waited<0&&errno==EINTR);e->child=0;
    if(waited!=child||!WIFEXITED(status)||WEXITSTATUS(status)!=0||e->abort)ok=0;
    jshortArray result=NULL;
    if(ok){result=(*env)->NewShortArray(env,(jsize)header[3]);if(result)(*env)->SetShortArrayRegion(env,result,0,(jsize)header[3],pcm);}
    else LOGE("renderer failed dialect=0x%x child_status=%d",e->dialect,status);
    free(pcm);if(ok)LOGI("rendered dialect=0x%x samples=%u",e->dialect,header[3]);return result;
}
JNIEXPORT void JNICALL Java_com_eloquence_tts_EloquenceNative_nativeStop(
        JNIEnv *env,jclass clazz,jlong handle) {
    (void)env;(void)clazz;Engine *e=(Engine*)(intptr_t)handle;if(!e)return;
    e->abort=1;if(e->child>0)kill(e->child,SIGTERM);
}
JNIEXPORT void JNICALL Java_com_eloquence_tts_EloquenceNative_nativeShutdown(
        JNIEnv *env,jclass clazz,jlong handle) {
    (void)env;(void)clazz;Engine *e=(Engine*)(intptr_t)handle;if(!e)return;
    if(e->child>0){kill(e->child,SIGTERM);while(waitpid(e->child,NULL,0)<0&&errno==EINTR){}}
    free(e->data);free(e->libraries);free(e);
}

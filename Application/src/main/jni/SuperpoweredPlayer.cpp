#include "SuperpoweredPlayer.h"
#include <SuperpoweredSimple.h>
#include <jni.h>
#include <stdio.h>
#include <android/log.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_AndroidConfiguration.h>

static void playerEventCallback(void *clientData, SuperpoweredAdvancedAudioPlayerEvent event, void * __unused value) {
    if (event == SuperpoweredAdvancedAudioPlayerEvent_LoadSuccess) {
    	SuperpoweredAdvancedAudioPlayer *player = *((SuperpoweredAdvancedAudioPlayer **)clientData);
        player->setPosition(0, false, false);
    };
}


static bool audioProcessing(void *clientdata, short int *audioIO, int numberOfSamples, int __unused samplerate) {
	return ((SuperpoweredPlayer *)clientdata)->process(audioIO, (unsigned int)numberOfSamples);
}

SuperpoweredPlayer::SuperpoweredPlayer(unsigned int samplerate, unsigned int buffersize, const char *path, int fileOffset, int fileLength) : vol(1.0f * headroom) {
    //stereoBuffer = (float *)memalign(16, (buffersize + 16) * sizeof(float) * 2);

    player = new SuperpoweredAdvancedAudioPlayer(&player , playerEventCallback, samplerate, 1);
    player->open(path, fileOffset, fileLength);
    player->cachePosition(0, 255);

    audioSystem = new SuperpoweredAndroidAudioIO(samplerate, buffersize, false, true, audioProcessing, this, -1, SL_ANDROID_STREAM_MEDIA, buffersize * 2);
}

SuperpoweredPlayer::~SuperpoweredPlayer() {
    delete audioSystem;
    delete player;
    free(stereoBuffer);
}

void SuperpoweredPlayer::onPlayPause(bool play) {
    if (!play) {
        player->setPosition(0, true, false);
    } else {
        player->play(false);
        player->loop(0, 1000, false, 255, false);
    };
}


bool SuperpoweredPlayer::process(short int *output, unsigned int numberOfSamples) {

    stereoBuffer = (float *)malloc(numberOfSamples * 8 + 256);
    bool silence = !player->process(stereoBuffer, false, numberOfSamples, vol, 0.0f, -1.0);

    // The stereoBuffer is ready now, let's put the finished audio into the requested buffers.
    if (!silence) SuperpoweredFloatToShortInt(stereoBuffer, output, numberOfSamples);
    return !silence;
}

static SuperpoweredPlayer *example = NULL;

extern "C" JNIEXPORT void Java_com_example_android_bluetoothchat_PhasedArrayFragment_SuperpoweredPlayer(JNIEnv *javaEnvironment, jobject __unused obj, jint samplerate, jint buffersize, jstring apkPath, jint fileOffset, jint fileLength, jint fileBoffset, jint fileBlength) {
    const char *path = javaEnvironment->GetStringUTFChars(apkPath, JNI_FALSE);
    example = new SuperpoweredPlayer((unsigned int)samplerate, (unsigned int)buffersize, path, fileOffset, fileLength);
    javaEnvironment->ReleaseStringUTFChars(apkPath, path);

}

extern "C" JNIEXPORT void Java_com_example_android_bluetoothchat_PhasedArrayFragment_onPlayPause(JNIEnv * __unused javaEnvironment, jobject __unused obj, jboolean play) {
	example->onPlayPause(play);
}


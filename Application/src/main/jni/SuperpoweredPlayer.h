#ifndef Header_SuperpoweredPlayer
#define Header_SuperpoweredPlayer

#include <math.h>
#include <pthread.h>

#include "SuperpoweredPlayer.h"
#include <SuperpoweredAdvancedAudioPlayer.h>
#include <AndroidIO/SuperpoweredAndroidAudioIO.h>

#define HEADROOM_DECIBEL 3.0f
static const float headroom = powf(10.0f, -HEADROOM_DECIBEL * 0.025f);

class SuperpoweredPlayer {
public:

	SuperpoweredPlayer(unsigned int samplerate, unsigned int buffersize, const char *path, int fileOffset, int fileLength);
	~SuperpoweredPlayer();

	bool process(short int *output, unsigned int numberOfSamples);
	void onPlayPause(bool play);

private:
    SuperpoweredAndroidAudioIO *audioSystem;
    SuperpoweredAdvancedAudioPlayer *player;
    float *stereoBuffer;
    float vol;
};

#endif

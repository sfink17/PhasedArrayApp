# PhasedArrayApp

THis app was made as part of a summer project to investigate the feasibility of audio beam steering
with Android phone speakers. Unfortunately, the timing precision required for proper beam steering
is beyond the scope of Android's underlying audio architechture, which produces an irreducable amount
of output lag. Nonetheless, this app achieves fair synchronization, utilizing a repeated handshake method
to achieve sub-millisecond precision across phone clocks.

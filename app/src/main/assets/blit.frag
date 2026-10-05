#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
// Stretches the low-resolution scene texture over the whole screen.
uniform sampler2D uTexture;
uniform vec2 uResolution;  // screen size, pixels
void main(){gl_FragColor=texture2D(uTexture,gl_FragCoord.xy/uResolution);}

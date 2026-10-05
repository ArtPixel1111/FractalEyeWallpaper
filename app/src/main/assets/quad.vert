// Fullscreen triangle: positions are already in clip space, so no matrices are needed.
attribute vec2 aPosition;
void main(){gl_Position=vec4(aPosition,0.,1.);}

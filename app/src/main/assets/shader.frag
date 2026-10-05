// Fractal Eye: a ray-marched Mandelbulb (power 8) shaded as an eye.
// Keep this file ASCII-only. Edit it here, then run tools/sync_shader.py to update the web preview.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
// Uniforms are set once per frame (SceneRenderer.java on Android, fractal-eye.html on the web).
uniform vec2 uResolution;  // render target size, pixels
uniform float uPupil;      // pupil half-angle measured from the +z pole, radians (animated)
uniform float uZoom;       // camera distance from the origin, scene units
uniform float uSteps;      // max ray-march steps; the loop below is capped at 200 for GLES 2.0
uniform float uIters;      // fractal iterations; the loop below is capped at 12 for GLES 2.0
uniform float uTime;       // seconds since start; drives the rotating glow rays
uniform mat3 uRotation;    // camera rotation, column-major
// Distance estimator for the Mandelbulb, power 8. Returns (distance, orbit trap).
// Each iteration is z -> z^8 + p in spherical coordinates: radius r^8, angles 8*theta and 8*phi.
// dr is the running derivative (8*r^7*dr + 1), so distance = 0.5*log(r)*r/dr.
// The orbit trap (smallest |z|^2 seen) is used later to colour the surface.
vec2 map(vec3 p){
  vec3 z=p;float dr=1.,r=0.,tr=9.;
  // GLES 2.0 needs constant loop bounds, so the real limit comes from uIters via an early break.
  for(int i=0;i<12;i++){if(float(i)>=uIters)break;
    r=length(z);if(r>2.)break;
    // Avoid undefined 0/0 at the exact Mandelbulb origin on mediump GPUs.
    float rs=max(r,1e-5);
    float r2=rs*rs,r4=r2*r2;
    float th=acos(clamp(z.z/rs,-1.,1.))*8.,ph=atan(z.y,z.x)*8.;
    dr=8.*r4*r2*r*dr+1.;
    z=r4*r4*vec3(sin(th)*cos(ph),sin(th)*sin(ph),cos(th))+p;
    tr=min(tr,dot(z,z));
  }
  return vec2(.5*log(r)*r/dr,tr);
}
// Surface normal from 4 samples of the distance field (tetrahedron pattern); e is the sample offset.
vec3 nrm(vec3 p,float e){
  const vec2 k=vec2(1,-1);
  return normalize(k.xyy*map(p+k.xyy*e).x+k.yyx*map(p+k.yyx*e).x+k.yxy*map(p+k.yxy*e).x+k.xxx*map(p+k.xxx*e).x);
}
// Camera ray: uv is centred and scaled by the shorter screen side; 1.25 is the focal length.
// The fractal fits inside a sphere of radius 1.3 (1.69 = 1.3^2). Rays that miss it only get the glow.
void main(){
  vec2 uv=(gl_FragCoord.xy-.5*uResolution)/min(uResolution.x,uResolution.y);
  vec3 ro=uRotation*vec3(0.,0.,uZoom),rd=uRotation*normalize(vec3(uv,-1.25));
  vec3 L=normalize(uRotation*vec3(-.6,.7,.7)),L2=normalize(uRotation*vec3(.8,-.3,.5));
  float b=dot(ro,rd),c=dot(ro,ro)-1.69,dm=sqrt(max(dot(ro,ro)-b*b,0.));
  vec3 col=mix(vec3(.05,.025,.012),vec3(.004,.002,.004),smoothstep(0.,.7,length(uv)));
  // Background: dark vignette plus an orange halo with slowly rotating rays.
  float ang=atan(uv.y,uv.x),rays=.65+.35*sin(ang*13.+uTime*.6)*sin(ang*5.-uTime*.4),ex=max(dm-1.05,0.);col+=vec3(1.,.5,.12)*exp(-ex*4.5)*.55*rays+vec3(1.,.8,.4)*exp(-ex*14.)*.5;vec3 bgc=col;
  float disc=b*b-c;
  if(disc>0.){
    // March from the sphere entry point. pw is the pixel footprint per unit distance;
    // a ray counts as a hit when the distance is below 0.45 of that footprint.
    // Near misses keep the closest approach (md) and get partial coverage (cov) as cheap anti-aliasing.
    float sq=sqrt(disc),t=max(-b-sq,0.),tm=-b+sq,st=0.,pw=1./(min(uResolution.x,uResolution.y)*1.25),md=9.,tb=t;
    vec2 h=vec2(1.),hb=vec2(1.);bool hit=false;
    for(int i=0;i<200;i++){
      if(float(i)>=uSteps||t>tm)break;
      h=map(ro+rd*t);
      float cn=h.x/max(pw*t,1e-5);if(cn<md){md=cn;tb=t;hb=h;}if(cn<.45){hit=true;break;}
      // Keep the march monotonic even if the distance estimator becomes slightly negative.
      t+=max(h.x*.85,1e-4);st+=1.;
    }
    float cov=hit?1.:1.-smoothstep(.45,1.6,md);
    if(hit||cov>.01){if(!hit){t=tb;h=hb;}
      vec3 p=ro+rd*t;float e=pw*t*.5;
      vec3 n=nrm(p,e),V=-rd,q=normalize(p);
      // Eye layout on the sphere: th is the polar angle from +z. Inside uPupil it is black,
      // out to ir (0.52 rad) it is the iris with radial fibres, beyond that the veined white.
      float th=acos(clamp(q.z,-1.,1.)),ph=atan(q.y,q.x),pu=uPupil,ir=.52;
      float aw=1.-smoothstep(.5,1.5,72.*pw*t/max(length(p.xy),.06)),f1=mix(.5,.5+.5*sin(ph*72.+h.y*40.),aw),f2=mix(.5,.5+.5*sin(ph*29.-h.y*17.+th*12.),aw);
      float fib=f1*(.55+.45*f2),rad=smoothstep(pu,ir,th),cx=(th-pu-.04)*30.;
      vec3 ic=mix(vec3(1.,.9,.45),vec3(.85,.2,.02),rad);
      ic=mix(ic,vec3(1.,.97,.75),.6*exp(-cx*cx));
      ic*=(.25+1.1*fib)*(1.-.85*smoothstep(ir-.09,ir,th));
      float vein=smoothstep(.62,.95,.5+.5*sin(h.y*31.+ph*4.+sin(th*20.)));
      vec3 sc=mix(vec3(1.,.82,.5),vec3(1.,.3,.03),vein*.75);
      float im=1.-smoothstep(ir,ir+.03,th),pm=smoothstep(pu-.02,pu+.03,th);
      vec3 alb=mix(sc,ic,im)*pm;
      // Cheap ambient occlusion: the more steps a ray needed, the darker the crevice.
      float ao=clamp(1.2-st*.02,.12,1.);
      float dif=max(dot(n,L),0.),d2=max(dot(n,L2),0.);
      float s1=pow(max(dot(n,normalize(L+V)),0.),90.)*(.35+.9*im);
      float s2=pow(max(dot(n,normalize(L2+V)),0.),220.)*(.4+im);
      float fr=pow(1.-max(dot(n,V),0.),3.);
      col=alb*(dif*vec3(1.,.88,.65)*1.05+d2*vec3(.6,.25,.1)*.5+.12)*ao;
      col+=(ic*im*pm*.6+sc*(1.-im)*vein*vec3(1.,.5,.15)*.5+fr*vec3(1.,.5,.12)*.6)*ao+s1*vec3(1.,.9,.7)+s2;col=mix(bgc,col,cov);
    }
  }
  // Tone mapping, gamma 1/2.2 (0.4545), a mild contrast curve and a vignette.
  col=col/(1.+col*.35);
  col=clamp(pow(col,vec3(.4545)),0.,1.);col=mix(col,col*col*(3.-2.*col),.4)*(1.-.5*dot(uv,uv));
  gl_FragColor=vec4(col,1.);
}
import React from 'react';
import {AbsoluteFill, Audio, OffthreadVideo, Sequence, continueRender, delayRender, interpolate, staticFile, useCurrentFrame, Easing} from 'remotion';
import timeline from './timeline.json';

export const FPS = 30;
type Seg =
  | {kind: 'card'; title: string; subtitle?: string; seconds: number}
  | {kind: 'clip'; src: string; from: number; seconds: number; rate?: number; sound?: string; soundTrim?: number}
  | {kind: 'outro'; seconds: number};
const SEGMENTS = timeline as Seg[];
const XFADE = 10; // frames of overlap between segments

export const totalFrames = () => {
  let t = 0;
  SEGMENTS.forEach((s, i) => (t += Math.round(s.seconds * FPS) - (i > 0 ? XFADE : 0)));
  return t;
};

const fontHandle = typeof window !== 'undefined' ? delayRender('font') : 0;
if (typeof window !== 'undefined') {
  const f = new FontFace('InterTight', `url(${staticFile('inter_tight.ttf')})`, {weight: '100 900'});
  f.load().then((ff) => {
    (document as any).fonts.add(ff);
    continueRender(fontHandle);
  }).catch(() => continueRender(fontHandle));
}

const fade = (frame: number, dur: number) =>
  Math.min(
    interpolate(frame, [0, XFADE], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'}),
    interpolate(frame, [dur - XFADE, dur], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'}),
  );

const Glow: React.FC = () => (
  <AbsoluteFill style={{background: 'radial-gradient(1200px 700px at 50% 60%, rgba(120,140,255,0.16), rgba(0,0,0,0) 70%), #000'}} />
);

const Card: React.FC<{title: string; subtitle?: string; dur: number}> = ({title, subtitle, dur}) => {
  const f = useCurrentFrame();
  const y = interpolate(f, [0, dur], [18, -10], {easing: Easing.out(Easing.cubic)});
  return (
    <AbsoluteFill style={{opacity: fade(f, dur)}}>
      <Glow />
      <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', transform: `translateY(${y}px)`, fontFamily: 'InterTight', color: '#fff', textAlign: 'center'}}>
        <div style={{fontSize: 92, fontWeight: 300, letterSpacing: -2}}>{title}</div>
        {subtitle ? <div style={{fontSize: 34, fontWeight: 400, opacity: 0.72, marginTop: 22, maxWidth: 1300, lineHeight: 1.35}}>{subtitle}</div> : null}
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

const Clip: React.FC<{s: Extract<Seg, {kind: 'clip'}>; dur: number}> = ({s, dur}) => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill style={{opacity: fade(f, dur), backgroundColor: '#000'}}>
      <OffthreadVideo src={staticFile(s.src)} trimBefore={Math.round(s.from * FPS)} playbackRate={s.rate ?? 1} muted
        style={{width: '100%', height: '100%', objectFit: 'cover'}} />
      {s.sound ? <Audio src={staticFile(s.sound)} trimBefore={Math.round((s.soundTrim ?? 0) * FPS)} /> : null}
    </AbsoluteFill>
  );
};

const Outro: React.FC<{dur: number}> = ({dur}) => {
  const f = useCurrentFrame();
  const sheen = interpolate(f, [8, 50], [-30, 130], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
  return (
    <AbsoluteFill style={{opacity: fade(f, dur)}}>
      <Glow />
      <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', fontFamily: 'InterTight', color: '#fff', textAlign: 'center'}}>
        <div style={{fontSize: 150, fontWeight: 500, letterSpacing: -4,
          backgroundImage: `linear-gradient(100deg, #fff 0%, #fff ${sheen - 12}%, #c9d2ff ${sheen}%, #fff ${sheen + 12}%, #fff 100%)`,
          WebkitBackgroundClip: 'text', backgroundClip: 'text', WebkitTextFillColor: 'transparent'}}>
          Lumen <span style={{fontWeight: 300}}>OS</span>
        </div>
        <div style={{fontSize: 30, fontWeight: 400, letterSpacing: 10, opacity: 0.75, marginTop: 6}}>YOUR CINEMA AT HOME</div>
        <div style={{fontSize: 26, fontWeight: 400, opacity: 0.55, marginTop: 70}}>Launcher de código abierto para Android TV y Google TV</div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

export const Promo: React.FC = () => {
  let at = 0;
  return (
    <AbsoluteFill style={{backgroundColor: '#000'}}>
      {SEGMENTS.map((s, i) => {
        const dur = Math.round(s.seconds * FPS);
        const from = at;
        at += dur - XFADE;
        return (
          <Sequence key={i} from={from} durationInFrames={dur}>
            {s.kind === 'card' ? <Card title={s.title} subtitle={s.subtitle} dur={dur} /> : s.kind === 'clip' ? <Clip s={s} dur={dur} /> : <Outro dur={dur} />}
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};

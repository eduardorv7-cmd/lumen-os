import React from 'react';
import {Composition} from 'remotion';
import {Promo, totalFrames, FPS} from './Promo';

export const RemotionRoot: React.FC = () => (
  <Composition id="LumenPromo" component={Promo} durationInFrames={totalFrames()} fps={FPS} width={1920} height={1080} />
);

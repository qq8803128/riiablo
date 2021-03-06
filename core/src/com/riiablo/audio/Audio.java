package com.riiablo.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Sounds;

public class Audio {
  private static final String TAG = "Audio";
  private static final boolean DEBUG = true;

  private static final String GLOBAL = "data\\global\\sfx\\";
  private static final String LOCAL  = "data\\local\\sfx\\";

  // TODO: Add support for different channels -- will require API change in com.riiablo.audio
  //       Audio should maintain a list of all instances and adjust volumes as needed
  public enum Channel {
    SFX, MUSIC, ENVIRONMENT, SPEECH
  }

  private final AssetManager assets;
  private final ObjectMap<Sounds.Entry, AssetDescriptor<?>> descriptors = new ObjectMap<>();

  public Audio(AssetManager assets) {
    this.assets = assets;
  }

  // TODO: Fix memory leak and dispose sound after playing
  // TODO: Support group size
  // TODO: global vs local sounds
  public synchronized Instance play(final Sounds.Entry sound, boolean global) {
    if (sound.FileName.isEmpty()) return null;
    if (sound.Stream) {
      Music stream;
      AssetDescriptor<Music> descriptor = (AssetDescriptor<Music>) descriptors.get(sound);
      if (descriptor == null) {
        descriptor = new AssetDescriptor<>((global ? GLOBAL : LOCAL) + sound.FileName, Music.class);
        descriptors.put(sound, descriptor);
        assets.load(descriptor);
        assets.finishLoadingAsset(descriptor);

        stream = assets.get(descriptor);
        stream.setVolume(sound.Volume / 255f);
      } else {
        stream = assets.get(descriptor);
      }

      if (sound.Defer_Inst && stream.isPlaying()) {
        return null;
      }

      stream.play();
      Instance instance = Instance.obtain(stream, -1);
      return instance;
    } else {
      AssetDescriptor<Sound> descriptor = (AssetDescriptor<Sound>) descriptors.get(sound);
      if (descriptor == null) {
        descriptor = new AssetDescriptor<>((global ? GLOBAL : LOCAL) + sound.FileName, Sound.class);
        descriptors.put(sound, descriptor);
        assets.load(descriptor);
        assets.finishLoadingAsset(descriptor);
      }

      // FIXME: sounds do not play on their current frame on Android, play next frame
      final Sound sfx = assets.get(descriptor);
      long id = sfx.play(sound.Volume / 255f);
      if (id == -1) {
        Gdx.app.postRunnable(new Runnable() {
          @Override
          public void run() {
            sfx.play(sound.Volume / 255f);
          }
        });
      }
      return Instance.obtain(sfx, id);
    }
  }

  public static class Instance implements Pool.Poolable {

    boolean stream;
    Object  delegate;
    long    id;

    static Instance obtain(Object delegate, long id) {
      Instance instance = Pools.obtain(Instance.class);
      instance.stream   = delegate instanceof Music;
      instance.delegate = delegate;
      instance.id       = id;
      return instance;
    }

    @Override
    public void reset() {
      delegate = null;
      id = -1;
    }

    public void stop() {
      if (stream) {
        ((Music) delegate).stop();
      } else {
        ((Sound) delegate).stop(id);
      }
    }

    public void setVolume(float volume) {
      if (stream) {
        ((Music) delegate).setVolume(volume);
      } else {
        ((Sound) delegate).setVolume(id, volume);
      }
    }
  }

  public Instance play(int id, boolean global) {
    Sounds.Entry sound = Riiablo.files.Sounds.get(id);
    return play(sound, global);
  }

  public Instance play(String id, boolean global) {
    if (id.isEmpty()) return null;
    Sounds.Entry sound = Riiablo.files.Sounds.get(id);
    if (sound == null) return null;
    if (sound.Group_Size > 0) {
      int randomId = sound.Index + MathUtils.random.nextInt(sound.Group_Size);
      sound = Riiablo.files.Sounds.get(randomId);
    }

    return play(sound, global);
  }
}

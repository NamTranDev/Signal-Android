package org.thoughtcrime.securesms.webrtc;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.thoughtcrime.securesms.webrtc.CameraState.Direction.BACK;
import static org.thoughtcrime.securesms.webrtc.CameraState.Direction.FRONT;
import static org.thoughtcrime.securesms.webrtc.CameraState.Direction.NONE;

public class PeerConnectionWrapper {
  private static final String TAG = PeerConnectionWrapper.class.getSimpleName();

  private static final PeerConnection.IceServer STUN_SERVER = new PeerConnection.IceServer("stun:stun1.l.google.com:19302");

  @NonNull  private final PeerConnection peerConnection;
  @NonNull  private final AudioTrack     audioTrack;
  @NonNull  private final AudioSource    audioSource;
  @NonNull  private final Cameras        cameras;

  @Nullable private final MediaStream    mediaStream;

  public PeerConnectionWrapper(@NonNull Context context,
                               @NonNull PeerConnectionFactory factory,
                               @NonNull PeerConnection.Observer observer,
                               @NonNull VideoRenderer.Callbacks localRenderer,
                               @NonNull List<PeerConnection.IceServer> turnServers,
                               boolean hideIp)
  {
    List<PeerConnection.IceServer> iceServers = new LinkedList<>();
    iceServers.add(STUN_SERVER);
    iceServers.addAll(turnServers);

    MediaConstraints                constraints      = new MediaConstraints();
    MediaConstraints                audioConstraints = new MediaConstraints();
    PeerConnection.RTCConfiguration configuration    = new PeerConnection.RTCConfiguration(iceServers);

    configuration.bundlePolicy  = PeerConnection.BundlePolicy.MAXBUNDLE;
    configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;

    if (hideIp) {
      configuration.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
    }

    constraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    audioConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

    this.peerConnection = factory.createPeerConnection(configuration, constraints, observer);
    this.peerConnection.setAudioPlayout(false);
    this.peerConnection.setAudioRecording(false);

    this.mediaStream = factory.createLocalMediaStream("ARDAMS");
    this.audioSource = factory.createAudioSource(audioConstraints);
    this.audioTrack  = factory.createAudioTrack("ARDAMSa0", audioSource);
    this.audioTrack.setEnabled(false);
    this.mediaStream.addTrack(audioTrack);

    this.cameras = new Cameras(context, factory, localRenderer);

    VideoTrack videoTrack = cameras.getTrack();
    if (videoTrack != null) {
      this.mediaStream.addTrack(videoTrack);
    }

    this.peerConnection.addStream(mediaStream);
  }

  public void setVideoEnabled(boolean enabled) {
    cameras.setEnabled(enabled);
  }

  public void setCameraDirection(@NonNull CameraState.Direction direction) {
    if (mediaStream != null) {
      cameras.setDirection(direction, mediaStream);
    }
  }

  public CameraState getCameraState() {
    if (cameras.isEnabled()) {
      return new CameraState(cameras.getActiveDirection(), cameras.getCount());
    }
    return new CameraState(CameraState.Direction.NONE, cameras.getCount());
  }

  public void setCommunicationMode() {
    this.peerConnection.setAudioPlayout(true);
    this.peerConnection.setAudioRecording(true);
  }

  public void setAudioEnabled(boolean enabled) {
    this.audioTrack.setEnabled(enabled);
  }

  public DataChannel createDataChannel(String name) {
    DataChannel.Init dataChannelConfiguration = new DataChannel.Init();
    dataChannelConfiguration.ordered = true;

    return this.peerConnection.createDataChannel(name, dataChannelConfiguration);
  }

  public SessionDescription createOffer(MediaConstraints mediaConstraints) throws PeerConnectionException {
    final SettableFuture<SessionDescription> future = new SettableFuture<>();

    peerConnection.createOffer(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {
        future.set(sdp);
      }

      @Override
      public void onCreateFailure(String error) {
        future.setException(new PeerConnectionException(error));
      }

      @Override
      public void onSetSuccess() {
        throw new AssertionError();
      }

      @Override
      public void onSetFailure(String error) {
        throw new AssertionError();
      }
    }, mediaConstraints);

    try {
      return correctSessionDescription(future.get());
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new PeerConnectionException(e);
    }
  }

  public SessionDescription createAnswer(MediaConstraints mediaConstraints) throws PeerConnectionException {
    final SettableFuture<SessionDescription> future = new SettableFuture<>();

    peerConnection.createAnswer(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {
        future.set(sdp);
      }

      @Override
      public void onCreateFailure(String error) {
        future.setException(new PeerConnectionException(error));
      }

      @Override
      public void onSetSuccess() {
        throw new AssertionError();
      }

      @Override
      public void onSetFailure(String error) {
        throw new AssertionError();
      }
    }, mediaConstraints);

    try {
      return correctSessionDescription(future.get());
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new PeerConnectionException(e);
    }
  }

  public void setRemoteDescription(SessionDescription sdp) throws PeerConnectionException {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    peerConnection.setRemoteDescription(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {}

      @Override
      public void onCreateFailure(String error) {}

      @Override
      public void onSetSuccess() {
        future.set(true);
      }

      @Override
      public void onSetFailure(String error) {
        future.setException(new PeerConnectionException(error));
      }
    }, sdp);

    try {
      future.get();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new PeerConnectionException(e);
    }
  }

  public void setLocalDescription(SessionDescription sdp) throws PeerConnectionException {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    peerConnection.setLocalDescription(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {
        throw new AssertionError();
      }

      @Override
      public void onCreateFailure(String error) {
        throw new AssertionError();
      }

      @Override
      public void onSetSuccess() {
        future.set(true);
      }

      @Override
      public void onSetFailure(String error) {
        future.setException(new PeerConnectionException(error));
      }
    }, sdp);

    try {
      future.get();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new PeerConnectionException(e);
    }
  }

  public void dispose() {
    this.cameras.dispose();
    this.audioSource.dispose();
    this.peerConnection.close();
    this.peerConnection.dispose();
  }

  public boolean addIceCandidate(IceCandidate candidate) {
    return this.peerConnection.addIceCandidate(candidate);
  }


  private SessionDescription correctSessionDescription(SessionDescription sessionDescription) {
    String updatedSdp = sessionDescription.description.replaceAll("(a=fmtp:111 ((?!cbr=).)*)\r?\n", "$1;cbr=1\r\n");
    updatedSdp = updatedSdp.replaceAll(".+urn:ietf:params:rtp-hdrext:ssrc-audio-level.*\r?\n", "");

    return new SessionDescription(sessionDescription.type, updatedSdp);
  }

  public static class PeerConnectionException extends Exception {
    public PeerConnectionException(String error) {
      super(error);
    }

    public PeerConnectionException(Throwable throwable) {
      super(throwable);
    }
  }

  private static class Cameras {

    private final Map<CameraState.Direction, Camera> cameras;

    @Nullable
    private Camera  active;
    private boolean enabled;

    Cameras(@NonNull Context                 context,
            @NonNull PeerConnectionFactory   peerConnectionFactory,
            @NonNull VideoRenderer.Callbacks localRenderer)
    {
      Camera front = createCamera(context, peerConnectionFactory, localRenderer, FRONT);
      Camera back  = createCamera(context, peerConnectionFactory, localRenderer, BACK);

      cameras = new HashMap<>();

      if (front != null) cameras.put(FRONT, front);
      if (back != null)  cameras.put(BACK, back);

      if (front != null) {
        active = front;
      } else if (back != null) {
        active = back;
      }
    }

    void setEnabled(boolean enabled) {
      this.enabled = enabled;
      if (active != null) {
        active.setEnabled(enabled);
      }
    }

    boolean isEnabled() {
      return enabled;
    }

    void setDirection(@NonNull CameraState.Direction direction, @NonNull MediaStream mediaStream) {
      if (direction == NONE) {
        setEnabled(false);
        return;
      }

      if (!cameras.containsKey(direction)) {
        Log.w(TAG, "Tried to switch to " + direction.name() + " camera, but we don't have one.");
        return;
      }

      if (active != null) {
        setEnabled(false);
        mediaStream.removeTrack(active.track);
      }
      active = cameras.get(direction);
      mediaStream.addTrack(active.track);
      setEnabled(true);
    }

    void dispose() {
      if (cameras.containsKey(FRONT)) cameras.get(FRONT).dispose();
      if (cameras.containsKey(BACK))  cameras.get(BACK).dispose();
    }

    int getCount() {
      return cameras.size();
    }

    CameraState.Direction getActiveDirection() {
      if (cameras.containsKey(FRONT) && active == cameras.get(FRONT)) return FRONT;
      if (cameras.containsKey(BACK) && active == cameras.get(BACK))   return BACK;
      return NONE;
    }

    @Nullable
    VideoTrack getTrack() {
      return active != null ? active.track : null;
    }

    private Camera createCamera(@NonNull Context                 context,
                                @NonNull PeerConnectionFactory   peerConnectionFactory,
                                @NonNull VideoRenderer.Callbacks localRenderer,
                                @NonNull CameraState.Direction   direction)
    {
      VideoCapturer capturer = createVideoCapturer(context, direction);
      if (capturer == null) {
        Log.w(TAG, "Failed to find a " + direction.name() + " camera");
        return null;
      }

      VideoSource source = peerConnectionFactory.createVideoSource(capturer);
      VideoTrack  track  = peerConnectionFactory.createVideoTrack("ARDAMSv0", source);
      track.addRenderer(new VideoRenderer(localRenderer));

      return new Camera(capturer, source, track);
    }

    @Nullable
    private CameraVideoCapturer createVideoCapturer(@NonNull Context context, CameraState.Direction direction) {
      boolean camera2EnumeratorIsSupported = false;
      try {
        camera2EnumeratorIsSupported = Camera2Enumerator.isSupported(context);
      } catch (final Throwable throwable) {
        Log.w(TAG, "Camera2Enumator.isSupport() threw.", throwable);
      }

      Log.w(TAG, "Camera2 enumerator supported: " + camera2EnumeratorIsSupported);
      CameraEnumerator enumerator;

      enumerator = camera2EnumeratorIsSupported ? new Camera2Enumerator(context)
                                                : new Camera1Enumerator(true);

      String[] deviceNames = enumerator.getDeviceNames();
      for (String deviceName : deviceNames) {
        if ((direction == FRONT && enumerator.isFrontFacing(deviceName)) ||
            (direction == BACK  && enumerator.isBackFacing(deviceName)))
        {
          return enumerator.createCapturer(deviceName, null);
        }
      }

      return null;
    }
  }

  private static class Camera {

    final VideoCapturer capturer;
    final VideoSource   source;
    final VideoTrack    track;

    Camera(@NonNull VideoCapturer capturer, @NonNull VideoSource source, @NonNull VideoTrack track) {
      this.capturer = capturer;
      this.source   = source;
      this.track    = track;
    }

    void setEnabled(boolean enabled) {
      track.setEnabled(enabled);
      try {
        if (enabled) {
          capturer.startCapture(1280, 720, 30);
        } else {
          capturer.stopCapture();
        }
      } catch (InterruptedException e) {
        Log.w(TAG, "Got interrupted while trying to stop video capture", e);
      }
    }

    void dispose() {
      setEnabled(false);
      capturer.dispose();
      source.dispose();
    }
  }
}

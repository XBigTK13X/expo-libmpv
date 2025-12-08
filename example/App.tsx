import * as React from 'react';
import { findNodeHandle } from 'react-native'
import { View, Pressable, Modal, TouchableOpacity, AppState, Text } from 'react-native';
import LibmpvView, { DEFAULT_DECODING_MODE, DEFAULT_ACCELERATED_CODECS } from 'expo-libmpv';

const circularReplacer = () => {
  const seen = new WeakSet();
  return (key, value) => {
    if (typeof value === 'object' && value !== null) {
      if (seen.has(value)) {
        return; // Break the cycle
      }
      seen.add(value);
    }
    return value;
  };
};

const DEBUG_EVENTS = true

const TRACK_DISABLED = -1;
const FIVE_MINUTES = 300;

const animeUrl = 'http://juggernaut.9914.us/tv/anime/collection/precure/Star ☆ Twinkle Precure/Season 1/S01E006 - An Imagination of Darkness! The Dark Pen Appears!.mkv'
const cartoonSubbedUrl = 'http://juggernaut.9914.us/tv/cartoon/k/King of the Hill/Season 14/S14E003 - Chore Money, Chore Problems.mkv'
const videoUrl = animeUrl;
let audioTrack = 0
let subtitleTrack = 0

const resolutions = {
  ultraHd: {
    width: 3840,
    height: 2160
  },
  fullHd: {
    width: 1920,
    height: 1080
  }
}

const styles = {
  homePage: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'black'
  },
  homeButton: {
    width: '75%'
  },
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'black'
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
  video: {
    flex: 1
  },
  button: {
    height: 250,
    backgroundColor: 'blue',
    textAlign: 'center',
    alignItems: 'center',
    justifyContent: 'center'
  },
  buttonText: {
    color: 'white',
    fontSize: 60
  }
}

function LandingPage({ setPage }) {
  return (
    <View style={styles.homePage}>
      <View style={styles.homeButton}>
        <Pressable style={styles.button} onPress={() => { setPage('video') }}>
          <Text style={styles.buttonText}>Play Video</Text>
        </Pressable>
      </View>
    </View>
  )
}


function VideoPage({ setPage }) {
  const [isPlaying, setIsPlaying] = React.useState(true);
  const [loadError, setError] = React.useState('')
  const [seekSeconds, setSeekSeconds] = React.useState(FIVE_MINUTES)
  const [subScale, setSubScale] = React.useState(1.0)

  const nativeRef = React.useRef(null);

  React.useEffect(() => {
    const appStateSubscription = AppState.addEventListener('change', appState => {
      if (appState === 'background') {
        console.log("Cleanup background")
        setPage('home')
      }
    });

    return () => {
      appStateSubscription.remove();
    };
  }, []);

  React.useEffect(() => {
    nativeRef.current.runCommand(`set|sub-scale|${subScale}`)
  }, [subScale])

  if (loadError) {
    return <Text>{loadError}</Text>
  }

  function onLibmpvEvent(libmpvEvent) {
    if (DEBUG_EVENTS) {
      if (!libmpvEvent.property && libmpvEvent.property !== 'track-list' && libmpvEvent?.property !== 'demuxer-cache-time') {
        console.log(JSON.stringify({ libmpvEvent }, circularReplacer(), 4))
      }
    }
  }

  function onLibmpvLog(libmpvLog) {
    if (DEBUG_EVENTS) {
      if (libmpvLog.hasOwnProperty('method')) {
        console.log("=-=-=-=-=-=-==- NATIVE METHOD =-=-=-=--==-=")
      }
    }
    if (libmpvLog.text && libmpvLog.text.indexOf('Opening failed or was aborted') !== -1) {
      setError("Unable to open file.")
    }
    if (libmpvLog.text && libmpvLog.prefix === 'vd' && libmpvLog.text.indexOf('Using software decoding') !== -1) {
      //setError("Unable to use hardware decoding!.")
    }
    if (DEBUG_EVENTS) {
      console.log(JSON.stringify({ libmpvLog }, circularReplacer(), 4))
    }
  }

  const onPress = async () => {
    setIsPlaying(!isPlaying)
    if (nativeRef.current) {
      console.log("=-=-=-=-=-=-=- Running command =-=-=-=-=-=-")
      setSubScale((prev) => { return prev + 0.1 });
    }
  }

  console.log({ videoUrl, DEFAULT_DECODING_MODE, DEFAULT_ACCELERATED_CODECS })
  const viewKey = React.useMemo(
    () => `${videoUrl}-${Date.now()}`,
    []
  );
  return (
    <Modal style={styles.container} onRequestClose={() => {
      setPage('home')
    }}>
      <TouchableOpacity
        transparent
        style={styles.video}
        onPress={onPress} >
        <LibmpvView
          ref={nativeRef}
          key={viewKey}
          videoOutput="gpu"
          isPlaying={isPlaying}
          playUrl={videoUrl}
          decodingMode={DEFAULT_DECODING_MODE}
          acceleratedCodecs={DEFAULT_ACCELERATED_CODECS}
          surfaceWidth={resolutions.fullHd.width}
          surfaceHeight={resolutions.fullHd.height}
          selectedAudioTrack={audioTrack}
          selectedSubtitleTrack={subtitleTrack}
          seekToSeconds={seekSeconds}
          onLibmpvEvent={onLibmpvEvent}
          onLibmpvLog={onLibmpvLog}
        />
      </TouchableOpacity>
    </Modal>
  )
}


export default function App() {
  //const surfaceRef = React.useRef(null);
  //const [playing, setPlaying] = React.useState(false)

  const [page, setPage] = React.useState('home')

  if (page === 'home') {
    return <LandingPage setPage={setPage} />
  }

  return <VideoPage setPage={setPage} />
}


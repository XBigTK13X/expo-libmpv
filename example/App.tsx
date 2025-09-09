import * as React from 'react';
import { View, Button, Modal, TouchableOpacity, AppState, Text } from 'react-native';
import { LibmpvVideo } from 'expo-libmpv';

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

const TRACK_DISABLED = -1;

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
    width: '75%',
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
  button: {
    flex: 1,
    backgroundColor: 'black'
  }
}

function LandingPage({ setPage }) {
  return (
    <View style={styles.homePage}>
      <View style={styles.homeButton}>
        <Button onPress={() => { setPage('video') }} title="Play Video" />
      </View>
    </View>
  )
}


function VideoPage({ setPage }) {
  const [isPlaying, setIsPlaying] = React.useState(true);
  const [seekSeconds, setSeekSeconds] = React.useState(0)
  const [loadError, setError] = React.useState('')
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

  if (loadError) {
    return <Text>{loadError}</Text>
  }

  function onLibmpvEvent(libmpvEvent) {
    if (!libmpvEvent.property || libmpvEvent.property !== 'track-list') {
      console.log(JSON.stringify({ libmpvEvent }, circularReplacer(), 4))
    }
  }

  function onLibmpvLog(libmpvLog) {
    if (libmpvLog.hasOwnProperty('method')) {
      console.log("=-=-=-=-=-=-==- NATIVE METHOD =-=-=-=--==-=")
    }
    if (seekSeconds === 0 && libmpvLog.text && libmpvLog.text.indexOf('Starting playback') !== -1) {
      //setSeekSeconds(300)
    }
    if (libmpvLog.text && libmpvLog.text.indexOf('Opening failed or was aborted') !== -1) {
      setError("Unable to open file.")
    }
    if (libmpvLog.text && libmpvLog.prefix === 'vd' && libmpvLog.text.indexOf('Using software decoding') !== -1) {
      //setError("Unable to use hardware decoding!.")
    }

    console.log(JSON.stringify({ libmpvLog }, circularReplacer(), 4))
  }

  const onPress = () => {
    setIsPlaying(!isPlaying)
    if (nativeRef.current) {
      console.log("=-=-=-=-=-=-=- Running command =-=-=-=-=-=-")
      nativeRef.current.runCommand(`set|sub-ass-override|force`);
      nativeRef.current.runCommand(`set|sub-font-size|${20 + Math.floor(Math.random() * 10)}`)
    }
  }

  const animeUrl = 'http://juggernaut.9914.us/tv/anime/precure/Star ☆ Twinkle Precure/Season 1/S01E006 - An Imagination of Darkness! The Dark Pen Appears!.mkv'
  const videoUrl = animeUrl;
  console.log({ videoUrl })
  return (
    <Modal style={styles.container} onRequestClose={() => {
      setPage('home')
    }}>
      <TouchableOpacity
        transparent
        style={styles.button}
        onPress={onPress} >
        <LibmpvVideo
          ref={nativeRef}
          isPlaying={isPlaying}
          playUrl={videoUrl}
          useHardwareDecoder={true}
          surfaceWidth={resolutions.fullHd.width}
          surfaceHeight={resolutions.fullHd.height}
          selectedAudioTrack={0}
          selectedSubtitleTrack={0}
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


import { useState, useEffect } from 'react'
import './App.css'

interface Camera {
  id: string;
  name: string;
  zone: string;
  status: 'online' | 'offline';
}

function App() {
  const [cameras, setCameras] = useState<Camera[]>([])
  const [activeCameraId, setActiveCameraId] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/cameras')
      .then((res) => res.json())
      .then((data) => {
        setCameras(data);
        if (data.length > 0) {
          setActiveCameraId(data[0].id);
        }
      })
      .catch((err) => {
        console.error('Error fetching cameras:', err);
      });
  }, [])

  const activeCamera = cameras.find(c => c.id === activeCameraId)

  return (
    <div className="dashboard">
      <header>
        <h1>CRT-angarine Dashboard</h1>
      </header>
      
      <main>
        <aside className="camera-list">
          <h2>Cameras</h2>
          {cameras.length === 0 ? (
            <p>No cameras found.</p>
          ) : (
            <ul>
              {cameras.map(camera => (
                <li 
                  key={camera.id} 
                  className={activeCameraId === camera.id ? 'active' : ''}
                  onClick={() => setActiveCameraId(camera.id)}
                >
                  {camera.name} ({camera.zone})
                </li>
              ))}
            </ul>
          )}
        </aside>

        <section className="feed-viewer">
          {activeCamera ? (
            <div className="feed-container">
              <h2>{activeCamera.name} - {activeCamera.zone}</h2>
              <div className="placeholder-feed">
                [ CAMERA FEED PLACEHOLDER ]
              </div>
            </div>
          ) : (
            <div className="no-selection">
              Select a camera to view feed
            </div>
          )}
        </section>
      </main>
    </div>
  )
}

export default App

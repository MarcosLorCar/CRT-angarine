import { useState, useEffect } from 'react'
import './App.css'

interface Camera {
  id: string;
  name: string;
  x: number;
  y: number;
  z: number;
  isOnline: boolean;
}

function App() {
  const [token, setToken] = useState<string | null>(localStorage.getItem('auth_token'))
  const [loginInput, setLoginInput] = useState('')
  const [loginError, setLoginError] = useState('')
  const [isLoggingIn, setIsLoggingIn] = useState(false)
  
  const [cameras, setCameras] = useState<Camera[]>([])
  const [activeCameraId, setActiveCameraId] = useState<string | null>(null)

  useEffect(() => {
    if (!token) return;
    
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
  }, [token])

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!loginInput.trim()) return;
    setIsLoggingIn(true);
    setLoginError('');
    
    try {
      const res = await fetch('/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: loginInput })
      });
      if (res.ok) {
        setToken(loginInput);
        localStorage.setItem('auth_token', loginInput);
      } else {
        setLoginError('Authentication failed.');
      }
    } catch (err) {
      setLoginError('Could not reach the server.');
    } finally {
      setIsLoggingIn(false);
    }
  }
  
  const handleLogout = () => {
    setToken(null);
    localStorage.removeItem('auth_token');
    setCameras([]);
    setActiveCameraId(null);
  }

  if (!token) {
    return (
      <div className="login-container">
        <form className="login-form" onSubmit={handleLogin}>
          <h2>CRT-angarine Access</h2>
          <p>Please enter your access token</p>
          <input 
            type="text" 
            value={loginInput} 
            onChange={e => setLoginInput(e.target.value)} 
            placeholder="Token..."
            disabled={isLoggingIn}
          />
          {loginError && <div className="error">{loginError}</div>}
          <button type="submit" disabled={isLoggingIn || !loginInput.trim()}>
            {isLoggingIn ? 'Connecting...' : 'Connect'}
          </button>
        </form>
      </div>
    )
  }

  const activeCamera = cameras.find(c => c.id === activeCameraId)

  return (
    <div className="dashboard">
      <header>
        <h1>CRT-angarine Dashboard</h1>
        <div className="header-controls">
          <span className="auth-status">Authenticated</span>
          <button onClick={handleLogout} className="logout-btn">Log out</button>
        </div>
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
                  <div className="camera-name">
                    <span className={`status-dot ${camera.isOnline ? 'online' : 'offline'}`}></span>
                    {camera.name}
                  </div>
                  <div className="camera-zone">
                    [{Math.round(camera.x)}, {Math.round(camera.y)}, {Math.round(camera.z)}]
                  </div>
                </li>
              ))}
            </ul>
          )}
        </aside>

        <section className="feed-viewer">
          {activeCamera ? (
            <div className="feed-container">
              <h2>{activeCamera.name} - [{Math.round(activeCamera.x)}, {Math.round(activeCamera.y)}, {Math.round(activeCamera.z)}]</h2>
              <div className="placeholder-feed">
                {activeCamera.isOnline ? '[ CAMERA FEED PLACEHOLDER ]' : '[ CAMERA OFFLINE ]'}
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

import React, { useState, useEffect, useRef } from 'react'
import { type CameraData } from './shared-types'
import { Login } from './components/Login'
import { Sidebar } from './components/Sidebar'
import { Viewport } from './components/Viewport'
import './App.css'

function App() {
  const [token, setToken] = useState<string | null>(() => {
    const params = new URLSearchParams(window.location.search);
    const urlToken = params.get('token');
    if (urlToken) {
      localStorage.setItem('auth_token', urlToken);
      // Clean up URL parameters so it doesn't linger in the address bar
      window.history.replaceState({}, document.title, window.location.pathname);
      return urlToken;
    }
    return localStorage.getItem('auth_token');
  })
  const [loginInput, setLoginInput] = useState('')
  const [loginError, setLoginError] = useState('')
  const [isLoggingIn, setIsLoggingIn] = useState(false)
  
  const [cameras, setCameras] = useState<CameraData[]>([])
  const [activeCameraId, setActiveCameraId] = useState<string | null>(() => {
    return localStorage.getItem('active_camera_id');
  })
  
  const activeCameraIdRef = useRef(activeCameraId)
  useEffect(() => {
    if (activeCameraId) {
      localStorage.setItem('active_camera_id', activeCameraId);
    } else {
      localStorage.removeItem('active_camera_id');
    }
    activeCameraIdRef.current = activeCameraId;
  }, [activeCameraId])

  // Synchronize registered cameras via WebSocket (no polling)
  useEffect(() => {
    if (!token) return;
    
    let active = true;
    let ws: WebSocket | null = null;
    let reconnectTimeout: any = null;

    const connect = () => {
      if (!active) return;
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = `${protocol}//${window.location.host}/api/webapp/registry?token=${encodeURIComponent(token)}`;
      
      console.log('Connecting to registry websocket...', wsUrl);
      ws = new WebSocket(wsUrl);

      ws.onmessage = (event) => {
        if (!active) return;
        try {
          const data = JSON.parse(event.data);
          if (Array.isArray(data)) {
            setCameras(data);
            
            const currentActiveId = activeCameraIdRef.current;
            // Manage active camera selection
            if (data.length === 0) {
              setActiveCameraId(null);
            } else {
              // If activeCameraId is set but no longer exists in the new list, switch to the first camera
              if (currentActiveId && !data.some(c => c.id === currentActiveId)) {
                setActiveCameraId(data[0].id);
              } else if (!currentActiveId) {
                setActiveCameraId(data[0].id);
              }
            }
          }
        } catch (err) {
          console.error('Failed to parse registry update message:', err);
        }
      };

      ws.onerror = (err) => {
        console.error('Registry websocket error:', err);
      };

      ws.onclose = (event) => {
        console.log('Registry websocket closed. Reconnecting in 3s...', event.code, event.reason);
        // Code 1008 is standard WebSocket code for VIOLATED_POLICY (Unauthorized or missing parameters)
        if (event.code === 1008) {
          console.warn("Registry authorization rejected. Logging out.");
          handleLogout();
          return;
        }
        if (active) {
          reconnectTimeout = setTimeout(connect, 3000);
        }
      };
    };

    connect();

    return () => {
      active = false;
      if (ws) {
        ws.close();
      }
      if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
      }
    };
  }, [token])

  // Login handler
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
  
  // Logout handler
  const handleLogout = () => {
    setToken(null);
    localStorage.removeItem('auth_token');
    setCameras([]);
    setActiveCameraId(null);
  }



  const activeCamera = cameras.find(c => c.id === activeCameraId);

  if (!token) {
    return (
      <div className="dashboard-wrapper">
        <div className="scanlines-overlay"></div>
        <div className="vignette-overlay"></div>
        <Login
          loginInput={loginInput}
          setLoginInput={setLoginInput}
          loginError={loginError}
          isLoggingIn={isLoggingIn}
          handleLogin={handleLogin}
        />
      </div>
    )
  }

  return (
    <div className="dashboard-wrapper">
      <div className="scanlines-overlay"></div>
      <div className="vignette-overlay"></div>
      
      <header>
        <h1>CRT-angarine Matrix</h1>
        <div className="header-controls">
          <span className="auth-status">LINK SECURE</span>
          <button onClick={handleLogout} className="logout-btn">DISCONNECT</button>
        </div>
      </header>
      
      <div className="dashboard-main">
        <Sidebar
          cameras={cameras}
          activeCameraId={activeCameraId}
          setActiveCameraId={setActiveCameraId}
        />
        <Viewport
          token={token}
          activeCamera={activeCamera}
        />
      </div>
    </div>
  )
}

export default App

import React, { useState, useEffect, useRef } from 'react'
import { type CameraData } from './shared-types'
import { Login } from './components/Login'
import { Sidebar } from './components/Sidebar'
import { Viewport } from './components/Viewport'
import './App.css'

function App() {
  const [token, setToken] = useState<string | null>(() => {
    return localStorage.getItem('auth_token');
  })
  const [loginUsername, setLoginUsername] = useState('')
  const [loginPassword, setLoginPassword] = useState('')
  const [loginError, setLoginError] = useState('')
  const [isLoggingIn, setIsLoggingIn] = useState(false)

  // URL Auto-login parameter check on mount
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const urlToken = params.get('token');
    const urlUsername = params.get('username');
    if (urlToken) {
      window.history.replaceState({}, document.title, window.location.pathname);
      handleLogin(undefined, urlUsername || '', urlToken);
    }
  }, []);
  
  const [cameras, setCameras] = useState<CameraData[]>([])
  const [isSidebarOpen, setIsSidebarOpen] = useState(false)
  const [activeCameraId, setActiveCameraId] = useState<string | null>(() => {
    return localStorage.getItem('active_camera_id');
  })
  
  const activeCameraIdRef = useRef(activeCameraId)

  const [isFullscreen, setIsFullscreen] = useState(false);

  const toggleFullscreen = async () => {
    const elem = document.documentElement;
    try {
      if (!document.fullscreenElement && !(document as any).webkitFullscreenElement) {
        if (elem.requestFullscreen) {
          await elem.requestFullscreen();
        } else if ((elem as any).webkitRequestFullscreen) {
          await (elem as any).webkitRequestFullscreen();
        }
        setIsFullscreen(true);
        if (window.screen && window.screen.orientation && 'lock' in window.screen.orientation) {
          await window.screen.orientation.lock('landscape').catch(err => {
            console.warn('Orientation lock failed:', err);
          });
        }
      } else {
        if (document.exitFullscreen) {
          await document.exitFullscreen();
        } else if ((document as any).webkitExitFullscreen) {
          await (document as any).webkitExitFullscreen();
        }
        setIsFullscreen(false);
        if (window.screen && window.screen.orientation && 'unlock' in window.screen.orientation) {
          window.screen.orientation.unlock();
        }
      }
    } catch (err) {
      console.error('Fullscreen toggle error:', err);
    }
  };

  useEffect(() => {
    const handleFullscreenChange = () => {
      const isCurrentlyFullscreen = !!(document.fullscreenElement || (document as any).webkitFullscreenElement);
      setIsFullscreen(isCurrentlyFullscreen);
      if (!isCurrentlyFullscreen && window.screen && window.screen.orientation && 'unlock' in window.screen.orientation) {
        window.screen.orientation.unlock();
      }
    };

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    document.addEventListener('webkitfullscreenchange', handleFullscreenChange);
    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange);
      document.removeEventListener('webkitfullscreenchange', handleFullscreenChange);
    };
  }, []);

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
  const handleLogin = async (e?: React.FormEvent, overrideUsername?: string, overrideToken?: string) => {
    if (e) e.preventDefault();
    const u = overrideUsername !== undefined ? overrideUsername : loginUsername;
    const p = overrideToken !== undefined ? overrideToken : loginPassword;
    
    if (!p.trim()) return;
    setIsLoggingIn(true);
    setLoginError('');
    
    try {
      const payload: any = {};
      if (u.trim()) {
        payload.username = u.trim();
        payload.password = p.trim();
      } else {
        payload.token = p.trim();
      }

      const res = await fetch('/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (res.ok) {
        const sessionToken = data.token;
        setToken(sessionToken);
        localStorage.setItem('auth_token', sessionToken);
      } else {
        setLoginError(data.message || 'Authentication failed. Check your credentials.');
      }
    } catch (err) {
      setLoginError('Server is offline or unreachable. Please check your network connection.');
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
          loginUsername={loginUsername}
          setLoginUsername={setLoginUsername}
          loginPassword={loginPassword}
          setLoginPassword={setLoginPassword}
          loginError={loginError}
          isLoggingIn={isLoggingIn}
          handleLogin={handleLogin}
        />
      </div>
    )
  }

  return (
    <div className={`dashboard-wrapper ${isFullscreen ? 'app-fullscreen' : ''} ${isSidebarOpen ? 'menu-open' : ''}`}>
      <div className="scanlines-overlay"></div>
      <div className="vignette-overlay"></div>
      
      {isSidebarOpen && (
        <div className="sidebar-backdrop" onClick={() => setIsSidebarOpen(false)}></div>
      )}

      <button onClick={() => setIsSidebarOpen(true)} className="floating-menu-btn" aria-label="Open Menu">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <line x1="3" y1="12" x2="21" y2="12" />
          <line x1="3" y1="6" x2="21" y2="6" />
          <line x1="3" y1="18" x2="21" y2="18" />
        </svg>
      </button>

      <header>
        <div className="header-title-group">
          <button onClick={() => setIsSidebarOpen(!isSidebarOpen)} className="sidebar-toggle-btn" aria-label="Toggle Menu">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </svg>
          </button>
          <h1>CRT-angarine Matrix</h1>
        </div>
        <div className="header-controls">
          <span className="auth-status">LINK SECURE</span>
          <button onClick={toggleFullscreen} className="fullscreen-btn" title="Toggle Fullscreen">
            {isFullscreen ? (
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M4 10h6V4" />
                <path d="M20 10h-6V4" />
                <path d="M4 14h6v6" />
                <path d="M20 14h-6v6" />
              </svg>
            ) : (
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M8 3H5a2 2 0 0 0-2 2v3" />
                <path d="M16 3h3a2 2 0 0 1 2 2v3" />
                <path d="M8 21H5a2 2 0 0 1-2-2v-3" />
                <path d="M16 21h3a2 2 0 0 0 2-2v-3" />
              </svg>
            )}
          </button>
        </div>
      </header>
      
      <div className="dashboard-main">
        <Sidebar
          cameras={cameras}
          activeCameraId={activeCameraId}
          setActiveCameraId={setActiveCameraId}
          isOpen={isSidebarOpen}
          onClose={() => setIsSidebarOpen(false)}
          onLogout={handleLogout}
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

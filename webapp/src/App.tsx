import React, { useState, useEffect, useRef } from 'react'
import * as THREE from 'three'
import './App.css'

interface Camera {
  id: string;
  name: string;
  x: number;
  y: number;
  z: number;
  isOnline: boolean;
}

interface BlockData {
  x: number;
  y: number;
  z: number;
  stateId: number;
}

interface EntityData {
  id: string;
  type: string; // PLAYER, MONSTER, PASSIVE, ITEM
  x: number;
  y: number;
  z: number;
  yaw: number;
  pitch: number;
}

// CRT Shader code
const crtVertexShader = `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position, 1.0);
  }
`;

const crtFragmentShader = `
  uniform sampler2D tDiffuse;
  uniform float uTime;
  uniform bool uCrtEnabled;
  varying vec2 vUv;

  vec2 curve(vec2 uv) {
    if (!uCrtEnabled) return uv;
    uv = (uv - 0.5) * 2.0;
    uv.x *= 1.0 + pow((abs(uv.y) / 6.0), 2.0);
    uv.y *= 1.0 + pow((abs(uv.x) / 5.0), 2.0);
    uv = (uv / 2.0) + 0.5;
    return uv;
  }

  void main() {
    vec2 uv = curve(vUv);
    
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
      gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
      return;
    }

    float shift = uCrtEnabled ? 0.0015 : 0.0;
    vec4 col;
    col.r = texture2D(tDiffuse, vec2(uv.x + shift, uv.y)).r;
    col.g = texture2D(tDiffuse, uv).g;
    col.b = texture2D(tDiffuse, vec2(uv.x - shift, uv.y)).b;
    col.a = 1.0;

    if (uCrtEnabled) {
      // Scanlines (tuned down slightly to reduce CPU/GPU workload)
      float scanline = sin(uv.y * 400.0) * 0.04;
      col.rgb -= scanline;

      // Subtle Noise
      float noise = (fract(sin(dot(uv * uTime, vec2(12.9898, 78.233))) * 43758.5453)) * 0.015;
      col.rgb += noise;

      // Phosphor tint
      col.rgb *= vec3(0.9, 1.1, 0.9);
      
      // Vignette
      float vig = uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y);
      vig = clamp(pow(16.0 * vig, 0.25), 0.0, 1.0);
      col.rgb *= vig;
    }

    gl_FragColor = col;
  }
`;

function App() {
  const [token, setToken] = useState<string | null>(localStorage.getItem('auth_token'))
  const [loginInput, setLoginInput] = useState('')
  const [loginError, setLoginError] = useState('')
  const [isLoggingIn, setIsLoggingIn] = useState(false)
  
  const [cameras, setCameras] = useState<Camera[]>([])
  const [activeCameraId, setActiveCameraId] = useState<string | null>(null)
  
  // HUD states
  const [blocksStreamed, setBlocksStreamed] = useState(0)
  const [activeEntitiesCount, setActiveEntitiesCount] = useState(0)
  const [latency, setLatency] = useState(0)
  
  // Customization states
  const [isAudioOn, setIsAudioOn] = useState(false)
  const [isCrtEnabled, setIsCrtEnabled] = useState(true)
  const [renderMode, setRenderMode] = useState<'solid' | 'wireframe' | 'radar'>('solid')
  
  // References for WebAudio
  const audioCtxRef = useRef<AudioContext | null>(null)
  const oscRef = useRef<OscillatorNode | null>(null)
  const oscSawRef = useRef<OscillatorNode | null>(null)
  const gainRef = useRef<GainNode | null>(null)

  // Webgl/canvas references
  const containerRef = useRef<HTMLDivElement>(null)
  const rendererRef = useRef<THREE.WebGLRenderer | null>(null)
  const mainCameraRef = useRef<THREE.PerspectiveCamera | null>(null)
  
  // Dynamic 3D Object references
  const solidMeshRef = useRef<THREE.InstancedMesh | null>(null)
  const hazardMeshRef = useRef<THREE.InstancedMesh | null>(null)
  const interactableMeshRef = useRef<THREE.InstancedMesh | null>(null)
  const entitiesGroupRef = useRef<THREE.Group | null>(null)
  
  // Time and Shader references
  const uTimeRef = useRef({ value: 0.0 })
  const crtMaterialRef = useRef<THREE.ShaderMaterial | null>(null)

  // Sync renderMode to Ref to prevent websocket reconnect churn
  const renderModeRef = useRef(renderMode);
  useEffect(() => {
    renderModeRef.current = renderMode;
  }, [renderMode]);

  // Fetch registered cameras
  useEffect(() => {
    if (!token) return;
    
    let active = true;
    fetch('/api/cameras', {
      headers: { 'Authorization': token }
    })
      .then((res) => {
        if (!res.ok) {
          throw new Error('Unauthorized or invalid token');
        }
        return res.json();
      })
      .then((data) => {
        if (!active) return;
        if (Array.isArray(data)) {
          setCameras(data);
          if (data.length > 0) {
            setActiveCameraId(data[0].id);
          }
        } else {
          console.error('Expected cameras array but received:', data);
          setToken(null);
          localStorage.removeItem('auth_token');
          setCameras([]);
          setActiveCameraId(null);
        }
      })
      .catch((err) => {
        if (!active) return;
        console.error('Error fetching cameras:', err);
        setToken(null);
        localStorage.removeItem('auth_token');
        setCameras([]);
        setActiveCameraId(null);
      });

    return () => {
      active = false;
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
    if (isAudioOn) toggleAudio();
    setToken(null);
    localStorage.removeItem('auth_token');
    setCameras([]);
    setActiveCameraId(null);
  }

  // Audio Toggle (Retro CRT Hum)
  const toggleAudio = () => {
    if (isAudioOn) {
      gainRef.current?.gain.exponentialRampToValueAtTime(0.0001, audioCtxRef.current!.currentTime + 0.3);
      setTimeout(() => {
        oscRef.current?.stop();
        oscSawRef.current?.stop();
        audioCtxRef.current?.close();
        audioCtxRef.current = null;
        oscRef.current = null;
        oscSawRef.current = null;
      }, 350);
      setIsAudioOn(false);
    } else {
      try {
        const ctx = new (window.AudioContext || (window as any).webkitAudioContext)();
        audioCtxRef.current = ctx;

        const osc = ctx.createOscillator();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(60, ctx.currentTime); // 60Hz mains hum

        const oscSaw = ctx.createOscillator();
        oscSaw.type = 'sawtooth';
        oscSaw.frequency.setValueAtTime(180, ctx.currentTime); // 180Hz harmonic

        const filter = ctx.createBiquadFilter();
        filter.type = 'lowpass';
        filter.frequency.setValueAtTime(90, ctx.currentTime);

        const gain = ctx.createGain();
        gain.gain.setValueAtTime(0.0001, ctx.currentTime);

        osc.connect(filter);
        oscSaw.connect(filter);
        filter.connect(gain);
        gain.connect(ctx.destination);

        osc.start();
        oscSaw.start();
        gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.3); // low hum volume

        oscRef.current = osc;
        oscSawRef.current = oscSaw;
        gainRef.current = gain;
        setIsAudioOn(true);
      } catch (e) {
        console.error('WebAudio initialization failed:', e);
      }
    }
  }

  // Active camera object
  const activeCamera = cameras.find(c => c.id === activeCameraId);

  // Initialize WebGL context exactly ONCE when token is active
  useEffect(() => {
    if (!token || !containerRef.current) return;

    const width = containerRef.current.clientWidth;
    const height = containerRef.current.clientHeight;

    // Renderer (Antialiasing disabled, capped pixel ratio for performance)
    const renderer = new THREE.WebGLRenderer({ antialias: false });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    containerRef.current.innerHTML = '';
    containerRef.current.appendChild(renderer.domElement);
    rendererRef.current = renderer;

    // Scene
    const scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0x000800, 0.015);

    // Camera (Fixed at origin lens offset)
    const camera = new THREE.PerspectiveCamera(65, width / height, 0.1, 100);
    camera.position.set(0, 0.2, 0);
    mainCameraRef.current = camera;

    // Lighting
    const ambientLight = new THREE.AmbientLight(0x114411, 0.8);
    scene.add(ambientLight);

    const dirLight = new THREE.DirectionalLight(0x33ff33, 1.2);
    dirLight.position.set(10, 20, 10);
    scene.add(dirLight);

    // Instanced meshes
    const maxInstances = 8000;
    const voxelGeom = new THREE.BoxGeometry(1.0, 1.0, 1.0);

    const customizeMaterial = (material: THREE.Material) => {
      material.onBeforeCompile = (shader) => {
        // Inject varying definition in vertex shader header
        shader.vertexShader = shader.vertexShader.replace(
          'void main() {',
          `varying float vDepth;
          void main() {`
        );
        // Inject vDepth calculation in vertex shader body after project_vertex
        shader.vertexShader = shader.vertexShader.replace(
          '#include <project_vertex>',
          `#include <project_vertex>
          vDepth = -mvPosition.z;`
        );
        // Inject varying in fragment shader header
        shader.fragmentShader = shader.fragmentShader.replace(
          'void main() {',
          `varying float vDepth;
          void main() {`
        );
        // Inject depth-based color scaling right before closing brace of main
        shader.fragmentShader = shader.fragmentShader.replace(
          /\}\s*$/,
          `  float maxDist = 24.0;
             float depthFactor = 1.0 - clamp(vDepth / maxDist, 0.0, 1.0);
             depthFactor = pow(depthFactor, 1.8);
             gl_FragColor.rgb *= depthFactor;
             gl_FragColor.a *= depthFactor;
          }`
        );
      };
    };

    const solidMat = new THREE.MeshLambertMaterial({
      color: 0x22ee22,
      wireframe: renderModeRef.current === 'wireframe',
      transparent: true,
      opacity: renderModeRef.current === 'solid' ? 0.8 : 0.2
    });
    customizeMaterial(solidMat);
    const solidMesh = new THREE.InstancedMesh(voxelGeom, solidMat, maxInstances);
    solidMesh.count = 0;
    solidMesh.frustumCulled = false;
    scene.add(solidMesh);
    solidMeshRef.current = solidMesh;

    const hazardMat = new THREE.MeshLambertMaterial({
      color: 0xff2222,
      wireframe: renderModeRef.current === 'wireframe',
      transparent: true,
      opacity: renderModeRef.current === 'solid' ? 0.85 : 0.3
    });
    customizeMaterial(hazardMat);
    const hazardMesh = new THREE.InstancedMesh(voxelGeom, hazardMat, maxInstances);
    hazardMesh.count = 0;
    hazardMesh.frustumCulled = false;
    scene.add(hazardMesh);
    hazardMeshRef.current = hazardMesh;

    const interactableMat = new THREE.MeshLambertMaterial({
      color: 0x22aaff,
      wireframe: renderModeRef.current === 'wireframe',
      transparent: true,
      opacity: renderModeRef.current === 'solid' ? 0.85 : 0.3
    });
    customizeMaterial(interactableMat);
    const interactableMesh = new THREE.InstancedMesh(voxelGeom, interactableMat, maxInstances);
    interactableMesh.count = 0;
    interactableMesh.frustumCulled = false;
    scene.add(interactableMesh);
    interactableMeshRef.current = interactableMesh;

    // Entities
    const entitiesGroup = new THREE.Group();
    scene.add(entitiesGroup);
    entitiesGroupRef.current = entitiesGroup;

    // CRT Post process Shader Pass
    // Initialize render target safely (fallback to 1x1 if width/height is 0, will resize in loop)
    const renderTarget = new THREE.WebGLRenderTarget(width || 1, height || 1);
    const crtScene = new THREE.Scene();
    const crtOrthoCamera = new THREE.OrthographicCamera(-1, 1, 1, -1, -1, 1);
    
    const crtMaterial = new THREE.ShaderMaterial({
      vertexShader: crtVertexShader,
      fragmentShader: crtFragmentShader,
      uniforms: {
        tDiffuse: { value: renderTarget.texture },
        uTime: uTimeRef.current,
        uCrtEnabled: { value: isCrtEnabled }
      }
    });
    crtMaterialRef.current = crtMaterial;

    const crtPlaneGeom = new THREE.PlaneGeometry(2, 2);
    const crtPlane = new THREE.Mesh(crtPlaneGeom, crtMaterial);
    crtScene.add(crtPlane);

    let animationFrameId: number;
    let clock = new THREE.Clock();
    
    let currentW = width;
    let currentH = height;

    const animate = () => {
      animationFrameId = requestAnimationFrame(animate);

      // Auto-resize handling (solves mount size 0 and canvas/container sync layout races)
      if (containerRef.current) {
        const w = containerRef.current.clientWidth;
        const h = containerRef.current.clientHeight;
        if (w > 0 && h > 0 && (w !== currentW || h !== currentH)) {
          currentW = w;
          currentH = h;
          camera.aspect = w / h;
          camera.updateProjectionMatrix();
          renderer.setSize(w, h);
          renderTarget.setSize(w, h);
        }
      }

      const t = clock.getElapsedTime();
      uTimeRef.current.value = t;

      const isCrt = crtMaterialRef.current?.uniforms.uCrtEnabled.value ?? true;

      if (isCrt) {
        renderer.setRenderTarget(renderTarget);
        renderer.render(scene, camera);
        renderer.setRenderTarget(null);
        renderer.render(crtScene, crtOrthoCamera);
      } else {
        renderer.setRenderTarget(null);
        renderer.render(scene, camera);
      }
    };
    animate();

    return () => {
      cancelAnimationFrame(animationFrameId);
      renderer.dispose();
      voxelGeom.dispose();
      solidMat.dispose();
      hazardMat.dispose();
      interactableMat.dispose();
      crtPlaneGeom.dispose();
      crtMaterial.dispose();
      renderTarget.dispose();
    };
  }, [token]);

  // Update CRT Enable state instantly without WebGL context reconstruction
  useEffect(() => {
    if (crtMaterialRef.current) {
      crtMaterialRef.current.uniforms.uCrtEnabled.value = isCrtEnabled;
    }
  }, [isCrtEnabled]);

  // Update RenderMode properties instantly on existing materials
  useEffect(() => {
    if (!solidMeshRef.current || !hazardMeshRef.current || !interactableMeshRef.current) return;
    
    const sm = solidMeshRef.current.material as THREE.MeshLambertMaterial;
    const hm = hazardMeshRef.current.material as THREE.MeshLambertMaterial;
    const im = interactableMeshRef.current.material as THREE.MeshLambertMaterial;

    if (renderMode === 'solid') {
      sm.wireframe = false;
      sm.opacity = 0.8;
      hm.wireframe = false;
      hm.opacity = 0.85;
      im.wireframe = false;
      im.opacity = 0.85;
    } else if (renderMode === 'wireframe') {
      sm.wireframe = true;
      sm.opacity = 0.2;
      hm.wireframe = true;
      hm.opacity = 0.35;
      im.wireframe = true;
      im.opacity = 0.35;
    } else if (renderMode === 'radar') {
      sm.wireframe = true;
      sm.opacity = 0.35;
      hm.wireframe = true;
      hm.opacity = 0.55;
      im.wireframe = true;
      im.opacity = 0.55;
    }
  }, [renderMode]);

  // WebSocket Connection Handler
  useEffect(() => {
    if (!token || !activeCameraId) return;
    const currentCamera = cameras.find(c => c.id === activeCameraId);
    if (!currentCamera) return;

    // Reset meshes and counters
    setBlocksStreamed(0);
    setActiveEntitiesCount(0);
    if (solidMeshRef.current) solidMeshRef.current.count = 0;
    if (hazardMeshRef.current) hazardMeshRef.current.count = 0;
    if (interactableMeshRef.current) interactableMeshRef.current.count = 0;
    
    // Clear entities with memory safety
    const group = entitiesGroupRef.current;
    if (group) {
      const disposeNode = (node: THREE.Object3D) => {
        if (node instanceof THREE.Mesh || node instanceof THREE.Line) {
          node.geometry.dispose();
          if (Array.isArray(node.material)) {
            node.material.forEach(m => m.dispose());
          } else {
            node.material.dispose();
          }
        }
        node.children.forEach(disposeNode);
      };
      while (group.children.length > 0) {
        const child = group.children[0];
        group.remove(child);
        disposeNode(child);
      }
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/api/webapp/view?token=${encodeURIComponent(token)}&cameraId=${encodeURIComponent(activeCameraId)}`;
    const ws = new WebSocket(wsUrl);

    let lastTime = Date.now();

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        
        // Measure latency
        const currentTime = Date.now();
        setLatency(currentTime - lastTime);
        lastTime = currentTime;

        if (msg.type === "me.orange.crtangarine.shared.FrustumPayloadMessage" && msg.data?.blocks) {
          const blocks: BlockData[] = msg.data.blocks;
          const pitch = msg.data.pitch ?? 0;
          const yaw = msg.data.yaw ?? 0;

          // Lock camera look direction stably
          const yawRad = -yaw * (Math.PI / 180.0);
          const pitchRad = -pitch * (Math.PI / 180.0);
          const dx = Math.sin(yawRad) * Math.cos(pitchRad);
          const dy = Math.sin(pitchRad);
          const dz = Math.cos(yawRad) * Math.cos(pitchRad);

          if (mainCameraRef.current) {
            const targetX = dx * 50.0;
            const targetY = dy * 50.0 + 0.2;
            const targetZ = dz * 50.0;
            mainCameraRef.current.position.set(0, 0.2, 0);
            mainCameraRef.current.lookAt(new THREE.Vector3(targetX, targetY, targetZ));
          }

          // Culling enclosed interior faces
          const solidSet = new Set<string>();
          blocks.forEach(b => {
            if (b.stateId === 1) {
              solidSet.add(`${b.x},${b.y},${b.z}`);
            }
          });

          const camX = currentCamera.x;
          const camY = currentCamera.y;
          const camZ = currentCamera.z;

          const dummy = new THREE.Object3D();
          let solidCount = 0;
          let hazardCount = 0;
          let interactableCount = 0;
          const maxInstances = 8000;

          const scale = renderModeRef.current === 'radar' ? 0.15 : 1.0;

          blocks.forEach(block => {
            const rx = block.x - camX;
            const ry = block.y - camY;
            const rz = block.z - camZ;

            if (block.stateId === 1) {
              const hasX1 = solidSet.has(`${block.x + 1},${block.y},${block.z}`);
              const hasX2 = solidSet.has(`${block.x - 1},${block.y},${block.z}`);
              const hasY1 = solidSet.has(`${block.x},${block.y + 1},${block.z}`);
              const hasY2 = solidSet.has(`${block.x},${block.y - 1},${block.z}`);
              const hasZ1 = solidSet.has(`${block.x},${block.y},${block.z + 1}`);
              const hasZ2 = solidSet.has(`${block.x},${block.y},${block.z - 1}`);

              if (hasX1 && hasX2 && hasY1 && hasY2 && hasZ1 && hasZ2) {
                return;
              }
            }

            dummy.position.set(rx, ry, rz);
            dummy.scale.set(scale, scale, scale);
            dummy.updateMatrix();

            if (block.stateId === 1 && solidCount < maxInstances) {
              solidMeshRef.current?.setMatrixAt(solidCount++, dummy.matrix);
            } else if (block.stateId === 2 && hazardCount < maxInstances) {
              hazardMeshRef.current?.setMatrixAt(hazardCount++, dummy.matrix);
            } else if (block.stateId === 3 && interactableCount < maxInstances) {
              interactableMeshRef.current?.setMatrixAt(interactableCount++, dummy.matrix);
            }
          });

          setBlocksStreamed(solidCount + hazardCount + interactableCount);

          if (solidMeshRef.current) {
            solidMeshRef.current.count = solidCount;
            solidMeshRef.current.instanceMatrix.needsUpdate = true;
          }
          if (hazardMeshRef.current) {
            hazardMeshRef.current.count = hazardCount;
            hazardMeshRef.current.instanceMatrix.needsUpdate = true;
          }
          if (interactableMeshRef.current) {
            interactableMeshRef.current.count = interactableCount;
            interactableMeshRef.current.instanceMatrix.needsUpdate = true;
          }

        } else if (msg.type === "me.orange.crtangarine.shared.EntityStreamMessage" && msg.data?.entities) {
          const entities: EntityData[] = msg.data.entities;
          setActiveEntitiesCount(entities.length);

          const group = entitiesGroupRef.current;
          if (!group) return;

          // Dispose entity resources
          const disposeNode = (node: THREE.Object3D) => {
            if (node instanceof THREE.Mesh || node instanceof THREE.Line) {
              node.geometry.dispose();
              if (Array.isArray(node.material)) {
                node.material.forEach(m => m.dispose());
              } else {
                node.material.dispose();
              }
            }
            node.children.forEach(disposeNode);
          };
          while (group.children.length > 0) {
            const child = group.children[0];
            group.remove(child);
            disposeNode(child);
          }

          const camX = currentCamera.x;
          const camY = currentCamera.y;
          const camZ = currentCamera.z;

          entities.forEach(entity => {
            const rx = entity.x - camX;
            const ry = entity.y - camY;
            const rz = entity.z - camZ;

            const color = getEntityColor(entity.type);
            
            // Diamond geometry for entity
            const geom = new THREE.OctahedronGeometry(0.3, 0);
            const mat = new THREE.MeshBasicMaterial({ color, wireframe: false });
            const mesh = new THREE.Mesh(geom, mat);
            mesh.position.set(rx, ry + 0.4, rz);

            // Neon footprint ring
            const ringGeom = new THREE.RingGeometry(0.3, 0.4, 8);
            const ringMat = new THREE.MeshBasicMaterial({ color, side: THREE.DoubleSide, transparent: true, opacity: 0.6 });
            const ring = new THREE.Mesh(ringGeom, ringMat);
            ring.rotation.x = Math.PI / 2;
            ring.position.y = -0.4;
            mesh.add(ring);

            // Orientation line
            const entityYaw = entity.yaw ?? 0;
            const entityPitch = entity.pitch ?? 0;
            const yawRad = -entityYaw * (Math.PI / 180.0);
            const pitchRad = -entityPitch * (Math.PI / 180.0);
            const lx = Math.sin(yawRad) * Math.cos(pitchRad) * 1.2;
            const ly = Math.sin(pitchRad) * 1.2;
            const lz = Math.cos(yawRad) * Math.cos(pitchRad) * 1.2;

            const linePoints = [
              new THREE.Vector3(0, 0, 0),
              new THREE.Vector3(lx, ly, lz)
            ];
            const lineGeom = new THREE.BufferGeometry().setFromPoints(linePoints);
            const lineMat = new THREE.LineBasicMaterial({ color: 0xffffff });
            const line = new THREE.Line(lineGeom, lineMat);
            mesh.add(line);

            group.add(mesh);
          });
        }
      } catch (e) {
        console.error('Error handling websocket stream packet:', e);
      }
    };

    ws.onclose = () => {
      console.log('Websocket closed for camera feed', activeCameraId);
    };

    return () => {
      ws.close();
    };
  }, [token, activeCameraId, cameras]);

  // Get color for entity type
  const getEntityColor = (type: string): number => {
    switch (type) {
      case 'PLAYER': return 0x00e5ff;
      case 'MONSTER': return 0xff1744;
      case 'PASSIVE': return 0x00e676;
      case 'ITEM': return 0xffd700;
      default: return 0xffffff;
    }
  }

  if (!token) {
    return (
      <div className="dashboard-wrapper">
        <div className="scanlines-overlay"></div>
        <div className="vignette-overlay"></div>
        <div className="login-container">
          <form className="login-form" onSubmit={handleLogin}>
            <h2>CRT Surveillance</h2>
            <p>Enter secret keycard token to connect</p>
            <input 
              type="text" 
              value={loginInput} 
              onChange={e => setLoginInput(e.target.value)} 
              placeholder="Token..."
              disabled={isLoggingIn}
              autoFocus
            />
            {loginError && <div className="error">{loginError}</div>}
            <button type="submit" disabled={isLoggingIn || !loginInput.trim()}>
              {isLoggingIn ? 'AUTHENTICATING...' : 'ESTABLISH LINK'}
            </button>
          </form>
        </div>
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
          <button 
            onClick={toggleAudio} 
            className={`audio-toggle-btn ${isAudioOn ? 'active' : ''}`}
          >
            🔊 CRT HUM
          </button>
          <span className="auth-status">LINK SECURE</span>
          <button onClick={handleLogout} className="logout-btn">DISCONNECT</button>
        </div>
      </header>
      
      <div className="dashboard-main">
        {/* Left Side: Camera Selection Grid */}
        <aside className="camera-list-panel">
          <h2>Node Select</h2>
          {cameras.length === 0 ? (
            <p>No active stations detected.</p>
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
                    GRID [{Math.round(camera.x)}, {Math.round(camera.y)}, {Math.round(camera.z)}]
                  </div>
                </li>
              ))}
            </ul>
          )}
        </aside>

        {/* Center/Right Side: ThreeJS Viewport */}
        <section className="feed-viewer-panel">
          <div className="canvas-container" style={{ display: activeCamera ? 'block' : 'none', width: '100%', height: '100%' }}>
            {/* Raw Canvas Wrapper */}
            <div ref={containerRef} style={{ width: '100%', height: '100%' }}></div>

            {/* HUD overlay for active stats */}
            {activeCamera && (
              <div className="hud-overlay">
                <div className="hud-panel">
                  <h3>Active Terminal Data</h3>
                  <div className="hud-item">FEED: {activeCamera.name}</div>
                  <div className="hud-item">COORDINATES: [{Math.round(activeCamera.x)}, {Math.round(activeCamera.y)}, {Math.round(activeCamera.z)}]</div>
                  <div className="hud-item">BLOCKS IN VIEW: {blocksStreamed}</div>
                  <div className="hud-item">ENTITIES DETECTED: {activeEntitiesCount}</div>
                  <div className="hud-item">LATENCY: {latency} ms</div>
                </div>
              </div>
            )}
          </div>

          {!activeCamera && (
            <div className="no-feed-selection">
              [ WAITING FOR LINK INBOUND SIGNAL... ]
            </div>
          )}

          {/* Controls and Legend bar */}
          <div className="control-bar">
            <div className="control-group">
              <span className="control-label">Post Process:</span>
              <button 
                onClick={() => setIsCrtEnabled(!isCrtEnabled)} 
                className={`control-btn ${isCrtEnabled ? 'active' : ''}`}
              >
                CRT Shader
              </button>
            </div>

            <div className="control-group">
              <span className="control-label">Render Mode:</span>
              <button 
                onClick={() => setRenderMode('solid')} 
                className={`control-btn ${renderMode === 'solid' ? 'active' : ''}`}
              >
                Solid
              </button>
              <button 
                onClick={() => setRenderMode('wireframe')} 
                className={`control-btn ${renderMode === 'wireframe' ? 'active' : ''}`}
              >
                Wireframe
              </button>
              <button 
                onClick={() => setRenderMode('radar')} 
                className={`control-btn ${renderMode === 'radar' ? 'active' : ''}`}
              >
                Blips
              </button>
            </div>

            <div className="legend-panel">
              <div className="legend-item">
                <span className="legend-color" style={{ color: '#00e5ff', backgroundColor: '#00e5ff' }}></span>
                Player
              </div>
              <div className="legend-item">
                <span className="legend-color" style={{ color: '#ff1744', backgroundColor: '#ff1744' }}></span>
                Monster
              </div>
              <div className="legend-item">
                <span className="legend-color" style={{ color: '#00e676', backgroundColor: '#00e676' }}></span>
                Passive
              </div>
              <div className="legend-item">
                <span className="legend-color" style={{ color: '#ffd700', backgroundColor: '#ffd700' }}></span>
                Item
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}

export default App

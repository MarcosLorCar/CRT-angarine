import React, { useState, useEffect, useRef } from 'react';
import * as THREE from 'three';
import { type CameraData, type BlockData, type EntityData } from '../shared-types';

interface ViewportProps {
  token: string;
  activeCamera: CameraData | undefined;
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
      // Scanlines
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

export const Viewport: React.FC<ViewportProps> = ({ token, activeCamera }) => {
  const [blocksStreamed, setBlocksStreamed] = useState(0);
  const [activeEntitiesCount, setActiveEntitiesCount] = useState(0);
  const [latency, setLatency] = useState(0);
  const [isInfoOpen, setIsInfoOpen] = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const rendererRef = useRef<THREE.WebGLRenderer | null>(null);
  const mainCameraRef = useRef<THREE.PerspectiveCamera | null>(null);

  // Dynamic 3D Object references
  const solidMeshRef = useRef<THREE.InstancedMesh | null>(null);
  const hazardMeshRef = useRef<THREE.InstancedMesh | null>(null);
  const interactableMeshRef = useRef<THREE.InstancedMesh | null>(null);
  const solidWireframeMeshRef = useRef<THREE.InstancedMesh | null>(null);
  const hazardWireframeMeshRef = useRef<THREE.InstancedMesh | null>(null);
  const interactableWireframeMeshRef = useRef<THREE.InstancedMesh | null>(null);
  const entitiesGroupRef = useRef<THREE.Group | null>(null);

  // Instanced attribute references for face culling
  const solidVisibilityRef = useRef<THREE.InstancedBufferAttribute | null>(null);
  const hazardVisibilityRef = useRef<THREE.InstancedBufferAttribute | null>(null);
  const interactableVisibilityRef = useRef<THREE.InstancedBufferAttribute | null>(null);

  // Glitch effect canvas ref
  const glitchCanvasRef = useRef<HTMLCanvasElement | null>(null);

  // Shared Entity resources refs for performance (zero allocation loop)
  const entityGeomRef = useRef<THREE.OctahedronGeometry | null>(null);
  const ringGeomRef = useRef<THREE.RingGeometry | null>(null);
  const lineGeomRef = useRef<THREE.BufferGeometry | null>(null);
  const entityMatsRef = useRef<Record<string, THREE.MeshBasicMaterial> | null>(null);
  const ringMatsRef = useRef<Record<string, THREE.MeshBasicMaterial> | null>(null);
  const lineMatRef = useRef<THREE.LineBasicMaterial | null>(null);

  // Performance-optimized HUD update refs (avoids React re-renders when closed)
  const isInfoOpenRef = useRef(false);
  const blocksStreamedRef = useRef(0);
  const activeEntitiesCountRef = useRef(0);
  const latencyRef = useRef(0);

  useEffect(() => {
    isInfoOpenRef.current = isInfoOpen;
    if (isInfoOpen) {
      setBlocksStreamed(blocksStreamedRef.current);
      setActiveEntitiesCount(activeEntitiesCountRef.current);
      setLatency(latencyRef.current);
    }
  }, [isInfoOpen]);

  // Time and Shader references
  const uTimeRef = useRef({ value: 0.0 });
  const crtMaterialRef = useRef<THREE.ShaderMaterial | null>(null);

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
    renderer.setClearColor(scene.fog.color);

    const aspect = (width && height) ? width / height : 1.0;

    // Camera (Fixed at origin lens offset, locked to 90 FOV)
    const camera = new THREE.PerspectiveCamera(90, aspect, 0.1, 100);
    camera.position.set(0, 0, 0);
    mainCameraRef.current = camera;

    // Lighting
    const ambientLight = new THREE.AmbientLight(0x114411, 0.8);
    scene.add(ambientLight);

    const dirLight = new THREE.DirectionalLight(0x33ff33, 1.2);
    dirLight.position.set(10, 20, 10);
    scene.add(dirLight);

    // Instanced meshes
    const maxInstances = 8000;
    
    const solidGeom = new THREE.BoxGeometry(1.0, 1.0, 1.0);
    const solidVisibilityAttr = new THREE.InstancedBufferAttribute(new Float32Array(maxInstances), 1);
    solidVisibilityAttr.setUsage(THREE.DynamicDrawUsage);
    solidGeom.setAttribute('aFaceVisibility', solidVisibilityAttr);
    solidVisibilityRef.current = solidVisibilityAttr;

    const hazardGeom = new THREE.BoxGeometry(1.0, 1.0, 1.0);
    const hazardVisibilityAttr = new THREE.InstancedBufferAttribute(new Float32Array(maxInstances), 1);
    hazardVisibilityAttr.setUsage(THREE.DynamicDrawUsage);
    hazardGeom.setAttribute('aFaceVisibility', hazardVisibilityAttr);
    hazardVisibilityRef.current = hazardVisibilityAttr;

    const interactableGeom = new THREE.BoxGeometry(1.0, 1.0, 1.0);
    const interactableVisibilityAttr = new THREE.InstancedBufferAttribute(new Float32Array(maxInstances), 1);
    interactableVisibilityAttr.setUsage(THREE.DynamicDrawUsage);
    interactableGeom.setAttribute('aFaceVisibility', interactableVisibilityAttr);
    interactableVisibilityRef.current = interactableVisibilityAttr;

    const customizeMaterial = (material: THREE.Material) => {
      material.onBeforeCompile = (shader) => {
        // Inject attribute and varying definition in vertex shader header
        shader.vertexShader = shader.vertexShader.replace(
          'void main() {',
          `attribute float aFaceVisibility;
          varying float vDepth;
          void main() {`
        );
        // Inject vDepth calculation and face culling in vertex shader body after project_vertex
        shader.vertexShader = shader.vertexShader.replace(
          '#include <project_vertex>',
          `#include <project_vertex>
          vDepth = -mvPosition.z;
          
          // Face culling logic using float bit extraction (compatible with WebGL1 and WebGL2)
          float visibilityVal = aFaceVisibility;
          bool isFaceVisible = true;
          if (normal.x > 0.5) isFaceVisible = mod(visibilityVal, 2.0) >= 1.0;
          else if (normal.x < -0.5) isFaceVisible = mod(floor(visibilityVal / 2.0), 2.0) >= 1.0;
          else if (normal.y > 0.5) isFaceVisible = mod(floor(visibilityVal / 4.0), 2.0) >= 1.0;
          else if (normal.y < -0.5) isFaceVisible = mod(floor(visibilityVal / 8.0), 2.0) >= 1.0;
          else if (normal.z > 0.5) isFaceVisible = mod(floor(visibilityVal / 16.0), 2.0) >= 1.0;
          else if (normal.z < -0.5) isFaceVisible = mod(floor(visibilityVal / 32.0), 2.0) >= 1.0;
          
          if (!isFaceVisible) {
            gl_Position = vec4(0.0, 0.0, 0.0, 0.0);
          }`
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
          `  float depthFactor = 1.0 - clamp(vDepth / 36.0, 0.0, 1.0);
             gl_FragColor.rgb *= depthFactor;
          }`
        );
      };
    };

    const customizeWireframeMaterial = (material: THREE.Material) => {
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
          `  float depthFactor = 1.0 - clamp(vDepth / 36.0, 0.0, 1.0);
             gl_FragColor.rgb *= depthFactor;
          }`
        );
      };
    };

    // Solid view is always used, so we use solid view settings with transparent false
    // We add polygonOffset to allow wireframe lines to render on top cleanly
    const solidMat = new THREE.MeshLambertMaterial({
      color: 0x22ee22, // Original solid green view
      wireframe: false,
      transparent: false,
      polygonOffset: true,
      polygonOffsetFactor: 1,
      polygonOffsetUnits: 1
    });
    customizeMaterial(solidMat);
    const solidMesh = new THREE.InstancedMesh(solidGeom, solidMat, maxInstances);
    solidMesh.count = 0;
    solidMesh.frustumCulled = false;
    scene.add(solidMesh);
    solidMeshRef.current = solidMesh;

    // Solid Wireframe Outline Overlay (no diagonals, using EdgesGeometry)
    const solidEdgesGeom = new THREE.EdgesGeometry(solidGeom);
    const solidWireframeMat = new THREE.LineBasicMaterial({
      color: 0x0a5e0a, // Dark green outline
      transparent: true,
      opacity: 0.6
    });
    customizeWireframeMaterial(solidWireframeMat);
    const solidWireframeMesh = new THREE.InstancedMesh(solidEdgesGeom, solidWireframeMat, maxInstances);
    solidWireframeMesh.count = 0;
    solidWireframeMesh.frustumCulled = false;
    solidWireframeMesh.instanceMatrix = solidMesh.instanceMatrix; // Share instance matrices!
    scene.add(solidWireframeMesh);
    solidWireframeMeshRef.current = solidWireframeMesh;

    const hazardMat = new THREE.MeshLambertMaterial({
      color: 0xff2222, // Original solid red view
      wireframe: false,
      transparent: false,
      polygonOffset: true,
      polygonOffsetFactor: 1,
      polygonOffsetUnits: 1
    });
    customizeMaterial(hazardMat);
    const hazardMesh = new THREE.InstancedMesh(hazardGeom, hazardMat, maxInstances);
    hazardMesh.count = 0;
    hazardMesh.frustumCulled = false;
    scene.add(hazardMesh);
    hazardMeshRef.current = hazardMesh;

    // Hazard Wireframe Outline Overlay (no diagonals, using EdgesGeometry)
    const hazardEdgesGeom = new THREE.EdgesGeometry(hazardGeom);
    const hazardWireframeMat = new THREE.LineBasicMaterial({
      color: 0x5e0a0a, // Dark red outline
      transparent: true,
      opacity: 0.6
    });
    customizeWireframeMaterial(hazardWireframeMat);
    const hazardWireframeMesh = new THREE.InstancedMesh(hazardEdgesGeom, hazardWireframeMat, maxInstances);
    hazardWireframeMesh.count = 0;
    hazardWireframeMesh.frustumCulled = false;
    hazardWireframeMesh.instanceMatrix = hazardMesh.instanceMatrix; // Share instance matrices!
    scene.add(hazardWireframeMesh);
    hazardWireframeMeshRef.current = hazardWireframeMesh;

    const interactableMat = new THREE.MeshLambertMaterial({
      color: 0x22aaff, // Original solid blue/interactable view
      wireframe: false,
      transparent: false,
      polygonOffset: true,
      polygonOffsetFactor: 1,
      polygonOffsetUnits: 1
    });
    customizeMaterial(interactableMat);
    const interactableMesh = new THREE.InstancedMesh(interactableGeom, interactableMat, maxInstances);
    interactableMesh.count = 0;
    interactableMesh.frustumCulled = false;
    scene.add(interactableMesh);
    interactableMeshRef.current = interactableMesh;

    // Interactable Wireframe Outline Overlay (no diagonals, using EdgesGeometry)
    const interactableEdgesGeom = new THREE.EdgesGeometry(interactableGeom);
    const interactableWireframeMat = new THREE.LineBasicMaterial({
      color: 0x0a3b5e, // Dark blue outline
      transparent: true,
      opacity: 0.6
    });
    customizeWireframeMaterial(interactableWireframeMat);
    const interactableWireframeMesh = new THREE.InstancedMesh(interactableEdgesGeom, interactableWireframeMat, maxInstances);
    interactableWireframeMesh.count = 0;
    interactableWireframeMesh.frustumCulled = false;
    interactableWireframeMesh.instanceMatrix = interactableMesh.instanceMatrix; // Share instance matrices!
    scene.add(interactableWireframeMesh);
    interactableWireframeMeshRef.current = interactableWireframeMesh;

    // Shared Entity Geometries
    const entityGeom = new THREE.OctahedronGeometry(1.0, 0);
    const ringGeom = new THREE.RingGeometry(0.75, 1.0, 8);
    const lineGeom = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(0, 0, 0),
      new THREE.Vector3(0, 0, 1)
    ]);

    // Shared Entity Materials
    const entityMats: Record<string, THREE.MeshBasicMaterial> = {
      PLAYER: new THREE.MeshBasicMaterial({ color: 0x00e5ff }),
      MONSTER: new THREE.MeshBasicMaterial({ color: 0xff1744 }),
      PASSIVE: new THREE.MeshBasicMaterial({ color: 0x00e676 }),
      ITEM: new THREE.MeshBasicMaterial({ color: 0xffd700 })
    };
    const ringMats: Record<string, THREE.MeshBasicMaterial> = {
      PLAYER: new THREE.MeshBasicMaterial({ color: 0x00e5ff, side: THREE.DoubleSide, transparent: true, opacity: 0.6 }),
      MONSTER: new THREE.MeshBasicMaterial({ color: 0xff1744, side: THREE.DoubleSide, transparent: true, opacity: 0.6 }),
      PASSIVE: new THREE.MeshBasicMaterial({ color: 0x00e676, side: THREE.DoubleSide, transparent: true, opacity: 0.6 }),
      ITEM: new THREE.MeshBasicMaterial({ color: 0xffd700, side: THREE.DoubleSide, transparent: true, opacity: 0.6 })
    };
    const lineMat = new THREE.LineBasicMaterial({ color: 0xffffff });

    entityGeomRef.current = entityGeom;
    ringGeomRef.current = ringGeom;
    lineGeomRef.current = lineGeom;
    entityMatsRef.current = entityMats;
    ringMatsRef.current = ringMats;
    lineMatRef.current = lineMat;

    // Entities
    const entitiesGroup = new THREE.Group();
    scene.add(entitiesGroup);
    entitiesGroupRef.current = entitiesGroup;

    // CRT Post process Shader Pass (Always Enabled)
    const renderTarget = new THREE.WebGLRenderTarget(width || 1, height || 1);
    const crtScene = new THREE.Scene();
    const crtOrthoCamera = new THREE.OrthographicCamera(-1, 1, 1, -1, -1, 1);
    
    const crtMaterial = new THREE.ShaderMaterial({
      vertexShader: crtVertexShader,
      fragmentShader: crtFragmentShader,
      depthTest: false,
      depthWrite: false,
      uniforms: {
        tDiffuse: { value: renderTarget.texture },
        uTime: uTimeRef.current,
        uCrtEnabled: { value: true }
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
          const newAspect = w / h;
          camera.aspect = newAspect;
          camera.fov = 90;

          camera.updateProjectionMatrix();
          renderer.setSize(w, h);
          renderTarget.setSize(w, h);
        }
      }

      const t = clock.getElapsedTime();
      uTimeRef.current.value = t;

      // Render with CRT post-process always enabled
      renderer.setRenderTarget(renderTarget);
      renderer.render(scene, camera);
      renderer.setRenderTarget(null);
      renderer.render(crtScene, crtOrthoCamera);
    };
    animate();

    return () => {
      cancelAnimationFrame(animationFrameId);
      renderer.dispose();
      solidGeom.dispose();
      solidEdgesGeom.dispose();
      hazardGeom.dispose();
      hazardEdgesGeom.dispose();
      interactableGeom.dispose();
      interactableEdgesGeom.dispose();
      solidMat.dispose();
      solidWireframeMat.dispose();
      hazardMat.dispose();
      hazardWireframeMat.dispose();
      interactableMat.dispose();
      interactableWireframeMat.dispose();
      
      // Dispose shared entity resources
      entityGeom.dispose();
      ringGeom.dispose();
      lineGeom.dispose();
      Object.values(entityMats).forEach(m => m.dispose());
      Object.values(ringMats).forEach(m => m.dispose());
      lineMat.dispose();

      crtPlaneGeom.dispose();
      crtMaterial.dispose();
      renderTarget.dispose();
    };
  }, [token]);

  // WebSocket Connection Handler
  useEffect(() => {
    if (!token || !activeCamera) return;

    // If camera is offline, do not connect to socket, just reset meshes/counters
    if (!activeCamera.isOnline) {
      blocksStreamedRef.current = 0;
      activeEntitiesCountRef.current = 0;
      latencyRef.current = 0;
      if (isInfoOpenRef.current) {
        setBlocksStreamed(0);
        setActiveEntitiesCount(0);
        setLatency(0);
      }
      if (solidMeshRef.current) solidMeshRef.current.count = 0;
      if (solidWireframeMeshRef.current) solidWireframeMeshRef.current.count = 0;
      if (hazardMeshRef.current) hazardMeshRef.current.count = 0;
      if (hazardWireframeMeshRef.current) hazardWireframeMeshRef.current.count = 0;
      if (interactableMeshRef.current) interactableMeshRef.current.count = 0;
      if (interactableWireframeMeshRef.current) interactableWireframeMeshRef.current.count = 0;
      return;
    }

    // Reset meshes and counters (using refs to avoid state re-render during reset)
    blocksStreamedRef.current = 0;
    activeEntitiesCountRef.current = 0;
    latencyRef.current = 0;
    if (isInfoOpenRef.current) {
      setBlocksStreamed(0);
      setActiveEntitiesCount(0);
      setLatency(0);
    }
    if (solidMeshRef.current) solidMeshRef.current.count = 0;
    if (solidWireframeMeshRef.current) solidWireframeMeshRef.current.count = 0;
    if (hazardMeshRef.current) hazardMeshRef.current.count = 0;
    if (hazardWireframeMeshRef.current) hazardWireframeMeshRef.current.count = 0;
    if (interactableMeshRef.current) interactableMeshRef.current.count = 0;
    if (interactableWireframeMeshRef.current) interactableWireframeMeshRef.current.count = 0;
    
    // Clear entities (no need to dispose shared geometries/materials)
    const group = entitiesGroupRef.current;
    if (group) {
      while (group.children.length > 0) {
        group.remove(group.children[0]);
      }
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/api/webapp/view?token=${encodeURIComponent(token)}&cameraId=${encodeURIComponent(activeCamera.id)}`;
    const ws = new WebSocket(wsUrl);

    let lastTime = Date.now();

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        
        // Measure latency
        const currentTime = Date.now();
        const currentLatency = currentTime - lastTime;
        lastTime = currentTime;
        latencyRef.current = currentLatency;
        if (isInfoOpenRef.current) {
          setLatency(currentLatency);
        }

        if (msg.type === "me.orange.crtangarine.shared.FrustumPayloadMessage" && msg.data?.blocks) {
          const payload = msg.data;
          const blocks: BlockData[] = payload.blocks;
          const pitch = payload.pitch ?? 0;
          const yaw = payload.yaw ?? 0;

          // Lock camera look direction stably
          const yawRad = -yaw * (Math.PI / 180.0);
          const pitchRad = -pitch * (Math.PI / 180.0);
          const dx = Math.sin(yawRad) * Math.cos(pitchRad);
          const dy = Math.sin(pitchRad);
          const dz = Math.cos(yawRad) * Math.cos(pitchRad);

          if (mainCameraRef.current) {
            const targetX = dx * 50.0;
            const targetY = dy * 50.0;
            const targetZ = dz * 50.0;
            mainCameraRef.current.position.set(0, 0, 0);
            mainCameraRef.current.lookAt(new THREE.Vector3(targetX, targetY, targetZ));
          }

          const camX = activeCamera.x;
          const camY = activeCamera.y;
          const camZ = activeCamera.z;

          // Create a map of coordinate packed relative coordinates to block stateId for fast neighbor checks
          const blockMap = new Map<number, number>();
          blocks.forEach(b => {
            const rx = b.x - camX;
            const ry = b.y - camY;
            const rz = b.z - camZ;
            const key = ((rx + 64) << 16) | ((ry + 64) << 8) | (rz + 64);
            blockMap.set(key, b.stateId);
          });

          const dummy = new THREE.Object3D();
          let solidCount = 0;
          let hazardCount = 0;
          let interactableCount = 0;
          const maxInstances = 8000;

          blocks.forEach(block => {
            const S = block.stateId;
            const x = block.x;
            const y = block.y;
            const z = block.z;

            // Neighbor culling logic (using relative packed integer keys)
            const hasNeighbor = (nx: number, ny: number, nz: number): boolean => {
              const rnx = nx - camX;
              const rny = ny - camY;
              const rnz = nz - camZ;
              const key = ((rnx + 64) << 16) | ((rny + 64) << 8) | (rnz + 64);
              const neighborState = blockMap.get(key);
              if (neighborState === undefined) return false;
              // Cull face if neighbor is solid (1) or of the same type
              return neighborState === 1 || neighborState === S;
            };

            // Calculate visibility bitmask (1 = visible, 0 = culled)
            let visibilityMask = 0;
            if (!hasNeighbor(x + 1, y, z)) visibilityMask |= 1;
            if (!hasNeighbor(x - 1, y, z)) visibilityMask |= 2;
            if (!hasNeighbor(x, y + 1, z)) visibilityMask |= 4;
            if (!hasNeighbor(x, y - 1, z)) visibilityMask |= 8;
            if (!hasNeighbor(x, y, z + 1)) visibilityMask |= 16;
            if (!hasNeighbor(x, y, z - 1)) visibilityMask |= 32;

            // If all 6 faces are culled, skip rendering this block entirely
            if (visibilityMask === 0) {
              return;
            }

            const rx = x + 0.5 - camX;
            const ry = y + 0.5 - camY;
            const rz = z + 0.5 - camZ;

            dummy.position.set(rx, ry, rz);
            dummy.scale.set(0.98, 0.98, 0.98);
            dummy.updateMatrix();

            if (S === 1 && solidCount < maxInstances) {
              solidMeshRef.current?.setMatrixAt(solidCount, dummy.matrix);
              if (solidVisibilityRef.current) {
                solidVisibilityRef.current.setX(solidCount, visibilityMask);
              }
              solidCount++;
            } else if (S === 2 && hazardCount < maxInstances) {
              hazardMeshRef.current?.setMatrixAt(hazardCount, dummy.matrix);
              if (hazardVisibilityRef.current) {
                hazardVisibilityRef.current.setX(hazardCount, visibilityMask);
              }
              hazardCount++;
            } else if (S === 3 && interactableCount < maxInstances) {
              interactableMeshRef.current?.setMatrixAt(interactableCount, dummy.matrix);
              if (interactableVisibilityRef.current) {
                interactableVisibilityRef.current.setX(interactableCount, visibilityMask);
              }
              interactableCount++;
            }
          });

          blocksStreamedRef.current = solidCount + hazardCount + interactableCount;
          if (isInfoOpenRef.current) {
            setBlocksStreamed(blocksStreamedRef.current);
          }

          if (solidMeshRef.current) {
            solidMeshRef.current.count = solidCount;
            solidMeshRef.current.instanceMatrix.needsUpdate = true;
            if (solidVisibilityRef.current) {
              solidVisibilityRef.current.needsUpdate = true;
            }
          }
          if (solidWireframeMeshRef.current) {
            solidWireframeMeshRef.current.count = solidCount;
          }
          if (hazardMeshRef.current) {
            hazardMeshRef.current.count = hazardCount;
            hazardMeshRef.current.instanceMatrix.needsUpdate = true;
            if (hazardVisibilityRef.current) {
              hazardVisibilityRef.current.needsUpdate = true;
            }
          }
          if (hazardWireframeMeshRef.current) {
            hazardWireframeMeshRef.current.count = hazardCount;
          }
          if (interactableMeshRef.current) {
            interactableMeshRef.current.count = interactableCount;
            interactableMeshRef.current.instanceMatrix.needsUpdate = true;
            if (interactableVisibilityRef.current) {
              interactableVisibilityRef.current.needsUpdate = true;
            }
          }
          if (interactableWireframeMeshRef.current) {
            interactableWireframeMeshRef.current.count = interactableCount;
          }

        } else if (msg.type === "me.orange.crtangarine.shared.EntityStreamMessage" && msg.data?.entities) {
          const entities: EntityData[] = msg.data.entities;
          activeEntitiesCountRef.current = entities.length;
          if (isInfoOpenRef.current) {
            setActiveEntitiesCount(activeEntitiesCountRef.current);
          }

          const group = entitiesGroupRef.current;
          if (!group) return;

          // Clear entities (no need to dispose shared geometries/materials)
          while (group.children.length > 0) {
            group.remove(group.children[0]);
          }

          const camX = activeCamera.x;
          const camY = activeCamera.y;
          const camZ = activeCamera.z;

          const entityGeom = entityGeomRef.current;
          const ringGeom = ringGeomRef.current;
          const lineGeom = lineGeomRef.current;
          const entityMats = entityMatsRef.current;
          const ringMats = ringMatsRef.current;
          const lineMat = lineMatRef.current;

          if (entityGeom && ringGeom && lineGeom && entityMats && ringMats && lineMat) {
            entities.forEach(entity => {
              const rx = entity.x - camX;
              const ry = entity.y - camY;
              const rz = entity.z - camZ;

              const type = entity.type || 'PLAYER';
              const mat = entityMats[type] || entityMats['PLAYER'];
              const ringMat = ringMats[type] || ringMats['PLAYER'];

              // Dynamic height and centering based on entity classification
              let entityHeight = 1.8;
              if (type === 'MONSTER') {
                entityHeight = 1.95;
              } else if (type === 'PASSIVE') {
                entityHeight = 0.9;
              } else if (type === 'ITEM') {
                entityHeight = 0.25;
              }

              const centerY = entityHeight / 2.0;

              // Mesh for entity (reuses shared geometry and material)
              const mesh = new THREE.Mesh(entityGeom, mat);
              mesh.position.set(rx, ry + centerY, rz);
              mesh.scale.setScalar(centerY * 0.4);
              group.add(mesh);

              // Neon footprint ring (offset by +0.01 to prevent Z-fighting)
              const ring = new THREE.Mesh(ringGeom, ringMat);
              ring.rotation.x = Math.PI / 2;
              ring.position.set(rx, ry + 0.01, rz);
              ring.scale.setScalar(centerY * 0.4);
              group.add(ring);

              // Orientation line (reuses shared geometry and material, added directly to group to preserve scale)
              const entityYaw = entity.yaw ?? 0;
              const entityPitch = entity.pitch ?? 0;
              const yawRad = -entityYaw * (Math.PI / 180.0);
              const pitchRad = -entityPitch * (Math.PI / 180.0);
              const lx = Math.sin(yawRad) * Math.cos(pitchRad) * 1.2;
              const ly = Math.sin(pitchRad) * 1.2;
              const lz = Math.cos(yawRad) * Math.cos(pitchRad) * 1.2;

              const line = new THREE.Line(lineGeom, lineMat);
              line.position.set(rx, ry + centerY, rz);
              line.scale.set(1, 1, 1.2);
              line.lookAt(rx + lx, ry + centerY + ly, rz + lz);
              group.add(line);
            });
          }
        }
      } catch (e) {
        console.error('Error handling websocket stream packet:', e);
      }
    };

    ws.onclose = () => {
      console.log('Websocket closed for camera feed', activeCamera.id);
    };

    return () => {
      ws.close();
    };
  }, [token, activeCamera]);

  // Render glitch/static effect for offline cameras
  const isOnline = activeCamera?.isOnline ?? false;
  useEffect(() => {
    if (isOnline || !activeCamera || !glitchCanvasRef.current) return;

    const canvas = glitchCanvasRef.current;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId: number;
    let width = canvas.width = canvas.clientWidth || 400;
    let height = canvas.height = canvas.clientHeight || 300;

    const drawNoise = () => {
      // Resize handler if size changes
      if (canvas.clientWidth !== width || canvas.clientHeight !== height) {
        width = canvas.width = canvas.clientWidth;
        height = canvas.height = canvas.clientHeight;
      }

      const imgData = ctx.createImageData(width, height);
      const data = imgData.data;
      
      // Draw matrix-green tinted noise
      for (let i = 0; i < data.length; i += 4) {
        const val = Math.random() * 255;
        data[i] = val * 0.05;     // Red
        data[i + 1] = val * 0.5;  // Green (matrix static)
        data[i + 2] = val * 0.05; // Blue
        data[i + 3] = 255;        // Alpha
      }
      ctx.putImageData(imgData, 0, 0);

      // Draw rolling scanline
      const scanLineY = (Date.now() / 15) % height;
      ctx.fillStyle = 'rgba(0, 255, 0, 0.15)';
      ctx.fillRect(0, scanLineY, width, 4);

      // Draw warning message
      const isUnloaded = activeCamera?.status === 'UNLOADED';
      ctx.fillStyle = isUnloaded ? '#ffaa00' : '#ff2222';
      ctx.font = 'bold 18px "Share Tech Mono", monospace';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      
      const statusText = isUnloaded ? '[ CHUNK UNLOADED ]' : '[ SIGNAL LOST / OFFLINE ]';

      // Flashing offline text
      if (Math.floor(Date.now() / 500) % 2 === 0) {
        ctx.fillText(statusText, width / 2, height / 2);
      }

      animId = requestAnimationFrame(drawNoise);
    };

    drawNoise();

    return () => {
      cancelAnimationFrame(animId);
    };
  }, [isOnline, activeCamera]);

  return (
    <section className="feed-viewer-panel">
      <div className="canvas-container" style={{ display: activeCamera ? 'block' : 'none', width: '100%', height: '100%' }}>
        {/* Raw Canvas Wrapper (only visible when online) */}
        <div 
          ref={containerRef} 
          style={{ width: '100%', height: '100%', display: (activeCamera && activeCamera.isOnline) ? 'block' : 'none' }}
        ></div>

        {/* Glitch Canvas Wrapper (only visible when offline) */}
        {activeCamera && !activeCamera.isOnline && (
          <div className="glitch-container" style={{ width: '100%', height: '100%', position: 'absolute', top: 0, left: 0 }}>
            <canvas ref={glitchCanvasRef} style={{ width: '100%', height: '100%', display: 'block' }}></canvas>
          </div>
        )}

        {/* Toggable Info Overlay Button at the top right of camera preview */}
        {activeCamera && (
          <>
            <button 
              className={`info-toggle-btn ${isInfoOpen ? 'active' : ''}`}
              onClick={() => setIsInfoOpen(!isInfoOpen)}
            >
              [DATA LOG]
            </button>
            
            {isInfoOpen && (
              <div className="hud-panel-topright">
                <h3>Active Terminal Data</h3>
                <div className="hud-item">FEED: {activeCamera.name}</div>
                <div className="hud-item">COORDINATES: [{Math.round(activeCamera.x)}, {Math.round(activeCamera.y)}, {Math.round(activeCamera.z)}]</div>
                <div className="hud-item">BLOCKS IN VIEW: {activeCamera.isOnline ? blocksStreamed : 'N/A (OFFLINE)'}</div>
                <div className="hud-item">ENTITIES DETECTED: {activeCamera.isOnline ? activeEntitiesCount : '0 (OFFLINE)'}</div>
                <div className="hud-item">STATUS: {activeCamera.isOnline ? 'ONLINE' : 'OFFLINE'}</div>
                <div className="hud-item">LATENCY: {activeCamera.isOnline ? `${latency} ms` : 'DISCONNECTED'}</div>
              </div>
            )}
          </>
        )}
      </div>

      {!activeCamera && (
        <div className="no-feed-selection">
          [ WAITING FOR LINK INBOUND SIGNAL... ]
        </div>
      )}

      {/* Legend bar at the bottom */}
      <div className="control-bar">
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
  );
};

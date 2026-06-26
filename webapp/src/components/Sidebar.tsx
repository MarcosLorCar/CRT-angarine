import React from 'react';
import { type CameraData } from '../shared-types';

interface SidebarProps {
  cameras: CameraData[];
  activeCameraId: string | null;
  setActiveCameraId: (id: string) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({
  cameras,
  activeCameraId,
  setActiveCameraId,
}) => {
  // Group cameras by stationName
  const groupedCameras = cameras.reduce((groups, camera) => {
    const station = camera.stationName || 'Unknown Station';
    if (!groups[station]) {
      groups[station] = [];
    }
    groups[station].push(camera);
    return groups;
  }, {} as Record<string, CameraData[]>);

  return (
    <aside className="camera-list-panel">
      <h2>Node Select</h2>
      {cameras.length === 0 ? (
        <p>No active stations detected.</p>
      ) : (
        <div className="station-groups">
          {Object.entries(groupedCameras).map(([stationName, stationCameras]) => (
            <div key={stationName} className="station-group">
              <div className="station-header">{stationName}</div>
              <ul>
                {stationCameras.map(camera => (
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
            </div>
          ))}
        </div>
      )}
    </aside>
  );
};

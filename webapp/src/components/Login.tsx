import React from 'react';

interface LoginProps {
  loginUsername: string;
  setLoginUsername: (val: string) => void;
  loginPassword: string;
  setLoginPassword: (val: string) => void;
  loginError: string;
  isLoggingIn: boolean;
  handleLogin: (e: React.FormEvent) => void;
}

export const Login: React.FC<LoginProps> = ({
  loginUsername,
  setLoginUsername,
  loginPassword,
  setLoginPassword,
  loginError,
  isLoggingIn,
  handleLogin,
}) => {
  return (
    <div className="login-container">
      <form className="login-form" onSubmit={handleLogin}>
        <h2>CRT Surveillance</h2>
        
        <div style={{ marginBottom: '1rem', width: '100%' }}>
          <input 
            type="text" 
            value={loginUsername} 
            onChange={e => setLoginUsername(e.target.value)} 
            placeholder="Player Name"
            disabled={isLoggingIn}
            style={{ width: '100%', boxSizing: 'border-box' }}
          />
        </div>

        <div style={{ marginBottom: '1.2rem', width: '100%' }}>
          <input 
            type="password" 
            value={loginPassword} 
            onChange={e => setLoginPassword(e.target.value)} 
            placeholder="Password"
            disabled={isLoggingIn}
            autoFocus
            style={{ width: '100%', boxSizing: 'border-box' }}
          />
        </div>

        {loginError && <div className="error" style={{ marginBottom: '1rem' }}>{loginError}</div>}
        
        <button type="submit" disabled={isLoggingIn || !loginPassword.trim() || !loginUsername.trim()}>
          {isLoggingIn ? 'CONNECTING...' : 'CONNECT'}
        </button>
      </form>
    </div>
  );
};

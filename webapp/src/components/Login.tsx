import React from 'react';

interface LoginProps {
  loginInput: string;
  setLoginInput: (val: string) => void;
  loginError: string;
  isLoggingIn: boolean;
  handleLogin: (e: React.FormEvent) => void;
}

export const Login: React.FC<LoginProps> = ({
  loginInput,
  setLoginInput,
  loginError,
  isLoggingIn,
  handleLogin,
}) => {
  return (
    <div className="login-container">
      <form className="login-form" onSubmit={handleLogin}>
        <h2>CRT Surveillance</h2>
        <input 
          type="text" 
          value={loginInput} 
          onChange={e => setLoginInput(e.target.value)} 
          placeholder="Access Token..."
          disabled={isLoggingIn}
          autoFocus
        />
        {loginError && <div className="error">{loginError}</div>}
        <button type="submit" disabled={isLoggingIn || !loginInput.trim()}>
          {isLoggingIn ? 'CONNECTING...' : 'CONNECT'}
        </button>
      </form>
    </div>
  );
};

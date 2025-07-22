import React, { useState } from 'react';
import { authenticateWithWindows, getAuthInfo, getUserInfo, logout, testBasicAuth } from './services/authService';
import type { AuthResult, UserInfo, GroupInfo } from './services/authService';
import './App.css';

function App() {
  const [result, setResult] = useState<AuthResult | null>(null);
  const [loading, setLoading] = useState(false);

  const handleWindowsAuth = async () => {
    setLoading(true);
    try {
      const authResult = await authenticateWithWindows();
      setResult(authResult);
    } catch (error) {
      setResult({
        success: false,
        message: 'Windows認証でエラーが発生しました',
        errorCode: 'UNEXPECTED_ERROR'
      });
    } finally {
      setLoading(false);
    }
  };

  const handleGetAuthInfo = async () => {
    setLoading(true);
    try {
      const authResult = await getAuthInfo();
      setResult(authResult);
    } catch (error) {
      setResult({
        success: false,
        message: '認証情報の取得でエラーが発生しました',
        errorCode: 'UNEXPECTED_ERROR'
      });
    } finally {
      setLoading(false);
    }
  };

  const handleGetUserInfo = async () => {
    setLoading(true);
    try {
      const authResult = await getUserInfo();
      setResult(authResult);
    } catch (error) {
      setResult({
        success: false,
        message: 'ユーザー情報の取得でエラーが発生しました',
        errorCode: 'UNEXPECTED_ERROR'
      });
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    setLoading(true);
    try {
      const logoutResult = await logout();
      setResult(logoutResult);
    } catch (error) {
      setResult({
        success: false,
        message: 'ログアウトでエラーが発生しました',
        errorCode: 'UNEXPECTED_ERROR'
      });
    } finally {
      setLoading(false);
    }
  };

  const handleTestLogin = async (username: string, password: string) => {
    setLoading(true);
    try {
      const authResult = await testBasicAuth(username, password);
      setResult(authResult);
    } catch (error) {
      setResult({
        success: false,
        message: 'テスト認証でエラーが発生しました',
        errorCode: 'UNEXPECTED_ERROR'
      });
    } finally {
      setLoading(false);
    }
  };

  const handleClearResult = () => {
    setResult(null);
  };

  const renderUserInfo = (userInfo: UserInfo) => (
    <div className="user-info-section">
      <h3>ユーザー情報</h3>
      <div className="info-grid">
        <div className="info-item">
          <strong>ユーザー名:</strong> {userInfo.username}
        </div>
        {userInfo.fullName && (
          <div className="info-item">
            <strong>フルネーム:</strong> {userInfo.fullName}
          </div>
        )}
        {userInfo.domain && (
          <div className="info-item">
            <strong>ドメイン:</strong> {userInfo.domain}
          </div>
        )}
        {userInfo.sid && (
          <div className="info-item">
            <strong>SID:</strong> {userInfo.sid}
          </div>
        )}
        {userInfo.email && (
          <div className="info-item">
            <strong>メール:</strong> {userInfo.email}
          </div>
        )}
        {userInfo.authenticationType && (
          <div className="info-item">
            <strong>認証タイプ:</strong> {userInfo.authenticationType}
          </div>
        )}
      </div>
      
      {userInfo.groups && userInfo.groups.length > 0 && (
        <div className="groups-section">
          <h4>所属グループ</h4>
          <div className="groups-list">
            {userInfo.groups.map((group: GroupInfo, index: number) => (
              <div key={index} className="group-item">
                <div className="group-name">{group.name}</div>
                {group.fullName && group.fullName !== group.name && (
                  <div className="group-full-name">{group.fullName}</div>
                )}
                {group.sid && (
                  <div className="group-sid">SID: {group.sid}</div>
                )}
                {group.description && (
                  <div className="group-description">{group.description}</div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );

  return (
    <div className="App">
      <header className="App-header">
        <h1>Windows統合認証 API クライアント</h1>
        <p>Spring Boot APIサーバーと連携してWindows認証を実行します</p>
        
        <div className="button-container">
          <button 
            onClick={handleWindowsAuth} 
            disabled={loading}
            className="auth-button secure"
          >
            {loading ? '処理中...' : 'Windows認証でセキュアページにアクセス'}
          </button>
          
          <button 
            onClick={handleGetAuthInfo} 
            disabled={loading}
            className="auth-button info"
          >
            {loading ? '処理中...' : '認証情報を取得'}
          </button>
          
          <button 
            onClick={handleGetUserInfo} 
            disabled={loading}
            className="auth-button info"
          >
            {loading ? '処理中...' : 'ユーザー情報を取得'}
          </button>
          
          <button 
            onClick={handleLogout} 
            disabled={loading}
            className="auth-button logout"
          >
            {loading ? '処理中...' : 'ログアウト'}
          </button>
          
          <div className="test-section">
            <h3>テスト認証</h3>
            <button 
              onClick={() => handleTestLogin('testuser', 'password123')}
              disabled={loading}
              className="auth-button test"
            >
              {loading ? '処理中...' : 'テストユーザーでログイン'}
            </button>
            
            <button 
              onClick={() => handleTestLogin('admin', 'password123')}
              disabled={loading}
              className="auth-button test"
            >
              {loading ? '処理中...' : '管理者でログイン'}
            </button>
          </div>
          
          {result && (
            <button 
              onClick={handleClearResult} 
              className="auth-button clear"
            >
              結果をクリア
            </button>
          )}
        </div>

        {result && (
          <div className="result-container">
            <h2>APIレスポンス</h2>
            
            <div className={`status-indicator ${result.success ? 'success' : 'error'}`}>
              {result.success ? '✅ 成功' : '❌ エラー'}
            </div>
            
            <div className="message-section">
              <strong>メッセージ:</strong> {result.message}
            </div>
            
            {result.errorCode && (
              <div className="error-code">
                <strong>エラーコード:</strong> {result.errorCode}
              </div>
            )}
            
            {result.userInfo && renderUserInfo(result.userInfo)}
            
            <details className="json-details">
              <summary>JSONレスポンス（詳細）</summary>
              <pre className="json-content">
                {JSON.stringify(result, null, 2)}
              </pre>
            </details>
          </div>
        )}
      </header>
    </div>
  );
}

export default App;
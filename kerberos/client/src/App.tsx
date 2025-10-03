import { useState } from 'react';
import { 
  authenticateWithWindows, 
  logout
} from './services/authService';
import type { AuthResult, UserInfo, GroupInfo } from './services/authService';
import './App.css';

function App() {
  const [result, setResult] = useState<AuthResult | null>(null);
  const [loading, setLoading] = useState(false);

  const handleWindowsAuth = async () => {
    console.log('[App] Windows認証ボタンクリック');
    setLoading(true);
    try {
      const authResult = await authenticateWithWindows();
      console.log('[App] 認証結果:', authResult);
      setResult(authResult);
    } catch (error) {
      console.error('[App] Windows認証エラー:', error);
      setResult({
        success: false,
        message: 'Windows認証でエラーが発生しました',
        errorCode: 'UNEXPECTED_ERROR'
      });
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    setLoading(true);
    try {
      const authResult = await logout();
      setResult(authResult);
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

  return (
    <div className="App">
      <header className="App-header">
        <h1>Windows Kerberos Authentication Test</h1>

        <div className="button-group">
          <button onClick={handleWindowsAuth} disabled={loading}>
            ユーザー情報取得
          </button>
          <button onClick={handleLogout} disabled={loading} className="logout-btn">
            ログアウト
          </button>
        </div>

        {loading && <div className="loading">処理中...</div>}

        {result && result.success && (
          <div className="user-info">
            <h3>認証結果</h3>
            <pre style={{ textAlign: 'left', background: '#f5f5f5', padding: '20px', borderRadius: '8px', overflow: 'auto' }}>
              {JSON.stringify(result, null, 2)}
            </pre>
          </div>
        )}

        {result && !result.success && (
          <div className="result error">
            <h2>エラー</h2>
            <p>{result.message}</p>
            {result.errorCode && (
              <p className="error-code">エラーコード: {result.errorCode}</p>
            )}
          </div>
        )}
      </header>
    </div>
  );
}

export default App;
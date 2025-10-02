import { useState } from 'react';
import { 
  authenticateWithWindows, 
  getAuthInfo, 
  getUserInfo, 
  logout, 
  testBasicAuth,
  loginWithAD,
  getCurrentUser,
  authenticateWithKerberos
} from './services/authService';
import type { AuthResult, UserInfo, GroupInfo, LoginResponse } from './services/authService';
import './App.css';

function App() {
  const [result, setResult] = useState<AuthResult | LoginResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [serverMode, setServerMode] = useState<'server1' | 'server2'>('server2');
  const [adUsername, setAdUsername] = useState('');
  const [adPassword, setAdPassword] = useState('');

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

  const handleADLogin = async () => {
    if (!adUsername || !adPassword) {
      setResult({
        success: false,
        message: 'ユーザー名とパスワードを入力してください'
      });
      return;
    }

    setLoading(true);
    try {
      const authResult = await loginWithAD(adUsername, adPassword);
      setResult(authResult);
    } catch (error) {
      setResult({
        success: false,
        message: 'AD認証でエラーが発生しました'
      });
    } finally {
      setLoading(false);
    }
  };

  const handleGetCurrentUser = async () => {
    setLoading(true);
    try {
      const authResult = await getCurrentUser();
      setResult(authResult);
    } catch (error) {
      setResult({
        success: false,
        message: '現在のユーザー情報の取得でエラーが発生しました'
      });
    } finally {
      setLoading(false);
    }
  };

  const handleKerberosAuth = async () => {
    setLoading(true);
    try {
      const authResult = await authenticateWithKerberos();
      setResult(authResult);
    } catch (error) {
      setResult({
        success: false,
        message: 'Kerberos認証でエラーが発生しました'
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
      setAdUsername('');
      setAdPassword('');
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

  const handleBasicAuth = async () => {
    const username = prompt('ユーザー名を入力してください:');
    const password = prompt('パスワードを入力してください:');
    
    if (username && password) {
      setLoading(true);
      try {
        const authResult = await testBasicAuth(username, password);
        setResult(authResult);
      } catch (error) {
        setResult({
          success: false,
          message: 'Basic認証でエラーが発生しました',
          errorCode: 'UNEXPECTED_ERROR'
        });
      } finally {
        setLoading(false);
      }
    }
  };

  const renderUserInfo = (userInfo: UserInfo) => (
    <div className="user-info">
      <h3>ユーザー情報</h3>
      <table>
        <tbody>
          <tr>
            <td>ユーザー名:</td>
            <td>{userInfo.username}</td>
          </tr>
          {userInfo.fullName && (
            <tr>
              <td>フルネーム:</td>
              <td>{userInfo.fullName}</td>
            </tr>
          )}
          {userInfo.domain && (
            <tr>
              <td>ドメイン:</td>
              <td>{userInfo.domain}</td>
            </tr>
          )}
          {userInfo.email && (
            <tr>
              <td>メール:</td>
              <td>{userInfo.email}</td>
            </tr>
          )}
          {userInfo.sid && (
            <tr>
              <td>SID:</td>
              <td className="sid">{userInfo.sid}</td>
            </tr>
          )}
          {userInfo.authenticationType && (
            <tr>
              <td>認証タイプ:</td>
              <td>{userInfo.authenticationType}</td>
            </tr>
          )}
        </tbody>
      </table>
      
      {userInfo.groups && userInfo.groups.length > 0 && (
        <div className="groups">
          <h4>所属グループ</h4>
          <ul>
            {userInfo.groups.map((group: GroupInfo, index: number) => (
              <li key={index}>
                <strong>{group.name}</strong>
                {group.fullName && ` - ${group.fullName}`}
                {group.description && <div className="group-desc">{group.description}</div>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );

  const renderLoginResponse = (response: LoginResponse) => (
    <div className="user-info">
      <h3>認証結果</h3>
      <table>
        <tbody>
          {response.username && (
            <tr>
              <td>ユーザー名:</td>
              <td>{response.username}</td>
            </tr>
          )}
          {response.roles && response.roles.length > 0 && (
            <tr>
              <td>ロール:</td>
              <td>{response.roles.join(', ')}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );

  return (
    <div className="App">
      <header className="App-header">
        <h1>Windows/AD Authentication Test</h1>
        
        <div className="server-selector">
          <label>
            <input 
              type="radio" 
              value="server1" 
              checked={serverMode === 'server1'}
              onChange={() => setServerMode('server1')}
            />
            AD/LDAP認証 (デフォルトプロファイル - ポート8082)
          </label>
          <label>
            <input 
              type="radio" 
              value="server2" 
              checked={serverMode === 'server2'}
              onChange={() => setServerMode('server2')}
            />
            Kerberos認証 (kerberosプロファイル - ポート8082)
          </label>
        </div>

        {serverMode === 'server1' ? (
          <div className="button-group">
            <button onClick={handleWindowsAuth} disabled={loading}>
              Windows統合認証
            </button>
            <button onClick={handleGetAuthInfo} disabled={loading}>
              認証情報取得
            </button>
            <button onClick={handleGetUserInfo} disabled={loading}>
              ユーザー情報取得
            </button>
            <button onClick={handleBasicAuth} disabled={loading}>
              Basic認証テスト
            </button>
            <button onClick={handleLogout} disabled={loading} className="logout-btn">
              ログアウト
            </button>
          </div>
        ) : (
          <div className="ad-auth-form">
            <h3>AD認証フォーム</h3>
            <div className="form-group">
              <input
                type="text"
                placeholder="ドメイン\ユーザー名 または UPN"
                value={adUsername}
                onChange={(e) => setAdUsername(e.target.value)}
                disabled={loading}
              />
            </div>
            <div className="form-group">
              <input
                type="password"
                placeholder="パスワード"
                value={adPassword}
                onChange={(e) => setAdPassword(e.target.value)}
                disabled={loading}
              />
            </div>
            <div className="kerberos-section">
              <h4>自動認証（ドメイン参加クライアント用）</h4>
              <button onClick={handleKerberosAuth} disabled={loading}>
                Windows統合認証（Kerberos）
              </button>
            </div>
            
            <div className="button-group">
              <button onClick={handleADLogin} disabled={loading}>
                ADログイン
              </button>
              <button onClick={handleGetCurrentUser} disabled={loading}>
                現在のユーザー
              </button>
              <button onClick={handleLogout} disabled={loading} className="logout-btn">
                ログアウト
              </button>
            </div>
          </div>
        )}

        {loading && <div className="loading">処理中...</div>}
        
        {result && (
          <div className={`result ${result.success ? 'success' : 'error'}`}>
            <h2>{result.success ? '成功' : 'エラー'}</h2>
            <p>{result.message}</p>
            
            {result && typeof result === 'object' && 'userInfo' in result && result.userInfo && renderUserInfo(result.userInfo)}
            {result && typeof result === 'object' && 'username' in result && result.username && renderLoginResponse(result as LoginResponse)}
            
            {result && typeof result === 'object' && 'errorCode' in result && result.errorCode && (
              <p className="error-code">エラーコード: {result.errorCode}</p>
            )}
          </div>
        )}
      </header>
    </div>
  );
}

export default App;
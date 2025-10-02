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

  return (
    <div className="App">
      <header className="App-header">
        <h1>Windows NTLM Authentication Test</h1>
        
        <div className="button-group">
          <button onClick={handleWindowsAuth} disabled={loading}>
            Windows統合認証（NTLM）
          </button>
          <button onClick={handleLogout} disabled={loading} className="logout-btn">
            ログアウト
          </button>
        </div>

        {loading && <div className="loading">処理中...</div>}
        
        {result && (
          <div className={`result ${result.success ? 'success' : 'error'}`}>
            <h2>{result.success ? '成功' : 'エラー'}</h2>
            <p>{result.message}</p>
            
            {result && typeof result === 'object' && 'userInfo' in result && result.userInfo && renderUserInfo(result.userInfo)}
            
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
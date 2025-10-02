import axios from 'axios';

// APIベースURL（プロキシ経由でアクセス）
const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

// Axiosインスタンスの作成
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true, // Windows認証のクレデンシャルを送信
  headers: {
    'Content-Type': 'application/json',
  },
});

// 認証結果の型定義
export interface AuthResult {
  success: boolean;
  message: string;
  userInfo?: UserInfo;
  errorCode?: string;
}

export interface UserInfo {
  username: string;
  fullName?: string;
  domain?: string;
  sid?: string;
  email?: string;
  groups?: GroupInfo[];
  authenticationType?: string;
}

export interface GroupInfo {
  name: string;
  fullName?: string;
  sid?: string;
  description?: string;
}

// Windows統合認証（NTLM）のAPI呼び出し
export const authenticateWithWindows = async (): Promise<AuthResult> => {
  console.log('[AuthService] Windows統合認証を開始...');
  try {
    const response = await apiClient.get('/user');
    console.log('[AuthService] /user レスポンス:', response);
    
    // HTMLレスポンスのチェック
    if (typeof response.data === 'string' && response.data.includes('<!doctype html>')) {
      return {
        success: false,
        message: 'サーバーからHTMLが返されました。エンドポイントを確認してください。',
        errorCode: 'HTML_RESPONSE'
      };
    }
    
    console.log('[AuthService] Windows認証成功:', response.data);
    return response.data;
  } catch (error: any) {
    console.error('[AuthService] Windows認証エラー:', error);
    console.error('[AuthService] エラー詳細:', {
      response: error.response,
      request: error.request,
      message: error.message,
      config: error.config
    });
    if (error.response && error.response.data) {
      console.error('[AuthService] エラーレスポンスデータ:', error.response.data);
      // HTMLレスポンスのチェック
      if (typeof error.response.data === 'string' && error.response.data.includes('<!doctype html>')) {
        return {
          success: false,
          message: 'サーバーエラー: HTMLページが返されました',
          errorCode: 'HTML_ERROR_PAGE'
        };
      }
      // サーバーからエラーレスポンスが返ってきた場合
      return error.response.data;
    } else if (error.request) {
      // リクエストは送信されたが、レスポンスがない場合
      return {
        success: false,
        message: 'サーバーに接続できません。サーバーが起動しているか確認してください。',
        errorCode: 'NETWORK_ERROR'
      };
    } else {
      // その他のエラー
      return {
        success: false,
        message: `エラーが発生しました: ${error.message}`,
        errorCode: 'UNKNOWN_ERROR'
      };
    }
  }
};

// ログアウトAPI呼び出し
export const logout = async (): Promise<AuthResult> => {
  try {
    await apiClient.post('/logout');

    return {
      success: true,
      message: 'ログアウトしました',
    };
  } catch (error: any) {
    return {
      success: false,
      message: `ログアウトに失敗しました: ${error.message}`,
      errorCode: 'LOGOUT_ERROR'
    };
  }
};
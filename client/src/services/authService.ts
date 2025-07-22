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

// AD認証用のレスポンス型
export interface LoginResponse {
  success: boolean;
  message: string;
  username?: string;
  roles?: string[];
}

// Windows統合認証のAPI呼び出し
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

// ホームページのAPI呼び出し（認証情報を取得）
export const getAuthInfo = async (): Promise<AuthResult> => {
  try {
    const response = await apiClient.get('/home');
    
    // HTMLレスポンスのチェック
    if (typeof response.data === 'string' && response.data.includes('<!doctype html>')) {
      return {
        success: false,
        message: 'サーバーからHTMLが返されました。エンドポイントを確認してください。',
        errorCode: 'HTML_RESPONSE'
      };
    }
    
    return response.data;
  } catch (error: any) {
    if (error.response && error.response.data) {
      // HTMLレスポンスのチェック
      if (typeof error.response.data === 'string' && error.response.data.includes('<!doctype html>')) {
        return {
          success: false,
          message: 'サーバーエラー: HTMLページが返されました',
          errorCode: 'HTML_ERROR_PAGE'
        };
      }
      return error.response.data;
    }
    return {
      success: false,
      message: `認証情報の取得に失敗しました: ${error.message}`,
      errorCode: 'FETCH_ERROR'
    };
  }
};

// ユーザー情報のAPI呼び出し
export const getUserInfo = async (): Promise<AuthResult> => {
  try {
    const response = await apiClient.get('/user-info');
    
    return response.data;
  } catch (error: any) {
    if (error.response && error.response.data) {
      return error.response.data;
    }
    return {
      success: false,
      message: `ユーザー情報の取得に失敗しました: ${error.message}`,
      errorCode: 'USER_INFO_ERROR'
    };
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

// Basic認証テスト用（開発・テスト用）
export const testBasicAuth = async (username: string, password: string): Promise<AuthResult> => {
  try {
    const credentials = btoa(`${username}:${password}`);
    const response = await apiClient.get('/secure', {
      headers: {
        'Authorization': `Basic ${credentials}`,
      },
    });
    
    return response.data;
  } catch (error: any) {
    if (error.response && error.response.data) {
      return error.response.data;
    }
    return {
      success: false,
      message: `Basic認証に失敗しました: ${error.message}`,
      errorCode: 'BASIC_AUTH_ERROR'
    };
  }
};

// AD認証（フォームベース）
export const loginWithAD = async (username: string, password: string): Promise<LoginResponse> => {
  try {
    const response = await apiClient.post('/login', {
      username,
      password
    });
    
    return response.data;
  } catch (error: any) {
    if (error.response && error.response.data) {
      return error.response.data;
    }
    return {
      success: false,
      message: `AD認証に失敗しました: ${error.message}`
    };
  }
};

// 現在のユーザー情報を取得（server2用）
export const getCurrentUser = async (): Promise<LoginResponse> => {
  try {
    const response = await apiClient.get('/user');
    
    return response.data;
  } catch (error: any) {
    if (error.response && error.response.data) {
      return error.response.data;
    }
    return {
      success: false,
      message: `ユーザー情報の取得に失敗しました: ${error.message}`
    };
  }
};

// Windows統合認証（SPNEGO/Kerberos）- ドメイン参加クライアント用
export const authenticateWithKerberos = async (): Promise<LoginResponse> => {
  try {
    // withCredentials: true でNegotiate認証を有効化
    const response = await apiClient.get('/user', {
      withCredentials: true,
      headers: {
        'Cache-Control': 'no-cache',
      },
    });
    
    return response.data;
  } catch (error: any) {
    if (error.response && error.response.status === 401) {
      // 401の場合、ブラウザがNegotiate認証を試行する
      return {
        success: false,
        message: 'Windows統合認証が利用できません。ドメインに参加しているか確認してください。'
      };
    }
    
    if (error.response && error.response.data) {
      return error.response.data;
    }
    
    return {
      success: false,
      message: `認証エラー: ${error.message}`
    };
  }
};
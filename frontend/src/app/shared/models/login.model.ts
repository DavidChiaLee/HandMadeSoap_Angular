export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  username: string;
  role: string;
  // refresh token 只走 HttpOnly Cookie，後端 login/refresh 回應會把此欄位置 null；
  // 前端一律不讀不存，留 optional 僅為相容舊回應結構。
  refreshToken?: string | null;
}

/**
 * 存在 localStorage（current_user）的可顯示登入者資料。
 *
 * <p>{@code exp} 為 JWT 的過期時間（秒級，Unix timestamp），於 {@link AuthService.setUser}
 * 時由 access token 解碼取得，供 authGuard 判斷 token 是否已過期 —— 避免過期 token
 * 仍可進入 admin 路由、進頁後各請求才相繼 401 的體驗跳動。</p>
 */
export interface CurrentUser {
  username: string;
  role: string;
  // 秒級過期時間；舊資料或解碼失敗時可能缺此欄，optional。
  exp?: number | null;
}

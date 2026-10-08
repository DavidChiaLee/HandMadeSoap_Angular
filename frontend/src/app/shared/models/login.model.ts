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

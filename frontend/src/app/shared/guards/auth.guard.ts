import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);

  // 三重把關（review L）：有 token、token 未過期、且角色為 ADMIN。
  // 後端才是權威，這裡為 UX 層提前攔截，避免過期／越權仍進頁再相繼 401。
  const user = authService.getCurrentUser();
  const allowed = authService.isTokenValid() && user?.role === 'ADMIN';

  if (allowed) {
    return true;
  }

  // 帶上原本想去的頁面作為 returnUrl，登入成功後導回；並避免登入頁重複導向。
  authService.redirectToLogin(state.url);
  return false;
};

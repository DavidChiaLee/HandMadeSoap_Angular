/**
 * 本機開發用環境設定（`ng serve` / `ng test` 讀這一份）。
 * 後端在本機 8080，因此 API 與圖片都指向絕對位址。
 *
 * 正式版建置會被 angular.json → configurations.production.fileReplacements
 * 換成 environment.prod.ts（同源相對路徑），請勿在此檔寫入正式環境設定。
 */
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api/v1',
  uploadUrl: 'http://localhost:8080/uploads/'
};

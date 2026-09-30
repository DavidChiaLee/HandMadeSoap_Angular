/**
 * 正式環境（production build）用設定。
 *
 * 網站與 API 由同一個 nginx 提供（/api/ 與 /uploads/ 反向代理到 backend:8080），
 * 所以一律使用同源的相對路徑：不受網域／連接埠影響，也不會出現 mixed content。
 *
 * 注意：本檔必須由 angular.json → configurations.production.fileReplacements 取代
 * environment.ts 才會生效；只改這裡、沒設定 fileReplacements，產物不會有任何變化。
 */
export const environment = {
  production: true,
  apiUrl: '/api/v1',
  uploadUrl: '/uploads/'
};

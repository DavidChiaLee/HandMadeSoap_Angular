export const MAX_UPLOAD_SIZE = 20 * 1024 * 1024; // 20MB，與後端 application.properties 同步
export const MAX_UPLOAD_SIZE_MB = 20;
export const UPLOAD_SIZE_ERROR_MSG = `圖片檔案不能超過 ${MAX_UPLOAD_SIZE_MB}MB，請壓縮後再選取`;

/**
 * 沒有圖片時顯示的本機佔位圖。
 * 刻意不使用外部服務（如 via.placeholder.com）：避免第三方請求、離網破圖與 CSP 放行問題。
 */
export const PLACEHOLDER_IMAGE = 'assets/placeholder.svg';
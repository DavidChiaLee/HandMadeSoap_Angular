# Build stage
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
# npm ci 而非 npm install（review M11 / 表 729）：
#   - 嚴格依 package-lock.json 安裝，映像內容可重現，並做 lockfile 完整性校驗；
#     npm install 每次依 semver 重新解析，同原始碼兩次 build 可能得到不同版本。
#   - lock 與 package.json 不同步時直接失敗（fail-fast，早一步抓錯）。
#   - CI（.github/workflows/ci.yml）已用 npm ci，兩端語意一致（該處註解已同步）。
# --ignore-scripts 經評估「暫不採用」：lock 內有 6 個套件帶安裝腳本
# （esbuild、lmdb、msgpackr-extract、nice-napi 等，Angular 18 的 ng build 底層
# bundler 正是 esbuild），關掉腳本很可能讓建置失敗。若日後要開，必須先實測
# `npm ci --ignore-scripts && npm run build` 通過再改，勿直接加參數。
RUN npm ci
COPY . .
RUN npm run build

# Serve stage
FROM nginx:alpine
# outputPath 已設 { browser: "dist/frontend", server: "" }（angular.json），
# 產物直接在 dist/frontend/（不再有 browser/browser 雙層）。若日後改 outputPath，
# 這裡的 COPY 來源必須同步，否則會 COPY 到空目錄——CI 的 compose build + up 當防線。
COPY --from=build /app/dist/frontend/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
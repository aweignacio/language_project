# ══════════════════════════════════════════════════════════════════════════
#  這個檔案負責什麼
# ══════════════════════════════════════════════════════════════════════════
#
#  把「一堆原始碼」變成「一個可以直接執行的東西」。
#
#  這個專案有兩種技術：Angular 要用 Node 建置，Spring Boot 要用 Maven 建置，
#  而且前端建置出來的檔案要塞進後端。所以說明書分三段。
#
#  ★ 為什麼要「多階段」：
#    建置時需要 Node 和 Maven，但執行時完全用不到它們。
#    分階段之後，那些工具不會被帶進最後的成品 ——
#    映像檔從 1GB 以上縮到 300MB 左右，啟動更快，可被攻擊的面積也更小。
# ══════════════════════════════════════════════════════════════════════════

# ── 第一階段：建置 Angular ────────────────────────────────────────────────
FROM node:22-alpine AS frontend-build

WORKDIR /build

# 先只複製相依定義再安裝。
# ★ 這是為了讓 Docker 的快取生效：只要 package.json 沒變，
#   下次建置就直接沿用上一次裝好的 node_modules，省好幾分鐘。
#   如果一開始就 COPY 全部，改一行 CSS 也會重裝全部套件。
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# 產出落在 /build/dist/frontend/browser/


# ── 第二階段：建置 Spring Boot ────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS backend-build

WORKDIR /build

# 同樣先只複製 pom.xml 讓相依套件的下載能被快取。
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src/ ./src/

# ★ 關鍵的一步：把前端的產出塞進 Spring Boot 的靜態資源資料夾。
#   放進 static/ 之後，Spring Boot 會把它當成一般的網頁檔案直接吐出去，
#   前端和後端從此是同一個網址，不會有跨網址被瀏覽器阻擋的問題。
COPY --from=frontend-build /build/dist/frontend/browser/ ./src/main/resources/static/

# 測試在本機或 CI 跑，這裡不重複跑：
# 建置階段沒有資料庫可連，而本專案的 Repository 測試打的是真資料庫。
RUN mvn -B clean package -DskipTests


# ── 第三階段：執行環境 ────────────────────────────────────────────────────
# 只有 Java，沒有 Maven、沒有 Node、沒有原始碼。
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# 不用 root 執行。萬一程式被攻破，攻擊者拿到的權限也有限。
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=backend-build /build/target/*.jar app.jar

# Cloud Run 會透過 PORT 環境變數告訴容器要聽哪個埠，預設 8080。
ENV PORT=8080
EXPOSE 8080

# ★ -XX:MaxRAMPercentage 讓 JVM 依「容器實際分配到多少記憶體」自動調整堆大小。
#   寫死 -Xmx 的話，之後在 Cloud Run 調整記憶體規格就得跟著改這裡，很容易忘。
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT} -jar app.jar"]

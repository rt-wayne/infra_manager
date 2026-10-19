# 待辦清單（BACKLOG）

> 給 Claude Code 讀的工作檔。維護規則：
> 1. 只放**未結案**項目。項目完成或拍板不做 → 立刻從對應分區移除／移入「已拍板不做」，
>    並在 `CHANGELOG.md` 記一行。歷史不留在本檔
> 2. 狀態用**分區**表達，不寫在標題的自由文字裡（避免「（已拍板 → ✅ 已完成）」這種
>    只有人看得懂、grep 必然失準的寫法）
> 3. 摘要一行講完。**只有累積了實質討論記錄的項目**才另開 `backlog/<ID>-<短名>.md`，
>    一句話就講完的項目不要建檔
> 4. **動工前必須先讀該項的詳情檔（若有）**，不得只憑摘要行開工
> 5. ID 只增不重用（移除的項目其編號不再指派給別人），方便對話中直接以編號指涉

## 進行中

| ID | 摘要 | 詳情 |
|----|------|------|
| 89 | 角色權限維護介面：角色指派、執行人對照兩頁（取代改 DB），另加第三頁設定角色可做哪些事／看到哪些頁面（角色 × 權限碼）；每次異動寫系統管理軌跡、原因必填，移除會卡住在途單或最後一位系統管理員時拒絕 | [backlog/89-角色權限維護介面.md](backlog/89-角色權限維護介面.md) |

### 交接狀態（每次停下回報時更新；無進行中項目時三欄留空）
- 下一步：階段 2 已結束（審查必須修正零項、測試 311＋73 全過、建議改善登記第 99～105 項；D1 裁示 A 已改 PRD 第 46、120 行，第 25 行偏離第 3 條照計畫等階段 6 刪）。開階段 3 回合 1（後端角色權限碼：`ADMIN_VISIBLE_PERMS` 常數、GET /admin/role-perms、COUNT_HOLDERS 的「排除某角色＋某碼」路徑），依計畫檔「階段 3」；開工前先問使用者第 100 項（DENY 軌跡灌爆）要不要在第三頁上線前處理；開工時把 HEAD 填進「本階段起點」
- 已改動（未 commit）：無（階段 2 收尾已 commit）
- 本階段起點：階段 3 開工時填（階段 2 起點 fc24068 已結案）
- 卡住／待確認：無

## 僅記錄未拍板

| ID | 摘要 | 提出日 | 詳情 |
|----|------|--------|------|
| 8 | `/me` 回 401 以外的錯誤時，需權限的頁面導向無權限頁而非顯示 U09「查詢失敗，請稍後再試」，要整頁重新整理才恢復（`useSession.ts:59-64`） | 2026-10-10 | — |
| 11 | IM_ACCT_ADMIN_LOG.CONTENT_JSON 宣告 VARCHAR2(2000 CHAR)，受單欄 4000 bytes 限制實際約只容 1333 個中文字；目前只存切換對象顯示名不會超過，日後寫入更多內容前須處理 | 2026-10-10 | — |
| 12 | DAO 以參數顯式綁定 NULL 會蓋過 DB DEFAULT（IS_PRIV、EXPIRY_TYPE_CODE、IS_UNIQUE_CONF 等必填欄）；目前 Service 都先填值所以沒出事，日後新路徑漏填會直接 ORA-01400 | 2026-10-10 | — |
| 13 | 顯示名兩種算法不一致：`AcctReqDao.nameExpr`（第 42 行，用於第 56-58、181 行與 `AttachDao.java:43`）不 trim、無工號退回；快照用的 `Texts.displayName`（`Texts.java:35-42`）有 trim 與英文名 → 中文名 → 工號退回 | 2026-10-10 | — |
| 14 | 第二階段功能：定期審查、統計頁、批次匯出帳號歷史（SA 第 8.2 節，同用 ACC_EXPORT）、巡檢匯出、資產分類與巡檢項目維護（緊急存取已拆出為第 46 項；帳號權限清單匯出已於第 80 項完成）；目前只有權限碼與預留欄位，無程式 | 2026-10-10 | — |
| 16 | 原始規格書缺件：子 README 與 `.example` 註解（範本已於 2026-10-14 刪除）引用 `spec_im_acct_insp_v2.0.md`、變更單 #1～#4，但 repo 與使用者手上都沒有；PRD 不引用 | 2026-10-10 | — |
| 17 | `infra_manager_web\README.md` 第 2 節「兩包結構」表仍是範本初始描述：說 Controller 目錄只有 `package-info.java`、`src\types\` 與 `src\views\` 只有 `.gitkeep`、router 的 routes 是空陣列，實際都已有程式（4 個型別檔、10 個畫面、14 個 path） | 2026-10-10 | — |
| 18 | 登入查詢後才被改成停用（STATUS＝0）的帳號，該次登入仍取得 token 與回應中的權限清單；下一次呼叫 API 即因 `TokenIdentityProvider` 檢查 STATUS 而回 A03，攻擊面接近零 | 2026-10-10 | — |
| 19 | 人工鎖定（DB 直接寫 LOCK_DATE）只擋之後的登入，不擋已發出的 token；`TokenIdentityProvider` 每次請求只查 STATUS 不查 LOCK_DATE。要不要讓鎖定也立即作廢既有 token 屬設計決策 | 2026-10-10 | — |
| 20 | 門禁卡新增複驗退回後再次回報、卡號沒變時不應更新主表帳號名稱（`RequestService.java:465` 的 `else if`），此分支沒有測試；只有換卡號會更新的測試（`RequestServiceTest.java:231`） | 2026-10-10 | — |
| 21 | `im_acct_insp_交接文件.md`（初始交付文件）第 692 行寫「空檔視同未上傳」，未含「副檔名不符仍回 E14」的例外，與現行程式不一致；先決定這份文件是否持續隨程式維護 | 2026-10-10 | — |
| 22 | J02（每週逾期彙總信）以「本週有無任何通知紀錄」判斷是否已寄，寄送失敗（FAIL）也寫紀錄，所以本週信全部寄失敗時同週不會再重寄（`JobService.java:299`、`NotificationService.java:296,303`）；J01 同一套判斷。SETUP 已註明後果，要不要改程式未拍板 | 2026-10-10 | — |
| 23 | SETUP 第 7 節的 J01／J02 觸發 curl 尚未實跑（使用者裁示不在第 6 項實跑；登入與登出已於 2026-10-11 實跑），日後有安全的測試時機時補跑並核對回應。可用測試 DB 的測試帳號 2410305（密碼同工號；2026-10-11 已加 DC_ADMIN、INFRA_MGR、GOV、GOV_MGR、IT_HEAD、SYS_ADMIN、INSP_STAFF、ACCT_REGISTRY 八個角色與全部 8 個帳號類別的執行人，Email 為使用者本人信箱；使用者裁示這些權限不移除） | 2026-10-10 | — |
| 24 | token 標登出（系統登出或 SETUP 第 6 節第 4 段外洩處理）COMMIT 前已通過 token 檢查的進行中請求都會照常完成（每個請求各一次）；其中正卡在更新最後存取時間的那類，程式本可依影響 0 筆擋下卻沒擋（`TokenIdentityProvider.java:57-59`）。日後補縫時不能只檢查更新回傳值，那只擋得住後面那類 | 2026-10-11 | — |
| 29 | 切換身分再切回後在首頁按登出，會多送一個不帶 token 的 `GET /home/stats` 並回 401（第 28 項瀏覽器實測發現，3 次登出穩定重現於此路徑）；推測 `resetSession` 把 `viewKey` 歸 0 讓首頁重建。畫面無差別，只多一行 console error；401 處理會寫 `loginNotice`，之後開登入頁會不會冒出「登入已逾時」未驗證。同類：第 28 項第二、三輪實測各見一次登出瞬間 `GET /todos` 回 401（第三輪是在待辦頁登出，重跑 3 次未重現）；推測 `AppLayout.vue` 換頁時的 `watch(route.fullPath) → refreshBadges` 在「token 已清、版面未卸載」瞬間觸發，未證實 | 2026-10-11 | — |
| 34 | 正式環境走 HTTPS：使用者確認（2026-10-11）正式環境能用 HTTPS（公司憑證或反向代理）；目前 `SETUP.md` 範例網址為 `http://`，密碼與 token 明文傳輸。做法（殼 jar 自己掛憑證或前面放反向代理）、憑證來源未定；完成後補 HSTS 標頭（第 30 項不含） | 2026-10-11 | — |
| 36 | 測試 DB 的附件 14～18、20 在 DB 有紀錄但實體檔不存在，後端下載回 500「附件檔案不存在」，真實附件下載無法實測（第 30 項瀏覽器實測改以模擬 PDF 驗證下載）。要補檔、刪紀錄或維持現狀未定 | 2026-10-11 | — |
| 37 | 帳號歷史彈窗在視窗高 900px 時，底部「關閉」鈕被畫面底緣切掉一半（按得到、關得掉）；第 30 項瀏覽器補測發現，與安全標頭無關 | 2026-10-11 | — |
| 41 | `PRD.md` 表單欄位表「使用人聯絡方式」列寫「灰底範例『#1614 或 Email』」，但畫面（`RequestFormView.vue` 的 `#holderCntct`）是一般 placeholder「請填寫使用人的分機、電話或 Email（外部廠商請務必填寫）」、無灰底；第 40 項審查時發現的既有落差，要改 PRD 照現況或改畫面照 PRD 未定 | 2026-10-11 | — |
| 44 | `SETUP.md` 第 71 行寫 `npm run build` 會清掉 `infra_manager_web\src\frontend\.gitkeep`、要用 `git checkout` 還原，且「該目錄只有 `.gitkeep` 時殼 jar 也能建置」；但使用者已確認刪除該 `.gitkeep`（第 43 項動工前快照 `83a5f62` 一併 commit），現在 git 還原不回來，新 clone 沒有該目錄時殼 jar 能否建置未驗證。`infra_manager_web\README.md` 第 32 行「平時只有 `.gitkeep`」同樣過時。第 43 項 test-runner 發現。補充（第 46 項 S3 審查，2026-10-13）：`infra_manager_web\infra_manager_web\.gitignore` 第 12 行 `!src/frontend/.gitkeep` 例外仍在但檔已不在 git；單純補回沒用，每次 `npm run build`（vite 清空輸出目錄）都會再刪，要一併改建置設定或改用其他方式保留目錄 | 2026-10-12 | — |
| 45 | 處理中的按鈕沒有停用外觀：`src/assets/app.css` 第 84 行 `.btn-link` 無 `:disabled` 樣式（只有 `.btn:disabled` 半透明，第 79 行），附件檔名鈕與下載鈕在處理中停用時外觀、游標都不變，下載大檔時看不出在處理。改法一行 `.btn-link:disabled { opacity: .55; cursor: default; }`，但屬全站共用樣式，要目視確認其他畫面的停用連結鈕。第 43 項 code-reviewer 發現，使用者裁示（2026-10-12）留到下一批 | 2026-10-12 | — |
| 31 | 一個分頁收到 401 清 token 時，其他分頁被連帶整頁重新整理後落在空白登入頁，看不到「登入已逾時」原因（`loginNotice` 只在記憶體，`useSession.ts`）；可在重新整理前把訊息暫存 sessionStorage、登入頁讀出即刪。使用者裁示（2026-10-11）先只記錄：8 小時到期一天只遇一次 | 2026-10-11 | — |
| 47 | IM_ACCT_JOB_RUN 沒有清理機制；J03（每 15 分鐘）上線後每天約 96 列、一年約 3.5 萬列。第 46 項 architect 發現 | 2026-10-12 | — |
| 48 | `db\infra_manager_create_table.sql` 與 `SETUP.md` 都沒有記載 public synonym 與 ap_user 授權怎麼建（原本只有 `database.properties.example:20` 一句「有 Public Synonym」，該範本已於 2026-10-14 刪除），新環境照 SETUP 建好後殼 jar 可能 ORA-00942 讀不到表。第 46 項 architect 發現；S1 會把緊急存取新表的 synonym／GRANT 寫進遞增腳本，但第一階段 21 表的建法仍待補 | 2026-10-12 | — |
| 49 | 第一階段 21 張表對照 `DB規範_V1_20261002_JLA.xlsx` 規則 3（單字超過 6 碼要用字典縮寫）有 7 個字未縮寫：AFFECTED_CNT、CONTENT_JSON、IS_ENABLED、IS_SELECTABLE、SUGGEST_*、IS_REVIEW_TRIGGER、VERSION_NO；「IM_」前綴也不在規範的業務簡碼表。已上線欄位改名要動程式、PRD 與既有資料。第 46 項 DB 規範對照時發現，使用者裁示（2026-10-12）只登記 | 2026-10-12 | — |
| 50 | `SqlConstantScanTest` 的黏字檢查目前只抓 `END` 接下一個字（`\bEND[A-Z]`）；同類拼接錯誤還有 `)AS`、`'X'AS`、`:nowAND`、`NULLAS`、漏逗號、連兩個逗號等沒抓。可擴充方向：關鍵字前必須有空白、`,\s*,`、`,\s*(FROM\|WHERE)\b`，要先確認不誤報既有 SQL。另：掃描清單比對只認 `@Repository`，DAO 日後改標 `@Component`、用 `@Bean` 建立或寫成介面／抽象類別時會漏掃而測試照過（目前 15 支都有標），可改依「含 static final String 的類別」或類別名 `*Dao` 比對。第 46 項 ENDAS 修正審查時提出，使用者裁示（2026-10-13）只登記 | 2026-10-13 | — |
| 51 | Service 單元測試把 DAO 全 mock，SQL 文法錯誤要連 DB 才會發現（第 46 項 ENDAS 即此）。不連 DB 的做法有四種：(a) 在片段接縫處斷言前後有空白 (b) 擴充第 50 項的規則 (c) 把各 SQL 常數存快照逐字比對 (d) 引入 JSqlParser 解析（新套件要先審查）；連 DB 的話可在測試環境以 `WHERE 1=0` 包住執行，或呼叫 `DBMS_SQL.PARSE` 只解析不執行。第 46 項 ENDAS 修正審查時提出，使用者裁示（2026-10-13）只登記 | 2026-10-13 | — |
| 52 | `NotificationService` 把寄信流程的 RuntimeException 一律 catch 後只寫 log：寄信前讀單失敗（例如第 46 項 ENDAS 那種 SQL 錯）時，送出仍成功、主管收不到通知，系統沒有任何可見警示（畫面、排程失敗通知都沒有）。「寄信失敗不影響送出結果」是既定規則，要補的是可見性。第 46 項 ENDAS 修正審查時提出，使用者裁示（2026-10-13）只登記 | 2026-10-13 | — |
| 53 | 前端「字數按碼點、去頭尾空白」的寫法 `[...s.trim()].length` 重複 5 次，分布在 `EmergencyDetailModal.vue`、`EmergencyReportModal.vue`（兩處）、`EmgAcctPicker.vue`、`EmergencyFormView.vue`；可抽成 `utils` 共用函式。使用者看不到差別，這是內部品質問題；日後改字數規則要改 5 處。第 46 項 S5 回合 2 審查時提出，使用者裁示（2026-10-13）只登記 | 2026-10-13 | — |
| 56 | 上線前確認正式環境後端 `infra_manager` 是否只跑一個實例：排程鎖（`JobService` 的 AtomicBoolean）只在單一 JVM 內有效，多台會重複寄 J01／J02／J03 的信、互相把執行中的紀錄誤記成失敗；殼 jar 不跑排程、不算。多台或要做高可用時另立項改 DB 鎖。第 46 項排程階段動工時使用者未回答實際部署，先照單台施工 | 2026-10-13 | — |
| 57 | J03 逾期通知在「信已寄出但通知紀錄全部寫不進 DB」（例如新環境沒補 `SEQ_IM_ACCT_NOTIFY_LOG` synonym 或 INSERT 權限、tablespace 滿）時，`NOT EXISTS` 仍成立，同一張單每 15 分鐘重寄給所有 EMG_DECIDE 持有人、一天約 96 封；排程失敗信（回合 2 起當天一封）只警示管理者，擋不住逾期信本身。可改為程式記憶體記住本次執行期間寄過的單號（重啟就清空）。J01 有同樣模式但一天一次。第 46 項 S7 回合 1 審查提出，使用者裁示（2026-10-13）維持現狀只登記 | 2026-10-13 | — |
| 54 | 待辦頁（`TodoView.vue`）疑似有「彈窗關閉後通知被丟掉」問題：第 46 項 S5 回合 2 審查指出它與緊急存取詳情彈窗同款（彈窗卸載後 emit 遺失），原始細節未留存。2026-10-13 查看結果：核准／退回等動作是在頁面本身的 `run()` 執行（第 221-245 行，成功或 409 時清狀態並重載），但四個彈窗的 `@close`（第 71、88、104、118 行）處理中沒有擋，處理中關掉彈窗後結果是否真的沒顯示未證實，動工前先在瀏覽器重現。使用者裁示（2026-10-13）登記 | 2026-10-13 | — |
| 59 | 鈴鐺徽章查詢（`useSession.ts` 的 `refreshBadges`）有「舊回應蓋掉新狀態」競態：只在開頭檢查 token，await 回來直接寫入；請求未回時登出（或 401 觸發 `resetSession`）後，晚到的回應會把上一位使用者的資料寫回模組狀態，下一位登入者首次 refresh 完成前可能短暫看到上一位的徽章（含緊急存取單號與申請人姓名）。視窗很窄（請求要比登出慢）；改法：模組層遞增序號，`resetSession` 與每次 `refreshBadges` 開頭 +1，await 回來序號不符就丟棄。既有問題，第 46 項 S9 把外洩範圍擴大到緊急存取資料；S9 審查提出，使用者裁示（2026-10-13）只登記 | 2026-10-13 | — |
| 60 | 鈴鐺面板（`app.css` `.bell-panel`）沒有 `max-height` 與 `overflow-y`，後端到期與已退回查詢都沒有筆數上限，兩類筆數一多面板會超出視窗、底部點不到；改法一行 `max-height: 70vh; overflow-y: auto`。第 46 項 S9 審查提出，使用者裁示（2026-10-13）只登記 | 2026-10-13 | — |
| 61 | 鈴鐺面板視覺與排序小問題：(1) 沒有「查看全部到期清單」鈕時，第二節標題緊貼最後一筆到期項目（`.bell-panel-title` 只有 `margin-bottom`）；(2) `.bell-item:last-of-type` 依元素類型比對，兩節項目都是 `button`，第一節最後一筆的底線會保留；(3) 類別 3 依後端送出時間遞減排序，畫面顯示的卻是退回日期，日期可能看起來不是遞減（SA 沒規定排序，可不改或改顯示送出日）。第 46 項 S9 審查提出，使用者裁示（2026-10-13）只登記 | 2026-10-13 | — |
| 62 | 「我曾申請新增的帳號」區塊的列（`RequestListView.vue` 的 `.mine-row`）在前端測試完全沒有渲染驗證（tests 底下搜尋 `mine-row` 零筆）：門禁卡待發卡顯示、在途「申請處理中」與查看／歷史鈕、AcctOpButtons 權限分支都沒鎖住。既有缺口，申請單列表加收合（2026-10-14）時 code-reviewer 發現，使用者裁示登記 | 2026-10-14 | — |
| 58 | `AppLayout.vue` 第 80 行 router-view 的 key 含 `route.fullPath`（同一畫面換 query 就整頁重建），三頁靠它在換網址參數時重置：申請表單 `RequestFormView.vue`（setup 一次讀完 resubmit／reqType／acctId／acctName、不監看 query；最嚴重——在異動表單點側欄「＋ 新增申請」會留在異動表單，使用者以為填新增、送出的卻是異動）、帳號列表 `?expiring=1`、緊急存取清單 `?state=`。目前沒有測試鎖住，key 被拿掉時三頁同時退化而測試全綠。可在 `tests/AppLayout.spec.ts` 補測試，以「新增 ↔ 異動切換後表單重建」為主案例。第 46 項 S8 回合 2 審查提出，使用者裁示（2026-10-13）只登記 | 2026-10-13 | — |
| 63 | UAT 的 Apache 有 `ProxyPass /infra_manager` 直通後端，等於後端 API 在 Apache 的 port 上對外可達：繞過殼 jar（前端連不到後端的設計、11 MB 上傳上限都失效），只剩後端自己的 token 檢查。要問 UAT 管理者該 port 是否只限內網；正式環境上線前要確認是否沿用同一條規則。後端加網址前綴（2026-10-14）時 code-reviewer 發現，使用者裁示登記 | 2026-10-14 | — |
| 64 | `SETUP.md` 第 26 行寫了郵件主機名稱，與檔頭「不記主機位址」的規則衝突；要移到 PRD 環境設定、改成「向負責人取得」或維持現狀未定。後端加網址前綴（2026-10-14）時 code-reviewer 發現 | 2026-10-14 | — |
| 65 | `im_acct_insp_交接文件.md` 第 22、29 行仍寫後端根路徑 `/api/v1`，後端已加前綴改為 `/infra_manager/api/v1`；與第 21 項同一題：先決定這份初始交付文件是否持續隨程式維護。後端加網址前綴（2026-10-14）時 code-reviewer 發現 | 2026-10-14 | — |
| 67 | 未來不再有英文名（IM_ACCT_USER.USER_EN_NAME）：顯示名目前都是「英文名 (中文名)」，影響登入身分、切換身分、申請單／待辦／緊急存取／附件人名、Email 內文、三種稽核快照欄、DDL 的 NOT NULL、SETUP 的 SQL 與約 14 個測試檔；先盤點、未定改法（刪欄位或不填、顯示改什麼、舊快照怎麼辦），可與第 13 項（顯示名兩種算法不一致）一起處理。使用者 2026-10-15 交代記錄 | 2026-10-15 | [backlog/67-移除英文名.md](backlog/67-移除英文名.md) |
| 69 | 本專案完成後，把原有的 Infra_Manager 整併進本專案；Infra_Manager_Java（原 Infra_Manager 改寫到一半的版本，本專案寄信寫法與信件版面曾比照它）預計不再使用。整併完成後要清掉 DB 中沒有用到的 IM_ 開頭表（IM 即專案名縮寫；那些表是開發 Infra_Manager_Java 時建的，目前沒有任何系統在用；使用者 2026-10-15 裁示維持整併後再清、留作整併設計參考，DROP 前逐表列清單給使用者確認）。現況（2026-10-15 查看）：原 Infra_Manager＝「機房設備異動申請系統」，Node.js（Express＋EJS）、資料存 JSON 檔、port 3200 根路徑，正式機運作中，程式在 `D:\ai\Infra_Manager_OLD`；Infra_Manager_Java 未部署，程式在 `D:\ai\Infra_Manager_Java改寫`（repo `rt-wayne/Infra_Manager_Java`、Java 檔 217 個、套件 `com.mpx.infra_manager_java`、殼 jar 名 `infra_manager_web`，最後程式 commit 2026-10-13，2026-10-16 文件加註停止開發 `3986ec2`）。路線已裁示（使用者 2026-10-16）：①C 以本專案為底，舊 Node 系統（原 Infra_Manager）當需求與驗收依據，從 Infra_Manager_Java 搬業務層、DDL 與測試；②A Java 版 repo 已改名為 `rt-wayne/Infra_Manager_Java` 並封存（唯讀）；③A 舊系統取號漏洞維持現狀、不改正式機 Node 程式，等整併上線。整併範圍細節（詳情檔待確認 ④～⑩）、時程未定。使用者 2026-10-15 交代記錄 | 2026-10-15 | [backlog/69-整併原Infra_Manager.md](backlog/69-整併原Infra_Manager.md) |
| 70 | `PRD.md` 第 11 行與第 660 行（DB 共用硬規則）把 IM_USER、IM_ROLE、IM_USER_ROLE_MAP、IM_APPR*、IM_ATTACH 等表寫成同 schema「既有『機房異動申請』模組」的表；實際上那些是開發 Infra_Manager_Java 時建的、沒有任何系統在用（原 Infra_Manager 資料存 JSON 檔，使用者 2026-10-15 說明）。「本模組不可建立、刪除、修改或引用」的規則目前無害，整併（第 69 項）開工時要改寫。第 69 項 architect 評估時發現 | 2026-10-15 | — |
| 71 | `infra_manager\README.md` 第 10 節（業務程式概要）表格「排程」列只寫「J01、J02 的 cron」，漏了 J03（`infra.manager.job.j03.cron`）；第 68 項批 1 文件同步時 doc-writer 發現，未改 | 2026-10-16 | — |
| 72 | BACKLOG 項目以行號指涉 SETUP 位置已漂移：第 44 項寫「`SETUP.md` 第 71 行」（`npm run build` 那行實為第 69 行）、第 64 項寫「`SETUP.md` 第 26 行」（郵件主機名稱實為第 22 行）；第 68 項批 1 文件同步時 doc-writer 發現，未改 | 2026-10-16 | — |
| 73 | `SETUP.md` 第 8 節「改名部署步驟」第 6 步新增 Apache `ProxyPass` 後沒寫要重載 Apache（例如 `apachectl -t` 檢查語法後重載，實際指令依 UAT 環境），第 9 步「移除舊規則」同樣；漏做時驗證會出現「直連通、經 Apache 404」，負責人會誤以為規則寫錯。第 68 項批 1 階段末 code-reviewer 發現 | 2026-10-16 | — |
| 74 | `SETUP.md` 第 8 節「改完 `host.properties` 要重啟殼 jar」（暗示不用重新打包）與 `infra_manager_web\README.md` 第 399 行「打包在 jar 內，改完要重新建置」互相矛盾；要先向 UAT 負責人確認 UAT 實際讀 jar 內那份還是共用 config 目錄的同名檔，再統一說法。已知線索（2026-10-16）：jar 一律在開發機打包、由使用者人工交給 UAT 負責人；UAT 啟動參數帶 `--spring.config.additional-location=<共用 config 目錄>`，該目錄同名檔**有寫到的鍵**優先於 jar 內的值（逐鍵合併）——若 UAT 共用目錄的 `host.properties` 有那個鍵，改它重啟即可，否則要在開發機改 jar 內那份並重新打包。批 1 之前就存在，第 68 項批 1 階段末 code-reviewer 發現 | 2026-10-16 | — |
| 81 | 帳號列表匯出沒有後端參與，兩個缺口：(1) ACC_EXPORT 只在前端判斷（決定按鈕顯示），沒有匯出 API、後端不檢查——檔案由瀏覽器用 `/accounts` 已查到的資料產生，那支 API 只檢查 ACC_LIST_FULL；目前只有 ACCT_REGISTRY 同時有兩碼，沒有實際落差，日後把兩碼拆給不同角色時要改成後端產檔並檢查 ACC_EXPORT。(2) 匯出不寫稽核紀錄，與 `~/.claude/security.md`「批量資料操作（匯出）必須記錄 audit log」衝突：清冊含特權帳號名稱、所屬系統、使用人且可離線流通，外流時查不到誰、何時匯出；補法是後端加輕量 API，前端匯出前呼叫，寫一筆 IM_ACCT_ADMIN_LOG（誰、何時、篩選條件、筆數）。第 80 項使用者裁示 ④A（前端產檔）時登記 (1)；code-reviewer 提出 (2)，使用者 2026-10-16 裁示維持現狀、併入本項 | 2026-10-16 | — |
| 82 | `PRD.md` 權限碼段「階段 2、3 的權限碼只存在於資料，程式沒有對應功能」與現況不符：EMG_APPLY、EMG_VIEW_ALL、EMG_DECIDE 表上仍標階段 2，但緊急存取已上線（第 46 項）。第 80 項改 ACC_EXPORT 階段時發現，未改 | 2026-10-16 | — |
| 83 | 帳號匯出 xlsx 公式注入殘留：字串一律寫成文字（sharedStrings），開檔不會執行；但帳號名稱、所屬系統、使用人是自由輸入，使用者在 Excel 對以 `=`／`+`／`-`／`@` 開頭的格子按 F2→Enter 或另存 CSV 再開時會被當公式。較窄修法：這幾欄寫成 `{ value, format: '@' }`（`write-excel-file` 的 `row.js:76` 支援），CSV 路徑仍擋不住。第 80 項 code-reviewer 提出 | 2026-10-16 | — |
| 84 | 帳號匯出在背景執行緒被擋時會卡在「匯出中…」不報錯：fflate 的 `wk` 只掛 `onmessage` 沒有 `onerror`，CSP 擋下時 Promise 永不結束（test-runner 實測對照組 25 秒逾時、無錯誤）。目前殼 jar CSP 已放行，只在另有更嚴的 CSP（例如 UAT Apache 另加標頭、公司瀏覽器原則）時發生。建議 `exportAccounts` 外包約 30 秒逾時（`Promise.race`）並 toast；同時修正 `SecurityHeadersConfig.java` 註解、PRD「安全標頭」段寫的「匯出失敗」（實際是卡住），以及 `AccountExport.spec.ts` 以 reject 模擬失敗的不真實案例。第 80 項 code-reviewer 提出 | 2026-10-16 | — |
| 85 | 申請表單必填星號測試只涵蓋新增、門禁卡新增、停用三種；異動（只標未鎖的申請單位、使用人、原因、有效期限）、門禁卡異動（門禁範圍有、卡號無）、重新啟用、新增單退件重送（類別不標、名稱與系統要標）、刪除都沒測；預期陣列手寫、未從 `missingFields` 推導，「與送出檢查一致」本身沒有被鎖住。第 80 項 code-reviewer 提出 | 2026-10-16 | — |
| 86 | 帳號列表畫面的帳號名稱、所屬系統、到期日三欄（第 80 項改用 `utils/accountExport.ts` 共用函式）沒有任何畫面測試，「畫面與檔案同一套文字」沒有測試守住（測試只驗檔案內容）。第 80 項 code-reviewer 提出 | 2026-10-16 | — |
| 87 | 必填星號 `<span class="req-mark" title="必填">*</span>` 的 `title` 多半不會被螢幕報讀器讀出，`app.css` 註解「供報讀必填」不精確；可改星號加 `aria-hidden="true"`＋視覺隱藏的「必填」文字，或在輸入框加 `aria-required`。內部系統，優先度低。第 80 項 code-reviewer 提出 | 2026-10-16 | — |
| 88 | 「* 為必填欄位」圖例位置不一致：緊急存取表單（`EmergencyFormView.vue:25`）放在區塊內 `<h3>` 下方，申請表單、巡檢表單放在整頁最上方；PRD 寫「三張表單上方」。第 80 項 code-reviewer 提出 | 2026-10-16 | — |
| 90 | 測試帳號角色紀錄互相矛盾：BACKLOG 第 23 項寫測試帳號 2410305 已加 8 個角色與 8 類執行人（使用者裁示不移除），但 PRD 測試使用者表（約第 1007 行）寫 Wayne、Brian 的 8 個角色指派都是 STATUS＝0；要以測試 DB 實查為準更正其一。影響第 89 項（角色權限維護介面）實機測試要拿誰試。第 89 項 architect 規劃時發現 | 2026-10-16 | — |
| 91 | 程式連線帳號 AP_USER 對系統管理軌跡表 IM_ACCT_ADMIN_LOG 有 UPDATE、DELETE 權限（使用者 2026-10-19 實查測試 DB），軌跡設計為只增不改，但程式帳號可竄改或刪除稽核紀錄；可收回這兩項權限（需 DBA，正式環境也要查）。第 89 項 F8 查證時發現 | 2026-10-19 | — |
| 92 | 名單維護 DENY 軌跡的被擋單號是「每個權限碼各取 50 筆」再合併（`HolderGuard.java:98-99`、`blockedDocIds`），一個角色有多個卡單碼時會超過 50 筆，最後靠 3900 bytes 逐筆丟棄兜住；`AcctConst` 註解、PRD 軌跡表、計畫檔都寫「最多 50 筆」。改法：`blockedDocIds` 合併時截在 50 筆（第三頁另有「每碼 20 筆」規格，口徑分開較不混）；`AdminLogWriterTest` 補多碼案例。第 89 項階段 1 code-reviewer 提出 | 2026-10-19 | — |
| 93 | 全域鎖 `RoleAdminDao.lockGlobal()` 丟掉查詢結果不檢查筆數；GENERAL 列若被誤刪或改名，`FOR UPDATE` 鎖不到任何列也不報錯，互斥保護悄悄失效。改法：查無列時丟 IllegalStateException（寧可 500 也不放行）。第 89 項階段 1 code-reviewer 提出 | 2026-10-19 | — |
| 94 | 名單維護兩支 GET（/admin/roles、/admin/users）被拒時既不寫 DENY 軌跡（合理，沒有查詢類動作碼）也沒有應用 log，`GlobalExceptionHandler.handleApi` 只對 5xx 記 log，違反 security.md A09「權限拒絕必須記錄 log」。改法：比照 `RoleAdminService.requirePermOrDeny` 補 `log.info`。範圍外：全系統 `perm.require`／`requireAny` 被拒都有同一缺口，可在 `PermissionService.require` 統一補一行。第 89 項階段 1 code-reviewer 提出 | 2026-10-19 | — |
| 95 | `AdminMember.grantDate` 缺 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`，會輸出 ISO 格式（中間帶 T），與 `Acct`、`AcctHist`、`AcctAttach` 等既有回應 model 不一致；階段 4 前端接之前補上。第 89 項階段 1 code-reviewer 提出 | 2026-10-19 | — |
| 96 | 在途申請單查詢 `RoleAdminDao.FIND_IN_FLIGHT_BY_STAGE_PERM` 只以 `REQ_STATE_CODE IN ('REVIEW','VERIFY') AND S.PERM_ID = :permId` 篩選，沒過濾關卡 `S.STATUS = 1`、也沒核對 REVIEW 配關卡 2～5／VERIFY 配關卡 7，比 `PermissionService.canHandleNow` 寬；只有資料異常的單會被多擋（本來就沒人能處理）。改法：至少加 `S.STATUS = 1`。第 89 項階段 1 code-reviewer 提出 | 2026-10-19 | — |
| 97 | `USER_ID_MAX = 30` 在 `RoleAdminService`、`AdminLogWriter`、`AuthService` 各定義一次；`RoleAdminService.requirePermOrDeny` 以比對動作碼決定 JSON 鍵叫 roleId 還是 catgId，階段 2 加執行人對照時要同步。第 89 項階段 1 code-reviewer 提出 | 2026-10-19 | — |
| 99 | SETUP 第 6 節開頭叫讀者 DB 直接改前用「查每個人實際生效的角色」確認類別不會沒人處理，但那句只查角色、不查執行人對照。補一句唯讀查詢：依 CATG_ID 計啟用中執行人數（口徑同 `RoleAdminDao.COUNT_EXECUTORS`），加一句列出停在關卡 6（EXEC、6）的單號。第 89 項階段 2 code-reviewer 提出（S2） | 2026-10-19 | — |
| 100 | 無權限者能對 4 支名單寫入 API 無限次送 POST，每次在永久保留的 IM_ACCT_ADMIN_LOG 寫一筆 DENY，連同最多 500 字自訂原因，可灌爆軌跡、淹沒真正稽核紀錄；第三頁上線會再多 1 支。現行「403 DENY 有帶原因就存」是計畫已拍板設計，改動要推翻原裁示。方向：同一人一定時間內 DENY 只寫一筆，或 NO_PERM 不存原因只存長度。code-reviewer 建議第三頁（階段 3）上線前請使用者判斷是否處理。第 89 項階段 2 code-reviewer 提出（S3） | 2026-10-19 | — |
| 101 | `RoleAdminServiceTest` 沒鎖住兩碼分界：「只有 SYS_ROLE 呼叫 grantExecutor／revokeExecutor 要 403＋DENY」「只有 SYS_EXECUTOR 呼叫 grantRole 要 403」；revokeExecutor 的 403 寫 DENY、移除路徑類別不合法 400 也沒單獨案例。第 89 項階段 2 code-reviewer 提出（S4） | 2026-10-19 | — |
| 102 | `RoleAdminDao.FIND_IN_FLIGHT_EXEC_BY_CATG` 對 IM_ACCT_APPR_STAGE 用內部 JOIN（只為取關卡名）；關卡 6 那列若不見，在途回空，移除最後一位執行人會被放行。改 LEFT JOIN＋`NVL(S.STAGE_NAME, '執行回報')`。資料壞掉時往放行錯的性質，機率低。第 89 項階段 2 code-reviewer 提出（S5） | 2026-10-19 | — |
| 103 | 帳號類別 STATUS＝0（軟刪除）時，該類別的執行人不出現在 /admin/executors、移除 API 回 400，但 `PermDao.FIND_EXEC_CATGS`／`isExecutor` 不看類別狀態，那些人仍保有執行人身分（能處理該類別關卡 6 單、看到到期帳號）。目前 8 類皆 STATUS＝1。可讓 revoke 允許 STATUS＝0 類別，或在 PRD／SETUP 註明「軟刪除類別前先移除其執行人」。第 89 項階段 2 code-reviewer 提出（S6） | 2026-10-19 | — |
| 104 | `RoleAdminService.executors()` 呼叫 `codeService.getCodes()`，每次多查資產分類與關卡兩句，實際只用類別；CodeService 加只回類別的方法。第 89 項階段 2 code-reviewer 提出（S7） | 2026-10-19 | — |
| 105 | `db/infra_manager_89_role_admin.sql` 檔尾第二句只核對 5 個欄位 COMMENT，漏了 IM_EXEC_MAP 的 CREATE_BY／UPDATE_DATE／UPDATE_BY 與 IM_ACCT_ROLE_USER 的 UPDATE_DATE／UPDATE_BY；擴成 10 欄、期望值改 10 列（中文 COMMENT 無聲變亂碼是檔頭警告的失敗模式）。第 89 項階段 2 code-reviewer 提出（S8） | 2026-10-19 | — |
| 79 | UAT 的附件根目錄（`infra.manager.attach.dir` 的值）目前設在哪、要不要隨改名搬到新目錄名；DB 的 `IM_ACCT_ATTACH.FILE_PATH` 存相對路徑，搬檔＋改設定值即可、不用動資料。改名部署時 SETUP 第 8 節寫「值可以維持原路徑」，不搬也能運作。第 68 項計畫待確認 2，結案時移入 | 2026-10-15 | — |

## 已拍板不做

| ID | 摘要 | 決定日 | 理由（一句） |
|----|------|--------|------------|
| 15 | 帳號展延功能 | 交付時已定（SA／交接文件） | 延長以重新提新增申請，或以「修改」申請調整到期日即可 |
| 66 | 收緊一般人員對既有帳號的異動申請範圍（曾評估：限原新增申請人、四種異動全限、申請單列表查詢區塊只列範圍內帳號） | 2026-10-15 | 維持 SA 決策 D29：知道完整帳號名稱即可提異動，風險由關卡 2～5 簽核與關卡 6 執行人承擔；PRD「5. 帳號申請」已明寫 |

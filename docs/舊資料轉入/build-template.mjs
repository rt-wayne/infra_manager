// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-28
// 變更說明: 新增：產生舊資料轉入 Excel 範本（BACKLOG 第 133 項階段 1，範本格式改 B：直接產生 .xlsx）
//           用法（專案根目錄）：node "docs/舊資料轉入/build-template.mjs"
//           產出：同目錄 舊資料轉入範本.xlsx
//           套件：借用前端已安裝的 write-excel-file（不另裝套件）；它沒有下拉選單功能，
//           以其「feature」擴充介面在工作表 XML 插入 <dataValidations>（裁示 ②A）
//           代碼中文文字依 labels.ts、IM_ACCT_CATG、IM_ASSET_CLASS（初始資料），改代碼時兩邊一起改
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-28
// 變更說明: 階段 1 收尾審查必須修正：(1) 工號、卡號、電話 8 欄第 2 列起預設文字格式（keepZero；原 text 屬性沒有作用，移除）；
//           (2) 原新增申請人工號改「△ 知道就填」，查不到留空、不硬填別人（裁示 A）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-28
// 變更說明: 使用者確認舊資料沒有申請單：刪「申請單」工作表與代碼對照的申請類型欄，剩三個工作表（填寫說明、帳號、代碼對照）；
//           帳號表第 1 欄「帳號對照編號」改名「列編號」，用途改為第 2 階段回報錯誤時指出哪一列（裁示 A）；
//           帳號表欄位位置不變，已交給彙整人的舊版範本填的內容可直接沿用
// ============================================================
import { fileURLToPath } from 'node:url'

const PKG = '../../infra_manager_web/infra_manager_web_frontend/node_modules/write-excel-file/'
const { default: writeExcelFile } = await import(new URL(PKG + 'node/index.js', import.meta.url))
const { insertElementMarkupAccordingToOrderOfSiblings, getOrderOfSiblings, sanitizeTextContent, getCellAddress } =
  await import(new URL(PKG + 'utility/index.js', import.meta.url))

const OUT = fileURLToPath(new URL('./舊資料轉入範本.xlsx', import.meta.url))
const CODE_SHEET = '代碼對照'
// 下拉選單套用到第 2～2000 列
const LAST_ROW = 2000

// ---------- 代碼對照（下拉選單來源；欄位順序即代碼對照工作表的欄位順序） ----------
const ASSET = [
  ['H01 伺服器－與營運直接相關', '存取控制：只有獲得授權的人員，可以進入管制區域。'],
  ['H02 伺服器－與營運間接相關', '存取控制：只有獲得授權的人員，可以進入管制區域。'],
  ['H03 伺服器－與 IT 管理性作業相關', '存取控制：只有獲得授權的人員，可以進入管制區域。'],
  ['H04 測試設備', '得考量有適當之實體保護措施，限制授權人員方可進入管制區域。'],
  ['H05 IT 基礎設備－與營運直接相關', '診斷埠／組態埠之使用宜採帳號密碼辨識、限定來源位置、通訊加密等保護措施。'],
  ['H06 IT 基礎設備－與營運間接相關', '得考量適當的實體保護措施，必要時宜使用帳號密碼辨識使用者身分。'],
  ['H08 儲存設備', '得考量適當的實體保護措施。'],
  ['H09 支援設備', '得視資產特性採取適當之實體保護措施，例如僅經授權之人員可調整監視器角度。'],
  ['S01 與營運直接相關軟體', '存取權限賦予應與角色職責相符，且應至少每半年清查帳號與權限一次，交由權責單位覆核。'],
  ['S02 與營運間接相關軟體', '將可存取的帳號及權限減到最少或最小，賦予應與角色職責相符。'],
  ['S03 與 IT 管理性作業相關軟體', '須維持各系統之帳號權限清單，並於人員調離職時及時更新清單。'],
  ['S04 IT 基礎相關軟體', '須維持各系統之帳號權限清單，並於人員調離職時及時更新清單。'],
  ['S05 測試軟體／辦公室套裝軟體', '管理測試與套裝軟體的使用授權。']
]
// 資產分類只列可選的 13 個（H07、H10 IS_SELECTABLE＝0 不列）
const CODE_COLUMNS = [
  { key: 'CATG', header: '帳號類別', width: 16, values: ['主機帳號', '資料庫帳號', '應用系統帳號', '服務帳號', '網路設備帳號', '廠商/委外帳號', '憑證/金鑰', '機房門禁卡'] },
  { key: 'ACCT_STATE', header: '帳號狀態', width: 11, values: ['已核准', '已停用'] },
  { key: 'EXPIRY', header: '有效期限類型', width: 13, values: ['永久有效', '指定到期日'] },
  { key: 'DOOR', header: '門禁權限範圍', width: 23, values: ['僅測試區（第一道門）', '機房完整權限（兩道門）'] },
  { key: 'YN', header: '是否', width: 7, values: ['是', '否'] },
  { key: 'LEVEL', header: '權限範圍', width: 18, values: ['唯讀', '操作', '管理員（完全控制）'] },
  { key: 'ASSET', header: '資產分類', width: 30, values: ASSET.map((a) => a[0]) },
  { key: null, header: '資產分類的管控說明（參考用）', width: 62, values: ASSET.map((a) => a[1]) }
]

/** 下拉選單 key → 代碼對照工作表的絕對範圍，例如 '代碼對照'!$A$2:$A$9 */
function listRange(key) {
  const idx = CODE_COLUMNS.findIndex((c) => c.key === key)
  if (idx < 0) throw new Error('未定義的下拉選單：' + key)
  const col = getCellAddress(0, idx).replace(/\d+$/, '')
  return `'${CODE_SHEET}'!$${col}$2:$${col}$${CODE_COLUMNS[idx].values.length + 1}`
}

// ---------- 欄位定義（mark：req＝必填、cond＝視情況必填、opt＝選填、none＝不轉入） ----------
// keepZero：工號、卡號、電話這類數字字串欄，第 2 列起預設文字格式，避免 Excel 吃掉開頭的 0
// 或把超過 15 位的卡號轉成科學記號（第 133 項階段 1 審查必須修正 1）
const ACCT_FIELDS = [
  { header: '列編號', mark: 'req', width: 10, need: '＊ 必填', desc: '彙整人自己編的流水號，不會轉進系統；轉入前檢查發現錯誤時，用它指出是哪一列（排序、刪列後也對得上）。同一份檔案內不可重複', example: 'A001' },
  { header: '帳號類別', mark: 'req', width: 15, list: 'CATG', need: '＊ 必填', desc: '下拉選單選擇（8 種，見「代碼對照」）', example: '主機帳號' },
  { header: '帳號名稱（門禁卡填卡號）', name: '帳號名稱', mark: 'req', width: 20, keepZero: true, need: '＊ 必填', desc: '登入用的帳號；機房門禁卡填卡號（上限 100 字）', example: 'oracle' },
  { header: '所屬系統／設備名稱（門禁卡免填）', name: '所屬系統／設備名稱', mark: 'cond', width: 23, need: '△ 門禁卡以外必填', desc: '帳號屬於哪個系統或哪台設備（上限 100 字）；門禁卡免填，系統自動帶「機房門禁管制系統」', example: '財務 Oracle 主機' },
  { header: '帳號狀態', mark: 'req', width: 11, list: 'ACCT_STATE', need: '＊ 必填', desc: '已核准＝使用中；已停用＝暫停使用但帳號還保留。已刪除的帳號不轉入', example: '已核准' },
  // 第 133 項階段 1 審查必須修正 2、裁示 A：查不到原申請人就留空，不硬填別人
  { header: '原新增申請人工號（知道才填）', name: '原新增申請人工號', mark: 'cond', width: 18, keepZero: true, need: '△ 知道就填', desc: '當初申請新增這個帳號的人；轉入後，這個人在「我曾申請新增的帳號」會看到這個帳號，也會收到它的到期通知信。查不到就留空，不要填目前使用人或自己', example: '1234567' },
  { header: '實際使用人／保管人', mark: 'req', width: 16, need: '＊ 必填', desc: '實際在用這個帳號（或保管這張卡）的人的姓名（上限 50 字）', example: '王小明' },
  { header: '有效期限類型', mark: 'req', width: 13, list: 'EXPIRY', need: '＊ 必填', desc: '永久有效／指定到期日', example: '指定到期日' },
  { header: '到期日', mark: 'cond', width: 13, need: '△ 指定到期日時必填', desc: '永久有效時不填。已過期或 7 天內到期的帳號，轉入後會寄通知信（見「四、容易出錯的地方」）', example: '2026/12/31' },
  { header: '門禁權限範圍（門禁卡必填）', name: '門禁權限範圍', mark: 'cond', width: 23, list: 'DOOR', need: '△ 門禁卡必填', desc: '僅測試區（只能刷第一道門）／機房完整權限（第一、二道門都能刷）；其他類別不填', example: '僅測試區（第一道門）' },
  { header: '是否特權帳號', mark: 'opt', width: 12, list: 'YN', need: '選填', desc: 'root、Administrator、sa、enable 模式等選「是」；空白視為「否」。特權帳號一律列入定期權限審查', example: '是' },
  { header: '資產分類', mark: 'opt', width: 30, list: 'ASSET', need: '選填', desc: '下拉選單選擇（13 種，見「代碼對照」）；空白＝未分類；門禁卡不填', example: 'H01 伺服器－與營運直接相關' },
  { header: '使用人所屬單位', mark: 'opt', width: 16, need: '選填', desc: '上限 50 字', example: '資訊處' },
  { header: '使用人聯絡方式', mark: 'opt', width: 17, keepZero: true, need: '選填', desc: '分機或手機（上限 100 字）；門禁卡不填', example: '分機 1234' },
  { header: '使用人 Email（門禁卡才填）', name: '使用人 Email', mark: 'opt', width: 24, need: '選填', desc: '只有門禁卡填（上限 100 字）；其他類別不填', example: '（門禁卡才填）' },
  { header: '權限範圍', mark: 'opt', width: 18, list: 'LEVEL', need: '選填', desc: '唯讀／操作／管理員（完全控制）；門禁卡不填（系統固定為門禁通行權）', example: '唯讀' },
  { header: '存取對象', mark: 'opt', width: 24, need: '選填', desc: '這個帳號可以存取哪些資料或功能（上限 200 字）；門禁卡不填', example: '財務模組報表' },
  { header: '申請原因', mark: 'opt', width: 32, need: '選填', desc: '最近一次申請的原因（上限 500 字）', example: '月結報表查詢' },
  { header: '備註（不轉入）', mark: 'none', width: 26 }
]

// ---------- 樣式 ----------
const RED = '#C00000'
const ORANGE = '#C65911'
const HEAD_BG = '#D9E1F2'
const MARK_PREFIX = { req: '＊', cond: '△', opt: '', none: '' }
const MARK_COLOR = { req: RED, cond: ORANGE }

function headerCell(f) {
  const none = f.mark === 'none'
  return {
    value: MARK_PREFIX[f.mark] + f.header,
    fontWeight: none ? undefined : 'bold',
    fontStyle: none ? 'italic' : undefined,
    textColor: none ? '#808080' : MARK_COLOR[f.mark],
    backgroundColor: none ? '#EDEDED' : HEAD_BG,
    bottomBorderStyle: 'thin',
    wrap: true,
    alignVertical: 'center'
  }
}

function dataSheet(sheet, fields, headerHeight) {
  const header = fields.map(headerCell)
  header[0].height = headerHeight
  const validations = []
  fields.forEach((f, i) => {
    if (!f.list) return
    const col = getCellAddress(0, i).replace(/\d+$/, '')
    validations.push({ sqref: `${col}2:${col}${LAST_ROW}`, formula: listRange(f.list) })
  })
  // 第 2 列起：keepZero 欄放「空值＋文字格式」的儲存格，其餘欄不產生儲存格（日期欄維持一般格式，才會被認成日期）
  const lastKeep = fields.reduce((n, f, i) => (f.keepZero ? i : n), -1)
  const blankRow = fields.slice(0, lastKeep + 1).map((f) => (f.keepZero ? { value: null, type: String, format: '@' } : null))
  const data = [header]
  for (let r = 2; r <= LAST_ROW; r++) data.push(blankRow)
  return { sheet, data, columns: fields.map((f) => ({ width: f.width })), stickyRowsCount: 1, validations }
}

// ---------- 工作表 1：填寫說明 ----------
const SPAN = 4
const line = (text, style = {}) => [{ value: text, columnSpan: SPAN, ...style }, null, null, null]
const blank = () => [null]
const sect = (text) => [{ value: text, fontWeight: 'bold', fontSize: 12, textColor: '#1F4E78' }]
const warn = (text) => line(text, { textColor: RED })
const top = { wrap: true, alignVertical: 'top' }
const needStyle = (mark) => (MARK_COLOR[mark] ? { textColor: MARK_COLOR[mark] } : {})
const fieldRow = (name, mark, need, desc, example) => [
  { value: name, ...top },
  { value: need, ...top, ...needStyle(mark) },
  { value: desc, ...top },
  { value: example, ...top }
]

const guide = [
  [{ value: '帳號權限管理系統　舊資料轉入範本', fontWeight: 'bold', fontSize: 14, height: 24 }],
  blank(),
  sect('一、這份表要做什麼'),
  line('把系統上線前就已經存在的帳號，轉進帳號權限管理系統。轉入後：'),
  line('・「帳號」工作表的每一列，會出現在系統的「帳號列表」。'),
  line('・每位同仁在「我曾申請新增的帳號」，會看到「原新增申請人工號」是自己的帳號。'),
  line('・舊資料沒有申請單，所以轉入的帳號不會出現在任何人的「我的申請單」；上線後新送的單照常顯示。'),
  blank(),
  sect('二、怎麼填'),
  line('1. 一個帳號填一列在「帳號」工作表，自己編一個「列編號」（例如 A001、A002⋯），同一份檔案內不可重複。'),
  line('2. 欄位標題的符號：＊（紅色）＝必填；△（橘色）＝視情況必填，條件見下方欄位說明；沒有符號＝選填。'),
  line('3. 有下拉選單的欄位請用選單選，不要自己打字（打錯字轉入時會對不到）。日期請填 2026/12/31 這種格式。'),
  line('4. 工號請填與登入系統相同的工號。工號、卡號、電話照原樣打，開頭的 0 會保留。'),
  line('5. 「備註（不轉入）」欄是給彙整人自己記錄用，不會轉進系統。'),
  blank(),
  sect('三、不要填進來的資料'),
  line('・已經刪除、不再存在的帳號。'),
  blank(),
  sect('四、容易出錯的地方'),
  warn('・到期日已經過了的帳號：轉入後隔天，系統排程會把它改成「已逾期」並寄出逾期通知信；7 天內會到期的帳號會寄到期提醒信。'),
  warn('・「原新增申請人工號」查不到就留空，不要填目前使用人或自己：填了誰，誰就會在「我曾申請新增的帳號」看到這個帳號、收到它的到期信。'),
  warn('　留空的帳號只在帳號列表看得到，到期信也不會寄給申請人。'),
  warn('・機房門禁卡：帳號名稱填卡號；所屬系統、資產分類、使用人聯絡方式、權限範圍、存取對象都不用填（系統自動帶固定值）；門禁權限範圍必填。'),
  blank(),
  sect('五、欄位說明'),
  ['欄位', '必填', '說明', '範例'].map((v) => ({ value: v, fontWeight: 'bold', backgroundColor: HEAD_BG, bottomBorderStyle: 'thin' })),
  ...ACCT_FIELDS.filter((f) => f.mark !== 'none').map((f) => fieldRow(f.name || f.header, f.mark, f.need, f.desc, f.example)),
  fieldRow('備註（不轉入）', 'none', '不需要', '彙整人自己的紀錄（例如問了誰、還缺什麼），不會轉進系統', '待確認到期日')
]

// ---------- 工作表 3：代碼對照 ----------
const codeRows = Math.max(...CODE_COLUMNS.map((c) => c.values.length))
const codeData = [CODE_COLUMNS.map((c) => ({ value: c.header, fontWeight: 'bold', backgroundColor: HEAD_BG, bottomBorderStyle: 'thin' }))]
for (let r = 0; r < codeRows; r++) codeData.push(CODE_COLUMNS.map((c) => (c.values[r] === undefined ? null : c.values[r])))

const sheets = [
  { sheet: '填寫說明', data: guide, columns: [{ width: 27 }, { width: 22 }, { width: 80 }, { width: 28 }], validations: [] },
  dataSheet('帳號', ACCT_FIELDS, 33),
  { sheet: CODE_SHEET, data: codeData, columns: CODE_COLUMNS.map((c) => ({ width: c.width })), stickyRowsCount: 1, validations: [] }
]

// ---------- 下拉選單：在各工作表 XML 插入 <dataValidations> ----------
const validationsBySheet = new Map(sheets.map((s) => [s.sheet, s.validations]))
const dataValidationFeature = {
  files: {
    transform: {
      'xl/worksheets/sheet{id}.xml': {
        transform(xml, sheetOptions) {
          const list = validationsBySheet.get(sheetOptions.sheet) || []
          if (list.length === 0) return xml
          const inner = list.map((v) =>
            `<dataValidation type="list" allowBlank="1" showErrorMessage="1" errorTitle="請用下拉選單" error="請從下拉選單選擇，不要自己打字" sqref="${v.sqref}">` +
            `<formula1>${sanitizeTextContent(v.formula)}</formula1></dataValidation>`
          ).join('')
          const markup = `<dataValidations count="${list.length}">${inner}</dataValidations>`
          return insertElementMarkupAccordingToOrderOfSiblings(xml, markup,
            getOrderOfSiblings('xl/worksheets/sheet{id}.xml', 'worksheet'), 'worksheet')
        }
      }
    }
  }
}

await writeExcelFile(
  sheets.map(({ validations, ...s }) => s),
  { fontFamily: 'Microsoft JhengHei', fontSize: 11, features: [dataValidationFeature] }
).toFile(OUT)

const total = sheets.reduce((n, s) => n + s.validations.length, 0)
console.log(`已產生：${OUT}`)
console.log(`工作表：${sheets.map((s) => `${s.sheet}（${s.columns.length} 欄）`).join('、')}；下拉選單 ${total} 組`)

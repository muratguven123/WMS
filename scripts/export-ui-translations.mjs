import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const content = fs.readFileSync(
  path.join(__dirname, "../wms-ui/src/i18n/translations.ts"),
  "utf8",
);
const trMatch = content.match(/export const TR: Dict = \{([\s\S]*?)\};\s*export const EN/);
const enMatch = content.match(/export const EN: Dict = \{([\s\S]*?)\};\s*export const BUILT_IN/);

function parseDict(block) {
  const dict = {};
  const re = /"([^"]+)":\s*"((?:[^"\\]|\\.)*)"/g;
  let m;
  while ((m = re.exec(block)) !== null) {
    dict[m[1]] = m[2].replace(/\\n/g, "\n");
  }
  return dict;
}

const tr = parseDict(trMatch[1]);
const en = parseDict(enMatch[1]);
const keys = {};
for (const k of Object.keys(tr)) {
  keys[k] = { tr: tr[k], en: en[k] || tr[k] };
}

const out = path.join(
  __dirname,
  "../wms-localization-service/src/main/resources/i18n/ui-translations.json",
);
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, JSON.stringify({ keys }, null, 2));
console.log(`Exported ${Object.keys(keys).length} keys to ${out}`);
